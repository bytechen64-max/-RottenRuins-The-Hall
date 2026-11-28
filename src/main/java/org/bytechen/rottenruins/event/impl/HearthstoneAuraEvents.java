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
 * 王庭炉石的光环结算器：减伤 / 强化 / 削弱三条效果线统一在 {@link LivingHurtEvent} 落到伤害上。
 * 光环作用于别的生物，实体拿不到他人受伤事件，所以效果必须由本监听器兑现，实体只负责每 tick 登记。
 * 登记带 TTL，过期即失效，无需清理回调；所有数值都是 0~1 的比例/倍率，不是百分比。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HearthstoneAuraEvents {

    /** 登记的存活时长（tick）。炉石每 tick 刷新，2 秒足够宽松又不会残留太久。 */
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

    // 登记入口（由炉石每 tick 调用）

    /** 让某个生物受到的伤害减少 {@code fraction}（{@code 0~1}；{@code 0} 等于不登记）。 */
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
     * 让某个生物造成的伤害乘以 {@code multiplier}；强化与削弱共用这一条（{@code 1.0} 等于不登记）。
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

    // 结算

    /** 唯一的结算点：先乘攻击方输出倍率，再乘受击方减伤，两个乘子都夹到合法范围。 */
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
