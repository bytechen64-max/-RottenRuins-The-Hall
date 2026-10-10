package org.bytechen.hall.overworld.registry.entities.population.infected;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

public class InfSkeletonEntity extends BaseInfectedEntity {

    /** 与目标保持距离的最小值 */
    private static final double MIN_TARGET_DISTANCE = 8.0D;
    /** 与目标保持距离的最大值 */
    private static final double MAX_TARGET_DISTANCE = 20.0D;
    /** 与地面保持的最小高度（格） */
    private static final double MIN_GROUND_CLEARANCE = 2.0D;
    /** 距离调整时的飞行速度倍率 */
    private static final double DISTANCE_ADJUST_SPEED = 0.8D;

    /**
     * 转向死区（格）：目标水平距离小于这个值就不再转向。
     * <p>
     * 悬停到目标距离区间内以后，dx/dz 只剩噪声量级，{@code atan2} 的结果每 tick 都在跳，
     * 渲染出来就是"站在原地疯狂转头"。死区把这段噪声整个挡在转向逻辑之外。
     */
    private static final double FACING_DEADZONE = 1.5D;

    /** 每 tick 最大转向角度（度）。约 120°/秒，肉眼可见的顺滑转向。 */
    private static final float TURN_SPEED = 6.0F;

    /** 无目标时，只有水平速度大于这个值才按速度方向转向（避免悬停时的速度噪声）。 */
    private static final double FACING_VELOCITY_EPSILON = 5.0E-3D;

    public InfSkeletonEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                             Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        // 飞行生物：无重力 + 飞行寻路 + 顺滑飞行控制
        this.moveControl = new SmoothFlyingMoveControl(this);
        this.setNoGravity(true);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.5F);
        this.setRunSpeed(0.5F);
        this.setDoHurtTime(30);
        this.setDoHurtDistance(24.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            tickFlightBehavior();
        }
    }

    /**
     * 服务端飞行行为：自动与目标保持距离、始终离地面至少 2 格，并统一处理转向。
     * <p>
     * 转向必须<b>只在这里</b>发生（唯一写入者）。之前 {@link SmoothFlyingMoveControl}
     * 也写 yRot，两个写入者互相抢，加上 {@code rotlerp(..., 90F)} 的转向速度过快，
     * 表现出来就是飞行途中不停乱转头。
     */
    private void tickFlightBehavior() {
        double groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                getBlockX(), getBlockZ());
        double minY = groundY + MIN_GROUND_CLEARANCE;

        Vec3 desired = position();
        LivingEntity target = getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (hasTarget) {
            double dist = distanceTo(target);

            if (dist < MIN_TARGET_DISTANCE) {
                // 太近：水平拉开距离
                Vec3 away = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
                if (away.lengthSqr() < 1.0E-4D) {
                    away = new Vec3(random.nextDouble() - 0.5D, 0, random.nextDouble() - 0.5D);
                }
                desired = desired.add(away.normalize().scale(4.0D));
            } else if (dist > MAX_TARGET_DISTANCE) {
                // 太远：飞近一点
                Vec3 toward = target.position().subtract(position()).normalize();
                desired = desired.add(toward.scale(4.0D));
            }
        }

        // 高度低于地面 + 2 格时强制抬升
        if (desired.y < minY) {
            desired = new Vec3(desired.x, minY + 1.0D, desired.z);
        }

        if (desired.distanceToSqr(position()) > 1.0E-4D) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, DISTANCE_ADJUST_SPEED);
        }

        // 移动指令下完再转向：此时的位置与速度都是本 tick 的最终值
        updateFacing(hasTarget ? target : null);
    }

    /**
     * 慢速转向 —— 全实体唯一的 yRot 写入点。
     *
     * <h3>为什么必须带死区 + 限速</h3>
     * GeckoLib 的模型整体按实体 {@code yRot} 旋转（不读 yHeadRot / yBodyRot / xRot），
     * 所以"面向哪里"完全由这里决定：
     * <ul>
     *   <li>有目标且水平距离超过 {@link #FACING_DEADZONE} 时朝目标转；距离太近不转，
     *       噪声被挡在门外；</li>
     *   <li>无目标时按<b>实际速度</b>方向转，且速度要够大才转 —— 悬停时速度接近 0，
     *       方向没有意义；</li>
     *   <li>转向速度限死在 {@link #TURN_SPEED} 度/tick，并用
     *       {@link Mth#approachDegrees} 走最短弧，不会在 ±180° 处抽一下。</li>
     * </ul>
     */
    private void updateFacing(LivingEntity target) {
        Float desiredYaw = null;

        if (target != null) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz >= FACING_DEADZONE * FACING_DEADZONE) {
                desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            }
        } else {
            Vec3 velocity = getDeltaMovement();
            if (velocity.horizontalDistanceSqr() >= FACING_VELOCITY_EPSILON) {
                desiredYaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0 / Math.PI)) - 90.0F;
            }
        }

        if (desiredYaw == null) return;

        float next = Mth.approachDegrees(getYRot(), desiredYaw, TURN_SPEED);
        setYRot(next);
        // 头 / 身跟着走，避免任何读到它们的逻辑拿到上一帧的旧值
        this.yHeadRot = next;
        this.yBodyRot = next;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4F);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        // 与 inf_player 相同的索敌：40 格、无需视线
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        // 刻意不加 FaceTargetGoal：它写的是 yHeadRot / yBodyRot / xRot，
        // 而 GeckoLib 模型只按实体 yRot 旋转，头/身旋转量没有任何模型去读 ——
        // 加了就是个看不见的第二方在抢写旋转。朝向统一交给 tickFlightBehavior() 里的
        // updateFacing()（唯一写入者，带死区 + 限速）。
        // RandomLookAroundGoal 同理：它改的也是 yHeadRot / xRot，对 GeckoLib 模型无效，
        // 只会让服务端每 tick 把旋转量同步给客户端，白费带宽。
        this.goalSelector.addGoal(5, new RandomFlyGoal());
        super.registerGoals();
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        if (!level().isClientSide() && target != null && target.isAlive()) {
            Vec3 eyePos = position().add(0, getEyeHeight(), 0);
            Vec3 dir = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
            Vec3 spawnPos = eyePos.add(dir.scale(0.8D));
            InfSkeletonArrowEntity.spawn(level(), this, target, spawnPos);
        }
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // 飞行生物不受到摔落伤害
    }

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
        // 只有 idle 动画
        registrar.add(new AnimationController<>(this, "idle_controller", 3, state -> {
            if (isDeadOrDying() || isFakeDying()) return PlayState.STOP;
            state.setControllerSpeed(getIdleAnimSpeed());
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    /**
     * 无目标时在周围 3D 空间随机漂浮，避免一直呆站在空中。
     * <p>
     * 注意 {@link #canUse()} 只判断有没有移动指令、<b>不再判断
     * {@code getMoveControl().hasWanted()}</b>：{@code MoveControl} 撞墙时会进入
     * {@code WAIT} 状态并一直保持 {@code hasWanted() == true}，旧写法会在撞墙后
     * 永久卡死不再选新目的地。
     */
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

    /**
     * 没有移动指令时缓慢减速，保持悬浮。
     * <p>
     * <b>这里不再写 yRot。</b> 之前用 {@code atan2(dz, dx)} 求朝向，但悬浮到位后
     * dx/dz 只剩噪声量级，结果每 tick 都在乱跳 —— 这就是"飞行时乱转头"的直接来源。
     * 转向现在统一由 {@link InfSkeletonEntity#updateFacing} 负责。
     */
    private static class SmoothFlyingMoveControl extends MoveControl {
        private final InfSkeletonEntity mob;

        public SmoothFlyingMoveControl(InfSkeletonEntity mob) {
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

                // 前方有碰撞就停下，避免怼墙
                if (!canReach(dir, Mth.clamp(Mth.ceil(dist), 1, 16))) {
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
            } else {
                // 没有移动指令时缓慢减速，保持悬浮
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
