package com.flechazo.eos.client.skin;

import com.flechazo.eos.EchoesofSurvival;
import com.flechazo.eos.client.render.SurvivorPlayerSkin;
import com.flechazo.hkt.Either;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.Unit;
import com.flechazo.hkt.business.core.Attempts;
import com.flechazo.hkt.business.effect.CompletableFuturePath;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    private static final Map<UUID, SkinFailure> FAILED = new ConcurrentHashMap<>();
    private static boolean retryScheduled;

    public static Maybe<SurvivorPlayerSkin> getOrRequest(UUID uuid) {
        if (uuid == null) return Maybe.none();
        SurvivorPlayerSkin existing = LOADED.get(uuid);
        if (existing != null) return Maybe.some(existing);
        if (!FAILED.containsKey(uuid)) {
            request(uuid);
        }
        return Maybe.none();
    }

    private static void request(UUID uuid) {
        if (uuid == null || !IN_FLIGHT.add(uuid)) return;
        load(uuid)
                .unsafeRunAsync(EXECUTOR)
                .whenComplete((ignored, error) -> {
                    IN_FLIGHT.remove(uuid);
                    if (error == null) {
                        SkinFailure previous = FAILED.remove(uuid);
                        if (previous != null) {
                            EchoesofSurvival.LOGGER.info("Mojang skin loaded after retry: {}", uuid);
                        }
                    } else {
                        recordFailure(uuid, error);
                    }
                    scheduleRetryIfNeeded();
                });
    }

    private static Task<Unit> load(UUID uuid) {
        return readFromDisk(uuid)
                .flatMap(cached -> cached.isDefined()
                        ? registerDynamic(uuid, cached.get())
                                .recoverWith(error -> invalidateCache(uuid, error)
                                        .then(() -> fetchRegisterAndCache(uuid)))
                        : fetchRegisterAndCache(uuid));
    }

    private static Task<Unit> fetchRegisterAndCache(UUID uuid) {
        return fetchFromMojang(uuid)
                .flatMap(assets -> registerDynamic(uuid, assets)
                        .then(() -> writeToDisk(uuid, assets)
                                .recover(error -> {
                                    EchoesofSurvival.LOGGER.warn(
                                            "Mojang skin {} loaded, but disk cache write failed: {}",
                                            uuid,
                                            describeFailure(error)
                                    );
                                    EchoesofSurvival.LOGGER.debug("Mojang skin cache write failure details for " + uuid, error);
                                    return Unit.INSTANCE;
                                })));
    }

    private static Task<Unit> invalidateCache(UUID uuid, Throwable error) {
        return Task.delay(() -> {
            EchoesofSurvival.LOGGER.warn(
                    "Cached Mojang skin {} could not be registered; deleting cache and downloading again: {}",
                    uuid,
                    describeFailure(error)
            );
            deleteIfExists(cacheFile(uuid));
            deleteIfExists(capeFile(uuid));
            deleteIfExists(elytraFile(uuid));
            deleteIfExists(modelFile(uuid));
            return Unit.INSTANCE;
        });
    }

    private static void deleteIfExists(Path path) {
        Attempts.either(() -> Files.deleteIfExists(path))
                .peekLeft(error -> EchoesofSurvival.LOGGER.debug(
                        "Failed to delete invalid Mojang skin cache file " + path,
                        error
                ));
    }

    private static void recordFailure(UUID uuid, Throwable error) {
        Throwable cause = unwrap(error);
        SkinFailure failure = FAILED.compute(uuid, (ignored, previous) -> new SkinFailure(
                previous == null ? 1 : previous.attempts() + 1,
                describeFailure(cause)
        ));
        EchoesofSurvival.LOGGER.warn(
                "Mojang skin request failed for {} (attempt {}): {}",
                uuid,
                failure.attempts(),
                failure.reason()
        );
        EchoesofSurvival.LOGGER.debug("Mojang skin request failure details for {}", uuid, cause);
    }

    private static void scheduleRetryIfNeeded() {
        if (!IN_FLIGHT.isEmpty()) return;
        synchronized (MojangSkinCache.class) {
            if (retryScheduled || FAILED.isEmpty()) return;
            retryScheduled = true;
            EchoesofSurvival.LOGGER.warn(
                    "Mojang skin retry scheduled in {} min for {} unique UUID(s): {}",
                    RETRY_DELAY.toMinutes(),
                    FAILED.size(),
                    FAILED.keySet());
            EXECUTOR.schedule(MojangSkinCache::retryFailures, RETRY_DELAY.toMinutes(), TimeUnit.MINUTES);
        }
    }

    private static void retryFailures() {
        List<UUID> pending;
        synchronized (MojangSkinCache.class) {
            pending = new ArrayList<>(FAILED.keySet());
            retryScheduled = false;
        }
        if (!pending.isEmpty()) {
            EchoesofSurvival.LOGGER.info("Retrying Mojang skin download for {} unique UUID(s): {}", pending.size(), pending);
        }
        for (UUID uuid : pending) {
            if (!LOADED.containsKey(uuid)) {
                request(uuid);
            }
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String describeFailure(Throwable error) {
        if (error == null) return "unknown failure";
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return isBlank(message) ? type : type + ": " + message;
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
        return Task.async(() -> registerDynamic(
                uuid,
                assets.skinBytes(),
                assets.model(),
                assets.capeBytes(),
                assets.elytraBytes()
        ).run());
    }

    private static CompletableFuturePath<Unit> registerDynamic(
            UUID uuid,
            byte[] skinBytes,
            String model,
            Maybe<byte[]> capeBytes,
            Maybe<byte[]> elytraBytes) {
        if (uuid == null) {
            return CompletableFuturePath.failed(new IllegalArgumentException("skin UUID is null"));
        }
        if (skinBytes == null || skinBytes.length == 0) {
            return CompletableFuturePath.failed(new SkinLoadException(uuid, "empty skin data before texture registration"));
        }
        if (LOADED.containsKey(uuid)) {
            return CompletableFuturePath.completed(Unit.INSTANCE);
        }

        CompletableFuture<Unit> result = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> completeFuture(result, registerOnRenderThread(
                mc, uuid, skinBytes, model, capeBytes, elytraBytes
        )));
        return CompletableFuturePath.fromFuture(result);
    }

    private static Either<Throwable, Unit> registerOnRenderThread(
            Minecraft mc,
            UUID uuid,
            byte[] skinBytes,
            String model,
            Maybe<byte[]> capeBytes,
            Maybe<byte[]> elytraBytes) {
        if (LOADED.containsKey(uuid)) {
            return Either.right(Unit.INSTANCE);
        }
        return registerSkinTexture(mc, uuid, skinBytes)
                .flatMap(skin -> Attempts.either(() -> {
                    Maybe<ResourceLocation> cape = capeBytes
                            .flatMap(bytes -> registerRawTexture(mc, "skins/mojang/" + uuid + "_cape", bytes));
                    Maybe<ResourceLocation> elytra = elytraBytes
                            .flatMap(bytes -> registerRawTexture(mc, "skins/mojang/" + uuid + "_elytra", bytes));
                    LOADED.put(uuid, SurvivorPlayerSkin.fromMojang(skin, "slim".equalsIgnoreCase(model), cape, elytra));
                    return Unit.INSTANCE;
                }));
    }

    private static void completeFuture(CompletableFuture<Unit> future, Either<Throwable, Unit> result) {
        result.fold(
                future::completeExceptionally,
                future::complete
        );
    }

    private static Either<Throwable, ResourceLocation> registerSkinTexture(Minecraft mc, UUID uuid, byte[] skinBytes) {
        return Attempts.either(() -> NativeImage.read(new ByteArrayInputStream(skinBytes)))
                .flatMap(image -> Maybe.ofNullable(processLegacySkin(image))
                        .toEither(() -> new SkinLoadException(uuid, "unsupported skin dimensions")))
                .flatMap(image -> registerNativeImageRequired(mc, "skins/mojang/" + uuid, image));
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

    private static Either<Throwable, ResourceLocation> registerNativeImageRequired(Minecraft mc, String path, NativeImage img) {
        DynamicTexture texture = new DynamicTexture(img);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, path);
        return Attempts.either(() -> {
            mc.getTextureManager().register(location, texture);
            return location;
        }).peekLeft(error -> texture.close());
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

    private record ProfileTextures(String skinUrl, String model, String capeUrl, String elytraUrl) {}

    private record SkinAssets(
            byte[] skinBytes,
            String model,
            Maybe<byte[]> capeBytes,
            Maybe<byte[]> elytraBytes) {}

    private record SkinFailure(int attempts, String reason) {}

    private static final class SkinLoadException extends RuntimeException {
        private SkinLoadException(UUID uuid, String message) {
            super("Mojang skin " + uuid + ": " + message);
        }
    }
}
