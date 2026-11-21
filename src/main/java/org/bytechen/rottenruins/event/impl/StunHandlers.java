package org.bytechen.rottenruins.event.impl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.magic.effect.StunEffect;

/** 晕眩期间禁止使用物品、攻击与丢弃物品；位移和视角由 {@link StunEffect} 每 tick 锁回。 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StunHandlers {

    private StunHandlers() {
    }

    private static boolean stunned(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide && StunEffect.isStunned(entity);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (stunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (stunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (stunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (stunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (stunned(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player && stunned(player)) player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }
}
