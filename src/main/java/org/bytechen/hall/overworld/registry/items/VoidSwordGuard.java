package org.bytechen.hall.overworld.registry.items;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.compat.BCCoreCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;

/**
 * 虚空剑的<b>蹲下左键</b>能力：把目标的生命值直接改成 {@code -1}。
 *
 * <h3>能力构成</h3>
 * <pre>
 *   蹲下 + 手持虚空剑 + 左键点中生物
 *     → VitalProbe 把目标的有效血量精确置为 -1（负数，不是 0，不留浮点残值）
 *     → 补一次终结击，让死亡流程 / 掉落 / 击杀归因照常发生
 *     → 剑气特效 + 音效
 * </pre>
 *
 * <h3>为什么是 {@code -1} 而不是"打一堆伤害"</h3>
 * <p>写负数不是取巧：血量落在 {@code 1.0E-7} 这种"看着是 0、其实大于 0"的残值时，
 * 生物会永远不死。写 {@code -1} 之后，"血量 {@code <= 0}"这件事一定有确定的结果，
 * 终结击也就一定能收掉它。{@link BCCoreCompat#setHealthToMinusOne} 里记着完整理由。</p>
 *
 * <h3>为什么要"无视取消 hurt"</h3>
 * <p>本模组的其它武器都靠原版 {@code Player.attack} 走完伤害结算，可这条链路会被层层挡下：</p>
 * <ul>
 *   <li>{@code invulnerableTime}（原版无敌帧）—— 20 tick 内的第二刀直接返回 {@code false}；</li>
 *   <li>{@code LivingHurtEvent} 被别的模组 {@code setCanceled(true)} —— 掉血整段被砍掉；</li>
 *   <li>{@code hurt} 被目标覆写成"我不吃伤害"（Boss 的限伤 / 无敌阶段）。</li>
 * </ul>
 * <p>这条能力的定位是"强制选中"：命中判定一旦成立，血量就必须落到 {@code -1}。
 * 所以顺序是「先清无敌帧 → 再直写血量 → 最后才走 {@code hurt} 收尾」，
 * 而不是"打一下看看掉不掉血"。{@link #executeInstantKill} 与
 * {@link #forceSelectionOnHurt} 分别在 {@code AttackEntityEvent} 与
 * {@code LivingHurtEvent} 两条路上各兜一道。</p>
 *
 * <h3>为什么 {@code AttackEntityEvent} 不被取消</h3>
 * <p>理由与 {@link CrimsonVowAttack} 完全相同：取消事件会让 {@code Player.attack} 提前 return，
 * 受击红闪、击退、受击音、出刀落剑全部消失。这里只做"叠加"，原版那一下照常结算 ——
 * 反正血量已经被写到 {@code -1}，原版结算只会再确认一次死亡。</p>
 */
public final class VoidSwordGuard {

    /**
     * 固定的目标血量。
     *
     * <p>{@code -1}：既 {@code <= 0}（一定会死），又不是"刚好 0"那种会被某些模组
     * 的 {@code setHealth} 实现钳回来的边界值。</p>
     */
    public static final float KILL_HEALTH = -1.0f;

    /**
     * 「玩家 → 本 tick 已经结算过的目标」。
     *
     * <p>{@code AttackEntityEvent} 与客户端包会在同一 tick 各触发一次
     * （见 {@link org.bytechen.hall.network.c2s.VoidSwordStrikePacket}），
     * {@code LivingHurtEvent} 上的补刀路（{@link #forceSelectionOnHurt}）
     * 也可能对同一个目标再来一次。改血本身是幂等的（写 {@code -1} 就是 {@code -1}），
     * 需要挡的是<b>同一 tick 结算两次</b>。</p>
     *
     * <p>用 {@link java.util.WeakHashMap} 避免长期持有玩家对象（键弱、值里对实体的
     * 强引用随键一起回收）。</p>
     */
    private static final java.util.Map<Player, TargetTick> LAST_STRIKE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 去重记录：tick + 目标。 */
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

    /**
     * 事件入口：蹲下左键命中生物。
     *
     * <p><b>只在服务端调用</b>（{@code AttackEntityEvent} 本身也只在服务端触发）。
     * 原版伤害照常在事件返回后结算，这里只负责把血量钉死。</p>
     *
     * <p>同 tick 去重在这里做：原版链路（{@code AttackEntityEvent}）与自有链路
     * （{@link org.bytechen.hall.network.c2s.VoidSwordStrikePacket}）会同时触发，
     * 去重保证一次左键只结算一次。</p>
     *
     * @return 本次是否真的执行了一次强制改血
     */
    public static boolean executeInstantKill(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return false;
        if (attacker.level().isClientSide()) return false;
        if (!target.isAlive()) return false;
        if (!isArmed(attacker)) return false;
        if (!claim(attacker, target)) return false;
        return apply(attacker, target);
    }

    /**
     * 抢占"这个玩家 + 这个目标在本 tick 的结算权"。同样的组合同 tick 只成功一次。
     *
     * <p>不按"玩家"单独去重：一次 {@code Player.attack} 只打一个目标，
     * 但同一 tick 里不同目标（攻速很高的武器连续挥）应当各自成立。</p>
     */
    private static boolean claim(Player attacker, LivingEntity target) {
        TargetTick last = LAST_STRIKE.get(attacker);
        if (last != null && last.tick() == attacker.tickCount && last.target() == target) {
            return false;
        }
        LAST_STRIKE.put(attacker, new TargetTick(attacker.tickCount, target));
        return true;
    }

    /**
     * 真正的结算：清无敌帧 → 写 {@code -1} → 终结击 → 特效。
     *
     * <p>不与 {@link #executeInstantKill} 合并，是因为"无视取消 hurt"那条路
     * （{@link #forceSelectionOnHurt}）是<b>补刀</b>语义：它出现的时候这一次攻击
     * 已经被其它模组取消过一轮，不该再抢 tick —— 但要允许它把血量补到 {@code -1}；
     * 特效则靠 {@link #playFeedback} 自己的去重挡住第二次。</p>
     */
    static boolean apply(Player attacker, LivingEntity target) {
        DamageSource source = attacker.damageSources().playerAttack(attacker);

        // ── ① 清掉无敌帧与上一刀的时间戳：这是"无视"的第一层 ──
        // 不清的话，目标在 20 tick 内挨过任何一下，接下来的 hurt 会被原版直接吃掉。
        BCCoreCompat.clearHurtGuard(target);

        // ── ② VitalProbe 直写血量到 -1：绕过护甲/抗性/无敌帧/抗改血实现 ──
        boolean wrote = BCCoreCompat.setHealthToMinusOne(target);

        // 写入失败（VitalProbe 没装、或目标连逆向都写不动）时的兜底：
        // 走原版 hurt(-1) —— 至少让"负数血量"这件事在本模组自己能控制的范围内成立。
        if (!wrote) {
            writeHealthDirect(target, KILL_HEALTH);
        }

        // ── ③ 归因：直写血量完全绕过了原版的击杀归因，不补的话不算玩家的击杀 ──
        attributeKill(attacker, target);

        // ── ④ 补刀 + 特效。血量已经 <= 0，所以这一步基本一定会收掉它 ──
        boolean died = finishOff(target, source);
        playFeedback(attacker, target, died);

        return died || wrote;
    }

    /**
     * 在 {@code LivingHurtEvent} 上兜底：<b>伤害被别的模组取消时</b>把"强制选中"补回来。
     *
     * <p>为什么需要这一道：事件被取消只意味着"原版那一下不掉血"，
     * 玩家明明蹲着拿虚空剑砍到了目标，却什么都没发生，这条能力就名不副实了。
     * 所以只要①取消是的的确确发生在本玩家的攻击上、②玩家正处于触发姿态，
     * 就直接把血量钉到 {@code -1} —— 不做"累计伤害"这类中间态，语义就是一次斩杀。</p>
     *
     * <p>注意这里<b>不改 {@code setAmount}</b>：金额层面的减免（{@code WeaponBlock}
     * 那一类）是另一回事，让它们照常工作。这里只处理"整段被取消"。</p>
     *
     * @return 本次是否真的补了一次强制改血
     */
    public static boolean forceSelectionOnHurt(LivingEntity target, DamageSource source, boolean canceled) {
        if (!canceled || target == null || source == null) return false;
        if (target.level().isClientSide()) return false;
        if (!target.isAlive()) return false;
        // 只认玩家攻击：不是玩家打的伤害（摔落、火焰、别的模组技能）一概不碰
        if (!(source.getEntity() instanceof Player attacker)) return false;
        if (!isArmed(attacker)) return false;

        return apply(attacker, target);
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    /**
     * 直写血量的最后兜底。
     *
     * <p>原版 {@code setHealth} 会钳到 {@code [0, max]}，所以直接调它拿不到 {@code -1}。
     * 这里尽量绕过：先把血量打到 0，再用 {@code hurt} 推最后一下 ——
     * {@code hurt} 内部是 {@code setHealth(getHealth() - amount)}，通常能压到 0 以下。
     * 做不到就退化成"杀掉"，反正目标是"死透"，{@code -1} 只是执行手段。</p>
     */
    private static void writeHealthDirect(LivingEntity target, float value) {
        try {
            float before = target.getHealth();
            if (!Float.isNaN(before)) {
                target.setHealth(value);
            }
        } catch (Throwable ignored) {
            // 目标覆写了 setHealth 时可能抛异常，继续走 hurt
        }
        if (target.getHealth() <= 0.0f) return;
        try {
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().genericKill(), Float.MAX_VALUE);
        } catch (Throwable ignored) {
            // 尽力而为
        }
    }

    /** 让这一刀算在攻击者头上（否则没有经验、掉落归属和成就）。 */
    private static void attributeKill(Player attacker, LivingEntity target) {
        try {
            target.setLastHurtByPlayer(attacker);
            target.setLastHurtByMob(attacker);
        } catch (Throwable ignored) {
            // 归因是尽力而为，失败不影响斩杀本身
        }
    }

    /**
     * 终结击：血量已经被写到 {@code <= 0}，这里再走一遍真实的死亡流程，
     * 保证掉落物、死亡事件、Boss 血条等原版行为照常发生。
     *
     * <p>同 {@code BCCoreCompat.finishOff} / VitalProbe 自己 {@code VoidSwordHandler.finishOff}
     * 的做法：先 {@code hurt(Float.MAX_VALUE)}，还不死就 {@code kill()}。</p>
     *
     * @return 目标是否已经死亡
     */
    private static boolean finishOff(LivingEntity target, DamageSource source) {
        try {
            target.invulnerableTime = 0;
            target.hurt(source, Float.MAX_VALUE);
        } catch (Throwable ignored) {
            // 继续尝试 kill
        }
        if (target.isAlive()) {
            try {
                target.kill();
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
        return !target.isAlive();
    }

    /**
     * 命中反馈：一道虚空剑气 + 一声重击。纯表现层，失败也绝不影响斩杀。
     *
     * <h3>为什么要按目标去重</h3>
     * <p>三条路可能在同一 tick 都走到这里（原版事件、自有包、
     * {@code LivingHurtEvent} 上的补刀）。改血是幂等的，但特效不是 ——
     * 连着炸三道剑气、响三声雷，看得出是"重复触发"而不是"打了一刀"。
     * 所以按「玩家 + 目标 + tick」记一次账，同一 tick 只演一遍。</p>
     *
     * <h3>为什么要看 "是不是真的死了"</h3>
     * <p>只有这一刀<b>确实带走了目标</b>才炸剑气+雷声。打在无敌 / 免疫目标上时
     * 战斗文本还是靠原版的受击反馈，不额外加戏，免得每次左键都在耳边打雷。</p>
     */
    private static void playFeedback(Player attacker, LivingEntity target, boolean died) {
        if (!died) return;
        if (!claimFeedback(attacker, target)) return;

        try {
            SwordAuraEntity.spawn(attacker.level(), target.getBoundingBox().getCenter(),
                    2.5f, 50, 1.6f);
        } catch (Throwable ignored) {
            // 特效是尽力而为
        }
        try {
            attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
        } catch (Throwable ignored) {
            // 同上
        }
    }

    /**
     * 特效去重账本。
     *
     * <p>与 {@link #LAST_STRIKE} 分开记：那份是"改血结算权"，这份是"演出权"，
     * 两者可能各自成立（例如补刀路改了血但不是它演的）。</p>
     */
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