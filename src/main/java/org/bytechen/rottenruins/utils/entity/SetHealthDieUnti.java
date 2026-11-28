package org.bytechen.rottenruins.utils.entity;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/**
 * 改血小工具：写入走 {@link HealthAccess}（直接改原始血量字段），掉血记账进
 * {@link DirectDamageHooks}，血量归零时补一次真实死亡流程。
 */
public final class SetHealthDieUnti {

    private SetHealthDieUnti() {}

    /** 钳到 {@code [0, maxHealth]} 后写入。 */
    public static void setHealth(LivingEntity target, @Nullable LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        float before = HealthAccess.read(target);
        HealthAccess.write(target, Mth.clamp(count, 0.0F, target.getMaxHealth()));
        finish(target, source, before);
    }
    public static void setDataHealth(LivingEntity target, @Nullable LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        float before = HealthAccess.read(target);
        target.entityData.set(DATA_HEALTH_ID,count);
        finish(target, source, before);
    }
    /** 不钳制直接写原始血量（可写负值，避免 1.0E-7 这种残血让生物不死）。 */
    public static void setData(LivingEntity target, @Nullable LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        float before = HealthAccess.read(target);
        HealthAccess.write(target, count);
        finish(target, source, before);
    }

    /** 血量已归零就补一次真实死亡：掉落表、死亡事件、Boss 血条都要靠 {@code die()} 才会走。 */
    public static void dieIfDead(LivingEntity target, @Nullable LivingEntity source) {
        if (target == null || target.isRemoved() || target.dead) {
            return;
        }
        if (HealthAccess.read(target) > 0.0F) {
            return;
        }
        HealthAccess.kill(target, damageSource(target, source));
    }

    private static void finish(LivingEntity target, @Nullable LivingEntity source, float before) {
        report(target, source, before - HealthAccess.read(target));
        target.lastDamageSource = damageSource(target, source);
        dieIfDead(target, source);
    }

    private static DamageSource damageSource(LivingEntity target, @Nullable LivingEntity source) {
        return source == null ? target.damageSources().magic() : target.damageSources().mobAttack(source);
    }

    /** 只把「掉血」报到 {@link DirectDamageHooks}，回血与无变化都忽略。 */
    private static void report(LivingEntity target, @Nullable LivingEntity source, float delta) {
        if (delta > 0.0F) {
            DirectDamageHooks.fire(target, source, delta);
        }
    }
}
