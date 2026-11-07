package org.bytechen.rottenruins.event.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 王庭炉石的<b>光环结算器</b>：减伤、强化、削弱三条「效果线」都在这里落到伤害上。
 *
 * <h3>为什么要单独一个类，而不是写在实体里</h3>
 * <p>炉石的光环作用于<b>别的生物</b>（骸物队友与敌人），而"某个生物承受/输出多少伤害"
 * 只有一个统一的落点：{@link LivingHurtEvent}。实体本身拿不到别人的受伤事件，
 * 所以光环的实际效果必须由这个监听器兑现。实体只负责"每 tick 把该生效的数值登记进来"。</p>
 *
 * <h3>三条效果线</h3>
 * <table border="1">
 *   <tr><th>效果</th><th>作用对象</th><th>登记方式</th><th>事件里怎么用</th></tr>
 *   <tr><td>※减伤（光环）</td><td>范围内的骸物</td>
 *       <td>{@link #setIncomingReduction}</td><td>承伤 × (1 − 比例)</td></tr>
 *   <tr><td>强化</td><td>范围内的骸物</td>
 *       <td>{@link #setOutgoingMultiplier}</td><td>输出 × 倍率</td></tr>
 *   <tr><td>削弱</td><td>范围内的非骸物</td>
 *       <td>{@link #setOutgoingMultiplier}</td><td>输出 × 倍率</td></tr>
 * </table>
 *
 * <h3>过期机制</h3>
 * <p>登记时带一个 TTL（默认 2 秒）。炉石活着的时候每 tick 刷新一次，所以只要它还在、
 * 离得够近，效果就一直续着；一旦它死了、被移除、或者目标走出范围，登记自然过期，
 * <b>不需要</b>任何"清理"回调 —— 这是为了避免"炉石异常消失后光环永远挂在玩家身上"这种脏状态。</p>
 *
 * <p>所有数值都是 {@code 0~1} 的比例/倍率，不是百分比。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HearthstoneAuraEvents {

    /** 登记的存活时长（tick）。炉石每 tick 刷新，所以 2 秒足够宽松又不会残留太久。 */
    public static final int TTL_TICKS = 40;

    private static final Map<UUID, Entry> INCOMING_REDUCTION = new ConcurrentHashMap<>();
    private static final Map<UUID, Entry> OUTGOING_MULTIPLIER = new ConcurrentHashMap<>();

    private HearthstoneAuraEvents() {
    }

    /** 一条登记：数值 + 过期时刻（世界游戏时间）。 */
    private record Entry(float value, long expiresAt) {
        boolean expired(long now) {
            return now > expiresAt;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 登记入口（由炉石每 tick 调用）
    // ══════════════════════════════════════════════════════════════

    /**
     * 让某个生物受到的伤害减少 {@code fraction}。
     *
     * @param fraction {@code 0~1}；{@code 0} 等于不登记
     */
    public static void setIncomingReduction(LivingEntity target, float fraction) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        if (fraction <= 0.0F) {
            INCOMING_REDUCTION.remove(target.getUUID());
            return;
        }
        INCOMING_REDUCTION.put(target.getUUID(),
                new Entry(Math.min(1.0F, fraction), now(target.level())));
    }

    /**
     * 让某个生物造成的伤害乘以 {@code multiplier}。
     *
     * <p>强化与削弱共用这一条：强化给骸物一个 &gt;1 或按设计给定的倍率，
     * 削弱给敌人一个 &lt;1 的倍率（扣到 0 就是"完全打不出伤害"）。</p>
     *
     * @param multiplier 倍率；{@code 1.0} 等于不登记
     */
    public static void setOutgoingMultiplier(LivingEntity target, float multiplier) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        if (Math.abs(multiplier - 1.0F) < 1.0E-4F) {
            OUTGOING_MULTIPLIER.remove(target.getUUID());
            return;
        }
        OUTGOING_MULTIPLIER.put(target.getUUID(),
                new Entry(Math.max(0.0F, multiplier), now(target.level())));
    }

    /** 立刻清掉某个生物的两条登记（例如它被炉石"取消强化"时）。 */
    public static void clear(LivingEntity target) {
        if (target == null) {
            return;
        }
        INCOMING_REDUCTION.remove(target.getUUID());
        OUTGOING_MULTIPLIER.remove(target.getUUID());
    }

    /** 服务器停止 / 换世界时全清，避免跨存档残留。 */
    public static void clearAll() {
        INCOMING_REDUCTION.clear();
        OUTGOING_MULTIPLIER.clear();
    }

    private static long now(Level level) {
        return level.getGameTime() + TTL_TICKS;
    }

    // ══════════════════════════════════════════════════════════════
    // 结算
    // ══════════════════════════════════════════════════════════════

    /**
     * 唯一的结算点。
     *
     * <p>先算"输出"（攻击方倍率），再算"承伤"（受击方减伤）—— 顺序不影响结果，
     * 但固定下来更好排查。两个乘子都夹到合理范围，避免别的模组把倍率写进负数。</p>
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        long now = victim.level().getGameTime();
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }

        // ① 攻击方输出倍率（强化 / 削弱）
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            Entry out = OUTGOING_MULTIPLIER.get(livingAttacker.getUUID());
            if (out != null) {
                if (out.expired(now)) {
                    OUTGOING_MULTIPLIER.remove(livingAttacker.getUUID());
                } else if (out.value() != 1.0F) {
                    amount *= out.value();
                }
            }
        }

        // ② 受击方承伤减免（光环）
        Entry in = INCOMING_REDUCTION.get(victim.getUUID());
        if (in != null) {
            if (in.expired(now)) {
                INCOMING_REDUCTION.remove(victim.getUUID());
            } else if (in.value() > 0.0F) {
                amount *= Math.max(0.0F, 1.0F - in.value());
            }
        }

        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    /** 调试用：当前有多少条登记还在。 */
    public static String debugCounts() {
        return "incoming=" + INCOMING_REDUCTION.size() + ", outgoing=" + OUTGOING_MULTIPLIER.size();
    }
}
