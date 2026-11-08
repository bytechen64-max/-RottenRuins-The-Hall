package org.bytechen.rottenruins.utils.entity;

import net.minecraft.world.entity.LivingEntity;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/**
 * <b>直接改血量的小工具</b>：把目标的血改到某个值，如果改完已经死了就补一次真实的死亡流程。
 *
 * <h3>两个入口的区别</h3>
 * <ul>
 *   <li>{@link #setHealth} —— 走原版 {@code LivingEntity#setHealth}，
 *       值会被钳到 {@code [0, maxHealth]}；</li>
 *   <li>{@link #setData} —— 走同步数据字段 {@code DATA_HEALTH_ID}，<b>不钳制</b>，
 *       可以写成负数（用于"确保击杀"，避免 {@code 1.0E-7} 这种残血让生物不死）。</li>
 * </ul>
 *
 * <h3>修掉的 bug（重要）</h3>
 * <p>原来这两个方法的收尾判断写的是</p>
 * <pre>{@code
 * if (target.getHealth() >= 0 && !target.isRemoved()) target.die(...);
 * }</pre>
 * <p>{@code getHealth() >= 0} 几乎<b>恒为真</b>（血量永远不小于 0），
 * 于是不管改成多少血，目标都会被 {@code die()} 立刻打死 —— 这两个方法实际等于"秒杀工具"，
 * 而不是"改血到某个值、死了才收尾"。现在改成 {@code <= 0}。</p>
 *
 * <p>另外补了两条健壮性：{@code source == null} 不再 NPE（退化成 magic 来源），
 * {@code target == null} 直接返回。</p>
 */
public class SetHealthDieUnti {

    /**
     * 把血量设成 {@code count}（原版会钳到 {@code [0, maxHealth]}），死了就补死亡流程。
     */
    public static void setHealth(LivingEntity target, LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        target.setHealth(count);
        dieIfDead(target, source);
    }

    /**
     * 把血量<b>直接写成</b> {@code count}（不钳制，可负），死了就补死亡流程。
     *
     * <p>比 {@link #setHealth} 少一层钳制，所以能写 0 以下 —— 某些模组的血量存储会做
     * "±残值"处理，写到 0 附近可能留下"看着是 0、其实大于 0"的值，生物于是永远不死。</p>
     */
    public static void setData(LivingEntity target, LivingEntity source, float count) {
        if (target == null) {
            return;
        }
        target.entityData.set(DATA_HEALTH_ID, count);
        dieIfDead(target, source);
    }

    /**
     * 血量已落到 0 或以下、且还没走过死亡流程 → 用带攻击者的来源补一次 {@code die}。
     *
     * <p>死亡流程本身（{@code LivingDeathEvent} → 死亡消息 → {@code dropAllDeathLoot}
     * → 移除 → 经验）全部在 {@code die(DamageSource)} 里，所以掉落与击杀归因都照常。</p>
     */
    private static void dieIfDead(LivingEntity target, LivingEntity source) {
        if (target.isRemoved() || target.getHealth() > 0.0F) {
            return;
        }
        target.die(source == null
                ? target.damageSources().magic()
                : target.damageSources().mobAttack(source));
    }
}
