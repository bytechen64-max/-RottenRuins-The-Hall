package org.bytechen.rottenruins.overworld.registry.entities.population.death;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * 死亡猎手 —— 照恼鬼（Vex）做的飞行单位：数值取恼鬼的 {@value #SCALE} 倍，
 * 穿墙飞行、冲刺与随机游荡也沿用恼鬼那一套。
 */
public class DeathHunter extends AbstractRottenEntity {

    /** 相对恼鬼的数值倍率。 */
    public static final double SCALE = 1.2D;

    private static final double MAX_HEALTH = 14.0D * SCALE;
    private static final double ATTACK_DAMAGE = 4.0D * SCALE;
    private static final double MOVEMENT_SPEED = 0.7D * SCALE;
    private static final double FOLLOW_RANGE = 16.0D * SCALE;
    private static final int XP_REWARD = (int) Math.round(3 * SCALE);

    /** 碰撞箱：恼鬼 0.4 × 0.8 放大到 1.2 倍。 */
    public static final float WIDTH = 0.48F;
    public static final float HEIGHT = 0.96F;

    /** 眼睛高度相对碰撞箱顶部的偏移（恼鬼是 0.28125，按倍率放大）；恼鬼模型悬空，眼睛不在几何中心。 */
    private static final float EYE_OFFSET_FROM_TOP = 0.28125F * (float) SCALE;

    /** 近战判定距离（格，箱到箱）。 */
    private static final float HURT_DISTANCE = 1.2F;

    /** 冲刺时朝目标瞄准的距离上限（格）；和恼鬼一样取 3 格。 */
    private static final double CHARGE_CHASE_DISTANCE = 3.0D;

    /** 是否正在冲刺（同步给客户端，供渲染/动画用）。 */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(DeathHunter.class, EntityDataSerializers.BOOLEAN);

    public DeathHunter(@NotNull EntityType<? extends Monster> entityType, @NotNull Level level) {
        super(entityType, level);
        this.moveControl = new HunterMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = XP_REWARD;
        this.setDoHurtDistance(HURT_DISTANCE);
        // 基类构造期会用 walk/run 速度覆盖 MOVEMENT_SPEED（默认 0.6 / 1.2），两者都要设成"恼鬼 × 1.2"，
        // 否则属性表里那个 0.84 立刻被冲掉。
        this.setWalkSpeed((float) MOVEMENT_SPEED);
        this.setRunSpeed((float) MOVEMENT_SPEED);
    }

    public DeathHunter(@NotNull EntityType<? extends Monster> entityType, @NotNull Level level,
                       @NotNull Consumer<AbstractRottenEntity> resources) {
        this(entityType, level);
        resources.accept(this);
    }

    /** 实体资源（geo / 贴图 / 动画），名字全小写。 */
    public static Consumer<AbstractRottenEntity> resources() {
        return EntityTypeRegistry.defaultResources("death_hunter");
    }

    /** 属性表：以恼鬼的 createAttributes() 为底按 SCALE 覆盖；速度与跟随范围也显式写出，免得原版改默认值时跟着漂移。 */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CHARGING, false);
    }

    /** 恼鬼式飞行：整段逻辑里 noPhysics 打开所以无视碰撞（穿墙、不卡方块边），tick 结束立刻还原。 */
    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
    }

    /** 忽略碰撞之后仍然要检测"在方块里"，否则窒息/位置修正逻辑会失效（与恼鬼一致）。 */
    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 movement) {
        super.move(type, movement);
        this.checkInsideBlocks();
    }

    /** 眼睛高度：碰撞箱顶部往下 EYE_OFFSET_FROM_TOP 格（照恼鬼的算法）。 */
    @Override
    public float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions dimensions) {
        return dimensions.height - EYE_OFFSET_FROM_TOP;
    }

    /** 拾取半径：恼鬼是 0.4，同样放大。 */
    @Override
    public float getPickRadius() {
        return 0.4F * (float) SCALE;
    }

    /** 冲刺中（客户端也能读到，给动画/粒子用）。 */
    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    private void setCharging(boolean charging) {
        this.entityData.set(DATA_CHARGING, charging);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new ChargeAttackGoal());
        this.goalSelector.addGoal(8, new RandomMoveGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 近战：伤害由基类按 doHurtDistance 调用（冲刺时不重复结算）。 */
    @Override
    protected boolean getAttack(@NotNull LivingEntity target) {
        return this.doHurtTarget(target);
    }

    /**
     * 只保留出手，砍掉基类的 getNavigation().moveTo：位移必须由 HunterMoveControl 独家负责，
     * 否则两条位移逻辑同 tick 抢 moveControl，且寻路会把 hasWanted() 常置真、冲刺判定几乎进不去。
     * 这里也不要求视线 —— 穿墙飞行时隔方块打到会很怪。
     */
    @Override
    protected void tickChase() {
        if (this.isNoAi()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (this.getBoundingBoxDistanceSqrExact(target) <= this.getDoHurtDistance() * this.getDoHurtDistance()
                && this.doHurtCooldown == 0 && this.doHurtGoal(target)) {
            this.doHurtCooldown = this.getDoHurtTime();
        }
    }

    /** 恼鬼式移动控制：每 tick 朝目标点叠加 speed × 0.05 / 距离 的加速度（速度因此收敛到 speed 上限），到点后半速滑行。 */
    private final class HunterMoveControl extends MoveControl {

        private HunterMoveControl(@NotNull Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                return;
            }
            Vec3 delta = new Vec3(this.wantedX - DeathHunter.this.getX(),
                    this.wantedY - DeathHunter.this.getY(),
                    this.wantedZ - DeathHunter.this.getZ());
            double distance = delta.length();
            if (distance < DeathHunter.this.getBoundingBox().getSize()) {
                // 到点了：半速滑行（恼鬼的手感就来自这一步）
                this.operation = MoveControl.Operation.WAIT;
                DeathHunter.this.setDeltaMovement(DeathHunter.this.getDeltaMovement().scale(0.5D));
                return;
            }
            DeathHunter.this.setDeltaMovement(DeathHunter.this.getDeltaMovement()
                    .add(delta.scale(this.speedModifier * 0.05D / distance)));

            // 朝向：有目标就盯着目标，没目标就顺着速度方向
            LivingEntity target = DeathHunter.this.getTarget();
            if (target == null) {
                Vec3 velocity = DeathHunter.this.getDeltaMovement();
                DeathHunter.this.setYRot(-((float) Mth.atan2(velocity.x, velocity.z)) * (180F / (float) Math.PI));
            } else {
                double dx = target.getX() - DeathHunter.this.getX();
                double dz = target.getZ() - DeathHunter.this.getZ();
                DeathHunter.this.setYRot(-((float) Mth.atan2(dx, dz)) * (180F / (float) Math.PI));
            }
            DeathHunter.this.yBodyRot = DeathHunter.this.getYRot();
        }
    }

    /** 冲刺：锁定目标眼睛、把速度提到 1.0；触发条件是距离 > CHARGE_CHASE_DISTANCE 且每 tick 掷 1/7（恼鬼的随机节奏）。 */
    private final class ChargeAttackGoal extends Goal {

        private ChargeAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = DeathHunter.this.getTarget();
            if (target == null || !target.isAlive() || DeathHunter.this.getMoveControl().hasWanted()) {
                return false;
            }
            return DeathHunter.this.getRandom().nextInt(reducedTickDelay(7)) == 0
                    && DeathHunter.this.distanceToSqr(target) > CHARGE_CHASE_DISTANCE;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = DeathHunter.this.getTarget();
            return DeathHunter.this.getMoveControl().hasWanted()
                    && DeathHunter.this.isCharging()
                    && target != null && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = DeathHunter.this.getTarget();
            if (target != null) {
                Vec3 eye = target.getEyePosition();
                DeathHunter.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0D);
            }
            DeathHunter.this.setCharging(true);
            DeathHunter.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            DeathHunter.this.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = DeathHunter.this.getTarget();
            if (target == null) {
                return;
            }
            // 冲刺途中持续修正方向（目标会动）
            if (DeathHunter.this.distanceToSqr(target) < CHARGE_CHASE_DISTANCE * CHARGE_CHASE_DISTANCE * 1.5D) {
                Vec3 eye = target.getEyePosition();
                DeathHunter.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0D);
            }
        }
    }

    /** 随机游荡：挑一个附近空气方块飞过去；只执行一 tick，靠移动控制自己飘过去（和恼鬼一致）。 */
    private final class RandomMoveGoal extends Goal {

        private RandomMoveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !DeathHunter.this.getMoveControl().hasWanted()
                    && DeathHunter.this.getRandom().nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = DeathHunter.this.getRandom();
            for (int attempt = 0; attempt < 3; attempt++) {
                var pos = DeathHunter.this.blockPosition().offset(
                        random.nextInt(15) - 7,
                        random.nextInt(11) - 5,
                        random.nextInt(15) - 7);
                if (DeathHunter.this.level().isEmptyBlock(pos)) {
                    DeathHunter.this.getMoveControl().setWantedPosition(
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.25D);
                    if (DeathHunter.this.getTarget() == null) {
                        DeathHunter.this.getLookControl().setLookAt(
                                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }

    /** 冲刺状态要跟着存档走（读档后不至于一直"冲刺中"）。 */
    @Override
    public void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DeathHunterCharging", this.isCharging());
    }

    @Override
    public void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setCharging(tag.getBoolean("DeathHunterCharging"));
    }
}
