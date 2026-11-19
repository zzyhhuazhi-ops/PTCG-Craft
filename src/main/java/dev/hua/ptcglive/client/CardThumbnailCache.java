package dev.hua.ptcglive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.*;

/** Per-screen bounded thumbnail cache. Region browsing never loads full HD images into this cache. */
final class CardThumbnailCache implements AutoCloseable {
    record Art(Identifier texture, int width, int height) implements CardArt { }
    private static final int LIMIT = 48;
    private final String prefix = "region/" + UUID.randomUUID() + "/";
    private final LinkedHashMap<String, Art> loaded = new LinkedHashMap<>(48, 0.75f, true);
    private final Set<String> failed = new HashSet<>();
    private long resourceGeneration = -1;
    Art get(String cardId) {
        if (resourceGeneration != CardArtResources.generation()) { close(); resourceGeneration = CardArtResources.generation(); }
        if (loaded.containsKey(cardId)) return loaded.get(cardId);
        if (failed.contains(cardId) || !cardId.matches("[a-z0-9_-]+")) return null;
        var client = MinecraftClient.getInstance(); NativeImage image = null;
        NativeImageBackedTexture texture = null;
        try (var stream = client.getResourceManager().open(CardArtResources.thumbnail(client.getResourceManager(), cardId))) {
            image = CardImageDecoder.read(stream);
            int width = image.getWidth(), height = image.getHeight();
            texture = new NativeImageBackedTexture(image); image = null;
            texture.setFilter(false, false);
            Identifier id = Identifier.of("ptcglive", prefix + cardId);
            client.getTextureManager().registerTexture(id, texture);
            texture = null;
            var art = new Art(id, width, height); loaded.put(cardId, art);
            if (loaded.size() > LIMIT) {
                var iterator = loaded.entrySet().iterator(); var entry = iterator.next();
                client.getTextureManager().destroyTexture(entry.getValue().texture()); iterator.remove();
            }
            return art;
        } catch (IOException | RuntimeException ex) { failed.add(cardId); return null; }
        finally { if (image != null) image.close(); if (texture != null) texture.close(); }
    }
    @Override public void close() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        loaded.values().forEach(art -> manager.destroyTexture(art.texture())); loaded.clear(); failed.clear();
    }
}
