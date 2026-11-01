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
 * <b>死亡猎手</b> —— 完全照<b>恼鬼（Vex）</b>做的飞行单位：数值全部取恼鬼的 {@value #SCALE} 倍，
 * 索敌与飞行沿用恼鬼那一套（穿墙飞行、冲刺、随机游荡）。
 *
 * <h3>数值：恼鬼 × {@value #SCALE}</h3>
 * <table border="1">
 *   <tr><th>属性</th><th>恼鬼</th><th>本实体</th></tr>
 *   <tr><td>生命</td><td>14.0</td><td>{@value #MAX_HEALTH}</td></tr>
 *   <tr><td>攻击力</td><td>4.0</td><td>{@value #ATTACK_DAMAGE}</td></tr>
 *   <tr><td>移动速度</td><td>0.7</td><td>{@value #MOVEMENT_SPEED}</td></tr>
 *   <tr><td>跟随范围</td><td>16.0</td><td>{@value #FOLLOW_RANGE}</td></tr>
 *   <tr><td>碰撞箱</td><td>0.4 × 0.8</td><td>0.48 × 0.96</td></tr>
 * </table>
 * <p>护甲与击退抗性在恼鬼身上本来就是 0，乘 1.2 仍是 0，所以不显式写。</p>
 *
 * <h3>飞行与索敌：照抄恼鬼</h3>
 * <ul>
 *   <li><b>穿墙飞行</b>：每 tick 临时把 {@code noPhysics} 打开（这正是恼鬼"能穿方块"的原因），
 *       tick 结束立刻关掉；同时 {@code setNoGravity(true)}。</li>
 *   <li><b>移动控制</b>：{@link HunterMoveControl} 就是恼鬼的 {@code VexMoveControl} ——
 *       朝目标点持续叠加加速度（{@code speed × 0.05 / 距离}），到达后一半速度滑行，
 *       并把朝向转向速度或目标。</li>
 *   <li><b>冲刺</b>：{@link ChargeAttackGoal} 对应恼鬼的 {@code VexChargeAttackGoal} ——
 *       冲刺时锁定目标眼睛、把速度提到 1.0；伤害由基类的近战钩子结算。</li>
 *   <li><b>游荡</b>：{@link RandomMoveGoal} 对应 {@code VexRandomMoveGoal} ——
 *       随机找一个空气方块飞过去。</li>
 *   <li><b>索敌</b>：{@code HurtByTargetGoal}（被打就还手）+
 *       {@code NearestAttackableTargetGoal(Player)}（要看见），和恼鬼一致；
 *       恼鬼的"复制主人目标"那条不适用（本实体没有主人）。</li>
 * </ul>
 *
 * <h3>和基类的分工</h3>
 * <p>伤害走 {@link AbstractRottenEntity} 的近战钩子：{@link #getAttack} 里调
 * {@code doHurtTarget}，触发距离由 {@code setDoHurtDistance} 控制。
 * 冲刺目标只负责<b>移动</b>，不自己结算伤害 —— 否则会和基类各打一次。</p>
 *
 * <p>美术资源按小写命名：{@code geo/death_hunter.geo.json}、
 * {@code textures/entity/death_hunter.png}、{@code animations/death_hunter.animation.json}
 * （动画文件缺失不影响运行，只是不会播表情动作）。</p>
 */
public class DeathHunter extends AbstractRottenEntity {

    /** 相对恼鬼的数值倍率。 */
    public static final double SCALE = 1.2D;

    // ── 恼鬼原值 × SCALE ──
    private static final double MAX_HEALTH = 14.0D * SCALE;        // 16.8
    private static final double ATTACK_DAMAGE = 4.0D * SCALE;      // 4.8
    private static final double MOVEMENT_SPEED = 0.7D * SCALE;     // 0.84
    private static final double FOLLOW_RANGE = 16.0D * SCALE;      // 19.2
    private static final int XP_REWARD = (int) Math.round(3 * SCALE);   // 4

    /** 碰撞箱：恼鬼 0.4 × 0.8 放大到 1.2 倍。 */
    public static final float WIDTH = 0.48F;
    public static final float HEIGHT = 0.96F;

    /**
     * 眼睛高度相对碰撞箱顶部的偏移（恼鬼是 0.28125，按倍率放大）。
     * <p>它比"身高的一半"低 —— 恼鬼的模型是悬空的一小团，眼睛不在几何中心。</p>
     */
    private static final float EYE_OFFSET_FROM_TOP = 0.28125F * (float) SCALE;

    /** 近战判定距离（格，箱到箱）。恼鬼是"碰撞箱相交就打到"，这里给一点点余量。 */
    private static final float HURT_DISTANCE = 1.2F;

    /** 冲刺时朝目标瞄准的距离上限（格）—— 和恼鬼一样取 3 格。 */
    private static final double CHARGE_CHASE_DISTANCE = 3.0D;

    /** 是否正在冲刺（同步给客户端，供渲染/动画用）。 */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(DeathHunter.class, EntityDataSerializers.BOOLEAN);

    public DeathHunter(@NotNull EntityType<? extends Monster> entityType, @NotNull Level level) {
        super(entityType, level);
        // 恼鬼的飞行：自定义移动控制 + 无重力
        this.moveControl = new HunterMoveControl(this);
        this.setNoGravity(true);
        this.xpReward = XP_REWARD;
        // 伤害交给基类的近战钩子（见 getAttack）；冲刺目标只管移动
        this.setDoHurtDistance(HURT_DISTANCE);
        // 基类会在构造期用 walk/run 速度覆盖 MOVEMENT_SPEED（默认 0.6 / 1.2），
        // 所以必须把两者都设成"恼鬼 × 1.2"，否则属性表里那个 0.84 立刻被冲掉。
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

    /**
     * 属性表：以恼鬼的 {@code createAttributes()} 为底，再按 {@value #SCALE} 倍覆盖。
     *
     * <p>恼鬼只显式声明了生命 14 与攻击 4，速度 0.7 与跟随范围 16 都是
     * {@code Monster.createMonsterAttributes()} 的默认值 —— 这里把它们也显式写出来，
     * 免得以后原版改默认值时本实体跟着漂移。</p>
     */
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

    // ══════════════════════════════════════════════════════════════
    // 恼鬼式飞行
    // ══════════════════════════════════════════════════════════════

    /**
     * 恼鬼的 tick：整段逻辑里把 {@code noPhysics} 打开，所以它<b>无视碰撞</b>
     * （能穿墙、不会卡在方块边上），tick 结束立刻还原，不会影响别的系统。
     */
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

    /** 眼睛高度：碰撞箱顶部往下 {@value #EYE_OFFSET_FROM_TOP} 格（照恼鬼的算法）。 */
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

    // ══════════════════════════════════════════════════════════════
    // 恼鬼式索敌
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void registerGoals() {
        // 与恼鬼同序：先浮水，再冲刺、游荡，最后两个"看着玩家/生物"
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new ChargeAttackGoal());
        this.goalSelector.addGoal(8, new RandomMoveGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));

        // 恼鬼的 targetSelector 还有一条"复制主人目标"，本实体没有主人所以不适用
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 近战：伤害由基类按 {@code doHurtDistance} 调用（冲刺时不重复结算）。 */
    @Override
    protected boolean getAttack(@NotNull LivingEntity target) {
        return this.doHurtTarget(target);
    }

    /**
     * 基类每 tick 的"追击"里其实混了两件事：<b>寻路位移</b>和<b>贴身出手</b>。
     *
     * <p>本实体是恼鬼式飞行，位移必须由 {@link HunterMoveControl} 独家负责 —— 于是这里
     * <b>只保留出手</b>，把 {@code getNavigation().moveTo(...)} 那条路砍掉。不砍的话会有两个后果：
     * 两条位移逻辑同 tick 抢 {@code moveControl}；而且寻路会把 {@code hasWanted()} 常置为真，
     * 导致 {@link ChargeAttackGoal} 几乎永远进不了"开始冲刺"的判定。</p>
     *
     * <p>另外这里<b>不要求视线</b>（基类原版要求 {@code hasLineOfSight}）：恼鬼是穿墙飞行的，
     * 隔着方块贴到目标身上却打不到会很怪。</p>
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

    // ══════════════════════════════════════════════════════════════
    // 恼鬼式移动控制
    // ══════════════════════════════════════════════════════════════

    /**
     * 恼鬼的移动控制：不是寻路，而是"每 tick 朝目标点叠加速度"。
     *
     * <p>关键两点和恼鬼一致：</p>
     * <ol>
     *   <li>加速度大小是 {@code speed × 0.05 / 距离} —— 越远加得越多，但因为是<b>加</b>
     *       到速度上、每 tick 都算一次，最终速度会收敛到由 {@code speed} 决定的上限；</li>
     *   <li>到达目标点后速度乘 0.5 滑行（不是急停），读起来是"漂浮着刹住"。</li>
     * </ol>
     */
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

    // ══════════════════════════════════════════════════════════════
    // 恼鬼式两个 Goal
    // ══════════════════════════════════════════════════════════════

    /**
     * 冲刺：锁定目标眼睛、把速度提到 1.0 冲过去（伤害由基类结算）。
     *
     * <p>触发条件是"离得比较远（&gt;2 格）且这一 tick 掷到 1/7"，和恼鬼一样是随机节奏，
     * 不冲刺的时候看起来是在飘。</p>
     */
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

    /**
     * 随机游荡：没目标时随机挑一个附近的空气方块飞过去（和恼鬼一致）。
     *
     * <p>只"执行一 tick"（{@code canContinueToUse} 恒 false）—— 恼鬼本来就是
     * 隔一会儿丢一个目标点，靠移动控制自己飘过去。</p>
     */
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
