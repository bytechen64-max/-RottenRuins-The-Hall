package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.utils.entity.DirectHealth;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;

/**
 * 虚空剑的蹲下左键能力：直接把目标血量改成 {@code -1}，再补一次终结击走完死亡流程。
 * 写 -1 而非"打一堆伤害"：血量落在 {@code 1.0E-7} 这种"看着是 0、其实大于 0"的残值时生物永远不死。
 */
public final class VoidSwordGuard {

    /** 固定目标血量：{@code -1} 既 {@code <= 0}（一定死），又不是会被某些模组的 {@code setHealth} 实现钳回来的边界值。 */
    public static final float KILL_HEALTH = -1.0f;

    /** 「玩家 → 本 tick 已结算的目标」：三条路可能同 tick 各来一次，改血幂等，要挡的是同 tick 结算两次。 */
    private static final java.util.Map<Player, TargetTick> LAST_STRIKE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private record TargetTick(int tick, LivingEntity target) {}

    private VoidSwordGuard() {}

    /** 主手是不是虚空剑。只看主手 —— 这是一把主手武器，副手拿着不该触发。 */
    public static boolean isWielding(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getItem() instanceof VoidSword;
    }

    /** 蹲下 + 主手虚空剑，就是这条能力的触发姿态。 */
    public static boolean isArmed(Player player) {
        return player != null && player.isShiftKeyDown() && isWielding(player);
    }

    /** 事件入口，只在服务端调用：同 tick 去重在此做，保证一次左键只结算一次。返回本次是否真的改了血。 */
    public static boolean executeInstantKill(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return false;
        if (attacker.level().isClientSide()) return false;
        if (!target.isAlive()) return false;
        if (!isArmed(attacker)) return false;
        if (!claim(attacker, target)) return false;
        return apply(attacker, target);
    }

    private static boolean claim(Player attacker, LivingEntity target) {
        TargetTick last = LAST_STRIKE.get(attacker);
        if (last != null && last.tick() == attacker.tickCount && last.target() == target) {
            return false;
        }
        LAST_STRIKE.put(attacker, new TargetTick(attacker.tickCount, target));
        return true;
    }

    /** 真正的结算：清无敌帧 → 写 -1 → 归因 → 补刀 → 特效。拆出来是因为补刀路（forceSelectionOnHurt）不该再抢 tick。 */
    static boolean apply(Player attacker, LivingEntity target) {
        DamageSource source = attacker.damageSources().playerAttack(attacker);

        // ① 清无敌帧：不清的话目标 20 tick 内挨过任何一下，接下来的 hurt 会被原版直接吃掉
        DirectHealth.clearHurtGuard(target);

        // ② 直写血量到 -1：绕过护甲/抗性/无敌帧/抗改血实现
        boolean wrote = DirectHealth.setHealthToMinusOne(target);

        if (!wrote) {
            writeHealthDirect(target, KILL_HEALTH);
        }

        attributeKill(attacker, target);

        boolean died = finishOff(target, source);
        playFeedback(attacker, target, died);

        return died || wrote;
    }

    /** 在 {@code LivingHurtEvent} 上兜底：伤害被别的模组取消时把伤害补回来；不改 {@code setAmount}（金额减免是另一回事）。 */
    public static boolean forceSelectionOnHurt(LivingEntity target, DamageSource source, boolean canceled) {
        if (!canceled || target == null || source == null) return false;
        if (target.level().isClientSide()) return false;
        if (!target.isAlive()) return false;
        if (!(source.getEntity() instanceof Player attacker)) return false;
        if (!isArmed(attacker)) return false;

        return apply(attacker, target);
    }

    /** 直写血量的最后兜底：原版 {@code setHealth} 会钳到 {@code [0, max]}，拿不到 {@code -1}，所以要再用 {@code hurt} 推一下。 */
    private static void writeHealthDirect(LivingEntity target, float value) {
        try {
            float before = target.getHealth();
            if (!Float.isNaN(before)) {
                target.setHealth(value);
            }
        } catch (Throwable ignored) {
        }
        if (target.getHealth() <= 0.0f) return;
        try {
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().genericKill(), Float.MAX_VALUE);
        } catch (Throwable ignored) {
        }
    }

    /** 让这一刀算在攻击者头上（否则没有经验、掉落归属和成就）。 */
    private static void attributeKill(Player attacker, LivingEntity target) {
        try {
            target.setLastHurtByPlayer(attacker);
            target.setLastHurtByMob(attacker);
        } catch (Throwable ignored) {
        }
    }

    /** 终结击：血量已是 {@code <= 0}，再走一遍真实死亡流程，保证掉落物、死亡事件、Boss 血条照常发生。 */
    private static boolean finishOff(LivingEntity target, DamageSource source) {
        try {
            target.invulnerableTime = 0;
            target.hurt(source, Float.MAX_VALUE);
        } catch (Throwable ignored) {
        }
        if (target.isAlive()) {
            try {
                target.kill();
            } catch (Throwable ignored) {
            }
        }
        return !target.isAlive();
    }

    /** 命中反馈：一道虚空剑气 + 一声重击，纯表现层，失败也不影响斩杀。
     *  按「玩家 + 目标 + tick」去重（改血幂等而特效不幂等，否则会连炸三道剑气响三声雷）；
     *  只有确实带走目标才演出，打在无敌 / 免疫目标上不额外加戏。 */
    private static void playFeedback(Player attacker, LivingEntity target, boolean died) {
        if (!died) return;
        if (!claimFeedback(attacker, target)) return;

        try {
            SwordAuraEntity.spawn(attacker.level(), target.getBoundingBox().getCenter(),
                    2.5f, 50, 1.6f);
        } catch (Throwable ignored) {
        }
        try {
            attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
        } catch (Throwable ignored) {
        }
    }

    /** 特效去重账本。与 {@link #LAST_STRIKE} 分开记：那份是"改血结算权"，这份是"演出权"，两者可能各自成立。 */
    private static final java.util.Map<Player, TargetTick> LAST_FEEDBACK =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 同一 tick 内对同一目标的第二次演出请求返回 false。 */
    private static boolean claimFeedback(Player attacker, LivingEntity target) {
        TargetTick last = LAST_FEEDBACK.get(attacker);
        if (last != null && last.tick() == attacker.tickCount && last.target() == target) {
            return false;
        }
        LAST_FEEDBACK.put(attacker, new TargetTick(attacker.tickCount, target));
        return true;
    }
}
