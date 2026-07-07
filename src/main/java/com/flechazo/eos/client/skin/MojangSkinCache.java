package com.flechazo.eos.client.skin;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.render.SurvivorPlayerSkin;
import com.flechazo.hkt.Either;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.Unit;
import com.flechazo.hkt.Validated;
import com.flechazo.hkt.business.control.ValidatedNel;
import com.flechazo.hkt.business.core.Attempts;
import com.flechazo.hkt.business.data.NonEmptyList;
import com.flechazo.hkt.business.effect.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

// 此类用于测试 optics-java 的初步业务层 API
public final class MojangSkinCache {
    private MojangSkinCache() {
    }

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "eos-mojang-skin");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<UUID, SurvivorPlayerSkin> LOADED = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static Validated<NonEmptyList<UUID>, Unit> failed = Validated.valid(Unit.INSTANCE);
    private static boolean retryScheduled;

    public static Maybe<SurvivorPlayerSkin> getOrRequest(UUID uuid) {
        if (uuid == null) return Maybe.none();
        SurvivorPlayerSkin existing = LOADED.get(uuid);
        if (existing != null) return Maybe.some(existing);
        request(uuid);
        return Maybe.none();
    }

    private static void request(UUID uuid) {
        if (uuid == null || !IN_FLIGHT.add(uuid)) return;
        load(uuid)
                .peekFailure(err -> recordFailure(uuid))
                .unsafeRunAsync(EXECUTOR)
                .whenComplete((ignored, err) -> {
                    IN_FLIGHT.remove(uuid);
                    scheduleRetryIfNeeded();
                });
    }

    private static Task<Unit> load(UUID uuid) {
        return readFromDisk(uuid)
                .flatMap(cached -> cached.isDefined()
                        ? registerDynamic(uuid, cached.get())
                        : fetchFromMojang(uuid)
                                .flatMap(assets -> writeToDisk(uuid, assets)
                                        .recover(err -> Unit.INSTANCE)
                                        .map(ignored -> assets))
                                .flatMap(assets -> registerDynamic(uuid, assets)));
    }

    private static synchronized void recordFailure(UUID uuid) {
        failed = failed.combine(ValidatedNel.invalid(uuid), NonEmptyList.semigroup());
    }

    private static void scheduleRetryIfNeeded() {
        if (!IN_FLIGHT.isEmpty()) return;
        synchronized (MojangSkinCache.class) {
            if (retryScheduled || failed.isValid()) return;
            List<UUID> pending = failed.error().toList();
            if (pending.isEmpty()) return;
            retryScheduled = true;
            EchoesofSurvival.LOGGER.warn(
                    "Mojang skin failed for {} UUID(s), retrying in 5 min: {}",
                    pending.size(),
                    pending);
            EXECUTOR.schedule(MojangSkinCache::retryFailures, RETRY_DELAY.toMinutes(), TimeUnit.MINUTES);
        }
    }

    private static void retryFailures() {
        List<UUID> pending;
        synchronized (MojangSkinCache.class) {
            pending = failed.isInvalid() ? List.copyOf(new LinkedHashSet<>(failed.error().toList())) : List.of();
            failed = Validated.valid(Unit.INSTANCE);
            retryScheduled = false;
        }
        for (UUID uuid : pending) {
            if (!LOADED.containsKey(uuid)) {
                request(uuid);
            }
        }
    }

    private static Task<SkinAssets> fetchFromMojang(UUID uuid) {
        String id = uuid.toString().replace("-", "");
        String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + id;
        return getString(profileUrl)
                .flatMap(json -> extractTexturesFromProfileJson(json).fold(
                        error -> Task.failed(new SkinLoadException(uuid, error)),
                        textures -> downloadAssets(uuid, textures)));
    }

    private static Task<SkinAssets> downloadAssets(UUID uuid, ProfileTextures textures) {
        return downloadRequired(textures.skinUrl(), uuid, "skin")
                .flatMap(skinBytes -> downloadOptional(textures.capeUrl())
                        .parZipWith(downloadOptional(textures.elytraUrl()), (capeBytes, elytraBytes) -> new SkinAssets(
                                skinBytes,
                                textures.model(),
                                capeBytes,
                                elytraBytes)));
    }

    private static Task<String> getString(String url) {
        return Task.async(() -> HTTP.sendAsync(get(url), HttpResponse.BodyHandlers.ofString()))
                .flatMap(response -> successful(response)
                        ? Task.pure(response.body())
                        : Task.failed(new IllegalStateException("HTTP " + response.statusCode() + " for " + url)));
    }

    private static Task<byte[]> downloadRequired(String url, UUID uuid, String name) {
        if (isBlank(url)) {
            return Task.failed(new SkinLoadException(uuid, "missing " + name + " url"));
        }
        return Task.async(() -> HTTP.sendAsync(get(url), HttpResponse.BodyHandlers.ofByteArray()))
                .flatMap(response -> successful(response)
                        ? Maybe.ofNullable(response.body()).filter(bytes -> bytes.length > 0)
                                .toEither(() -> new SkinLoadException(uuid, "empty " + name + " data"))
                                .fold(Task::failed, Task::pure)
                        : Task.failed(new SkinLoadException(uuid, "HTTP " + response.statusCode() + " for " + name)));
    }

    private static Task<Maybe<byte[]>> downloadOptional(String url) {
        if (isBlank(url)) {
            return Task.pure(Maybe.none());
        }
        return Task.async(() -> HTTP.sendAsync(get(url), HttpResponse.BodyHandlers.ofByteArray()))
                .map(response -> successful(response)
                        ? Maybe.ofNullable(response.body()).filter(bytes -> bytes.length > 0)
                        : Maybe.<byte[]>none())
                .recover(err -> Maybe.none());
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();
    }

    private static boolean successful(HttpResponse<?> response) {
        int status = response.statusCode();
        return status >= 200 && status < 300;
    }

    private static Either<String, ProfileTextures> extractTexturesFromProfileJson(String json) {
        if (isBlank(json)) {
            return Either.left("blank profile json");
        }
        return Attempts.either("invalid profile json", () -> JsonParser.parseString(json).getAsJsonObject())
                .flatMap(MojangSkinCache::findEncodedTextures)
                .flatMap(MojangSkinCache::decodeTextures);
    }

    private static Either<String, ProfileTextures> decodeTextures(String encoded) {
        return Attempts.either("invalid textures payload", () -> {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return JsonParser.parseString(decoded).getAsJsonObject();
        }).flatMap(MojangSkinCache::readProfileTextures);
    }

    private static Either<String, String> findEncodedTextures(JsonObject root) {
        JsonArray props = root.getAsJsonArray("properties");
        if (props == null) {
            return Either.left("profile json has no properties");
        }
        for (JsonElement el : props) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            JsonElement name = obj.get("name");
            JsonElement value = obj.get("value");
            if (name == null || name.isJsonNull() || value == null || value.isJsonNull()) continue;
            if (!"textures".equals(name.getAsString())) continue;
            String encoded = value.getAsString();
            if (encoded != null && !encoded.isBlank()) {
                return Either.right(encoded);
            }
        }
        return Either.left("profile json has no textures property");
    }

    private static Either<String, ProfileTextures> readProfileTextures(JsonObject texturesRoot) {
        JsonObject textures = texturesRoot.getAsJsonObject("textures");
        if (textures == null) {
            return Either.left("textures payload has no textures object");
        }
        JsonObject skin = textures.getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) {
            return Either.left("textures payload has no skin url");
        }
        JsonElement skinUrl = skin.get("url");
        if (skinUrl == null || skinUrl.isJsonNull() || skinUrl.getAsString().isBlank()) {
            return Either.left("textures payload has no skin url");
        }

        String model = "default";
        JsonObject metadata = skin.getAsJsonObject("metadata");
        if (metadata != null && metadata.has("model") && !metadata.get("model").isJsonNull()) {
            model = metadata.get("model").getAsString();
        }

        JsonObject cape = textures.getAsJsonObject("CAPE");
        JsonObject elytra = textures.getAsJsonObject("ELYTRA");
        String capeUrl = cape != null && cape.has("url") && !cape.get("url").isJsonNull()
                ? cape.get("url").getAsString()
                : "";
        String elytraUrl = elytra != null && elytra.has("url") && !elytra.get("url").isJsonNull()
                ? elytra.get("url").getAsString()
                : "";
        return Either.right(new ProfileTextures(skinUrl.getAsString(), model, capeUrl, elytraUrl));
    }

    private static Task<Maybe<SkinAssets>> readFromDisk(UUID uuid) {
        //noinspection RedundantTypeArguments
        return Task.<Maybe<SkinAssets>>delay(() -> {
            Path skin = cacheFile(uuid);
            if (!Files.isRegularFile(skin)) {
                return Maybe.none();
            }
            Maybe<byte[]> skinBytes = Maybe.ofNullable(Files.readAllBytes(skin)).filter(bytes -> bytes.length > 0);
            if (skinBytes.isEmpty()) {
                return Maybe.none();
            }
            String model = Files.isRegularFile(modelFile(uuid)) ? Files.readString(modelFile(uuid)).trim() : "default";
            if (model.isBlank()) {
                model = "default";
            }
            return Maybe.some(new SkinAssets(
                    skinBytes.get(),
                    model,
                    readOptionalBytes(capeFile(uuid)),
                    readOptionalBytes(elytraFile(uuid))));
        }).recover(err -> Maybe.<SkinAssets>none());
    }

    private static Task<Unit> writeToDisk(UUID uuid, SkinAssets assets) {
        return Task.delay(() -> {
            Path cacheFile = cacheFile(uuid);
            Files.createDirectories(cacheFile.getParent());
            Files.write(cacheFile, assets.skinBytes());
            Files.writeString(modelFile(uuid), assets.model(), StandardCharsets.UTF_8);
            assets.capeBytes().ifPresent(bytes -> writeOptionalBytes(capeFile(uuid), bytes));
            assets.elytraBytes().ifPresent(bytes -> writeOptionalBytes(elytraFile(uuid), bytes));
            return Unit.INSTANCE;
        });
    }

    private static void writeOptionalBytes(Path path, byte[] bytes) {
        Attempts.maybe(() -> Files.write(path, bytes));
    }

    private static Maybe<byte[]> readOptionalBytes(Path path) {
        return Attempts.maybe(() -> Files.isRegularFile(path) ? Files.readAllBytes(path) : null)
                .flatMap(bytes -> Maybe.ofNullable(bytes).filter(value -> value.length > 0));
    }

    private static Task<Unit> registerDynamic(UUID uuid, SkinAssets assets) {
        return Task.delay(() -> {
            registerDynamic(uuid, assets.skinBytes(), assets.model(), assets.capeBytes(), assets.elytraBytes());
            return Unit.INSTANCE;
        });
    }

    private static void registerDynamic(
            UUID uuid,
            byte[] skinBytes,
            String model,
            Maybe<byte[]> capeBytes,
            Maybe<byte[]> elytraBytes) {
        if (uuid == null || skinBytes == null || skinBytes.length == 0) return;
        if (LOADED.containsKey(uuid)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (LOADED.containsKey(uuid)) return;
            registerSkinTexture(mc, uuid, skinBytes)
                    .peek(rl -> {
                        ResourceLocation cape = nullable(capeBytes
                                .flatMap(bytes -> registerRawTexture(mc, "skins/mojang/" + uuid + "_cape", bytes)));
                        ResourceLocation elytra = nullable(elytraBytes
                                .flatMap(bytes -> registerRawTexture(mc, "skins/mojang/" + uuid + "_elytra", bytes)));
                        LOADED.put(uuid, SurvivorPlayerSkin.fromMojang(rl, "slim".equalsIgnoreCase(model), cape, elytra));
                    });
        });
    }

    private static Maybe<ResourceLocation> registerSkinTexture(Minecraft mc, UUID uuid, byte[] skinBytes) {
        return readNativeImage(skinBytes)
                .map(MojangSkinCache::processLegacySkin)
                .flatMap(img -> registerNativeImage(mc, "skins/mojang/" + uuid, img));
    }

    private static Maybe<ResourceLocation> registerRawTexture(Minecraft mc, String path, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Maybe.none();
        return readNativeImage(bytes).flatMap(img -> registerNativeImage(mc, path, img));
    }

    private static Maybe<NativeImage> readNativeImage(byte[] bytes) {
        return Attempts.maybe(() -> NativeImage.read(new ByteArrayInputStream(bytes)));
    }

    private static Maybe<ResourceLocation> registerNativeImage(Minecraft mc, String path, NativeImage img) {
        if (img == null) return Maybe.none();
        return Attempts.maybe(() -> {
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, path);
            mc.getTextureManager().register(rl, tex);
            return rl;
        });
    }

    private static NativeImage processLegacySkin(NativeImage img) {
        if (img == null) return null;
        int height = img.getHeight();
        int width = img.getWidth();
        if (width == 64 && (height == 32 || height == 64)) {
            boolean legacy = height == 32;
            if (legacy) {
                NativeImage expanded = new NativeImage(64, 64, true);
                expanded.copyFrom(img);
                img.close();
                img = expanded;
                expanded.fillRect(0, 32, 64, 32, 0);
                expanded.copyRect(4, 16, 16, 32, 4, 4, true, false);
                expanded.copyRect(8, 16, 16, 32, 4, 4, true, false);
                expanded.copyRect(0, 20, 24, 32, 4, 12, true, false);
                expanded.copyRect(4, 20, 16, 32, 4, 12, true, false);
                expanded.copyRect(8, 20, 8, 32, 4, 12, true, false);
                expanded.copyRect(12, 20, 16, 32, 4, 12, true, false);
                expanded.copyRect(44, 16, -8, 32, 4, 4, true, false);
                expanded.copyRect(48, 16, -8, 32, 4, 4, true, false);
                expanded.copyRect(40, 20, 0, 32, 4, 12, true, false);
                expanded.copyRect(44, 20, -8, 32, 4, 12, true, false);
                expanded.copyRect(48, 20, -16, 32, 4, 12, true, false);
                expanded.copyRect(52, 20, -8, 32, 4, 12, true, false);
            }
            setNoAlpha(img, 0, 0, 32, 16);
            if (legacy) doNotchTransparencyHack(img, 32, 0, 64, 32);
            setNoAlpha(img, 0, 16, 64, 32);
            setNoAlpha(img, 16, 48, 48, 64);
            return img;
        } else {
            img.close();
            return null;
        }
    }

    private static void doNotchTransparencyHack(NativeImage img, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++)
            for (int y = y1; y < y2; y++)
                if ((img.getPixelRGBA(x, y) >> 24 & 0xFF) < 128) return;
        for (int x = x1; x < x2; x++)
            for (int y = y1; y < y2; y++)
                img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) & 0xFFFFFF);
    }

    private static void setNoAlpha(NativeImage img, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++)
            for (int y = y1; y < y2; y++)
                img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) | 0xFF000000);
    }

    private static Path cacheFile(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath()
                .resolve("config").resolve(EchoesofSurvival.MODID)
                .resolve("skins").resolve("mojang")
                .resolve(uuid.toString() + ".png");
    }

    private static Path capeFile(UUID uuid) { return cacheFile(uuid).resolveSibling(uuid + "_cape.png"); }
    private static Path elytraFile(UUID uuid) { return cacheFile(uuid).resolveSibling(uuid + "_elytra.png"); }
    private static Path modelFile(UUID uuid) { return cacheFile(uuid).resolveSibling(uuid + ".model"); }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <A> A nullable(Maybe<A> value) {
        return value.isDefined() ? value.get() : null;
    }

    private record ProfileTextures(String skinUrl, String model, String capeUrl, String elytraUrl) {}

    private record SkinAssets(
            byte[] skinBytes,
            String model,
            Maybe<byte[]> capeBytes,
            Maybe<byte[]> elytraBytes) {}

    private static final class SkinLoadException extends RuntimeException {
        private SkinLoadException(UUID uuid, String message) {
            super("Mojang skin " + uuid + ": " + message);
        }
    }
}
