package org.bytechen.hall.overworld.registry.items;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.compat.BCCoreCompat;
import org.bytechen.hall.network.s2c.HeartLosePacket;

/**
 * {@link CrimsonVow} 的<b>攻击</b>：在原版伤害之上叠加 VitalProbe 改血，并打出失心粒子。
 *
 * <h3>伤害构成</h3>
 * <pre>
 *   一次攻击 = 玩家面板伤害（原版结算）
 *            + 常量 20 点
 *            + 目标最大生命值的 20%（VitalProbe 改血）
 * </pre>
 * <p>两段改血分开算再相加，而不是写成"最大生命值的 X% + Y"：这样常量那部分对
 * 高血量目标不会显得毫无意义，百分比那部分对低血量目标也不会过于致命。</p>
 *
 * <h3>为什么用 {@link BCCoreCompat#damage} 而不是 {@code target.hurt}</h3>
 * <p>同 {@code VoidSword} / 使徒技能：VitalProbe 的改血是直接写血量存储位置，
 * 因此<b>无视无敌帧、护甲、抗性、以及各种"覆写 getHealth 返回自定义血量"的抗改血实现</b>。
 * 这把剑的定位就是"改血武器"，普通 {@code hurt} 会被这些机制层层削掉。
 * VitalProbe 没装时会自动回退到原版 {@code hurt}（见 {@code BCCoreCompat.damage}），
 * 所以玩家只装本模组时这把剑依然能打死东西，只是打不穿抗改血生物。</p>
 *
 * <h3>为什么不取消 AttackEntityEvent</h3>
 * <p>查过 {@code Player.attack} 的字节码：事件派发（{@code ForgeHooks.onPlayerAttackTarget}）
 * 是<b>第二条指令</b>，返回 false 会直接跳过后面整段伤害结算。
 * 让原版那一下照常走，收益是：</p>
 * <ul>
 *   <li>受击红闪、模型后仰、击退、受击音、仇恨记录<b>全部由原版自己负责</b>，
 *       不需要在这里逐项手工复刻（早先的版本取消了事件，就得自己补，
 *       连受击音都要反射去拿 {@code protected} 的 {@code playHurtSound}）；</li>
 *   <li>攻击冷却、冲刺暴击、附魔加成这些原版逻辑也一并保留；</li>
 *   <li>原版 {@code Player.attack} 内部的 {@code stopUsingItem()}（出刀自动落剑）
 *       照常执行，不必自己补。</li>
 * </ul>
 *
 * <h3>为什么叠加不会变成"双重扣血"</h3>
 * <p>{@code BCCoreCompat.damage} 是<b>先读当前血量、再减去伤害写回</b>
 * （{@code BCHealth.get(target)} → {@code -amount} → {@code BCHealth.set}）。
 * 原版那一下扣完之后，这里读到的是已经扣过的血量，两次扣血是<b>串联</b>的，
 * 不会按同一份血量重复结算。</p>
 *
 * <h3>粒子</h3>
 * <p>命中后在受击者位置生成失心粒子，方向与模长由
 * {@link HeartLosePacket#sendBurst} 在服务端算好广播 —— 方向是
 * "攻击者 → 受击者"的单位向量，模长随两者距离衰减（越近越大）。</p>
 */
public final class CrimsonVowAttack {

    /** 固定伤害部分（叠加在原版玩家伤害之上）。 */
    public static final float FLAT_DAMAGE = 20.0f;

    /** 目标最大生命值百分比部分（20 = 20%）。 */
    public static final float PERCENT_OF_MAX_HEALTH = 20.0f;

    /**
     * 每次命中打出的失心粒子数量。
     *
     * <p>26 颗：粒子现在会在受击者<b>碰撞箱体积内</b>随机撒点、受正常重力并会撞地弹跳，
     * 数量太少（原来的 8）在体型稍大的目标上就看不出"炸开"的感觉。
     * 注意这是一次性 {@code addParticle} 的量，不是每 tick 生成 ——
     * 单次攻击 26 个粒子的开销可以忽略。</p>
     */
    public static final int HEART_LOSE_COUNT = 26;

    private CrimsonVowAttack() {}

    /**
     * 该玩家此刻是否正在用这把剑挥砍。
     * 只看主手 —— 这把剑的定位是主手武器，副手拿着不该触发。
     */
    public static boolean isWielding(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getItem() instanceof CrimsonVow;
    }

    /**
     * 事件入口：玩家挥刀打实体时调用，在原版伤害之上叠加改血。
     *
     * <p>必须在<b>服务端</b>调用（{@code AttackEntityEvent} 本身也只在服务端触发），
     * 且<b>不要</b>取消事件 —— 原版的受击反馈与落剑都靠它继续走完，见类注释。</p>
     *
     * @return true 表示叠加的改血伤害真的生效了
     */
    public static boolean onAttackLanded(Player player, Entity target) {
        if (player == null || target == null) return false;
        if (!isWielding(player)) return false;
        if (player.level().isClientSide()) return false;
        if (!(target instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;

        DamageSource source = player.damageSources().playerAttack(player);

        // ── ① 常量部分 ──
        boolean dealt = BCCoreCompat.damage(living, FLAT_DAMAGE, source);

        // ── ② 最大生命值百分比部分 ──
        //  放在后面：第一段改血没打死才补百分比，两段各自判一次终结击
        if (living.isAlive()) {
            dealt |= BCCoreCompat.damagePercentOfMax(living, PERCENT_OF_MAX_HEALTH, source);
        }

        if (!dealt) return false;

        // ── ③ 失心粒子：方向 攻击者→受击者，模长随距离衰减（越近越大）──
        HeartLosePacket.sendBurst(player, living, HEART_LOSE_COUNT);

        return true;
    }
}
