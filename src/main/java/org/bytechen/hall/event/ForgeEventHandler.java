package org.bytechen.hall.event;

import net.minecraft.world.damagesource.DamageTypes;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.command.AnomalyCommand;
import org.bytechen.hall.command.BlackHoleCommand;
import org.bytechen.hall.command.CollapseCommand;
import org.bytechen.hall.command.SpreadCommand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEventHandler {

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        ForgeEventHelpers.handleAttachCapabilities(event);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() != null) ForgeEventHelpers.handleLivingHurt(event);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() != null) ForgeEventHelpers.handleLivingTick(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) ForgeEventHelpers.handleLivingDeath(event);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AnomalyCommand.register(event);
        SpreadCommand.register(event);
        CollapseCommand.register(event);
        BlackHoleCommand.register(event);
    }
}
