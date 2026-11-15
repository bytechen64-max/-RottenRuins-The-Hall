package org.bytechen.rottenruins.gameplay.anomaly;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.api.anomaly.AnomalyAttack;
import org.bytechen.rottenruins.api.anomaly.AnomalyDamageSource;
import org.bytechen.rottenruins.api.anomaly.AnomalyType;
import org.bytechen.rottenruins.core.anomaly.AnomalyState;
import org.bytechen.rottenruins.utils.entity.EntityBuffUtils;

/** Game orchestration shared by ordinary damage and explicit health-write skills. */
public final class AnomalyEngine {
    private AnomalyEngine() {}

    public static void onDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide() || !(amount > 0) || !victim.isAlive()) return;
        if (!(victim instanceof Player player) || player.isCreative() || player.isSpectator()) return;
        AnomalyRuntime.Services services = AnomalyRuntime.services();
        AnomalyAttack attack = services.attack(source);
        if (attack == null) return;
        AnomalyState<AnomalyType> state = services.state(victim);
        if (state == null) throw new IllegalStateException("Player anomaly capability missing");
        Entity owner = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        UUID attacker = source instanceof AnomalyDamageSource declared ? declared.attacker()
                : owner == null ? null : owner.getUUID();
        if (attacker == null) return;
        long now = victim.level().getGameTime();
        if (!state.canHit(attack.type(), attacker, now)) return;
        if (services.addProgress(victim, attack.type(), attack.amount())) {
            state.recordHit(attack.type(), attacker, now, attack.intervalTicks());
        }
    }

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide() || !entity.isAlive()) return;
        AnomalyState<AnomalyType> state = AnomalyRuntime.services().state(entity);
        if (state == null) return;
        state.tick(entity.level().getGameTime());
        if (entity.tickCount % 20 != 0) return;
        if (state.get(AnomalyType.HEAT) > 0) entity.hurt(entity.damageSources().inFire(), 1.0F);
        if (state.get(AnomalyType.COLD) > 0) entity.hurt(entity.damageSources().freeze(), 1.0F);
        int acid = state.get(AnomalyType.ACID);
        if (acid > 0 && entity.isAlive()) {
            // Acid keeps its health floor and durability formula, now once per second.
            entity.setHealth(Math.max(1.0F, entity.getHealth() - 1.0F));
            damageRandomEquipment(entity, 8 * acid);
        }
    }

    public static float modifyDamage(LivingEntity entity, DamageSource source, float amount) {
        AnomalyState<AnomalyType> state = AnomalyRuntime.services().state(entity);
        if (state == null) return amount;
        if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1.0F + 0.5F * state.get(AnomalyType.HEAT);
        if ("freeze".equals(source.getMsgId())) amount *= 1.0F + 0.5F * state.get(AnomalyType.COLD);
        return amount;
    }

    public static void trigger(LivingEntity entity, AnomalyType type) {
        if (entity.level().isClientSide()) return;
        float damage = entity.getMaxHealth() * 0.4F;
        switch (type) {
            case HEAT -> {
                entity.hurt(entity.damageSources().inFire(), damage);
                entity.setRemainingFireTicks(200);
            }
            case COLD -> {
                entity.hurt(entity.damageSources().freeze(), damage);
                EntityBuffUtils.addOrStackEffect(entity, MobEffects.MOVEMENT_SLOWDOWN, 1, 200);
                EntityBuffUtils.addOrStackEffect(entity, MobEffects.WEAKNESS, 1, 200);
            }
            case ACID -> entity.hurt(entity.damageSources().magic(), damage);
        }
    }

    private static void damageRandomEquipment(LivingEntity entity, int amount) {
        List<EquipmentSlot> slots = Arrays.asList(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
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
