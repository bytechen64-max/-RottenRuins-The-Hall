package org.bytechen.rottenruins.gameplay.story;

import org.bytechen.rottenruins.core.story.StoryMemory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.core.story.StoryRule;
import java.util.*;

/** Server-thread gameplay scheduling. Pending jobs capture definitions, never live reload lookups. */
public final class StoryDirector {
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
    private static StoryMemory memory(Player player) { return StoryRuntime.services().loadMemory(player); }
    public static StoryRule.Context context(ServerPlayer player) {
        return context(player, 0, 0, Integer.MAX_VALUE, "");
    }
    public static StoryRule.Context context(ServerPlayer player, int deaths, int streak, int interval, String cause) {
        Set<String> enabled = new HashSet<>();
        memory(player).flags().forEach((key, value) -> { if (value) enabled.add(key); });
        return new StoryRule.Context(deaths, streak, interval, cause, player.level().dimension().location().toString(), enabled);
    }
    public static void flag(ServerPlayer player, String key, boolean enabled) {
        StoryMemory memory = memory(player);
        memory.flag(key, enabled);
        StoryRuntime.services().saveMemory(player, memory);
    }
    public static Selection select(ServerPlayer player, String event, StoryRule.Context context) {
        var snapshot = StoryCatalog.snapshot();
        var state = memory(player);
        long now = player.server.overworld().getGameTime();
        for (var rule : snapshot.rules()) {
            if (!rule.event().equals(event) || !rule.conditions().matches(context)) continue;
            if (!rule.onceKey().isEmpty() && state.consumed(rule.onceKey())) continue;
            Long lastPlayed = state.lastPlayed(rule.id());
            if (lastPlayed != null) {
                long elapsed = now - lastPlayed;
                if (elapsed >= 0 && elapsed < rule.cooldownTicks()) continue;
            }
            if (PENDING.getOrDefault(player.getUUID(), List.of()).stream()
                    .anyMatch(job -> job.selection.rule().id().equals(rule.id()) || (!rule.onceKey().isEmpty()
                            && job.selection.rule().onceKey().equals(rule.onceKey())))) continue;
            String story = rule.choose(state.previous(rule.repeatGroup()), player.getRandom()::nextInt);
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
        StoryMemory memory = memory(player);
        memory.commit(rule.id(), rule.repeatGroup(), selection.story().id().toString(), rule.onceKey(),
                player.server.overworld().getGameTime());
        StoryRuntime.services().saveMemory(player, memory);
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
            if (!job.selection.rule().onceKey().isEmpty() && memory(player).consumed(job.selection.rule().onceKey())) continue;
            play(player, job.selection.story());
            commit(player, job.selection);
        }
        if (jobs.isEmpty()) PENDING.remove(player.getUUID());
    }
    public static void play(ServerPlayer player, StoryDefinition story) {
        StoryText.play(player, story, player.level().getGameTime(), UUID.randomUUID());
    }
    public static void cancel(ServerPlayer player) { PENDING.remove(player.getUUID()); }
    public static void clearAll() { PENDING.clear(); }
    public static int pendingCount(ServerPlayer player) { return PENDING.getOrDefault(player.getUUID(), List.of()).size(); }
    public static String previous(ServerPlayer player, String group) { return memory(player).previous(group); }
    public static void copyMemory(Player original, Player replacement) {
        Set<String> keys = new HashSet<>();
        for (var rule : StoryCatalog.snapshot().rules()) if (!rule.onceKey().isEmpty()) keys.add(rule.onceKey());
        StoryRuntime.services().copyMemory(original, replacement, keys);
    }
}
