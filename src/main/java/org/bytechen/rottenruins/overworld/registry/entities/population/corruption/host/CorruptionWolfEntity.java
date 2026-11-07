package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 同化狼：B2 感染嚎叫（群体增益 + 召来 1~2 只狼）+ B3 扑咬锁喉（14t 前摇 → 咬住 40t，
 * 持续减速/流血/每 10t 撕咬，结束时甩飞）。
 */
public class CorruptionWolfEntity extends BaseHostEntity {

    private static final int HOWL_COOLDOWN = 260;
    private static final double HOWL_RADIUS = 16.0;
    private static final int HOWL_MAX_SUMMONS = 2;
    private static final int PACK_CAP = 6;

    private static final int LATCH_WINDUP = 14;
    private static final int LATCH_TICKS = 40;
    private static final double LATCH_MIN_RANGE = 3.0;
    private static final double LATCH_MAX_RANGE = 9.0;
    private static final float BITE_DAMAGE = 6.0F;
    private static final int LATCH_COOLDOWN = 120;

    private int howlCooldown = 120;
    private int latchWindup = -1;
    private int latchTicks = 0;
    private LivingEntity latchTarget;
    private int latchCooldown = 0;

    public CorruptionWolfEntity(EntityType<? extends CorruptionWolfEntity> entityType, Level level,
                                Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
        this.enableHordeCharge(3, 14.0);
        this.enableFleshPull(0.85);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 7.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (howlCooldown > 0) howlCooldown--;
        if (latchCooldown > 0) latchCooldown--;

        tickLatch();
        if (latchTicks <= 0 && latchWindup < 0) tickHowl();
    }

    // =========================================================================
    // B2 感染嚎叫
    // =========================================================================

    private void tickHowl() {
        if (howlCooldown > 0) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        int pack = CorruptionSkillUtil.countAllies(this, HOWL_RADIUS);
        if (pack < 2) return;

        CorruptionSkillUtil.buffAllies(this, HOWL_RADIUS, MobEffects.DAMAGE_BOOST, 200, 1);
        CorruptionSkillUtil.buffAllies(this, HOWL_RADIUS, MobEffects.MOVEMENT_SPEED, 200, 0);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.WOLF_HOWL, 1.4F, 0.9F, 0.1F);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 3.0,
                CorruptionSkillUtil.CORRUPTION_RED, 16);

        int summons = Math.min(HOWL_MAX_SUMMONS, Math.max(0, PACK_CAP - pack));
        for (int i = 0; i < summons; i++) {
            CorruptionWolfEntity wolf = CorruptionEntityTypes.CORRUPTION_WOLF.get().create(this.level());
            if (wolf == null) continue;
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = 2.0 + this.random.nextDouble() * 2.0;
            wolf.moveTo(this.getX() + Math.cos(angle) * r, this.getY(), this.getZ() + Math.sin(angle) * r,
                    this.random.nextFloat() * 360.0F, 0.0F);
            wolf.setTarget(target);
            this.level().addFreshEntity(wolf);
            CorruptionSkillFx.pillar(this.level(), wolf.position(), 1.2,
                    CorruptionSkillUtil.CORRUPTION_RED, 8);
        }

        howlCooldown = HOWL_COOLDOWN;
    }

    // =========================================================================
    // B3 扑咬锁喉
    // =========================================================================

    private void tickLatch() {
        if (latchTicks > 0) {
            LivingEntity target = this.latchTarget;
            if (target == null || !target.isAlive() || this.distanceToSqr(target) > 36.0) {
                endLatch(false);
                return;
            }

            latchTicks--;
            CorruptionSkillUtil.slow(target, 10, 3);
            CorruptionSkillUtil.bleed(target, 40, 0);
            if (latchTicks % 10 == 0) {
                CorruptionSkillUtil.strike(this, target, 1.5F, 0);
            }
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            if (latchTicks <= 0) endLatch(true);
            return;
        }

        if (latchWindup >= 0) {
            latchWindup--;
            LivingEntity target = this.getTarget();
            if (target != null) CorruptionSkillUtil.faceTarget(this, target);
            if (latchWindup < 0) {
                if (target != null && target.isAlive()
                        && this.distanceToSqr(target) <= LATCH_MAX_RANGE * LATCH_MAX_RANGE) {
                    CorruptionSkillUtil.strike(this, target, BITE_DAMAGE, 0);
                    latchTarget = target;
                    latchTicks = LATCH_TICKS;
                    CorruptionSkillFx.impact(this.level(), target.position(),
                            CorruptionSkillUtil.CORRUPTION_RED, 0.7F);
                    CorruptionSkillUtil.playSoundPitched(this, SoundEvents.WOLF_HURT, 1.2F, 0.9F, 0.1F);
                } else {
                    latchCooldown = 40;
                }
            }
            return;
        }

        if (latchCooldown > 0) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < LATCH_MIN_RANGE || dist > LATCH_MAX_RANGE) return;
        latchWindup = LATCH_WINDUP;
    }

    private void endLatch(boolean fling) {
        LivingEntity target = this.latchTarget;
        latchTarget = null;
        latchTicks = 0;
        latchCooldown = LATCH_COOLDOWN;
        if (fling && target != null) {
            Vec3 away = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(away.x * 0.9, 0.42, away.z * 0.9);
            target.hurtMarked = true;
            CorruptionSkillFx.impact(this.level(), target.position(),
                    CorruptionSkillUtil.CORRUPTION_RED, 0.6F);
        }
    }

    @Override
    public boolean isPushable() {
        return latchTicks <= 0;
    }

    @Override
    public void push(Entity entity) {
        if (latchTicks > 0) return;
        super.push(entity);
    }
}
