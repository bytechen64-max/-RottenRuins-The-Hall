package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored;

import net.minecraft.sounds.SoundEvents;
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

import java.util.function.Consumer;

/**
 * 重构种·攻击者。
 *
 * <p>技能：<b>C8 扑击后撤</b> —— 突进打一套后主动后跳脱战，再接下一轮。
 * 打一下就跳开的节奏让它变成需要预判落点的单位，低空远跳同时能越过 1 格高障碍。</p>
 */
public class AttackerEntity extends BaseRefactoredEntity {

    // 跳跃参数常量（跳得远但不高）
    private static final double JUMP_HORIZONTAL_BOOST = 2.2;
    private static final double JUMP_VERTICAL_SPEED = 0.3;
    private static final double MAX_HORIZONTAL_SPEED = 3;

    // =========================================================================
    // C8 扑击后撤参数
    // =========================================================================
    private static final double DASH_MIN_RANGE = 3.5;
    private static final double DASH_MAX_RANGE = 12.0;
    private static final int DASH_WINDUP = 16;
    private static final int DASH_COOLDOWN = 110;
    private static final int RETREAT_COOLDOWN = 60;
    private static final float DASH_DAMAGE = 7.0F;

    private int dashCooldown = 80;
    private int dashWindup = -1;
    private boolean dashLanded = false;
    private int retreatCooldown = 0;

    public AttackerEntity(EntityType<? extends AttackerEntity> entityType, Level level,
                          Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.16F);
        this.setRunSpeed(0.22F);
        this.setFakeDeathEnabled(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        LivingEntity target = this.getTarget();
        // 每 50 tick 且目标存在且距离 < 10 时，触发低空远跳
        if (this.tickCount % 50 == 0 && target != null && this.distanceTo(target) < 10) {
            if (this.onGround()) {
                performLowLongJump();
            }
        }

        tickDash();
    }

    // =========================================================================
    // C8 扑击后撤
    // =========================================================================

    private void tickDash() {
        if (dashCooldown > 0) dashCooldown--;
        if (retreatCooldown > 0) retreatCooldown--;

        if (dashWindup >= 0) {
            dashWindup--;

            float progress = 1.0F - (float) dashWindup / DASH_WINDUP;
            CorruptionSkillUtil.chargeIn(this, 1.8, 4, progress);

            LivingEntity target = this.getTarget();
            if (target != null) CorruptionSkillUtil.faceTarget(this, target);

            if (dashWindup < 0) {
                if (target == null || !target.isAlive()) {
                    dashCooldown = 40;
                } else {
                    CorruptionSkillUtil.lungeAt(this, target, 0.85, 0.34);
                    dashLanded = false;
                    CorruptionSkillUtil.burst(this, 1.4, 12, 0.3);
                    CorruptionSkillUtil.playSoundPitched(this, SoundEvents.RAVAGER_ATTACK, 1.0F, 1.2F, 0.1F);
                }
            }
            return;
        }

        tickDashContact();

        if (dashCooldown > 0 || retreatCooldown > 0) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < DASH_MIN_RANGE || dist > DASH_MAX_RANGE) return;
        if (!this.onGround()) return;

        dashWindup = DASH_WINDUP;
    }

    /** 扑击接触判定：撞到就结算伤害并立刻后跳脱战。 */
    private void tickDashContact() {
        if (dashLanded) return;
        if (this.getDeltaMovement().horizontalDistanceSqr() < 0.05) return;

        for (LivingEntity target : CorruptionSkillUtil.findTargets(this, 1.6)) {
            if (CorruptionSkillUtil.strike(this, target, DASH_DAMAGE, 0.5)) {
                dashLanded = true;
                CorruptionSkillFx.impact(this.level(), target.position(),
                        CorruptionSkillUtil.CORRUPTION_RED, 0.6F);
                performRetreat(target);
                return;
            }
        }
    }

    /**
     * 后撤：沿"目标 → 自己"的方向跳出，并带一点弧线。
     * <p>后撤方向按当前位置计算而不是固定反向前进方向，因此玩家绕背也无法白嫖。</p>
     */
    private void performRetreat(LivingEntity from) {
        Vec3 away = this.position().subtract(from.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-4) {
            away = Vec3.directionFromRotation(0, this.getYRot()).reverse();
        }
        Vec3 n = away.normalize();

        this.setDeltaMovement(n.x * 1.15, 0.38, n.z * 1.15);
        this.hasImpulse = true;
        this.hurtMarked = true;
        this.setYRot((float) (Math.atan2(-n.x, n.z) * (180.0 / Math.PI)));
        this.setYHeadRot(this.getYRot());

        CorruptionSkillFx.breath(this.level(), this.position(), n.scale(-1), 2.0,
                CorruptionSkillUtil.CORRUPTION_RED, 10);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.RAVAGER_STEP, 1.0F, 1.3F, 0.1F);

        dashCooldown = DASH_COOLDOWN;
        retreatCooldown = RETREAT_COOLDOWN;
    }

    /** 执行"跳得远但不高"的跳跃。 */
    private void performLowLongJump() {
        Vec3 motion = this.getDeltaMovement();
        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());

        double newMotionX = motion.x + forward.x * JUMP_HORIZONTAL_BOOST;
        double newMotionZ = motion.z + forward.z * JUMP_HORIZONTAL_BOOST;

        double horSpeed = Math.sqrt(newMotionX * newMotionX + newMotionZ * newMotionZ);
        if (horSpeed > MAX_HORIZONTAL_SPEED) {
            newMotionX = newMotionX / horSpeed * MAX_HORIZONTAL_SPEED;
            newMotionZ = newMotionZ / horSpeed * MAX_HORIZONTAL_SPEED;
        }

        this.setDeltaMovement(newMotionX, JUMP_VERTICAL_SPEED, newMotionZ);
        this.hasImpulse = true;
    }
}
