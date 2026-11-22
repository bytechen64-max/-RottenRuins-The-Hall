package org.bytechen.rottenruins.utils.entity;

import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/** 改血小工具：改完已死就补真实死亡流程。setHealth 钳到 [0,maxHealth]，setData 走同步字段不钳制（可写负数）。 */
public class SetHealthDieUnti {

    public static void setHealth(LivingEntity target, LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        float before = target.getHealth();
        target.setHealth(count);
        report(target, source, before - target.getHealth());
        dieIfDead(target, source);
    }

    /** 不经钳制直接写 DATA_HEALTH_ID（可写负值，避免 1.0E-7 这种残血让生物不死），死了补死亡流程。 */
    public static void setData(LivingEntity target, LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        float before = target.getHealth();
        target.entityData.set(DATA_HEALTH_ID, count);
        report(target, source, before - target.getHealth());
        dieIfDead(target, source);
    }

    /** 只把"掉血"报到 {@link DirectDamageHooks}，回血与无变化都忽略。 */
    private static void report(LivingEntity target, @Nullable LivingEntity source, float delta) {
        if (delta > 0.0F) {
            DirectDamageHooks.fire(target, source, delta);
        }
    }

    private static void dieIfDead(LivingEntity target, LivingEntity source) {
        if (target.isRemoved() || target.getHealth() > 0.0F||target.isAlive()) {
            return;
        }
        target.die(source == null
                ? target.damageSources().magic()
                : target.damageSources().mobAttack(source));
    }
}
