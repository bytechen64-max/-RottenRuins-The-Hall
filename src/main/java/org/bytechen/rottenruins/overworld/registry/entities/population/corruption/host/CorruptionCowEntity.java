package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 同化牛：B5 狂奔冲撞 + B9 尸群践踏（3 只）+ B12 汲血（0.3）。
 * 冲撞：22t 蓄力 → 0.62 速度冲刺，命中 9 伤害 + 腐蚀 + 反胃；撞墙自伤 4 + 反胃 + 缓慢。
 */
public class CorruptionCowEntity extends BaseHostEntity {

    private static final double CHARGE_MIN_RANGE = 3.0;
    private static final double CHARGE_MAX_RANGE = 10.0;
    private static final int CHARGE_WINDUP = 22;
    private static final int CHARGE_MAX_TICKS = 45;
    private static final int CHARGE_RECOVERY = 40;
    private static final double CHARGE_SPEED = 0.62;
    private static final float CHARGE_DAMAGE = 9.0F;

    private enum Phase { IDLE, WINDUP, DASH, RECOVERY }
    private Phase phase = Phase.IDLE;
    private int phaseTimer = 0;
    private int cooldown = 80;

    public CorruptionCowEntity(EntityType<? extends CorruptionCowEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
        this.enableHordeCharge(3, 12.0);
        this.enableLifesteal(0.3F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (cooldown > 0) cooldown--;

        switch (phase) {
            case IDLE -> tickIdle();
            case WINDUP -> tickWindup();
            case DASH -> tickDash();
            case RECOVERY -> tickRecovery();
        }
    }

    private void tickIdle() {
        if (cooldown > 0) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < CHARGE_MIN_RANGE || dist > CHARGE_MAX_RANGE) return;
        phase = Phase.WINDUP;
        phaseTimer = CHARGE_WINDUP;
    }

    private void tickWindup() {
        phaseTimer--;
        CorruptionSkillUtil.chargeIn(this, 2.0, 6, 1.0F - (float) phaseTimer / CHARGE_WINDUP);
        LivingEntity target = this.getTarget();
        if (target != null) CorruptionSkillUtil.faceTarget(this, target);
        if (phaseTimer <= 0) {
            phase = Phase.DASH;
            phaseTimer = CHARGE_MAX_TICKS;
            CorruptionSkillUtil.burst(this, 1.4, 12, 0.3);
            CorruptionSkillUtil.playSoundPitched(this, SoundEvents.COW_HURT, 1.2F, 0.7F, 0.1F);
        }
    }

    private void tickDash() {
        phaseTimer--;
        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
        this.setDeltaMovement(forward.x * CHARGE_SPEED, this.getDeltaMovement().y, forward.z * CHARGE_SPEED);
        this.hasImpulse = true;
        this.hurtMarked = true;

        for (LivingEntity target : CorruptionSkillUtil.findTargets(this, 1.8)) {
            if (CorruptionSkillUtil.strike(this, target, CHARGE_DAMAGE, 1.0)) {
                CorruptionSkillUtil.corrupt(target, 140, 1);
                CorruptionSkillUtil.nausea(target, 60, 0);
                CorruptionSkillFx.impact(this.level(), target.position(),
                        CorruptionSkillUtil.CORRUPTION_RED, 0.7F);
                endCharge();
                return;
            }
        }

        if (this.horizontalCollision) {
            // 撞墙：自伤 + 反胃 + 缓慢，这就是"引导它撞墙"的收益
            this.hurt(this.damageSources().generic(), 4.0F);
            CorruptionSkillUtil.applyEffect(this, MobEffects.CONFUSION, 80, 1);
            CorruptionSkillUtil.slow(this, 60, 2);
            CorruptionSkillFx.impact(this.level(), this.position(), 0xFFB0202F, 1.0F);
            CorruptionSkillUtil.playSoundPitched(this, SoundEvents.ANVIL_LAND, 1.0F, 0.8F, 0.1F);
            endCharge();
            return;
        }

        if (phaseTimer <= 0) endCharge();
    }

    private void endCharge() {
        phase = Phase.RECOVERY;
        phaseTimer = CHARGE_RECOVERY;
        this.setDeltaMovement(this.getDeltaMovement().scale(0.25));
        cooldown = 100 + this.random.nextInt(40);
    }

    private void tickRecovery() {
        phaseTimer--;
        if (phaseTimer <= 0) phase = Phase.IDLE;
    }

    @Override
    public boolean isPushable() {
        return phase != Phase.DASH;
    }

    @Override
    public void push(Entity entity) {
        if (phase == Phase.DASH) return;
        super.push(entity);
    }
}
