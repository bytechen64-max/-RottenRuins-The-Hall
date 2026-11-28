package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.UUID;

/** Gameplay entry for private captions; the platform owns their wire format. */
public final class StoryText {
    public static final String ACTION = "story_text";
    public static final int MAX_TEXT_LENGTH = 4096;
    public static final double DEFAULT_DISTANCE = 1.6;
    private StoryText() {}
    public static void show(ServerPlayer p, Component text, int ticks) { send(p, text, ticks, 0, DEFAULT_DISTANCE, 0, false); }
    public static void showAutoplay(ServerPlayer p, Component text, int ticks) {
        StoryRuntime.services().send(p, new StoryMessage.Caption(text, ticks, 0, DEFAULT_DISTANCE, 0, false, true));
    }
    public static void enqueue(ServerPlayer p, Component text, int ticks) { send(p, text, ticks, 0, DEFAULT_DISTANCE, 0, true); }
    public static void send(ServerPlayer p, Component text, int ticks, float angle, double distance, double height, boolean append) {
        StoryRuntime.services().send(p, new StoryMessage.Caption(text, ticks, angle, distance, height, append, false));
    }
    public static void clear(ServerPlayer p) { StoryRuntime.services().send(p, new StoryMessage.Clear(null)); }
    public static void clearSequence(ServerPlayer p, UUID session) { StoryRuntime.services().send(p, new StoryMessage.Clear(java.util.Objects.requireNonNull(session, "session"))); }
    public static void timedSequence(ServerPlayer p, List<Component> pages, int pageTicks, long startTime, UUID session) {
        StoryRuntime.services().send(p, new StoryMessage.TimedSequence(pages, pageTicks, startTime, session));
    }
    public static void play(ServerPlayer p, StoryDefinition story, long startTime, UUID session) {
        StoryRuntime.services().send(p, new StoryMessage.Definition(story, startTime, session));
    }
}
