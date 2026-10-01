package org.bytechen.hall.overworld.registry.items.verdict;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 天穹裁决 —— 裁决系伤害的<b>统一入口</b>。
 *
 * <h3>伤害构成</h3>
 * <pre>
 *   25 点走原版 {@code hurt}      —— 吃护甲、抗性、无敌帧、附魔、击退、仇恨
 *    5 点走原版 {@code setHealth} —— 绕过护甲/抗性/无敌帧，直接削有效血量
 * </pre>
 * 两者相加就是这把剑对外的「30 有效伤害」。
 *
 * <h3>为什么不调用 BCCore</h3>
 * <p>本类<b>刻意</b>只走原版 {@link LivingEntity#setHealth(float)}，不走
 * {@code org.bytechen.hall.compat.BCCoreCompat#damage}。原因：</p>
 * <ul>
 *   <li>天穹裁决是<b>次毕业级</b>武器，且要能在"只装本模组"的世界里独立成立 ——
 *       零依赖是它的定位，不是妥协；</li>
 *   <li>对<b>覆写</b> {@code getHealth()} / {@code setHealth()} 的模组生物，
 *       原版 {@code setHealth} 会正常调到它的覆写，所以这一类照样打得动；</li>
 *   <li>真正的能力边界落在"血量存自定义存储、{@code setHealth} 被完全架空"的生物上
 *       —— 那种目标是<b>虚空剑 + BCCore</b> 的活。这条分层是有意留的。</li>
 * </ul>
 *
 * <h3>三个必须小心的点（都是踩过的坑）</h3>
 * <ol>
 *   <li><b>不钳制。</b>算出的新血量允许 ≤0。项目里 {@code AnomalyEventHandler}
 *       第 96 行的 {@code setHealth(Math.max(1f, ...))} 是<b>刻意</b>给异常状态留 1 点血
 *       （异常不该直接秒人）—— 技能伤害<b>绝不能</b>照抄那个写法，
 *       否则 1 血生物永远打不死。</li>
 *   <li><b>原版 setHealth 不触发死亡。</b>它会把血量钳到 [0, max]，但不会走死亡流程，
 *       所以血量归零后<b>必须</b>补一次终结击，否则会留下 0 血站着的怪。</li>
 *   <li><b>创造豁免要自己挡。</b>原版 {@code hurt} 有 {@code isInvulnerableTo} 挡创造模式，
 *       但 {@code setHealth} 是直写血量、绕过那一层 —— 不自己挡的话，
 *       创造模式开测试会被自己的技能打死。</li>
 * </ol>
 */
public final class VerdictDamage {

    /** 走原版 hurt 的那部分（吃护甲/抗性/无敌帧）。 */
    public static final float HURT_PART = 25.0f;
    /** 走原版 setHealth 的那部分（绕过护甲/抗性/无敌帧）。 */
    public static final float BYPASS_PART = 5.0f;
    /** 一个完整"裁决击"的有效伤害 = 25 + 5 = 30。 */
    public static final float TOTAL = HURT_PART + BYPASS_PART;

    // ── 三个技能各自的强度 ────────────────────────────────────────
    //  三者<b>共用一个冷却池</b>（见 VerdictCooldown），所以哪怕强度不同，
    //  同一时间内也只可能有一招在生效 —— 用技能本身"值一轮冷却"，而不是靠冷却去封。
    //
    //  刻意都<b>不</b>乘高度系数：伤害轴已由普攻定义，高度只给覆盖。

    /** ① 天穹裁决 · 光柱：单点略弱于平砍，靠"一条竖线上所有敌人同时各吃一下"换收益。 */
    public static final float BEAM_HURT = 20.0f;

    /** ② 截空 · 凌空斩：突进途中命中，风险最高（打进怪堆里），所以伤害最高。 */
    public static final float DASH_HURT = 25.0f;

    /** ③ 裁决领域 · 剑气：单次最低，但一个领域能打十秒、多目标，总量最高。 */
    public static final float FIELD_HURT = 18.0f;

    private VerdictDamage() {}

    /**
     * 造成一次裁决系伤害。
     *
     * <p>结算顺序刻意如此：<b>先 hurt(25)，再 setHealth(当前 - 5)</b>。</p>
     * <ul>
     *   <li>反过来（先 setHealth 再 hurt）时，5 点先削过血量，25 点的 hurt 便打在
     *       更低的血线上，{@code after} 判定也跟着错位；</li>
     *   <li>按当前顺序，两道伤害各自对"当时真实的血量"生效，任一道把血打到 ≤0
     *       都能被同一条终结逻辑收掉。</li>
     * </ul>
     *
     * <p><b>只在服务端调用。</b></p>
     *
     * @param target 目标
     * @param source 伤害来源；为 null 时退化为 magic
     * @param hurtAmount   走 hurt 的部分，U 为一击标准值时传 {@link #HURT_PART}
     * @param bypassAmount 走 setHealth 的部分，U 为一击标准值时传 {@link #BYPASS_PART}
     * @param attacker     归因用（击杀统计/掉落/成就）；可为 null
     * @return 本次是否真的造成了伤害
     */
    public static boolean strike(@Nullable LivingEntity target,
                                 @Nullable DamageSource source,
                                 float hurtAmount,
                                 float bypassAmount,
                                 @Nullable Player attacker) {
        if (target == null || !target.isAlive()) return false;
        if (hurtAmount <= 0f && bypassAmount <= 0f) return false;

        // ── 创造 / 旁观豁免：setHealth 是直写血量，绕过了原版 isInvulnerableTo 那一道 ──
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        DamageSource src = source != null ? source : target.damageSources().magic();

        boolean dealt = false;
        boolean dead = false;

        // ── ① 原版伤害：吃护甲、抗性、无敌帧、附魔 ──
        if (hurtAmount > 0f) {
            float before = target.getHealth();
            target.hurt(src, hurtAmount);
            // 用"血量是否真的下降"判断，而不是 hurt 的返回值 —— 被盾牌挡下、
            // 被无敌帧吃掉、被事件取消，都会体现在血量上，但返回值语义各家不一。
            if (target.getHealth() < before) dealt = true;
            if (!target.isAlive()) dead = true;
        }

        // ── ② 直写血量：绕过护甲/抗性/无敌帧 ──
        if (!dead && bypassAmount > 0f) {
            float before = target.getHealth();
            float after = before - bypassAmount;      // 故意不钳制，允许 ≤0

            if (Float.isFinite(before)) {
                target.setHealth(after);              // 原版会钳到 [0, max]，但不会触发死亡
                if (target.getHealth() < before) dealt = true;
                // 原版 setHealth 不会走死亡流程：血量归零后必须自己补刀，
                // 否则会留下"0 血站着、永远不死"的怪（BCCoreCompat 注释里记的就是这回事）。
                if (after <= 0f || target.getHealth() <= 0f) dead = true;
            }
        }

        // ── 归因：让击杀统计、掉落、成就认这个玩家 ──
        // 走 setHealth 那半程完全绕过了原版 hurt 内部的归因逻辑，
        // 不显式补一次的话，靠 setHealth 打死的怪不算玩家的击杀。
        if (attacker != null && dealt) {
            try {
                target.setLastHurtByPlayer(attacker);
                target.setLastHurtByMob(attacker);
            } catch (Throwable ignored) {
                // 归因是尽力而为，失败不影响伤害本身
            }
        }

        if (dead) finishOff(target, src);
        return dealt;
    }

    /**
     * 标准一击：25 hurt + 5 setHealth。
     *
     * <p>灼痕与领域标记<b>不再</b>追加任何 setHealth（已按决策移除），
     * 所以这是全套裁决系技能唯一的伤害形状 —— 差异只在覆盖形状、命中数与冷却。</p>
     */
    public static boolean strike(@Nullable LivingEntity target,
                                 @Nullable DamageSource source,
                                 @Nullable Player attacker) {
        return strike(target, source, HURT_PART, BYPASS_PART, attacker);
    }

    /**
     * 终结击：血量已被写到 ≤0，这里补一遍真实的死亡流程，
     * 保证掉落物、死亡事件、Boss 血条等原版行为照常发生。
     *
     * <p>与 {@code BCCoreCompat#finishOff} 同款做法：先 {@code hurt(Float.MAX_VALUE)}，
     * 还不死再 {@code kill()}。</p>
     */
    private static void finishOff(LivingEntity target, DamageSource source) {
        if (!target.isAlive()) return;
        try {
            target.hurt(source, Float.MAX_VALUE);
        } catch (Throwable ignored) {
            // 继续尝试别的路
        }
        if (target.isAlive()) {
            try {
                target.kill();
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
    }
}
