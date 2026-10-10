package org.bytechen.hall.overworld.registry.entities.population.ecological;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

/**
 * 重型炸弹飞行生物。
 * <p>
 * 行为和畸骸骷髅类似，但“保持距离”只通过高度实现：
 * 会飞到目标上方一定高度，然后向下投放 heavy_bomb_tnt。
 */
public class HeavyBombEntity extends BaseEcologicalEntity {

    /** 目标上方保持的高度 */
    private static final double HEIGHT_ABOVE_TARGET = 10.0D;
    /** 与地面保持的最小高度（格） */
    private static final double MIN_GROUND_CLEARANCE = 2.0D;
    /** 距离调整速度倍率 */
    private static final double DISTANCE_ADJUST_SPEED = 0.8D;

    public HeavyBombEntity(EntityType<? extends BaseEcologicalEntity> entityType, Level level,
                           Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.moveControl = new SmoothFlyingMoveControl(this);
        this.setNoGravity(true);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.4F);
        this.setRunSpeed(0.4F);
        this.setDoHurtTime(40);
        this.setDoHurtDistance(28.0F);
    }

    public static Consumer<AbstractHallEntity> resources() {
        return entity -> {
            entity.model = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "geo/hall_heavy_bomb.geo.json");
            entity.texture = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/entity/hall_heavy_bomb.png");
            entity.animation = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "animations/hall_heavy_bomb.animation.json");
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            tickFlightBehavior();
        }
    }

    private void tickFlightBehavior() {
        double groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                getBlockX(), getBlockZ());
        double minY = groundY + MIN_GROUND_CLEARANCE;

        Vec3 desired = position();

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            // 直接悬停在目标正上方，保证向下投放的炸弹能落在目标附近
            desired = new Vec3(target.getX(), target.getY() + HEIGHT_ABOVE_TARGET, target.getZ());
        }

        if (desired.y < minY) {
            desired = new Vec3(desired.x, minY + 1.0D, desired.z);
        }

        if (desired.distanceToSqr(position()) > 1.0E-4D) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, DISTANCE_ADJUST_SPEED);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6F);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 40));
        this.goalSelector.addGoal(5, new RandomFlyGoal());
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        super.registerGoals();
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        if (!level().isClientSide() && target != null && target.isAlive()) {
            // 从自身位置向下投放炸弹，让它自然下落
            HeavyBombTntEntity.spawn(level(), position().add(0, -1.0D, 0), this);
        }
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // 飞行生物不受到摔落伤害
    }

    // 死亡效果（冲击波 + 黑洞）不在这里实现 ——
    // AbstractHallEntity 的 hurt()/die() 都有假死拦截分支，从实体内部挂钩
    // 会被绕过。改由 ForgeEventHelpers.handleLivingDeath 调用
    // HeavyBombDeathEffect.trigger()，见该类的说明。


    @Override
    public void travel(Vec3 travelVector) {
        if (isFakeDying()) {
            super.travel(Vec3.ZERO);
            return;
        }
        if (this.isEffectiveAi()) {
            this.moveRelative(0.02F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "idle_controller", 3, state -> {
            if (isDeadOrDying() || isFakeDying()) return PlayState.STOP;
            state.setControllerSpeed(getIdleAnimSpeed());
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    private class RandomFlyGoal extends Goal {
        @Override
        public boolean canUse() {
            return getTarget() == null && !getMoveControl().hasWanted();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            double x = getX() + (random.nextDouble() - 0.5D) * 16.0D;
            double y = getY() + (random.nextDouble() - 0.5D) * 8.0D;
            double z = getZ() + (random.nextDouble() - 0.5D) * 16.0D;
            getMoveControl().setWantedPosition(x, y, z, 0.6D);
        }
    }

    private static class SmoothFlyingMoveControl extends MoveControl {
        private final HeavyBombEntity mob;

        public SmoothFlyingMoveControl(HeavyBombEntity mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public void tick() {
            if (operation == Operation.MOVE_TO) {
                double dx = wantedX - mob.getX();
                double dy = wantedY - mob.getY();
                double dz = wantedZ - mob.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq < 2.5000003E-7F) {
                    operation = Operation.WAIT;
                    return;
                }

                double dist = Math.sqrt(distSq);
                Vec3 dir = new Vec3(dx / dist, dy / dist, dz / dist);

                if (!canReach(dir, Mth.ceil(dist))) {
                    operation = Operation.WAIT;
                    return;
                }

                double maxSpeed = speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                Vec3 delta = mob.getDeltaMovement().add(dir.scale(0.1D));
                double speed = delta.length();
                if (speed > maxSpeed) {
                    delta = delta.scale(maxSpeed / speed);
                }
                mob.setDeltaMovement(delta);

                float yRot = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
                mob.setYRot(rotlerp(mob.getYRot(), yRot, 90.0F));
                // 保持直立，不随高度差俯仰，避免看起来像在疯狂旋转
            } else {
                Vec3 delta = mob.getDeltaMovement();
                if (delta.lengthSqr() > 1.0E-6D) {
                    mob.setDeltaMovement(delta.scale(0.8D));
                }
            }
        }

        private boolean canReach(Vec3 dir, int steps) {
            AABB aabb = mob.getBoundingBox();
            for (int i = 1; i < steps; ++i) {
                aabb = aabb.move(dir);
                if (!mob.level().noCollision(mob, aabb)) {
                    return false;
                }
            }
            return true;
        }
    }
}
