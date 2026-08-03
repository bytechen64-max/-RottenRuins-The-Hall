package org.bytechen.hall.event.impl;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.anomaly.AnomalyType;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.hall.utils.entity.EntityBuffUtils;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 异常事件处理器 —— 负责三种异常的持续伤害、伤害放大和触发效果。
 * <p>
 * 热异常：持续火焰伤害 + 受到的火焰伤害放大 + 触发时造成 40% 最大生命火焰伤害并点燃 1000s
 * <br>
 * 冷异常：持续冻伤 + 受到的冻伤放大 + 触发时造成 40% 最大生命冻伤并叠加缓慢/虚弱
 * <br>
 * 纳酸异常：每 3 tick setHealth 伤害 + 随机装备掉耐久 + 触发时造成 40% 最大生命 setHealth 伤害
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnomalyEventHandler {

    private AnomalyEventHandler() {}

    // ==================== 触发效果 ====================

    @SubscribeEvent
    public static void onAnomalyTrigger(AnomalyTriggerEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        switch (event.getAnomalyType()) {
            case HEAT -> handleHeatTrigger(entity);
            case COLD -> handleColdTrigger(entity);
            case ACID -> handleAcidTrigger(entity);
        }
    }

    /** 热异常触发：40% 最大生命火焰伤害 + 10s 着火 */
    private static void handleHeatTrigger(LivingEntity entity) {
        float damage = entity.getMaxHealth() * 0.4f;
        entity.hurt(entity.damageSources().inFire(), damage);
        entity.setRemainingFireTicks(200); // 10 秒
    }

    /** 冷异常触发：40% 最大生命冻伤 + 缓慢 + 虚弱（可叠加） */
    private static void handleColdTrigger(LivingEntity entity) {
        float damage = entity.getMaxHealth() * 0.4f;
        entity.hurt(entity.damageSources().freeze(), damage);
        // 使用 EntityBuffUtils 叠加 buff
        EntityBuffUtils.addOrStackEffect(entity, MobEffects.MOVEMENT_SLOWDOWN, 1, 200);
        EntityBuffUtils.addOrStackEffect(entity, MobEffects.WEAKNESS, 1, 200);
    }

    /** 纳酸异常触发：40% 最大生命魔法伤害（有反馈） */
    private static void handleAcidTrigger(LivingEntity entity) {
        float damage = entity.getMaxHealth() * 0.4f;
        entity.hurt(entity.damageSources().magic(), damage);
    }

    // ==================== 持续伤害（Tick） ====================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        AnomalyCapability cap = entity.getCapability(CapabilityRegistry.ANOMALY_CAP).orElse(null);
        if (cap == null) return;

        // 热异常：每秒火焰伤害
        if (cap.hasAnomaly(AnomalyType.HEAT) && entity.tickCount % 20 == 0) {
            entity.hurt(entity.damageSources().inFire(), 1.0f);
        }

        // 冷异常：每秒冻伤
        if (cap.hasAnomaly(AnomalyType.COLD) && entity.tickCount % 20 == 0) {
            entity.hurt(entity.damageSources().freeze(), 1.0f);
        }

        // 纳酸异常：每 3 tick setHealth 伤害 + 装备掉耐久
        if (cap.hasAnomaly(AnomalyType.ACID) && entity.tickCount % 3 == 0) {
            int progress = cap.getProgress(AnomalyType.ACID);
            // setHealth 伤害（绕过护甲）
            entity.setHealth(Math.max(1f, entity.getHealth() - 1.0f));
            // 随机装备掉耐久：每次 8 * 进度数
            damageRandomEquipment(entity, 8 * progress);
        }
    }

    // ==================== 伤害放大 ====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        AnomalyCapability cap = entity.getCapability(CapabilityRegistry.ANOMALY_CAP).orElse(null);
        if (cap == null) return;

        // 热异常：受到的火焰伤害增加 0.5 * 进度数
        if (cap.hasAnomaly(AnomalyType.HEAT) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            int progress = cap.getProgress(AnomalyType.HEAT);
            event.setAmount(event.getAmount() * (1.0f + 0.5f * progress));
        }

        // 冷异常：受到的冻伤增加 0.5 * 进度数
        if (cap.hasAnomaly(AnomalyType.COLD) && isFrostDamage(event.getSource())) {
            int progress = cap.getProgress(AnomalyType.COLD);
            event.setAmount(event.getAmount() * (1.0f + 0.5f * progress));
        }
    }

    // ==================== 工具方法 ====================

    /** 判断是否为冻伤（freeze）伤害 */
    private static boolean isFrostDamage(net.minecraft.world.damagesource.DamageSource source) {
        return "freeze".equals(source.getMsgId());
    }

    /** 随机选择一个有耐久条的装备槽扣除耐久 */
    private static void damageRandomEquipment(LivingEntity entity, int amount) {
        List<EquipmentSlot> slots = Arrays.asList(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        );
        Collections.shuffle(slots);
        for (EquipmentSlot slot : slots) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.hurtAndBreak(amount, entity, e -> {});
                break;
            }
        }
    }
}
