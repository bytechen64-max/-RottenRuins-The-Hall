package org.bytechen.rottenruins.overworld.registry.entities.population.death;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * <b>死亡猎手</b> —— 死亡系（{@link AbstractDeathEntity}）的飞行猎杀单位：
 * 数值沿<b>恼鬼（Vex）</b>的 {@value #SCALE} 倍，移动沿用恼鬼那套
 * （穿墙飞行 / 冲刺 / 随机游荡），攻击则是"近身挥匕首"的动画 + 延迟命中。
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
 *
 * <h3>动画：idle / fly / attack（驱动自本实体自己的控制器）</h3>
 * <p>{@code death_hunter.animation.json} 里只有这三段，<b>没有</b> walk / run ——
 * 所以这里不能直接用基类的 {@code movement_controller}（它会去找 walk / run，
 * 找不到就只会停在 T 姿）。本类自己注册控制器：</p>
 * <ul>
 *   <li><b>movement_controller</b>：静止 → {@code idle}；在飞 → {@code fly}
 *       （冲刺阶段把控制器速度提到 run 档，翅膀扇得更急）。</li>
 *   <li><b>attack_controller</b>：常态 STOP，出手时由
 *       {@link #doHurtGoal(LivingEntity)} 触发 {@code attack}。
 *       动画 0.7917s，0.125s 抬手 → <b>0.25s 挥砍峰值</b> → 0.7917s 回中，
 *       所以命中点固定在动画内 0.25s 处（见下面的常量说明）。</li>
 * </ul>
 *
 * <h3>攻击：动画 + 延迟命中</h3>
 * <p>出手时<b>不</b>立刻结算伤害，而是播 {@code attack} 并起倒计时，
 * 在挥砍峰值那一 tick 才调用 {@link #getAttack(LivingEntity)}。这样"刀挥到
 * 身上"与"目标掉血"是同一瞬间，不会出现动画还在起手、伤害已经跳出来的错位。
 * 延迟里补偿了 GeckoLib 的过渡帧（triggerAnim 后头
 * {@value #ATTACK_TRANSITION_TICKS} tick 动画时钟被钉在 0），详见
 * {@link #ATTACK_HIT_DELAY_TICKS}。</p>
 * <p>命中瞬间会重新校验主目标：前摇里目标跑出
 * {@code doHurtDistance + }{@value #ATTACK_HIT_GRACE} 就判空挥；
 * 命中后仍走基类的近战钩子（{@link #getAttack}），伤害含难度属性修饰符。</p>
 *
 * <h3>锁敌：跟项目里其它感染生物同一套</h3>
 * <ul>
 *   <li>死亡猎手属<b>独立的 DEATH 阵营</b>（{@link AbstractDeathEntity} 固定
 *       {@code faction() = RottenFaction.DEATH}），与王庭 / 腐化互为不同感染类型；
 *       开启跨阵营索敌后它天然敌视两派，而同为死亡系的生物之间不会互殴。</li>
 *   <li>注册 {@link InfectedTargetGoal}（不是原版的 {@code NearestAttackableTargetGoal}）：
 *       这样它才会按阵营规则索敌、能打王庭与腐化生物，并且<b>不需要视线</b> ——
 *       一个穿墙飞行的猎手不该被墙挡住索敌。</li>
 *   <li>{@code .filter(this)} 走 {@link AbstractRottenEntity#canTarget} 的
 *       <b>死亡系口径</b>：不看王庭威胁点数（那套闸门只属于王庭生物），
 *       只排除创造 / 旁观玩家；其余目标一律可被主动索敌。</li>
 *   <li>冲刺（{@link ChargeAttackGoal}）与随机游荡（{@link RandomMoveGoal}）只在
 *       {@code MOVE} 标记上竞争，索敌由 targetSelector 独立负责，互不顶掉。</li>
 * </ul>
 *
 * <h3>和基类的分工</h3>
 * <p>伤害走 {@link AbstractRottenEntity} 的近战钩子：{@link #getAttack} 结算伤害，
 * 距离由 {@code setDoHurtDistance} 控制；{@link #tickChase()} 只负责"贴身出手"，
 * 位移全部交给 {@link HunterMoveControl}。冲刺目标只负责<b>移动</b>，
 * 不自己结算伤害 —— 否则会和基类各打一次。</p>
 *
 * <h3>死亡系约定</h3>
 * <p>继承 {@link AbstractDeathEntity}：<b>独立 DEATH 阵营、受伤不溅血、死亡不爆体</b>
 * （详见父类注释）。美术资源：{@code geo/death_hunter.geo.json}、
 * {@code textures/entity/death_hunter.png}、{@code animations/death_hunter.animation.json}。</p>
 */
public class DeathHunterEntity extends AbstractDeathEntity {

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

    // ══════════════════════════════════════════════════════════════
    // 攻击动画 / 命中时机（attack_controller）
    // ══════════════════════════════════════════════════════════════
    //
    // death_hunter.animation.json 里的 attack：thenPlay、animation_length = 0.7917s、不循环：
    //   0.0   左臂回中
    //   0.125 抬手蓄力（X = -80.49°）
    //   0.25  挥砍峰值（X = +41.07°，匕首扫到目标）
    //   0.7917 回中
    // 命中时刻取挥砍峰值 0.25s。
    //
    // GeckoLib 4 的 AnimationController 第三个构造参数是 transitionLength（平滑切换 tick）：
    // triggerAnim 之后的头 ATTACK_TRANSITION_TICKS tick 只做骨骼混合、动画时钟被钉在 0，
    // 过渡结束那一 tick 才把时钟清零并真正开始播放。所以：
    //     triggerAnim ──(transition)──> 动画时钟 0 ──(0.25s)──> 命中
    // 从 triggerAnim 起算的真实延迟必须把 transition 补偿进来，否则会提前 transition 那么久就打中。

    /** 攻击动画平滑切换 tick（0.15s）：给 movement_controller 留出混合时间，挥刀不突兀。 */
    private static final int ATTACK_TRANSITION_TICKS = 3;
    /**
     * 动画真正开始播放后到命中的时间（秒）。
     * <p>取自 {@code death_hunter.animation.json} 里 attack 的挥砍峰值关键帧 0.25s；
     * <b>改动动画关键帧时必须同步改这里</b>，否则伤害会落在挥刀之前或之后。</p>
     */
    private static final float ATTACK_HIT_SECONDS = 0.25F;
    /** 命中点落在动画内部的 tick 数：0.25 * 20 = 5。 */
    private static final int ATTACK_HIT_TICK_IN_ANIM = Math.round(ATTACK_HIT_SECONDS * 20.0F);
    /** 从 triggerAnim 到结算伤害的真实延迟 = transition 补偿 + 动画内命中点 = 3 + 5 = 8 tick。 */
    private static final int ATTACK_HIT_DELAY_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_HIT_TICK_IN_ANIM;
    /** 攻击动画长度（秒），与 death_hunter.animation.json 的 animation_length 保持一致。 */
    private static final float ATTACK_ANIM_LENGTH_SECONDS = 0.7917F;
    /** 动画长度换算成 tick：ceil(0.7917 * 20) = ceil(15.834) = 16，向上取整宁可多等一 tick。 */
    private static final int ATTACK_ANIM_TICKS = (int) Math.ceil(ATTACK_ANIM_LENGTH_SECONDS * 20.0F);
    /** 一次攻击从 triggerAnim 到动画彻底播完的总 tick = 3 + 16 = 19，用于"上一刀没播完不许重触发"。 */
    private static final int ATTACK_TOTAL_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_ANIM_TICKS;
    /**
     * 命中瞬间对主目标的额外距离容差（格）。
     * <p>0.4s 前摇里目标可能后撤半步；在这个范围内仍算命中，
     * 超出则判空挥（不追打已经飞远的目标）。</p>
     */
    private static final float ATTACK_HIT_GRACE = 1.0F;

    /** 是否正在冲刺（同步给客户端，供渲染 / 动画用）。 */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(DeathHunterEntity.class, EntityDataSerializers.BOOLEAN);

    /** 移动控制器里的移动动画：模型只有 idle / fly 两段（没有 walk / run）。 */
    private static final RawAnimation ANIM_FLY = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("attack");

    // ══════════════════════════════════════════════════════════════
    // 攻击状态（仅服务端使用，不同步、不进 NBT —— 攻击被打断就丢掉即可）
    // ══════════════════════════════════════════════════════════════

    /** 命中倒计时：归零时结算一次近战伤害。 */
    private int attackHitTimer;
    /** 动画总时长倒计时：>0 表示上一刀还在播，期间不允许重新触发。 */
    private int attackAnimTimer;
    /**
     * 触发动画时锁定的主目标：命中时刻用它，而不是命中瞬间的 getTarget()。
     * 否则 0.4s 前摇里目标一换人，动画还在往旧目标的方向挥、伤害却打到新目标身上。
     */
    @Nullable
    private LivingEntity pendingAttackTarget;

    public DeathHunterEntity(@NotNull EntityType<? extends Monster> entityType, @NotNull Level level) {
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
        // 动画档位：飞行动画 1.0，冲刺时把控制器速度提到 1.4（翅膀扇得更急）
        this.setIdleAnimSpeed(1.0F);
        this.setWalkAnimSpeed(1.0F);
        this.setRunAnimSpeed(1.4F);
    }

    public DeathHunterEntity(@NotNull EntityType<? extends Monster> entityType, @NotNull Level level,
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

    /** 冲刺中（客户端也能读到，给动画 / 粒子用）。 */
    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    private void setCharging(boolean charging) {
        this.entityData.set(DATA_CHARGING, charging);
    }

    /**
     * 恼鬼的 tick：整段逻辑里把 {@code noPhysics} 打开，所以它<b>无视碰撞</b>
     * （能穿墙、不会卡在方块边上），tick 结束立刻还原，不会影响别的系统。
     *
     * <p>本类额外在 {@code super.tick()} 之前推进攻击倒计时：
     * 发起攻击的入口 {@code tickChase → doHurtGoal} 是在 {@code super.tick()}
     * 内部执行的，倒计时必须先走一步，命中才会准确落在 triggerAnim +
     * {@link #ATTACK_HIT_DELAY_TICKS} tick。</p>
     */
    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            this.tickAttack();
        }
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
    }

    /** 忽略碰撞之后仍然要检测"在方块里"，否则窒息 / 位置修正逻辑会失效（与恼鬼一致）。 */
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

    // ══════════════════════════════════════════════════════════════
    // 锁敌（与项目内其它感染生物同一套）
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void registerGoals() {
        // 与恼鬼同序：先浮水，再冲刺、游荡，最后两个"看着玩家 / 生物"
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new ChargeAttackGoal());
        this.goalSelector.addGoal(8, new RandomMoveGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));

        // 索敌：infcore 的 InfectedTargetGoal（项目内所有感染生物的公共索敌），
        // 而不是原版 NearestAttackableTargetGoal —— 后者只认玩家、要求视线，
        // 对穿墙飞行的猎手是错的。
        this.targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true)   // 死亡系独立阵营：可打王庭与腐化，不误伤同类死亡系
                .range(FOLLOW_RANGE)         // 跟随范围（恼鬼 × 1.2 = 19.2 格）
                .mustSee(false)              // 穿墙飞行：不要求视线
                .filter(this)                // 死亡系口径：不看王庭威胁点数，只排除创造 / 旁观玩家
                .build());
    }

    // ══════════════════════════════════════════════════════════════
    // 攻击：动画 + 延迟命中
    // ══════════════════════════════════════════════════════════════

    /**
     * 出手入口：由 {@link #tickChase()} 在贴身时调用。
     *
     * <p>这里<b>只触发动画 + 起倒计时，不立刻造成伤害</b>。伤害统一在
     * {@link #tickAttack()} 里、triggerAnim 之后 {@link #ATTACK_HIT_DELAY_TICKS} tick
     * 结算，对应动画里 0.25s 的挥砍峰值。返回 true 只是告诉基类"这次出手成立"，
     * 让基类照常进入 {@code doHurtTime} 冷却。</p>
     *
     * <p>上一刀动画还没播完时返回 false：既不重触发（会打断动画并让倒计时错位），
     * 也不吃掉基类的出手冷却。</p>
     */
    @Override
    protected boolean doHurtGoal(LivingEntity target) {
        if (this.isDeadOrDying() || this.attackAnimTimer > 0) {
            return false;
        }
        this.pendingAttackTarget = target;
        this.attackHitTimer = ATTACK_HIT_DELAY_TICKS;
        this.attackAnimTimer = ATTACK_TOTAL_TICKS;
        this.triggerAnim("attack_controller", "attack");
        return true;
    }

    /**
     * 近战伤害结算 —— 由 {@link #resolveAttackHit()} 在挥砍峰值那一 tick 调用。
     *
     * <p>读的是 {@code ATTACK_DAMAGE} 属性的<b>生效值</b>（含各种属性修饰符），
     * 而不是常量或属性基础值 —— 否则以后任何伤害加成挂到属性上都不会被读到。
     * 死亡系是独立阵营，不吃王庭难度倍率，所以这里直接读属性值（与腐化系一致）。</p>
     */
    @Override
    protected boolean getAttack(LivingEntity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        target.hurt(this.damageSources().mobAttack(this), damage);
        return true;
    }

    /**
     * 推进攻击倒计时，是这套"动画 + 延迟命中"的唯一伤害出口。
     *
     * <p>时序：doHurtGoal 在 tick T 触发动画并把 attackHitTimer 置为
     * {@link #ATTACK_HIT_DELAY_TICKS}；之后每 tick 进入本方法先减一，
     * 正好到 tick T+8 减到 0 并结算伤害。GeckoLib 的动画时钟在 triggerAnim 后
     * 先冻结 {@link #ATTACK_TRANSITION_TICKS} tick、再从 0 开始播，
     * 所以结算时刻 = 动画时钟 0.25s 处，与挥砍峰值对齐。</p>
     */
    private void tickAttack() {
        // 已死亡：残留状态必须清干净，避免复活 / 重进世界后旧目标在很远处补一刀。
        if (this.isDeadOrDying()) {
            this.attackHitTimer = 0;
            this.attackAnimTimer = 0;
            this.pendingAttackTarget = null;
            return;
        }

        if (this.attackHitTimer > 0) {
            this.attackHitTimer--;
            if (this.attackHitTimer == 0) {
                this.resolveAttackHit();
            }
        }

        if (this.attackAnimTimer > 0) {
            this.attackAnimTimer--;
            // 动画播完：清掉残留锁定的目标（命中时通常已经清过，这里兜底）。
            if (this.attackAnimTimer == 0) {
                this.pendingAttackTarget = null;
            }
        }
    }

    /**
     * 命中瞬间：用<b>起手时锁定</b>的主目标结算，并做一次距离复核。
     *
     * <p>0.4s 前摇里目标可能已经后撤；只要还在
     * {@code doHurtDistance + }{@value #ATTACK_HIT_GRACE} 的碰撞箱间距内就算命中，
     * 超出则判空挥 —— 猎手不会隔着几格凭空抽走别人的血。</p>
     */
    private void resolveAttackHit() {
        LivingEntity target = this.pendingAttackTarget;
        if (target == null) {
            return;
        }
        this.pendingAttackTarget = null;
        if (!target.isAlive()) {
            return;
        }
        double reach = this.getDoHurtDistance() + ATTACK_HIT_GRACE;
        if (this.getBoundingBoxDistanceSqrExact(target) > reach * reach) {
            return;
        }
        this.getAttack(target);
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
    // 动画控制器
    // ══════════════════════════════════════════════════════════════

    /**
     * 移动 / 待机控制器 —— 覆盖基类版本，因为本模型的动画是
     * {@code idle} / {@code fly}，<b>没有</b> {@code walk} / {@code run}。
     *
     * <p>其余控制器仍按基类顺序注册：movement → attack（扩展点）→ die，
     * GeckoLib 按注册顺序应用控制器、后注册的覆盖先注册的骨骼，
     * 攻击必须压在移动之上，死亡排最后。</p>
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) {
            return;
        }
        registrar.add(new AnimationController<>(this, "movement_controller", 3, state -> {
            // 死亡系不会爆体（见 AbstractDeathEntity），这里的判断只是保持与基类一致。
            if (isDeadOrDying() || isBurstAnimating()) {
                return PlayState.STOP;
            }
            if (!isActuallyMoving) {
                state.setControllerSpeed(getIdleAnimSpeed());
                return state.setAndContinue(ANIM_IDLE);
            }
            // 飞行：普通巡航用 walk 档，冲刺时用 run 档（翅膀扇得更急）
            state.setControllerSpeed(isCharging() ? getRunAnimSpeed() : getWalkAnimSpeed());
            return state.setAndContinue(ANIM_FLY);
        }));

        // 子类扩展点：attack_controller 夹在移动与死亡之间。
        registerAdditionalControllers(registrar);

        AnimationController<AbstractRottenEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        isBurstAnimating() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }

    /**
     * 只追加攻击控制器：{@code stateHandler} 恒返回 STOP，动画完全由 triggerAnim 驱动
     * （{@code attack} 是 thenPlay、不循环），播完自动回落到 movement_controller。
     */
    @Override
    protected void registerAdditionalControllers(AnimatableManager.ControllerRegistrar registrar) {
        AnimationController<AbstractRottenEntity> attackController =
                new AnimationController<>(this, "attack_controller", ATTACK_TRANSITION_TICKS,
                        state -> PlayState.STOP);
        attackController.triggerableAnim("attack", ANIM_ATTACK);
        registrar.add(attackController);
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
            Vec3 delta = new Vec3(this.wantedX - DeathHunterEntity.this.getX(),
                    this.wantedY - DeathHunterEntity.this.getY(),
                    this.wantedZ - DeathHunterEntity.this.getZ());
            double distance = delta.length();
            if (distance < DeathHunterEntity.this.getBoundingBox().getSize()) {
                // 到点了：半速滑行（恼鬼的手感就来自这一步）
                this.operation = MoveControl.Operation.WAIT;
                DeathHunterEntity.this.setDeltaMovement(DeathHunterEntity.this.getDeltaMovement().scale(0.5D));
                return;
            }
            DeathHunterEntity.this.setDeltaMovement(DeathHunterEntity.this.getDeltaMovement()
                    .add(delta.scale(this.speedModifier * 0.05D / distance)));

            // 朝向：有目标就盯着目标，没目标就顺着速度方向
            LivingEntity target = DeathHunterEntity.this.getTarget();
            if (target == null) {
                Vec3 velocity = DeathHunterEntity.this.getDeltaMovement();
                DeathHunterEntity.this.setYRot(-((float) Mth.atan2(velocity.x, velocity.z)) * (180F / (float) Math.PI));
            } else {
                double dx = target.getX() - DeathHunterEntity.this.getX();
                double dz = target.getZ() - DeathHunterEntity.this.getZ();
                DeathHunterEntity.this.setYRot(-((float) Mth.atan2(dx, dz)) * (180F / (float) Math.PI));
            }
            DeathHunterEntity.this.yBodyRot = DeathHunterEntity.this.getYRot();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 恼鬼式两个 Goal
    // ══════════════════════════════════════════════════════════════

    /**
     * 冲刺：锁定目标眼睛、把速度提到 1.0 冲过去（伤害由基类的近战钩子结算）。
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
            LivingEntity target = DeathHunterEntity.this.getTarget();
            if (target == null || !target.isAlive() || DeathHunterEntity.this.getMoveControl().hasWanted()) {
                return false;
            }
            return DeathHunterEntity.this.getRandom().nextInt(reducedTickDelay(7)) == 0
                    && DeathHunterEntity.this.distanceToSqr(target) > CHARGE_CHASE_DISTANCE;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = DeathHunterEntity.this.getTarget();
            return DeathHunterEntity.this.getMoveControl().hasWanted()
                    && DeathHunterEntity.this.isCharging()
                    && target != null && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = DeathHunterEntity.this.getTarget();
            if (target != null) {
                Vec3 eye = target.getEyePosition();
                DeathHunterEntity.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0D);
            }
            DeathHunterEntity.this.setCharging(true);
            DeathHunterEntity.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            DeathHunterEntity.this.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = DeathHunterEntity.this.getTarget();
            if (target == null) {
                return;
            }
            // 冲刺途中持续修正方向（目标会动）
            if (DeathHunterEntity.this.distanceToSqr(target)
                    < CHARGE_CHASE_DISTANCE * CHARGE_CHASE_DISTANCE * 1.5D) {
                Vec3 eye = target.getEyePosition();
                DeathHunterEntity.this.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.0D);
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
            return !DeathHunterEntity.this.getMoveControl().hasWanted()
                    && DeathHunterEntity.this.getRandom().nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = DeathHunterEntity.this.getRandom();
            for (int attempt = 0; attempt < 3; attempt++) {
                var pos = DeathHunterEntity.this.blockPosition().offset(
                        random.nextInt(15) - 7,
                        random.nextInt(11) - 5,
                        random.nextInt(15) - 7);
                if (DeathHunterEntity.this.level().isEmptyBlock(pos)) {
                    DeathHunterEntity.this.getMoveControl().setWantedPosition(
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.25D);
                    if (DeathHunterEntity.this.getTarget() == null) {
                        DeathHunterEntity.this.getLookControl().setLookAt(
                                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 存档
    // ══════════════════════════════════════════════════════════════

    /** 冲刺状态要跟着存档走（读档后不至于一直"冲刺中"）。 */
    @Override
    public void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsCharging", this.isCharging());
    }

    @Override
    public void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setCharging(tag.getBoolean("IsCharging"));
    }
}
