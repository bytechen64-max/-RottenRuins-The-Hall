package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.story.StoryDirector;

/** A short opening scene, once per player/save, after the login packets have settled. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstJoinStoryHandler {
    private static final String DELAY = "rotten_ruins:opening_story_delay";

    private FirstJoinStoryHandler() {}

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getPersistentData().remove(DELAY);
        StoryDirector.cancel(player);
        StoryDirector.trigger(player, "login", StoryDirector.context(player));
    }

}
