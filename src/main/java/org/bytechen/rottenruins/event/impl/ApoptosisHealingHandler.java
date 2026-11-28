package org.bytechen.rottenruins.event.impl;

import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;

@Mod.EventBusSubscriber(modid = HallMod.MODID)
public final class ApoptosisHealingHandler {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(RegisterEffect.APOPTOSIS.get())) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }
}
