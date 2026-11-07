package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseDegenerateEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * 腐化猪。
 *
 * <p>技能：<b>C3 腐化猪蓄力冲锋</b> —— 蓄力后连续三段冲撞，一段比一段快、伤害更高。
 * 第一段蓄力期间玩家可以绕背，或者引导它撞墙（撞墙会打断连段并进入长后摇）。</p>
 */
public class DegeneratePigEntity extends BaseDegenerateEntity {

    // =========================================================================
    // C3 蓄力冲锋参数
    // =========================================================================

    private static final int TOTAL_CHARGES = 3;
    private static final double CHARGE_MIN_RANGE = 3.0;
    private static final double CHARGE_RANGE = 11.0;
    private static final int WINDUP_TICKS = 24;
    private static final int CHARGE_TICKS = 26;
    private static final int GAP_TICKS = 10;
    private static final int RECOVERY_TICKS = 50;
    /** 每段冲锋的基础速度，按段数递增 */
    private static final double CHARGE_SPEED_BASE = 0.55;
    private static final double CHARGE_SPEED_STEP = 0.1;
    /** 每段冲锋的基础伤害，按段数递增 */
    private static final float CHARGE_DAMAGE_BASE = 6.5F;
    private static final float CHARGE_DAMAGE_STEP = 2.5F;

    private enum ChargePhase { IDLE, WINDUP, DASH, GAP, RECOVERY }

    private ChargePhase phase = ChargePhase.IDLE;
    private int phaseTimer = 0;
    private int chargeIndex = 0;
    private int cooldown = 80;

    public DegeneratePigEntity(EntityType<? extends DegeneratePigEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    // =========================================================================
    // C3 腐化猪蓄力冲锋
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (cooldown > 0) cooldown--;

        switch (phase) {
            case IDLE -> tickIdle();
            case WINDUP -> tickWindup();
            case DASH -> tickDash();
            case GAP -> tickGap();
            case RECOVERY -> tickRecovery();
        }
    }

    private void tickIdle() {
        if (cooldown > 0) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < CHARGE_MIN_RANGE || dist > CHARGE_RANGE) return;

        phase = ChargePhase.WINDUP;
        phaseTimer = WINDUP_TICKS;
        chargeIndex = 0;
    }

    /** 蓄力：红色先兆汇聚，玩家有 1.2 秒决定是否让开正面。 */
    private void tickWindup() {
        phaseTimer--;

        float progress = 1.0F - (float) phaseTimer / WINDUP_TICKS;
        CorruptionSkillUtil.chargeIn(this, 2.0, 5, progress);

        if (this.tickCount % 5 == 0) {
            // TODO(动画)：原版用 long_jump 的压缩姿态当蓄力表现；合并基类暂无技能动画通道
            CorruptionSkillUtil.playSoundPitched(this, SoundEvents.PIG_AMBIENT, 0.9F, 0.6F, 0.15F);
        }
        LivingEntity target = this.getTarget();
        if (target != null) CorruptionSkillUtil.faceTarget(this, target);

        if (phaseTimer <= 0) {
            startDash();
        }
    }

    private void startDash() {
        chargeIndex++;

        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
        double speed = CHARGE_SPEED_BASE + CHARGE_SPEED_STEP * (chargeIndex - 1);
        this.setDeltaMovement(forward.x * speed, 0.12, forward.z * speed);
        this.hasImpulse = true;
        this.hurtMarked = true;

        phase = ChargePhase.DASH;
        phaseTimer = CHARGE_TICKS;

        CorruptionSkillUtil.burst(this, 1.4, 12, 0.28);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.PIG_HURT, 1.1F, 0.7F, 0.1F);
    }

    /** 冲撞：接触判定 + 撞墙提前结束。 */
    private void tickDash() {
        phaseTimer--;

        // 保持水平速度，避免被上坡或台阶吃掉
        Vec3 motion = this.getDeltaMovement();
        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
        double speed = CHARGE_SPEED_BASE + CHARGE_SPEED_STEP * (chargeIndex - 1);
        this.setDeltaMovement(forward.x * speed, motion.y, forward.z * speed);
        this.hasImpulse = true;
        this.hurtMarked = true;

        ram();

        boolean wallHit = this.horizontalCollision;
        if (phaseTimer <= 0 || wallHit) {
            if (wallHit) {
                // 撞墙：连击中断，进入长后摇 —— 这就是"引导它撞墙"的收益
                CorruptionSkillFx.impact(this.level(), this.position(),
                        CorruptionSkillUtil.CORRUPTION_RED, 1.0F);
                CorruptionSkillUtil.playSoundPitched(this, SoundEvents.ANVIL_LAND, 1.0F, 0.8F, 0.1F);
                endCombo();
                return;
            }

            if (chargeIndex >= TOTAL_CHARGES) {
                endCombo();
            } else {
                phase = ChargePhase.GAP;
                phaseTimer = GAP_TICKS;
            }
        }
    }

    /** 段间间隔：重新对准目标，给玩家一个换位窗口。 */
    private void tickGap() {
        phaseTimer--;
        LivingEntity target = this.getTarget();
        if (target != null) CorruptionSkillUtil.faceTarget(this, target);

        if (phaseTimer <= 0) startDash();
    }

    private void tickRecovery() {
        phaseTimer--;
        if (phaseTimer <= 0) {
            phase = ChargePhase.IDLE;
            cooldown = 100 + this.random.nextInt(60);
        }
    }

    private void endCombo() {
        chargeIndex = 0;
        phase = ChargePhase.RECOVERY;
        phaseTimer = RECOVERY_TICKS;
        this.setDeltaMovement(this.getDeltaMovement().scale(0.25));
    }

    /** 冲撞命中判定。 */
    private void ram() {
        AABB box = this.getBoundingBox().inflate(0.35, 0.2, 0.35);
        float damage = CHARGE_DAMAGE_BASE + CHARGE_DAMAGE_STEP * (chargeIndex - 1);

        List<LivingEntity> hits = this.level().getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity target : hits) {
            if (!CorruptionSkillUtil.isValidTarget(this, target)) continue;
            if (CorruptionSkillUtil.strike(this, target, damage, 1.2)) {
                CorruptionSkillUtil.corrupt(target, 120, 1);
                CorruptionSkillFx.impact(this.level(), target.position(),
                        CorruptionSkillUtil.CORRUPTION_RED, 0.7F);
            }
        }

        // 沿途铲软方块，强化重量感
        if (this.level() instanceof ServerLevel serverLevel && this.horizontalCollision) {
            var front = this.blockPosition().relative(this.getDirection());
            BlockState state = serverLevel.getBlockState(front);
            if (!state.isAir() && state.getDestroySpeed(serverLevel, front) >= 0
                    && state.getDestroySpeed(serverLevel, front) < 1.0F) {
                serverLevel.destroyBlock(front, true, this);
            }
        }
    }

    /** 冲锋期间不可推动，保证连段不被打断。 */
    @Override
    public boolean isPushable() {
        return phase != ChargePhase.DASH;
    }

    @Override
    public void push(Entity entity) {
        // 冲锋姿态下忽略外部推动
        if (phase == ChargePhase.DASH) return;
        super.push(entity);
    }
}
