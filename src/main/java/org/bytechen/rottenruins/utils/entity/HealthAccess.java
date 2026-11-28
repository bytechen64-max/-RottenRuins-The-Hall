package org.bytechen.rottenruins.utils.entity;

import javax.annotation.Nullable;

import org.bytechen.rottenruins.utils.ModUtils;
import org.bytechen.rottenruins.utils.health.HealthEngine;
import org.bytechen.rottenruins.utils.health.runtime.HealthController;
import org.bytechen.rottenruins.utils.health.runtime.AccessStrength;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「真血量」读写通道，现在是 bccore 改血引擎（{@link HealthController}）的门面。
 *
 * <p>引擎对目标实体类的 {@code getHealth()} 做字节码分析，逆推出血量真正存放的位置
 * （同步字段 / 普通字段 / Map 条目 / setter），按「反射 → VarHandle → Unsafe」逐级加强，
 * 每次写入都回读校验、失败还原并换下一个方案，最后才退回原版 {@code setHealth}。</p>
 *
 * <p>调用方（{@link DirectHealth}、{@link SetHealthDieUnti}）只关心这里的四个方法，
 * 所以换引擎不需要动它们。必须在服务端线程调用。</p>
 */
public final class HealthAccess {

    private HealthAccess() {}

    /** 读血量：游戏自己认的那个值（负血也会如实返回）。 */
    public static float read(@Nullable LivingEntity entity) {
        if (entity == null) {
            return Float.NaN;
        }
        ensureInstalled();
        try {
            float health = HealthController.INSTANCE.readHealth(entity);
            return Float.isNaN(health) ? entity.getHealth() : health;
        } catch (Throwable failure) {
            ModUtils.LOGGER.debug("[HealthAccess] 读血量失败，退回原版 getHealth", failure);
            return entity.getHealth();
        }
    }

    /** 引擎是否可用（只用于自检与日志）。 */
    public static boolean isResolved() {
        ensureInstalled();
        return HealthEngine.isInstalled();
    }

    /**
     * 把血量写成 {@code value}（不钳制，可写负值）。
     *
     * @return 回读值与目标值一致时为 {@code true}
     */
    public static boolean write(@Nullable LivingEntity entity, float value) {
        if (entity == null || Float.isNaN(value)) {
            return false;
        }
        ensureInstalled();
        try {
            HealthController.Outcome outcome = HealthController.INSTANCE.setHealth(entity, value);
            if (!outcome.success()) {
                ModUtils.LOGGER.debug("[HealthAccess] 写入未命中：{}（{} -> {}，要求 {}）",
                        outcome.strategy(), outcome.before(), outcome.after(), value);
            }
            return outcome.success();
        } catch (Throwable failure) {
            ModUtils.LOGGER.warn("[HealthAccess] 写入异常，退回原版 setHealth", failure);
            try {
                entity.setHealth(value);
            } catch (Throwable ignored) {
                return false;
            }
            return Math.abs(entity.getHealth() - value) < 0.01F;
        }
    }

    /**
     * 血量归零并走一次原版 {@code die(source)}：战利品、死亡事件、Boss 血条、死亡消息全部照常。
     * 已经在死亡/移除流程里的实体跳过，避免重复掉落。
     */
    public static void kill(@Nullable LivingEntity target, @Nullable DamageSource source) {
        if (target == null || target.isRemoved() || target.dead) {
            return;
        }
        if (target.getHealth() > 0.0F) {
            write(target, 0.0F);
        }
        DamageSource cause = source != null ? source : target.damageSources().genericKill();
        try {
            target.die(cause);
        } catch (Throwable failure) {
            ModUtils.LOGGER.error("[HealthAccess] die() 抛异常，退化为移除实体（这次可能没有掉落）", failure);
            try {
                target.kill();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 调试用：引擎当前强度与初始化状态。 */
    public static String describe() {
        ensureInstalled();
        AccessStrength strength = HealthController.INSTANCE.strength();
        return (HealthEngine.isInstalled() ? "ready" : "uninstalled") + "@" + strength;
    }

    /** 调试用：某个实体类的分析结果与候选写入方案。 */
    public static String describe(@Nullable Class<?> type) {
        if (type == null) {
            return "unresolved";
        }
        ensureInstalled();
        try {
            return HealthController.INSTANCE.describe(type);
        } catch (Throwable failure) {
            return "describe failed: " + failure;
        }
    }

    /** 兜底：万一 common setup 没跑到（比如只在数据生成里用到），第一次调用时补装一次。 */
    private static void ensureInstalled() {
        if (!HealthEngine.isInstalled()) {
            HealthEngine.install();
        }
    }
}
