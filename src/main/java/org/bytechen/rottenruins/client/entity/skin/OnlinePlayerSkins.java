package org.bytechen.rottenruins.client.entity.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.HallMod;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Client-only profile lookup; vanilla SkinManager owns texture downloads, disk cache and legacy conversion. */
public final class OnlinePlayerSkins {
    public record Skin(ResourceLocation texture, boolean slim) {}

    private static final class Request {
        final CompletableFuture<Skin> result = new CompletableFuture<>();
        long retryAfter = Long.MAX_VALUE;
    }

    // Accessed only from the client thread. Keep failures briefly to avoid requesting every render frame.
    private static final Cache<String, Request> REQUESTS = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterAccess(30, TimeUnit.MINUTES).build();

    private OnlinePlayerSkins() {}

    public static Skin get(String playerId) {
        String key = playerId.trim().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9_]{1,16}|[a-f0-9]{32}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            return null;
        }
        Request request = REQUESTS.getIfPresent(key);
        if (request != null && request.result.isCompletedExceptionally()
                && Util.getMillis() >= request.retryAfter) {
            REQUESTS.invalidate(key);
            request = null;
        }
        if (request == null) {
            request = new Request();
            REQUESTS.put(key, request);
            start(key, request);
        }
        return request.result.isDone() && !request.result.isCompletedExceptionally()
                ? request.result.getNow(null) : null;
    }

    private static void start(String key, Request request) {
        Minecraft minecraft = Minecraft.getInstance();
        // The timeout also covers downloads that never call SkinManager's success callback.
        request.result.orTimeout(30, TimeUnit.SECONDS).whenComplete((skin, error) -> {
            if (error != null) minecraft.execute(() -> {
                request.retryAfter = Util.getMillis() + TimeUnit.MINUTES.toMillis(1);
                HallMod.LOGGER.debug("Could not load assimilated player skin for {}", key, error);
            });
        });
        CompletableFuture.supplyAsync(() -> resolveProfile(key), Util.backgroundExecutor())
                .whenComplete((profile, error) -> minecraft.execute(() -> {
                    if (request.result.isDone()) return;
                    if (error != null) {
                        request.result.completeExceptionally(error);
                        return;
                    }
                    minecraft.getSkinManager().registerSkins(profile, (type, location, texture) -> {
                        if (type == MinecraftProfileTexture.Type.SKIN) {
                            minecraft.execute(() -> request.result.complete(
                                    new Skin(location, "slim".equals(texture.getMetadata("model")))));
                        }
                    }, false);
                }));
    }

    private static GameProfile resolveProfile(String key) {
        try {
            String id = key.replace("-", "");
            if (id.length() != 32) {
                id = readJson("https://api.mojang.com/users/profiles/minecraft/" + key)
                        .get("id").getAsString();
            }
            UUID uuid = UUID.fromString(id.replaceFirst(
                    "([a-fA-F0-9]{8})([a-fA-F0-9]{4})([a-fA-F0-9]{4})([a-fA-F0-9]{4})([a-fA-F0-9]{12})",
                    "$1-$2-$3-$4-$5"));
            return new GameProfile(uuid, key);
        } catch (Exception error) {
            throw new java.util.concurrent.CompletionException(error);
        }
    }

    private static JsonObject readJson(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address)
                .openConnection(Minecraft.getInstance().getProxy());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Profile lookup returned HTTP " + connection.getResponseCode());
            }
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } finally {
            connection.disconnect();
        }
    }
}
