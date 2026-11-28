package org.bytechen.rottenruins.event.impl;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.infection.event.EntityEvolveEvent;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionPlayerEntity;

/** Preserve the source player's identity when evolution creates an assimilated player. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionPlayerSkinHandler {
    private CorruptionPlayerSkinHandler() {}

    @SubscribeEvent
    public static void onEvolution(EntityEvolveEvent event) {
        if (event.getOriginal() instanceof Player player
                && event.getResultEntity() instanceof CorruptionPlayerEntity result) {
            result.setCustomName(player.getName());
            result.setSkinPlayerId(player.getUUID().toString());
        }
    }
}
