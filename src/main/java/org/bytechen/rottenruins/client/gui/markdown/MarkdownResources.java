package org.bytechen.rottenruins.client.gui.markdown;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Resolves localized documents and their images through the active client resource packs. */
final class MarkdownResources {
    private static final String FALLBACK_LANGUAGE = "en_us";
    private static final int MAX_DOCUMENT_BYTES = 512 * 1024;
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int IHDR = 0x49484452;

    record Image(ResourceLocation texture, int width, int height) {}

    private final ResourceManager manager;
    private final ResourceLocation location;
    private final Resource resource;
    private final Map<String, Optional<Image>> images = new HashMap<>();

    MarkdownResources(ResourceManager manager, ResourceLocation document, String language) throws IOException {
        this.manager = manager;
        ResourceLocation localized = location(document, language);
        Optional<Resource> found = manager.getResource(localized);
        if (found.isEmpty() && !FALLBACK_LANGUAGE.equals(language)) {
            localized = location(document, FALLBACK_LANGUAGE);
            found = manager.getResource(localized);
        }
        if (found.isEmpty()) {
            throw new FileNotFoundException(document.toString());
        }
        this.location = localized;
        this.resource = found.get();
    }

    private static ResourceLocation location(ResourceLocation document, String language) {
        return document.withPath("markdown/" + language + "/" + document.getPath() + ".md");
    }

    String read() throws IOException {
        try (InputStream input = resource.open()) {
            byte[] bytes = input.readNBytes(MAX_DOCUMENT_BYTES + 1);
            if (bytes.length > MAX_DOCUMENT_BYTES) {
                throw new IOException("Markdown resource exceeds " + MAX_DOCUMENT_BYTES + " bytes: " + location);
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
    }

    Optional<Image> image(String destination) {
        return images.computeIfAbsent(destination, this::readImage);
    }

    private Optional<Image> readImage(String destination) {
        ResourceLocation texture = imageLocation(destination);
        if (texture == null || !texture.getPath().endsWith(".png")) {
            return Optional.empty();
        }
        Optional<Resource> found = manager.getResource(texture);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        // Read just the PNG dimensions. Minecraft's texture manager loads the pixels when blitting.
        try (DataInputStream input = new DataInputStream(found.get().open())) {
            if (input.readLong() != PNG_SIGNATURE || input.readInt() != 13 || input.readInt() != IHDR) {
                return Optional.empty();
            }
            int width = input.readInt();
            int height = input.readInt();
            return width > 0 && height > 0 ? Optional.of(new Image(texture, width, height)) : Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private ResourceLocation imageLocation(String destination) {
        if (destination.startsWith("/") || destination.isEmpty()) {
            return null;
        }
        if (destination.contains(":")) {
            ResourceLocation explicit = ResourceLocation.tryParse(destination);
            return explicit != null && !explicit.getPath().startsWith("/") ? explicit : null;
        }

        var segments = new ArrayDeque<String>();
        String directory = location.getPath().substring(0, location.getPath().lastIndexOf('/') + 1);
        for (String segment : (directory + destination).split("/")) {
            switch (segment) {
                case "", "." -> { }
                case ".." -> {
                    if (segments.pollLast() == null) {
                        return null;
                    }
                }
                default -> segments.addLast(segment);
            }
        }
        return ResourceLocation.tryBuild(location.getNamespace(), String.join("/", segments));
    }
}
