package org.bytechen.rottenruins.platform.forge.story;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.story.StoryDirector;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = HallMod.MODID)
public final class StoryLifecycle {
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) StoryDirector.tick(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StoryDirector.cancel(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StoryDirector.cancel(player);
    }
}
