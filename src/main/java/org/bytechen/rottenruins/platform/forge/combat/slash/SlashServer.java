package org.bytechen.rottenruins.platform.forge.combat.slash;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.api.combat.slash.ISlashWeapon;
import org.bytechen.rottenruins.gameplay.combat.slash.SlashEngine;

/** Forge event entry only; combat state and settlement belong to SlashEngine. */
@Mod.EventBusSubscriber(modid = "rotten_ruins", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlashServer {
    private SlashServer() {}
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventVanillaDuplicate(AttackEntityEvent event) {
        if (!ForgeSlashServices.resolvingTarget() && event.getEntity().getMainHandItem().getItem() instanceof ISlashWeapon) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) SlashEngine.tick(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SlashEngine.logout(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SlashEngine.clearAll(); }
}
