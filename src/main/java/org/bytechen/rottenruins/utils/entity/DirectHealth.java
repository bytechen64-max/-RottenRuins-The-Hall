package org.bytechen.rottenruins.utils.entity;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 直接改写血量存储的工具：绕过无敌帧、护甲与抗性，用于「说打多少就打多少」的技能伤害。
 *
 * <p>读写都走 {@link HealthAccess}（ASM 定位出来的原始血量字段 + VarHandle），所以既不受
 * {@code getHealth()} 覆写影响，也不受 {@code setHealth()} 的 {@code [0, max]} 钳制。
 * 掉血会进 {@link DirectDamageHooks}，生命汲取一类的「按实际伤害结算」照旧生效；
 * 血量归零统一走 {@link HealthAccess#kill}，保证掉落与死亡事件正常。</p>
 *
 * <p>必须在服务端线程调用。</p>
 */
public final class DirectHealth {

    private DirectHealth() {}

    /** 读当前血量；实体为空时返回 {@link Float#NaN} 让调用方走兜底。 */
    public static float getHealth(@Nullable LivingEntity entity) {
        return entity == null ? Float.NaN : HealthAccess.read(entity);
    }

    /** 把血量精确置为 {@code value}（不钳制，允许 0 以下）。 */
    public static boolean setHealth(@Nullable LivingEntity entity, float value) {
        if (entity == null) {
            return false;
        }
        SetHealthDieUnti.setData(entity, null, value);
        return true;
    }

    /** 在现有血量上加减 {@code delta}。 */
    public static boolean addHealth(@Nullable LivingEntity entity, float delta) {
        if (entity == null) {
            return false;
        }
        float current = getHealth(entity);
        if (!Float.isFinite(current)) {
            return false;
        }
        return setHealth(entity, current + delta);
    }

    /** 虚空剑「蹲下左键」的斩杀值：写负数保证判定到 <= 0。 */
    public static boolean setHealthToMinusOne(@Nullable LivingEntity target) {
        return setHealth(target, -1.0F);
    }

    /**
     * 技能伤害统一入口：读血量 → 减 → 写回；血量 {@code <= 0} 时走真实死亡流程（有掉落）。
     * <p>创造/旁观玩家直接跳过（否则测试 boss 会被自己的技能打死）。
     */
    public static boolean damage(@Nullable LivingEntity target, float amount, @Nullable DamageSource source) {
        if (target == null || amount <= 0.0F || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        float before = getHealth(target);
        if (!Float.isFinite(before) || before <= 0.0F) {
            return false;
        }

        LivingEntity attacker = source != null && source.getEntity() instanceof LivingEntity living ? living : null;
        // setData 内部已负责掉血记账、lastDamageSource 与「归零即 die(source)」（掉落、死亡事件照常）
        SetHealthDieUnti.setData(target, attacker, before - amount);
        target.lastDamageSource = source != null ? source : target.lastDamageSource;

        // 直接写血量不会触发 LivingDamageEvent，异常伤害的记账要自己补一次。
        if (source != null) {
            org.bytechen.rottenruins.gameplay.anomaly.AnomalyEngine.onDamage(target, source, amount);
        }
        return true;
    }

    /** 按最大生命值的百分比改血。 */
    public static boolean damagePercentOfMax(@Nullable LivingEntity target, float percent,
                                             @Nullable DamageSource source) {
        if (target == null || percent <= 0.0F) {
            return false;
        }
        float max = target.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0.0F) {
            return false;
        }
        return damage(target, max * (percent / 100.0F), source);
    }

    /** 清掉无敌帧与受击红闪，让紧接着的第二次结算真的落下。 */
    public static void clearHurtGuard(@Nullable LivingEntity target) {
        if (target == null) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurtTime = 0;
    }

    /**
     * 血量已经写到 0 以下时，补一次真实死亡（掉落、死亡事件、boss 血条都靠它）。
     *
     * <p>这里<b>必须</b>先确认血量真的归零：{@link HealthAccess#kill} 是「无条件把血写 0 再 die」，
     * 以前在这里不加判断地调用，等于每次技能命中都直接处决目标（满血 warden 一刀清零就是这么来的）。
     * 只对读不到的实体放行，避免残血怪打不死。</p>
     */
    public static void finishOff(@Nullable LivingEntity target, @Nullable DamageSource source) {
        if (target == null || target.isRemoved() || target.dead) {
            return;
        }
        float health = HealthAccess.read(target);
        if (Float.isFinite(health) && health > 0.0F) {
            return;
        }
        HealthAccess.kill(target, source);
    }
}
