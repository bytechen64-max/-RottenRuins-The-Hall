package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.UUID;

/** Server entry point for private, player-relative story captions. No client classes here. */
public final class StoryText {
    public static final String ACTION = "story_text";
    public static final int MAX_TEXT_LENGTH = 4096;
    public static final double DEFAULT_DISTANCE = 1.6;

    private StoryText() {}

    /** Replace the story. Hold the fully typed page for readingTicks before its per-character exit. */
    public static void show(ServerPlayer player, Component text, int readingTicks) {
        send(player, text, readingTicks, 0, DEFAULT_DISTANCE, 0, false);
    }

    /** Play at the normal text speed even while the player is looking away. */
    public static void showAutoplay(ServerPlayer player, Component text, int readingTicks) {
        send(player, text, readingTicks, 0, DEFAULT_DISTANCE, 0, false, true);
    }

    /** Append a paragraph; its direction is captured when that paragraph starts. */
    public static void enqueue(ServerPlayer player, Component text, int readingTicks) {
        send(player, text, readingTicks, 0, DEFAULT_DISTANCE, 0, true);
    }

    /**
     * @param yawOffset horizontal angle relative to the player's view when the paragraph starts
     * @param distance horizontal distance in blocks (1..8)
     * @param height offset above eye height in blocks (-2..2)
     */
    public static void send(ServerPlayer player, Component text, int readingTicks,
                            float yawOffset, double distance, double height, boolean append) {
        send(player, text, readingTicks, yawOffset, distance, height, append, false);
    }

    private static void send(ServerPlayer player, Component text, int readingTicks,
                             float yawOffset, double distance, double height, boolean append, boolean autoplay) {
        String json = Component.Serializer.toJson(text);
        if (json.length() > MAX_TEXT_LENGTH || !Float.isFinite(yawOffset)
                || !Double.isFinite(distance) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("Invalid story text or placement");
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("text", json);
        tag.putInt("ticks", Math.max(20, Math.min(12000, readingTicks)));
        tag.putFloat("angle", yawOffset);
        tag.putDouble("distance", Math.max(1, Math.min(8, distance)));
        tag.putDouble("height", Math.max(-2, Math.min(2, height)));
        tag.putBoolean("append", append);
        tag.putBoolean("autoplay", autoplay);
        tag.putString("dimension", player.level().dimension().location().toString());
        transmit(player, tag);
    }

    public static void clear(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("clear", true);
        transmit(player, tag);
    }

    /** An entire server-clock sequence: localization and wrapping never change its completion deadline. */
    public static void timedSequence(ServerPlayer player, List<Component> pages, int pageTicks, long startTime, UUID session) {
        if (pages.isEmpty() || pages.size() > 32 || pageTicks < 20 || pageTicks > 12000) {
            throw new IllegalArgumentException("Invalid story sequence");
        }
        ListTag encoded = new ListTag();
        for (Component page : pages) {
            String json = Component.Serializer.toJson(page);
            if (json.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("Story page too long");
            encoded.add(StringTag.valueOf(json));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("pages", encoded);
        tag.putBoolean("clocked", true);
        tag.putLong("startTime", startTime);
        tag.putInt("ticks", pageTicks);
        tag.putString("session", session.toString());
        tag.putDouble("distance", DEFAULT_DISTANCE);
        tag.putString("dimension", player.level().dimension().location().toString());
        transmit(player, tag);
    }

    /** Cancel only this sequence, leaving any later unrelated story untouched. */
    public static void clearSequence(ServerPlayer player, UUID session) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("clear", true);
        tag.putString("session", session.toString());
        transmit(player, tag);
    }

    private static void transmit(ServerPlayer player, CompoundTag tag) {
        StoryRuntime.services().send(player, tag);
    }

    /** Serialize one complete definition as a single packet; active sequences survive catalog reloads. */
    public static CompoundTag encode(StoryDefinition story, long startTime, UUID session) {
        CompoundTag tag = new CompoundTag();
        tag.putString("story", story.id().toString());
        tag.putString("conflict", story.conflict().name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("priority", story.priority());
        ListTag entries = new ListTag();
        long cursor = startTime;
        for (var page : story.pages()) {
            CompoundTag entry = new CompoundTag();
            Component text = Component.Serializer.fromJson(page.textJson());
            if (page.font() != null) text = text.copy().withStyle(style -> style.withFont(page.font()));
            entry.putString("text", Component.Serializer.toJson(text));
            entry.putInt("ticks", story.timing() == StoryDefinition.Timing.CLOCKED ? page.durationTicks() : page.holdTicks());
            entry.putBoolean("clocked", story.timing() == StoryDefinition.Timing.CLOCKED);
            entry.putBoolean("autoplay", story.timing() != StoryDefinition.Timing.READABLE);
            entry.putLong("startTime", cursor);
            entry.putBoolean("recapture_anchor", page.recaptureAnchor());
            entry.putFloat("angle", page.placement().angle());
            entry.putDouble("distance", page.placement().distance());
            entry.putDouble("height", page.placement().height());
            if (page.shadow() != null) entry.putBoolean("shadow", page.shadow());
            if (page.entrySpeed() != null) entry.putFloat("entry_speed", page.entrySpeed());
            if (page.exitSpeed() != null) entry.putFloat("exit_speed", page.exitSpeed());
            entries.add(entry);
            cursor += page.durationTicks();
        }
        tag.put("entries", entries);
        if (session != null) tag.putString("session", session.toString());
        return tag;
    }

    public static void sendEncoded(ServerPlayer player, CompoundTag snapshot) {
        CompoundTag tag = snapshot.copy();
        tag.putString("dimension", player.level().dimension().location().toString());
        transmit(player, tag);
    }
}
