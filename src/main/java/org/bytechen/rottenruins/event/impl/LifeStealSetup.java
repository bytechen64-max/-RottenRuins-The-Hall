package org.bytechen.rottenruins.event.impl;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.utils.entity.DirectDamageHooks;

/** 把 {@link LifeStealHandlers} 挂到直接改血入口上（{@code SetHealthDieUnti} 不走伤害事件）。 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LifeStealSetup {

    private LifeStealSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DirectDamageHooks.add(LifeStealHandlers::onDirectDamage));
    }
}
