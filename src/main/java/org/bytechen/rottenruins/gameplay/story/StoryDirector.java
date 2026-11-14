package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.core.story.StoryRule;
import java.util.*;

/** Server-thread gameplay scheduling. Pending jobs capture definitions, never live reload lookups. */
public final class StoryDirector {
    public static final String STATE = "rotten_ruins:story_state";
    private static final Map<UUID, List<Job>> PENDING = new HashMap<>();
    public record Selection(StoryRule rule, StoryDefinition story) {}
    private static final class Job {
        final Selection selection;
        final String dimension;
        int remaining;
        Job(Selection selection, ServerPlayer player) {
            this.selection = selection;
            dimension = player.level().dimension().location().toString();
            remaining = selection.rule().delayTicks();
        }
    }
    private StoryDirector() {}
    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
    private static CompoundTag state(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(STATE)) persisted.put(STATE, new CompoundTag());
        return persisted.getCompound(STATE);
    }
    public static StoryRule.Context context(ServerPlayer player) {
        return context(player, 0, 0, Integer.MAX_VALUE, "");
    }
    public static StoryRule.Context context(ServerPlayer player, int deaths, int streak, int interval, String cause) {
        CompoundTag flags = state(player).getCompound("flags");
        Set<String> enabled = new HashSet<>();
        for (String key : flags.getAllKeys()) if (flags.getBoolean(key)) enabled.add(key);
        return new StoryRule.Context(deaths, streak, interval, cause, player.level().dimension().location().toString(), enabled);
    }
    public static void flag(ServerPlayer player, String key, boolean enabled) {
        CompoundTag state = state(player);
        CompoundTag flags = state.getCompound("flags");
        flags.putBoolean(key, enabled);
        state.put("flags", flags);
    }
    public static Selection select(ServerPlayer player, String event, StoryRule.Context context) {
        var snapshot = StoryCatalog.snapshot();
        var state = state(player);
        long now = player.server.overworld().getGameTime();
        for (var rule : snapshot.rules()) {
            if (!rule.event().equals(event) || !rule.conditions().matches(context)) continue;
            if (!rule.onceKey().isEmpty() && persisted(player).getBoolean(rule.onceKey())) continue;
            CompoundTag cooldowns = state.getCompound("cooldowns");
            if (cooldowns.contains(rule.id())) {
                long elapsed = now - cooldowns.getLong(rule.id());
                if (elapsed >= 0 && elapsed < rule.cooldownTicks()) continue;
            }
            if (PENDING.getOrDefault(player.getUUID(), List.of()).stream()
                    .anyMatch(job -> job.selection.rule().id().equals(rule.id()) || (!rule.onceKey().isEmpty()
                            && job.selection.rule().onceKey().equals(rule.onceKey())))) continue;
            String story = rule.choose(state.getCompound("previous").getString(rule.repeatGroup()), player.getRandom()::nextInt);
            if (story != null) return new Selection(rule, snapshot.stories().get(net.minecraft.resources.ResourceLocation.parse(story)));
        }
        return null;
    }
    public static boolean trigger(ServerPlayer player, String event, StoryRule.Context context) {
        Selection selection = select(player, event, context);
        if (selection == null) return false;
        var jobs = PENDING.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
        if (jobs.size() >= 32) return false;
        jobs.add(new Job(selection, player));
        if (selection.rule().delayTicks() == 0) tick(player, false);
        return true;
    }
    public static void commit(ServerPlayer player, Selection selection) {
        var rule = selection.rule();
        CompoundTag state = state(player);
        CompoundTag previous = state.getCompound("previous");
        previous.putString(rule.repeatGroup(), selection.story().id().toString());
        state.put("previous", previous);
        CompoundTag cooldowns = state.getCompound("cooldowns");
        cooldowns.putLong(rule.id(), player.server.overworld().getGameTime());
        state.put("cooldowns", cooldowns);
        if (!rule.onceKey().isEmpty()) persisted(player).putBoolean(rule.onceKey(), true);
        if (!rule.onceKey().isEmpty()) {
            CompoundTag keys = state.getCompound("once_keys");
            keys.putBoolean(rule.onceKey(), true);
            state.put("once_keys", keys);
        }
    }
    public static void tick(ServerPlayer player) { tick(player, true); }
    private static void tick(ServerPlayer player, boolean decrement) {
        List<Job> jobs = PENDING.get(player.getUUID());
        if (jobs == null) return;
        if (!player.isAlive()) { cancel(player); return; }
        var iterator = jobs.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (!job.dimension.equals(player.level().dimension().location().toString())) { iterator.remove(); continue; }
            if (decrement) job.remaining--;
            if (job.remaining > 0) continue;
            iterator.remove();
            if (!job.selection.rule().onceKey().isEmpty() && persisted(player).getBoolean(job.selection.rule().onceKey())) continue;
            play(player, job.selection.story());
            commit(player, job.selection);
        }
        if (jobs.isEmpty()) PENDING.remove(player.getUUID());
    }
    public static void play(ServerPlayer player, StoryDefinition story) {
        StoryText.sendEncoded(player, StoryText.encode(story, player.level().getGameTime(), UUID.randomUUID()));
    }
    public static void cancel(ServerPlayer player) { PENDING.remove(player.getUUID()); }
    public static void clearAll() { PENDING.clear(); }
    public static int pendingCount(ServerPlayer player) { return PENDING.getOrDefault(player.getUUID(), List.of()).size(); }
    public static String previous(ServerPlayer player, String group) { return state(player).getCompound("previous").getString(group); }
    public static void copyMemory(Player original, Player replacement) {
        CompoundTag from = original.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag to = replacement.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (from.contains(STATE)) to.put(STATE, from.getCompound(STATE).copy());
        for (String key : from.getCompound(STATE).getCompound("once_keys").getAllKeys())
            if (from.contains(key)) to.putBoolean(key, from.getBoolean(key));
        if (from.contains("rotten_ruins:opening_story_seen")) to.putBoolean("rotten_ruins:opening_story_seen", from.getBoolean("rotten_ruins:opening_story_seen"));
        for (var rule : StoryCatalog.snapshot().rules()) {
            if (!rule.onceKey().isEmpty() && from.contains(rule.onceKey())) to.putBoolean(rule.onceKey(), from.getBoolean(rule.onceKey()));
        }
        replacement.getPersistentData().put(Player.PERSISTED_NBT_TAG, to);
    }
}
