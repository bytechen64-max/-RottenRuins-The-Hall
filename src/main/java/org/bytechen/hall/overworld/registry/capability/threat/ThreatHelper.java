package org.bytechen.hall.overworld.registry.capability.threat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;

/**
 * 威胁点数的读写入口。
 * <p>
 * 所有读写都走这里，避免各处散落 {@code getCapability(...).orElse(...)}。
 * 实体没有挂上能力时统一按 0 处理。
 */
public final class ThreatHelper {

    /** 王庭生物主动索敌玩家所需的威胁点数阈值（低于该值不会被主动锁定）。 */
    public static final int TARGET_THREAT_THRESHOLD = 5;

    /** 击杀一个王庭生物得到的威胁点数 = 其最大生命值 / 该系数。 */
    public static final int KILL_THREAT_DIVISOR = 5;

    private ThreatHelper() {
    }

    /** 读取威胁点数；无能力 → 0。 */
    public static int getThreat(Entity entity) {
        if (entity == null) return 0;
        return entity.getCapability(CapabilityRegistry.THREAT_CAP)
                .map(ThreatCapability::getThreatPoints)
                .orElse(0);
    }

    /** 写入威胁点数；无能力时静默忽略。 */
    public static void setThreat(Entity entity, int value) {
        if (entity == null) return;
        entity.getCapability(CapabilityRegistry.THREAT_CAP)
                .ifPresent(cap -> cap.setThreatPoints(value));
    }

    /** 增加威胁点数，返回增加后的值（无能力 → 原样返回 0）。 */
    public static int addThreat(Entity entity, int amount) {
        if (entity == null) return 0;
        return entity.getCapability(CapabilityRegistry.THREAT_CAP)
                .map(cap -> cap.addThreatPoints(amount))
                .orElse(0);
    }

    /** 清空威胁点数（玩家死亡时调用）。 */
    public static void resetThreat(Entity entity) {
        if (entity == null) return;
        entity.getCapability(CapabilityRegistry.THREAT_CAP)
                .ifPresent(ThreatCapability::reset);
    }

    /**
     * 击杀一个王庭生物应得的威胁点数：{@code 被击杀者最大生命值 / 5}（向下取整，至少 1）。
     */
    public static int threatFromKill(LivingEntity victim) {
        return Math.max(1, (int) (victim.getMaxHealth() / KILL_THREAT_DIVISOR));
    }

    /**
     * 该实体是否属于王庭阵营（{@link AbstractHallEntity} 即为王庭生物）。
     */
    public static boolean isHallCreature(Entity entity) {
        return entity instanceof AbstractHallEntity;
    }
}
