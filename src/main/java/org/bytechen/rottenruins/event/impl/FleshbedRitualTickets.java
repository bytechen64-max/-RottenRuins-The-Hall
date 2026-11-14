package org.bytechen.rottenruins.event.impl;

import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FleshbedRitualTickets {
    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ForgeChunkManager.setForcedChunkLoadingCallback(HallMod.MODID, (level, tickets) -> {
            for (var pos : tickets.getBlockTickets().keySet()) {
                if (!(level.getBlockEntity(pos) instanceof FleshbedBlockEntity bed) || !bed.needsTicket()) {
                    tickets.removeAllTickets(pos);
                }
            }
        }));
    }
}
