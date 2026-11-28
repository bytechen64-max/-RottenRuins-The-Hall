package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;
import java.util.EnumSet;

/**
 * 同化蝙蝠：B13 蝙蝠夜袭 —— 冷却 110t、距离 4~22 时瞬移到目标背后 2 格，
 * 咬击 3.5 + 吸血 60% + 恐惧 80t；另带 B1 吐息（3.5/70/14）、B10 骨爆（6/2.5）、B12 汲血（0.4）。
 *
 * 使用飞行寻路追击，空闲时在附近空气中游荡，夜袭仍按原冷却触发。
 */
public class CorruptionBatEntity extends BaseHostEntity {

    private static final int AMBUSH_COOLDOWN = 110;
    private static final double AMBUSH_MIN_RANGE = 4.0;
    private static final double AMBUSH_MAX_RANGE = 22.0;
    private static final float AMBUSH_DAMAGE = 3.5F;

    private int ambushCooldown = 60;
    private int nextFlightRepathTick;

    public CorruptionBatEntity(EntityType<? extends CorruptionBatEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.setNoGravity(true);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.18F);
        this.setRunSpeed(0.24F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
        this.enableSpit(3.5F, 70, 14.0);
        this.enableBoneBurst(6, 2.5F);
        this.enableLifesteal(0.4F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        this.targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0D)
                .mustSee(false)
                .targetOtherInfected(true)
                .filter(this)
                .build());
        this.goalSelector.addGoal(5, new RandomFlyGoal());
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    private final class RandomFlyGoal extends Goal {
        private int nextFlightTick;

        private RandomFlyGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !isFakeDying() && tickCount >= nextFlightTick;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            nextFlightTick = tickCount + 30 + random.nextInt(15);
            Vec3 view = getViewVector(0.0F);
            Vec3 destination = AirAndWaterRandomPos.getPos(
                    CorruptionBatEntity.this, 10, 6, 0, view.x, view.z, (float) Math.PI / 2.0F);
            if (destination != null) {
                getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.0D);
            }
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isFakeDying() || !isEffectiveAi() || isInWater() || isInLava()) {
            super.travel(travelVector);
            return;
        }
        moveRelative(getSpeed(), travelVector);
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.8D));
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 蝙蝠不近战，只靠夜袭
        return false;
    }

    @Override
    protected void tickChase() {
        if (isNoAi()) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        double distanceSqr = distanceToSqr(target);
        if (distanceSqr >= 16.0D && distanceSqr <= 144.0D) {
            getNavigation().stop();
            return;
        }
        if (tickCount < nextFlightRepathTick) return;
        nextFlightRepathTick = tickCount + 20;

        if (distanceSqr > 144.0D) {
            getNavigation().moveTo(target, 1.0D);
        } else {
            Vec3 away = position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 1.0E-4D) {
                away = Vec3.directionFromRotation(0.0F, getYRot());
            }
            Vec3 retreat = target.position().add(away.normalize().scale(7.0D)).add(0.0D, 2.0D, 0.0D);
            getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.0D);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isFakeDying() || !this.isAlive()) return;

        if (ambushCooldown > 0) {
            ambushCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < AMBUSH_MIN_RANGE || dist > AMBUSH_MAX_RANGE) return;

        performAmbush(target);
        ambushCooldown = AMBUSH_COOLDOWN;
    }

    /** 瞬移到目标背后 2 格，咬击 + 吸血 + 恐惧。 */
    private void performAmbush(LivingEntity target) {
        Vec3 behind = target.position().subtract(
                Vec3.directionFromRotation(0, target.getYRot()).scale(2.0));

        // 落点尽量可站立；不行就直接贴到目标上方
        Vec3 destination = this.level().noCollision(this, this.getBoundingBox().move(behind.subtract(this.position())))
                ? behind
                : target.position().add(0, 0.5, 0);

        CorruptionSkillFx.impact(this.level(), this.position(), 0xFF201030, 0.6F);
        this.teleportTo(destination.x, destination.y, destination.z);

        if (CorruptionSkillUtil.strike(this, target, AMBUSH_DAMAGE, 0.15)) {
            CorruptionSkillUtil.lifesteal(this, AMBUSH_DAMAGE, 0.6F);
            CorruptionSkillUtil.fear(target, 80, 0);
        }
        CorruptionSkillFx.impact(this.level(), target.position(), 0xFF5A0A2A, 0.6F);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.BAT_HURT, 1.0F, 1.6F, 0.2F);
    }

    // 动画：模型只有 idle / fly / speed_fly（没有 walk / run）

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "movement_controller", 3, state -> {
            if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;
            if (!isActuallyMoving) {
                state.setControllerSpeed(getIdleAnimSpeed());
                return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            }
            state.setControllerSpeed(getRunAnimSpeed());
            return state.setAndContinue(RawAnimation.begin().thenLoop("fly"));
        }));
    }
}
