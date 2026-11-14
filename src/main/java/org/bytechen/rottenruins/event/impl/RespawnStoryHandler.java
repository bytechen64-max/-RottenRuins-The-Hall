package org.bytechen.rottenruins.event.impl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.story.StoryDirector;

/** Per-save death memories; a real death respawn schedules one private, localized caption. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RespawnStoryHandler {
    public static final String STATE = "rotten_ruins:respawn_story";
    private static final String CAUSE = "rotten_ruins:death_story_cause";
    private static final String DEATH_TIME = "rotten_ruins:death_story_time";
    private static final int RAPID_DEATH_TICKS = 20 * 60 * 3;

    private RespawnStoryHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        StoryDirector.cancel(player);
        var source = event.getSource();
        String cause = source.is(DamageTypeTags.IS_FALL) ? "fall"
                : source.is(DamageTypes.DROWN) ? "drown"
                : source.getEntity() instanceof Mob ? "mob" : "normal";
        player.getPersistentData().putString(CAUSE, cause);
        player.getPersistentData().putLong(DEATH_TIME, player.server.overworld().getGameTime());
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        StoryDirector.cancel(player);
        StoryDirector.copyMemory(event.getOriginal(), player);
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag state = old.getCompound(Player.PERSISTED_NBT_TAG).getCompound(STATE).copy();
        if (!event.isWasDeath()) {
            if (!state.isEmpty()) {
                CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                persisted.put(STATE, state);
                player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            }
            return;
        }
        long now = old.contains(DEATH_TIME) ? old.getLong(DEATH_TIME) : player.server.overworld().getGameTime();
        long elapsed = now - state.getLong("lastDeath");
        int deaths = state.getInt("deaths");
        state.putInt("streak", deaths > 0 && elapsed >= 0 && elapsed <= RAPID_DEATH_TICKS
                ? Math.min(1000000, state.getInt("streak") + 1) : 1);
        state.putInt("deaths", Math.min(1000000, deaths + 1));
        state.putLong("lastDeath", now);
        state.putInt("interval", deaths == 0 || elapsed < 0 ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, elapsed));
        state.putString("cause", old.contains(CAUSE) ? old.getString(CAUSE) : "normal");
        state.putBoolean("pending", true);
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(STATE, state);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag state = state(player);
        if (!state.getBoolean("pending")) return;
        state.putBoolean("pending", false);
        StoryDirector.trigger(player, "respawn", StoryDirector.context(player, state.getInt("deaths"),
                state.getInt("streak"), state.getInt("interval"), state.getString("cause")));
    }

    private static CompoundTag state(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(STATE);
    }
}
