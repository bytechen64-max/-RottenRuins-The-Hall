package org.bytechen.rottenruins.platform.minecraft.story;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.gameplay.story.*;
import java.util.List;
import java.util.UUID;
import static org.bytechen.rottenruins.gameplay.story.StoryText.*;

/** Current story wire encoding; also used for the fleshbed's saved outgoing snapshot. */
public final class StoryPacketCodec {
    private StoryPacketCodec() {}
    public static CompoundTag encode(ServerPlayer player, StoryMessage message) {
        if (message instanceof StoryMessage.Caption c)
            return caption(player, c.text(), c.readingTicks(), c.yawOffset(), c.distance(), c.height(), c.append(), c.autoplay());
        if (message instanceof StoryMessage.TimedSequence s)
            return timedSequence(player, s.pages(), s.pageTicks(), s.startTime(), s.session());
        if (message instanceof StoryMessage.Definition d) {
            CompoundTag tag = encode(d.story(), d.startTime(), d.session());
            tag.putString("dimension", player.level().dimension().location().toString());
            return tag;
        }
        if (message instanceof StoryMessage.Clear c) {
            CompoundTag tag = new CompoundTag(); tag.putBoolean("clear", true);
            if (c.session() != null) tag.putString("session", c.session().toString());
            return tag;
        }
        throw new IllegalArgumentException("Unknown story message: " + message);
    }
    private static CompoundTag caption(ServerPlayer player, Component text, int readingTicks,
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
        return tag;
    }
    private static CompoundTag timedSequence(ServerPlayer player, List<Component> pages, int pageTicks, long startTime, UUID session) {

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
        return tag;
    }
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
}
