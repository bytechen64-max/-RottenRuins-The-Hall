package org.bytechen.rottenruins.event.impl;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.anomaly.AnomalyEngine;
import org.bytechen.rottenruins.platform.forge.anomaly.ForgeAnomalyServices;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge forwards events; anomaly rules and effect orchestration live in gameplay. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnomalyEventHandler {
    private AnomalyEventHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAnomalyTrigger(AnomalyTriggerEvent event) {
        AnomalyEngine.trigger(event.getEntity(), event.getAnomalyType());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        AnomalyEngine.tick(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        event.setAmount(AnomalyEngine.modifyDamage(event.getEntity(), event.getSource(), event.getAmount()));
    }

    // Capture first, settle after vanilla damage and every event listener have finished.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDamage(LivingDamageEvent event) {
        ForgeAnomalyServices.enqueue(event);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ForgeAnomalyServices.flush(event.player);
    }
}
