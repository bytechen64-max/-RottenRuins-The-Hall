package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.UUID;

/** Typed transport intent. Wire field names and NBT belong to the platform codec. */
public sealed interface StoryMessage {
    record Caption(Component text, int readingTicks, float yawOffset, double distance, double height,
                   boolean append, boolean autoplay) implements StoryMessage {
        public Caption { text = text.copy(); }
    }
    record Clear(UUID session) implements StoryMessage {}
    record TimedSequence(List<Component> pages, int pageTicks, long startTime, UUID session) implements StoryMessage {
        public TimedSequence { pages = pages.stream().<Component>map(Component::copy).toList(); }
    }
    record Definition(StoryDefinition story, long startTime, UUID session) implements StoryMessage {}
}
