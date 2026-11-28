package org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.BaseInfectedEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

/** 畸鱿：缓慢游动的水生溃烂种，以短时喷射推进接近敌人。 */
public class AbnormalSquidEntity extends BaseUlceratedEntity {
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(
            AbnormalSquidEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ADVANCE = RawAnimation.begin().thenPlay("advance");
    private static final int ADVANCE_DURATION = 24;
    private static final int ADVANCE_COOLDOWN = 60;
    private int advanceTicks;
    private int advanceCooldown;
    private int attackTicks;
    private Vec3 advanceDestination;

    public AbnormalSquidEntity(EntityType<? extends BaseInfectedEntity> type, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(type, level, resources);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.15F, 0.05F, false);
        lookControl = new SmoothSwimmingLookControl(this, 10);
        setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        setHasWalkAnim(false);
        setWalkSpeed(0.15F);
        setRunSpeed(0.15F);
        setDoHurtDistance(1.2F);
        setDoHurtTime(25);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, 0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ChaseLockGoal(this));
        goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0, 40));
        targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true).range(40).mustSee(false).filter(this).build());
        super.registerGoals();
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public MobType getMobType() { return MobType.WATER; }

    @Override
    public void travel(Vec3 input) {
        if (isEffectiveAi() && isInWater() && !isFakeDying()) {
            moveRelative(0.02F, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else {
            super.travel(input);
        }
    }

    @Override
    protected void tickChase() {
        if (!isInWater()) {
            cancelAdvance();
            return;
        }
        if (isNoAi()) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            cancelAdvance();
            return;
        }
        if (getBoundingBoxDistanceSqrExact(target) <= doHurtDistance * doHurtDistance
                && hasLineOfSight(target)) {
            cancelAdvance();
            getNavigation().stop();
            if (doHurtCooldown == 0 && getAttack(target)) doHurtCooldown = getDoHurtTime();
            return;
        }
        if (attackTicks > 0) return;
        if (advanceTicks == 0 && advanceCooldown == 0 && distanceToSqr(target) > 9
                && hasLineOfSight(target)) {
            // 锁定发动时的目标位置，保留普通碰撞，不穿墙或瞬移。
            advanceDestination = target.getBoundingBox().getCenter();
            advanceTicks = ADVANCE_DURATION;
            advanceCooldown = ADVANCE_COOLDOWN;
            entityData.set(ACTION, 1);
            getNavigation().stop();
        }
        if (advanceTicks > 0) {
            Vec3 offset = advanceDestination.subtract(getBoundingBox().getCenter());
            if (offset.lengthSqr() < 1 || horizontalCollision || verticalCollision) {
                cancelAdvance();
            } else {
                setDeltaMovement(offset.normalize().scale(Math.min(0.85, offset.length())));
            }
            return;
        }
        super.tickChase();
    }

    private void cancelAdvance() {
        if (advanceTicks > 0) setDeltaMovement(getDeltaMovement().scale(0.3));
        advanceTicks = 0;
        advanceDestination = null;
        if (entityData.get(ACTION) == 1) entityData.set(ACTION, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (advanceCooldown > 0) advanceCooldown--;
            if (advanceTicks == 1) cancelAdvance();
            else if (advanceTicks > 1) advanceTicks--;
            if (attackTicks > 0 && --attackTicks == 0) entityData.set(ACTION, 0);
            if (isFakeDying() || isNoAi()) {
                cancelAdvance();
                attackTicks = 0;
                entityData.set(ACTION, 0);
            }
        }
        // 朝向跟随实际速度，包含上下游动的俯仰；客户端同样计算以平滑显示。
        Vec3 velocity = getDeltaMovement();
        if (isInWater() && !isFakeDying() && velocity.lengthSqr() > 1.0E-5) {
            float yaw = (float)(Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float)(-Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG);
            setYRot(Mth.rotLerp(0.3F, getYRot(), yaw));
            setXRot(Mth.rotLerp(0.3F, getXRot(), pitch));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        attackTicks = 15;
        entityData.set(ACTION, 2);
        target.hurt(damageSources().mobAttack(this),
                HallDifficultyAttributes.effectiveAttackDamage(this, 7.0F));
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "squid", 2, state -> {
            if (isDeadOrDying()) return PlayState.STOP;
            return state.setAndContinue(switch (entityData.get(ACTION)) {
                case 1 -> ADVANCE;
                case 2 -> ATTACK;
                default -> IDLE;
            });
        }));
    }
}
