package org.bytechen.hall.overworld.registry.entities.population.ulcerated;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.hall.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.hall.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.BaseInfectedEntity;
import org.bytechen.hall.utils.combat.RangeAttackCallback;
import org.bytechen.hall.utils.combat.RangeAttackUtils;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

/**
 * 溃烂追蹤者 —— 中等体积生物被感染后的形态。
 * <p>
 * 用途：当作感染（{@code hall:inf}）作用于一个<b>没有专属感染形态</b>（进化表里查不到规则）
 * 且碰撞体积中等的生物（宽 × 高 2 ~ 8）时，转化为追蹤者
 * —— 马、铁傀儡这类中型生物走的就是这一档；再大（&gt; 8）才是 {@link MonolithEntity 巨碑}。
 * 判定与转化由 infcore 的感染流程
 * （{@code EvolutionManager#applyEvolution} → 兜底档位）完成，
 * 档位在 {@link UlceratedConversionRules#register()} 中注册。
 * <p>
 * 属性：生命 24、伤害 8、护甲 4、移动很快（0.3，是斥候的两倍），擅长追踪（索敌 40 格）。
 * 碰撞箱由 {@code EntityTypeRegistry} 按需求给成 0.8 × 1.6。
 * 模型 / 贴图 / 动画：{@code geo/pursuer.geo.json}、{@code textures/entity/pursuer.png}、
 * {@code animations/pursuer.animation.json}（idle / walk / attack 三段动画）。
 * <p>
 * 攻击：{@code attack} 动画不循环（0 → 0.2704 抬爪蓄力 → 0.4806 挥砍峰值 → 0.6909 回中），
 * 出手时只播动画并起倒计时，triggerAnim 之后 13 tick（0.65s，对应动画内 0.4806s 的挥砍峰值）
 * 才结算一次 100° 扇形范围伤害（半径 3 格），挥砍会扫到面前一圈目标，而不只是锁定的主目标。
 * <p>
 * <b>命中可靠性</b>：0.65s 前摇足够让目标走位，所以命中瞬间会<b>重新对准主目标当前位置</b>，
 * 并且如果主目标仍在「触发距离 + 1.5 格」内、却没被扇面扫到，会补一次近战判定（近战补正）。
 * 这样「爪挥出去」与「真的打中」不会因为目标挪半步而错开。
 * <p>
 * 与斥候的区别：不会爬墙，也不会参与「聚满 5 只合体成巨碑」的聚集。
 */
public class PursuerEntity extends BaseUlceratedEntity implements RangeAttackCallback {

    // ---------- 攻击动画 / 命中时机（attack_controller） ----------
    //
    // pursuer.animation.json 里的 attack 是 thenPlay、animation_length = 0.691s、不循环：
    // 0.0 中位 → 0.2704 抬爪蓄力 → 0.4806 挥砍峰值 → 0.6909 回中（group7 在 0.5407 另有峰值）。
    // 命中时刻取挥砍峰值 0.4806s。
    //
    // 不能直接拿 0.4806 * 20 = 10 tick 去倒计时：GeckoLib 4 的 AnimationController 第三个
    // 构造参数是 transitionLength（平滑切换 tick），triggerAnim 之后的头 ATTACK_TRANSITION_TICKS
    // 个 tick 只做骨骼混合、动画时钟被钉在 0，过渡结束的那一 tick 才把时钟清零并真正开始播放。所以：
    //     triggerAnim ──(transition)──> 动画时钟 0 ──(0.4806s)──> 命中
    // 从 triggerAnim 起算的真实延迟必须把 transition 补偿进来，否则会提前 transition 那么久就打中。
    /** 攻击动画平滑切换 tick（0.15s）：给 movement_controller 留出混合时间，挥爪不突兀。 */
    private static final int ATTACK_TRANSITION_TICKS = 3;
    /**
     * 动画真正开始播放后到命中的时间（秒）。
     * <p>取自 {@code pursuer.animation.json} 里 group6 的挥砍峰值关键帧 0.4806s；
     * <b>改动动画关键帧时必须同步改这里</b>，否则伤害会落在挥爪之前或之后。</p>
     */
    private static final float ATTACK_HIT_SECONDS = 0.4806F;
    /** 命中点落在动画内部的 tick 数：0.4806 * 20 = 9.612，四舍五入取 10。 */
    private static final int ATTACK_HIT_TICK_IN_ANIM = Math.round(ATTACK_HIT_SECONDS * 20.0F);
    /** 从 triggerAnim 到结算伤害的真实延迟 = transition 补偿 + 动画内命中点 = 3 + 10 = 13 tick。 */
    private static final int ATTACK_HIT_DELAY_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_HIT_TICK_IN_ANIM;
    /** 攻击动画长度（秒），与 pursuer.animation.json 的 animation_length 保持一致。 */
    private static final float ATTACK_ANIM_LENGTH_SECONDS = 0.691F;
    /** 动画长度换算成 tick：ceil(0.691 * 20) = ceil(13.82) = 14，向上取整宁可多等一 tick 也别提前打断。 */
    private static final int ATTACK_ANIM_TICKS = (int) Math.ceil(ATTACK_ANIM_LENGTH_SECONDS * 20.0F);
    /** 一次攻击从 triggerAnim 到动画彻底播完的总 tick = 3 + 14 = 17，用于「上一刀没播完不许重触发」。 */
    private static final int ATTACK_TOTAL_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_ANIM_TICKS;

    // ------ 扇形范围伤害参数（扇形轴 = 命中瞬间自身 → 主目标的水平方向） ------
    /** 扇面全角（度），不是半角：100 = 正前方左右各 50°。 */
    private static final float AOE_ANGLE_DEGREES = 100.0F;
    /** 扇面垂直容差：目标碰撞箱与 origin.y ± 1.5 相交即算命中，坡上/台阶上的目标也扫得到。 */
    private static final float AOE_VERTICAL_RANGE = 1.5F;
    /**
     * 半径在 {@code doHurtDistance} 之外的放宽量（格）。
     * <p>
     * 0.65s 前摇里目标完全可能后撤/横移 1~2 格；原来的 +1（半径 2.0）几乎必然落空，
     * 所以放宽到 +2（半径 3.0）做位移容差。
     * </p>
     * <p>只放宽命中判定，不放大基类的出手触发距离。</p>
     */
    private static final float AOE_RADIUS_EXTRA = 2.0F;
    /**
     * 主目标近战补正的额外距离（格，按<b>碰撞箱间距</b>算）。
     * <p>
     * 扇面负责扫旁边一圈，主目标另外兜底：只要还在
     * {@code doHurtDistance + AOE_PRIMARY_GRACE_EXTRA}（= 1 + 1.5 = 2.5 格间距，约 3.2 格中心距）
     * 内，就算没被扇面扫到（角度/几何误差）也补一刀。这个值比 {@link #AOE_RADIUS_EXTRA}
     * 略小：补正只用来消除「贴脸空挥」，不该变成小怪隔 4 格摸人。
     * </p>
     */
    private static final float AOE_PRIMARY_GRACE_EXTRA = 1.5F;

    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("attack");

    /** 命中倒计时：归零时结算一次扇形范围伤害。仅服务端使用，不同步、不进 NBT。 */
    private int attackHitTimer;
    /** 动画总时长倒计时：>0 表示上一刀还在播，期间不允许重新触发。仅服务端使用。 */
    private int attackAnimTimer;
    /**
     * 触发动画时锁定的主目标：命中过滤与伤害结算用它，而不是命中瞬间的 getTarget()。
     * 否则 0.65s 前摇里目标一换人，动画还在往旧目标的方向挥、伤害却打到新目标身上。
     */
    @Nullable
    private LivingEntity pendingAttackTarget;
    /**
     * 起手瞬间冻结的扇形轴（自身 → 主目标的水平单位向量），作为命中瞬间的兜底方向。
     * <p>主目标在命中时还活着的话，扇形轴会重新对准它当前位置（见 {@link #resolveAttackHit()}）；
     * 只有目标已死 / 丢失时才用这份冻结值把这一爪扫完。仅服务端使用，不同步、不进 NBT。</p>
     */
    @Nullable
    private Vec3 pendingAttackDirection;
    /**
     * 本次挥砍是否已经通过扇面扫到主目标 —— 用于「主目标近战补正」去重。
     * <p>主目标被扇面扫中时在 {@link #afterHit} 里置 true，命中瞬间就不再补刀；
     * 没扫中但人还在近战距离内才补一次，避免同一刀打两下。仅服务端使用。</p>
     */
    private boolean primaryHitThisSwing;

    public PursuerEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                         Consumer<AbstractHallEntity> consumer) {
        super(entityType, level, consumer);
        // 只有 idle / walk / attack 三段动画，没有 run 动画
        this.setHasWalkAnim(false);
        // 追蹤者：跑得快、出手快
        this.setWalkSpeed(0.3F);
        this.setRunSpeed(0.3F);
        this.setIdleAnimSpeed(1.0F);
        this.setWalkAnimSpeed(1.2F);
        this.setDoHurtTime(20);
        this.setDoHurtDistance(1);
    }

    // ---------- 属性 ----------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25F);
    }

    // ---------- 目标与行为（与其它王庭生物保持一致） ----------
    @Override
    protected void registerGoals() {
        // 追击占位：只占 MOVE 标记 —— 位移与出手全部由 AbstractHallEntity#tickChase 负责。
        // 原来这里是原版 MeleeAttackGoal：它会再算一份路径，而它的出手走的是
        // doHurtTarget()，在 AbstractHallEntity 里被写死为 false，本来就在空转。
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        // 索敌：goalSelector 优先级 2，40 格内不需要视线
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 40));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        super.registerGoals();
    }

    // ---------- 攻击动画 + 扇形范围伤害（attack_controller） ----------

    /**
     * 出手入口：由 {@link AbstractHallEntity#tickChase()} 在贴身且视线通时调用。
     *
     * <p>这里<b>只触发动画 + 起倒计时，不立刻造成伤害</b>。伤害统一在
     * {@link #tickAttack()} 里、triggerAnim 之后 {@link #ATTACK_HIT_DELAY_TICKS} tick 结算，
     * 对应「动画真正开始播放后 0.4806s（{@link #ATTACK_HIT_SECONDS}）」的挥砍峰值；
     * 若在这里直接 hurt，就会出现「动画刚起手目标已经掉血」的错位。</p>
     *
     * <p>返回 true 只是告诉基类「这次出手成立」，让基类照常进入 doHurtTime 冷却。</p>
     */
    @Override
    protected boolean doHurtGoal(LivingEntity target) {
        if (isFakeDying() || isDeadOrDying()) return false;
        // 上一刀动画还没播完：既不重触发（会打断动画并让倒计时错位，等于多结算一份伤害），
        // 也不返回 true —— 返回 true 会让基类立刻进入 doHurtTime 冷却，白白吃掉一次出手窗口。
        if (attackAnimTimer > 0) return false;

        pendingAttackTarget = target;
        // 先记一份起手方向兜底（主目标在命中前死了/丢了时仍然要把这一爪挥完）；
        // 命中瞬间若主目标还活着，会重新对准它当前位置（见 resolveAttackHit），不再死守这份方向。
        pendingAttackDirection = horizontalDirectionTo(target);
        attackHitTimer = ATTACK_HIT_DELAY_TICKS;
        attackAnimTimer = ATTACK_TOTAL_TICKS;
        triggerAnim("attack_controller", "attack");
        return true;
    }

    /**
     * 服务端先推进攻击倒计时，再走基类 tick。
     * <p>倒计时必须在 {@code super.tick()}（基类 {@code tickChase → doHurtGoal} 的触发点）
     * 之前推进：触发动画的那一 tick 里 doHurtGoal 才把计时器置满，从下一 tick 起
     * tickAttack 每 tick 先走一步，命中恰好落在 triggerAnim + {@link #ATTACK_HIT_DELAY_TICKS} tick，
     * 不会被基类 tick 的先后顺序多拖一 tick。</p>
     * <p>{@code super.tick()} 会正常走到 {@link BaseUlceratedEntity#tick()}，
     * 因此「溃烂肉屑」粒子逻辑保持不变。</p>
     */
    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            tickAttack();
        }
        super.tick();
    }

    /**
     * 推进攻击倒计时，是这套「动画 + 延迟命中」的唯一伤害出口。
     *
     * <p>时序：doHurtGoal 在 tick T 触发动画并把 attackHitTimer 置为
     * {@link #ATTACK_HIT_DELAY_TICKS}；之后每 tick 进入本方法先减一，正好到 tick T+13 减到 0
     * 并结算扇形范围伤害。GeckoLib 的动画时钟在 triggerAnim 后先冻结
     * {@link #ATTACK_TRANSITION_TICKS} tick、再从 0 开始播，所以结算时刻 = 动画时钟 0.4806s 处，
     * 与挥砍峰值对齐。</p>
     *
     * <p>命中判定不在这里做「一次性结算」，而是交给 {@link #resolveAttackHit()}：
     * 那里会先按主目标当前位置重新对准扇形，再做主目标近战补正，专门解决 0.65s 前摇里
     * 目标走位导致的高空挥率。</p>
     */
    private void tickAttack() {
        // 假死（爆体）/ 真死亡：已经不该再出手，残留状态必须清干净，
        // 否则复活或重进世界后旧的 pendingAttackTarget 可能在很远的地方炸出一刀。
        if (isFakeDying() || isDeadOrDying()) {
            attackHitTimer = 0;
            attackAnimTimer = 0;
            pendingAttackTarget = null;
            pendingAttackDirection = null;
            primaryHitThisSwing = false;
            return;
        }

        if (attackHitTimer > 0) {
            attackHitTimer--;
            if (attackHitTimer == 0) {
                resolveAttackHit();
            }
        }

        if (attackAnimTimer > 0) {
            attackAnimTimer--;
            // 动画播完：清掉残留锁定的目标/方向（命中时通常已经清过，这里兜底）。
            if (attackAnimTimer == 0) {
                pendingAttackTarget = null;
                pendingAttackDirection = null;
            }
        }
    }

    /**
     * 命中瞬间：按主目标当前位置重新对准扇形 → 结算扇形扫击 → 主目标近战补正 → 清空锁存。
     *
     * <h3>为什么命中时要重新对准</h3>
     * <p>起手到命中隔了 0.65s（13 tick），玩家只要后撤/横移 1~2 格，仍按起手瞬间冻结的方向
     * 就会把主目标甩出 100° 扇面 —— 表现就是「爪明明挥出去了却没伤害」。所以这里用
     * {@link #horizontalDirectionTo(LivingEntity)} 把扇形轴重新对准主目标<b>当前位置</b>；
     * 主目标已经死亡/丢失时才退回起手冻结的方向，保证这一爪仍会扫到周围其它目标。</p>
     *
     * <h3>主目标近战补正</h3>
     * <p>扇面只负责「扫到旁边一圈」，主目标单独兜一道底：只要它还活着且仍在
     * {@code doHurtDistance + AOE_PRIMARY_GRACE_EXTRA}（= 1 + 1.5 = 2.5 格碰撞箱间距）内，
     * 就补一次 {@link #getAttack(LivingEntity)}。这样即便目标恰好压在扇面边缘、或被几何误差漏掉，
     * 也不会出现「近战贴脸却打不中」。</p>
     *
     * <p>补正与扇面用 {@link #primaryHitThisSwing} 去重：扇面已经扫到主目标就不再补，
     * 保证同一刀对同一目标只结算一次伤害。</p>
     */
    private void resolveAttackHit() {
        LivingEntity primary = pendingAttackTarget;
        Vec3 direction = (primary != null && primary.isAlive())
                ? horizontalDirectionTo(primary)
                : pendingAttackDirection;

        if (direction != null) {
            primaryHitThisSwing = false;
            performAoeAttack(direction);

            double graceReach = this.getDoHurtDistance() + AOE_PRIMARY_GRACE_EXTRA;
            // 垂直方向沿用扇面同一套容差：补正只解决「水平贴脸/角度差」，不该变成
            // 隔着地板或两层高的台阶摸到头顶上的目标。
            double centerY = this.getBoundingBox().getCenter().y;
            boolean verticalOverlap = primary != null
                    && primary.getBoundingBox().maxY >= centerY - AOE_VERTICAL_RANGE
                    && primary.getBoundingBox().minY <= centerY + AOE_VERTICAL_RANGE;
            if (!primaryHitThisSwing && primary != null && primary.isAlive() && verticalOverlap
                    && getBoundingBoxDistanceSqrExact(primary) <= graceReach * graceReach) {
                getAttack(primary);
            }
        }

        pendingAttackTarget = null;
        pendingAttackDirection = null;
    }

    /**
     * 在给定方向上结算一次扇形范围伤害。
     * <p>伤害传 {@code damage(0.0F)}：工具类只做几何判定、通用过滤与回调，不直接 hurt；
     * 真正的伤害仍由 {@link #getAttack(LivingEntity)} 结算（含属性修饰符与难度加成），
     * 保证范围命中与普通攻击是同一份数值逻辑。</p>
     *
     * @param direction 命中瞬间的扇形轴（水平单位向量）
     */
    private void performAoeAttack(Vec3 direction) {
        RangeAttackUtils.perform(this, RangeAttackUtils.sector()
                .origin(this.getBoundingBox().getCenter())
                .direction(direction)
                .radius(Math.max(2.0F, this.getDoHurtDistance() + AOE_RADIUS_EXTRA))
                .angleDegrees(AOE_ANGLE_DEGREES)
                .verticalRange(AOE_VERTICAL_RANGE)
                .damage(0.0F)
                .filter(this::canHitByAoeAttack)
                .callback(this));
    }

    /**
     * 扇形范围伤害的目标过滤。
     * <p>主目标（起手瞬间锁定的那只）只要还活着就放行 —— 即使它的威胁点数在 0.65s 前摇里
     * 掉回阈值以下，这一刀也已经挥出去了；其余目标统一走基类的
     * {@link AbstractHallEntity#canHitWithRangeAttack(LivingEntity)}（玩家威胁闸门、
     * 感染生物不互伤、存活/可攻击/类型允许）。</p>
     */
    private boolean canHitByAoeAttack(LivingEntity target) {
        if (target == pendingAttackTarget && target.isAlive()) return true;
        return canHitWithRangeAttack(target);
    }

    /** 命中前：目标还活着才算命中（工具类已剔除死亡目标，这里再兜一层）。 */
    @Override
    public boolean beforeHit(RangeAttackUtils.Options options, LivingEntity target) {
        return target.isAlive();
    }

    /**
     * 命中后：用原有的纯伤害逻辑结算（{@link #getAttack(LivingEntity)}）。
     * <p>目标可能已经被同一刀的其他效果打死，所以先判 alive；伤害数值、属性修饰符
     * 与难度加成仍全部留在 getAttack 里算，这里不再另写一份。</p>
     * <p>若这一下扫到的是主目标，顺带置位 {@link #primaryHitThisSwing}，
     * 让 {@link #resolveAttackHit()} 的近战补正不再对同一目标重复结算。</p>
     */
    @Override
    public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
        if (target == pendingAttackTarget) {
            primaryHitThisSwing = true;
        }
        if (target.isAlive()) {
            getAttack(target);
        }
    }

    /**
     * 只追加攻击控制器：{@code stateHandler} 恒返回 STOP，动画完全由 triggerAnim 驱动
     * （{@code attack} 是 thenPlay、不循环），播完自动回落到 movement_controller。
     * <p>放在基类扩展点里（movement 之后、die 之前）：GeckoLib 按注册顺序应用控制器、
     * 后者覆盖前者的骨骼，攻击必须压在移动之上，否则走路动画会把爪子拽回去；
     * die 仍排最后，爆体动画优先级最高。</p>
     */
    @Override
    protected void registerAdditionalControllers(AnimatableManager.ControllerRegistrar registrar) {
        AnimationController<AbstractHallEntity> attackController =
                new AnimationController<>(this, "attack_controller", ATTACK_TRANSITION_TICKS,
                        state -> PlayState.STOP);
        attackController.triggerableAnim("attack", ANIM_ATTACK);
        registrar.add(attackController);
    }

    // ---------- 普通攻击 ----------
    @Override
    protected boolean getAttack(LivingEntity target) {
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 8.0F);
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }
}
