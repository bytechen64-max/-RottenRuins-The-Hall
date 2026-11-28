package org.bytechen.rottenruins.client.story;

import java.util.function.Consumer;

/** Layer submission order for translucent text, independent of font atlas boundaries. */
public final class StoryTextLayers {
    public static final boolean SORT_QUADS = false;

    private StoryTextLayers() {}

    public static void render(Consumer<Boolean> renderLayer, Runnable flush) {
        renderLayer.accept(true);
        flush.run();
        renderLayer.accept(false);
        flush.run();
    }
}
