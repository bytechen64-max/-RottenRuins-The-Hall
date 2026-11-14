package org.bytechen.rottenruins.platform.forge.infection.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;

/**
 * Forge 事件总线订阅器。
 * <p>
 * 将 Minecraft Forge 事件连接到 {@link InfCoreEventHelpers} 的处理逻辑。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfCoreEventHandler {

    private InfCoreEventHandler() {}

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        InfCoreEventHelpers.handleAttachCapabilities(event);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        InfCoreEventHelpers.handleLivingDeath(event);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() == null) return;
        InfCoreEventHelpers.handleLivingTick(event.getEntity());
    }
}
