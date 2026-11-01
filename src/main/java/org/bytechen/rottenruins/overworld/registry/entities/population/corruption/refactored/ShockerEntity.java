package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseRefactoredEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * 震撼者（重构种）。
 *
 * <p>技能：<b>C6 过载脉冲</b> —— 原地蓄力 1.8 秒后以自身为中心释放全方位红雷环。
 * 蓄力期有粒子汇聚 + 头顶烟柱 + 升调音效；正确解法从"绕后"变成"在蓄力结束前拉开距离"。</p>
 */
public class ShockerEntity extends BaseRefactoredEntity {

    // =========================================================================
    // C6 过载脉冲
    // =========================================================================
    private static final int PULSE_COOLDOWN = 200;
    private static final int PULSE_WINDUP = 36;
    private static final double PULSE_RADIUS = 6.5;
    private static final float PULSE_MIN_DAMAGE = 6.0F;
    private static final float PULSE_DAMAGE = 10.0F;
    private static final double PULSE_MIN_RANGE = 2.5;

    private int pulseCooldown = 100;
    private int pulseWindup = -1;

    /** 近身范围攻击冷却（碰撞触发） */
    private int attackCooldown = 30;
    private int collisionAttackCooldown = 0;
    private boolean wasHorizCollided = false;
    private boolean wasVertCollided = false;

    public ShockerEntity(EntityType<? extends ShockerEntity> entityType, Level level,
                         Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.22F);
        this.setRunSpeed(0.29F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 38.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 近身：小范围横扫（原工程是 180° 圆柱扇区，这里简化成范围伤害）
        doAreaAttack();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        tickOverloadPulse();

        // 原有目标攻击冷却
        if (this.getTarget() != null && this.attackCooldown > 0) {
            attackCooldown--;
            if (attackCooldown <= 0) {
                doAreaAttack();
                attackCooldown = 30;
            }
        }

        if (collisionAttackCooldown > 0) collisionAttackCooldown--;

        // 撞到方块/障碍时触发一次范围攻击（防止被卡住白打）
        boolean horizCollision = this.horizontalCollision;
        boolean vertCollision = this.verticalCollision;
        if (collisionAttackCooldown <= 0) {
            if ((horizCollision && !wasHorizCollided) || (vertCollision && !wasVertCollided)) {
                doAreaAttack();
                collisionAttackCooldown = 5;
            }
        }
        wasHorizCollided = horizCollision;
        wasVertCollided = vertCollision;
    }

    /** 简化版范围横扫：半径 3.2、伤害 4~8（距离衰减）、击退 0.8。 */
    private void doAreaAttack() {
        CorruptionSkillUtil.areaDamage(this, 3.2D, 6.0F, 0.8D);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.RAVAGER_ATTACK, 1.0F, 0.8F, 0.1F);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 3.2,
                CorruptionSkillUtil.CORRUPTION_RED, 16);
    }

    private void tickOverloadPulse() {
        if (pulseCooldown > 0) pulseCooldown--;

        // 蓄力阶段
        if (pulseWindup >= 0) {
            pulseWindup--;

            float progress = 1.0F - (float) pulseWindup / PULSE_WINDUP;
            CorruptionSkillUtil.chargeIn(this, 3.0, 7, progress);
            if (this.tickCount % 4 == 0) CorruptionSkillUtil.chargeSmoke(this, 2);

            LivingEntity target = this.getTarget();
            if (target != null) CorruptionSkillUtil.faceTarget(this, target);

            if (pulseWindup % 8 == 0) {
                CorruptionSkillUtil.playSoundPitched(this, SoundEvents.BEACON_ACTIVATE, 0.7F,
                        0.6F + progress * 0.8F, 0.05F);
            }

            if (pulseWindup < 0) {
                releaseOverloadPulse();
            }
            return;
        }

        if (pulseCooldown > 0) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < PULSE_MIN_RANGE) return;

        pulseWindup = PULSE_WINDUP;
    }

    /** 释放：全方位闪电环，造成真伤并附带短暂麻痹。 */
    private void releaseOverloadPulse() {
        CorruptionSkillUtil.burst(this, PULSE_RADIUS, 40, 0.55);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), PULSE_RADIUS,
                CorruptionSkillUtil.CORRUPTION_RED, 36);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.4F, 0.1F);

        List<LivingEntity> hits = CorruptionSkillUtil.findTargets(this, PULSE_RADIUS);
        for (LivingEntity target : hits) {
            if (org.bytechen.rottenruins.utils.combat.CorruptionDamageUtil.TrueHurt(
                    this, target, PULSE_MIN_DAMAGE, PULSE_DAMAGE, 2, true)) {
                // 麻痹：短时强减速，让脉冲具备"命中即吃后续"的威胁
                CorruptionSkillUtil.slow(target, 60, 3);
                CorruptionSkillUtil.applyEffect(target, MobEffects.WEAKNESS, 60, 0);
                CorruptionSkillFx.impact(this.level(), target.position(),
                        CorruptionSkillUtil.CORRUPTION_RED, 0.6F);
            }
        }

        // 脉冲后进入硬直，给玩家反打窗口
        this.setDeltaMovement(Vec3.ZERO);
        pulseCooldown = PULSE_COOLDOWN;
    }
}
