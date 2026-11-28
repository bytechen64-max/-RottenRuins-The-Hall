package org.bytechen.rottenruins.overworld.registry.items.verdict;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** 天穹裁决：25 点走原版 hurt（吃护甲/抗性/无敌帧），5 点直写 setHealth（绕过抗性，需自挡创造并补终结击）。 */
public final class VerdictDamage {

    /** 走原版 hurt 的那部分（吃护甲/抗性/无敌帧）。 */
    public static final float HURT_PART = 25.0f;
    /** 走原版 setHealth 的那部分（绕过护甲/抗性/无敌帧）。 */
    public static final float BYPASS_PART = 5.0f;
    public static final float TOTAL = HURT_PART + BYPASS_PART;

    /**
     * ② 截空 · 凌空斩：突进途中命中，风险最高（打进怪堆里），所以伤害最高；唯一空放不付全额冷却的一招。
     */
    public static final float DASH_HURT = 25.0f;

    /** ③ 裁决领域 · 剑气：单次最低，但一个领域能打十秒、多目标，总量最高。 */
    public static final float FIELD_HURT = 18.0f;

    public static final float BEAM_HURT_WAVE1 = 24.0f;
    public static final float BEAM_BYPASS_WAVE1 = 5.0f;
    public static final float BEAM_HURT_WAVE2 = 12.0f;
    public static final float BEAM_BYPASS_WAVE2 = 4.0f;

    private VerdictDamage() {}

    /** 仅服务端调用：先 hurt 再 setHealth，各自对当时真实血量结算，≤0 时统一补终结击。 */
    public static boolean strike(@Nullable LivingEntity target,
                                 @Nullable DamageSource source,
                                 float hurtAmount,
                                 float bypassAmount,
                                 @Nullable Player attacker) {
        if (target == null || !target.isAlive()) return false;
        if (hurtAmount <= 0f && bypassAmount <= 0f) return false;

        // 创造/旁观豁免：setHealth 直写血量，绕过了原版 isInvulnerableTo 那一道
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        DamageSource src = source != null ? source : target.damageSources().magic();

        boolean dealt = false;
        boolean dead = false;

        if (hurtAmount > 0f) {
            float before = target.getHealth();
            target.hurt(src, hurtAmount);
            // 用血量是否真降判断，而不是 hurt 返回值（盾牌/无敌帧/事件取消的返回值语义各家不一）
            if (target.getHealth() < before) dealt = true;
            if (!target.isAlive()) dead = true;
        }

        if (!dead && bypassAmount > 0f) {
            float before = target.getHealth();
            float after = before - bypassAmount;      // 故意不钳制，允许 ≤0

            if (Float.isFinite(before)) {
                target.setHealth(after);
                if (target.getHealth() < before) dealt = true;
                // setHealth 不走死亡流程，归零后必须补刀，否则留下 0 血站着的怪
                if (after <= 0f || target.getHealth() <= 0f) dead = true;
            }
        }

        // 归因：setHealth 那半程绕过 hurt 内部的归因，不补则击杀统计/掉落/成就算不到玩家
        if (attacker != null && dealt) {
            try {
                target.setLastHurtByPlayer(attacker);
                target.setLastHurtByMob(attacker);
            } catch (Throwable ignored) {
            }
        }

        if (dead) finishOff(target, src);
        return dealt;
    }

    /** 标准一击：25 hurt + 5 setHealth，全套裁决系技能唯一的伤害形状。 */
    public static boolean strike(@Nullable LivingEntity target,
                                 @Nullable DamageSource source,
                                 @Nullable Player attacker) {
        return strike(target, source, HURT_PART, BYPASS_PART, attacker);
    }

    /** 终结击：先 hurt(Float.MAX_VALUE) 再 kill()，保证掉落 / 死亡事件 / Boss 血条照常。 */
    private static void finishOff(LivingEntity target, DamageSource source) {
        if (!target.isAlive()) return;
        try {
            target.hurt(source, Float.MAX_VALUE);
        } catch (Throwable ignored) {
        }
        if (target.isAlive()) {
            try {
                target.kill();
            } catch (Throwable ignored) {
            }
        }
    }
}
