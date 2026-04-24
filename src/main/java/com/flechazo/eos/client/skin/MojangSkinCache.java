package com.flechazo.eos.client.skin;

import com.flechazo.eos.EchoesofSurvival;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mojang skin fetch + disk cache.
 * <p>
 * This class never blocks the render thread: it returns a fallback texture
 * while the skin is downloading/loading, then swaps in the dynamic texture
 * on later frames.
 * </p>
 */
public final class MojangSkinCache {
    private MojangSkinCache() {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "eos-mojang-skin");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<UUID, ResourceLocation> LOADED = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    public static Optional<ResourceLocation> getOrRequest(UUID uuid) {
        if (uuid == null) return Optional.empty();

        ResourceLocation existing = LOADED.get(uuid);
        if (existing != null) return Optional.of(existing);

        request(uuid);
        return Optional.empty();
    }

    private static void request(UUID uuid) {
        if (uuid == null) return;
        if (!IN_FLIGHT.add(uuid)) return;
        // Do not touch disk/network on the render thread: hop to the executor immediately.
        CompletableFuture.runAsync(() -> {
                    Path cacheFile = cacheFile(uuid);
                    if (Files.isRegularFile(cacheFile)) {
                        loadFromDisk(uuid, cacheFile);
                        return;
                    }
                    try {
                        fetchFromMojang(uuid, cacheFile).join();
                    } catch (Exception ignored) {
                    }
                }, EXECUTOR)
                .whenComplete((ok, err) -> IN_FLIGHT.remove(uuid));
    }

    private static CompletableFuture<Void> fetchFromMojang(UUID uuid, Path cacheFile) {
        String id = uuid.toString().replace("-", "");
        String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false";

        HttpRequest request = HttpRequest.newBuilder(URI.create(profileUrl))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(MojangSkinCache::extractSkinUrlFromProfileJson)
                .thenCompose(optUrl -> optUrl.map(MojangSkinCache::downloadBytes).orElseGet(() -> CompletableFuture.completedFuture(null)))
                .thenAccept(bytes -> {
                    if (bytes == null || bytes.length == 0) return;
                    try {
                        Files.createDirectories(cacheFile.getParent());
                        Files.write(cacheFile, bytes);
                    } catch (Exception ignored) {
                    }
                    registerDynamic(uuid, bytes);
                })
                .exceptionally(err -> null);
    }

    private static Optional<String> extractSkinUrlFromProfileJson(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray props = root.getAsJsonArray("properties");
            if (props == null) return Optional.empty();

            for (JsonElement el : props) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("name") || !obj.has("value")) continue;
                JsonElement nameEl = obj.get("name");
                JsonElement valueEl = obj.get("value");
                if (nameEl == null || valueEl == null) continue;
                if (!"textures".equals(nameEl.getAsString())) continue;
                String value = obj.get("value").getAsString();
                if (value == null || value.isBlank()) continue;

                String decoded = new String(Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
                JsonObject texturesRoot = JsonParser.parseString(decoded).getAsJsonObject();
                JsonObject textures = texturesRoot.getAsJsonObject("textures");
                if (textures == null) return Optional.empty();
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (skin == null) return Optional.empty();
                if (!skin.has("url")) return Optional.empty();
                return Optional.ofNullable(skin.get("url").getAsString());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static CompletableFuture<byte[]> downloadBytes(String url) {
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(null);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(HttpResponse::body)
                .exceptionally(err -> null);
    }

    private static void loadFromDisk(UUID uuid, Path cacheFile) {
        try {
            byte[] bytes = Files.readAllBytes(cacheFile);
            if (bytes == null || bytes.length == 0) return;
            registerDynamic(uuid, bytes);
        } catch (Exception ignored) {
        }
    }

    private static void registerDynamic(UUID uuid, byte[] pngBytes) {
        if (uuid == null || pngBytes == null || pngBytes.length == 0) return;
        if (LOADED.containsKey(uuid)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            if (LOADED.containsKey(uuid)) return;
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(pngBytes));
                img = processLegacySkin(img);
                if (img == null) return;
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(EchoesofSurvival.MODID, "skins/mojang/" + uuid);
                mc.getTextureManager().register(rl, tex);
                LOADED.put(uuid, rl);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Copied from vanilla {@code HttpTexture#processLegacySkin} to support classic 64x32 skins.
     * Without this, 64x32 skins will render as mostly black because player models use a 64x64 texHeight.
     */
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
            if (legacy) {
                doNotchTransparencyHack(img, 32, 0, 64, 32);
            }

            setNoAlpha(img, 0, 16, 64, 32);
            setNoAlpha(img, 16, 48, 48, 64);
            return img;
        } else {
            img.close();
            return null;
        }
    }

    private static void doNotchTransparencyHack(NativeImage img, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int rgba = img.getPixelRGBA(x, y);
                if ((rgba >> 24 & 0xFF) < 128) {
                    return;
                }
            }
        }

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) & 0xFFFFFF);
            }
        }
    }

    private static void setNoAlpha(NativeImage img, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) | 0xFF000000);
            }
        }
    }

    private static Path cacheFile(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc != null && mc.gameDirectory != null ? mc.gameDirectory.toPath() : Path.of(".");
        return gameDir
                .resolve("config")
                .resolve(EchoesofSurvival.MODID)
                .resolve("skins")
                .resolve("mojang")
                .resolve(uuid.toString() + ".png");
    }
}
