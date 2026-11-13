package org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.BaseInfectedEntity;
import org.bytechen.rottenruins.utils.combat.RangeAttackCallback;
import org.bytechen.rottenruins.utils.combat.RangeAttackUtils;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

/**
 * 溃烂追蹤者 —— 中等体积生物被感染后的形态（进化表查不到规则、碰撞体积 2~8 的中型生物走这一档，再大是巨碑）。
 * 出手只播 {@code attack} 动画并起倒计时，triggerAnim 之后 13 tick（0.65s，对应动画内挥砍峰值）才结算 100° 扇形伤害。
 * 命中瞬间会按主目标当前位置重新对准，并对其补一次近战判定，避免 0.65s 前摇里的走位导致空挥。
 */
public class PursuerEntity extends BaseUlceratedEntity implements RangeAttackCallback {

    // 命中时机不能直接用 0.4806*20 tick：GeckoLib 4 的 AnimationController 第三个构造参数是 transitionLength，
    // triggerAnim 后的头 ATTACK_TRANSITION_TICKS tick 只做骨骼混合、动画时钟钉在 0，所以真实延迟要把 transition 补上。
    private static final int ATTACK_TRANSITION_TICKS = 3;
    /** 动画播放后到命中的时间（秒）：取自 pursuer.animation.json 里 group6 的挥砍峰值 0.4806s，改关键帧时必须同步改这里。 */
    private static final float ATTACK_HIT_SECONDS = 0.4806F;
    private static final int ATTACK_HIT_TICK_IN_ANIM = Math.round(ATTACK_HIT_SECONDS * 20.0F);
    private static final int ATTACK_HIT_DELAY_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_HIT_TICK_IN_ANIM;
    private static final float ATTACK_ANIM_LENGTH_SECONDS = 0.691F;
    private static final int ATTACK_ANIM_TICKS = (int) Math.ceil(ATTACK_ANIM_LENGTH_SECONDS * 20.0F);
    /** 一次攻击的总 tick = 3 + 14 = 17，用于「上一刀没播完不许重触发」。 */
    private static final int ATTACK_TOTAL_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_ANIM_TICKS;

    /** 扇面全角 100°（不是半角：正前方左右各 50°）。 */
    private static final float AOE_ANGLE_DEGREES = 100.0F;
    /** 扇面垂直容差：目标碰撞箱与 origin.y ± 1.5 相交即算命中。 */
    private static final float AOE_VERTICAL_RANGE = 1.5F;
    /** 半径在 {@code doHurtDistance} 之外的放宽量（格）：0.65s 前摇里目标能后撤 1~2 格；只放宽命中判定，不放大出手触发距离。 */
    private static final float AOE_RADIUS_EXTRA = 2.0F;
    /** 主目标近战补正的额外距离（格，按碰撞箱间距算）：比 {@link #AOE_RADIUS_EXTRA} 略小，只用来消除「贴脸空挥」。 */
    private static final float AOE_PRIMARY_GRACE_EXTRA = 1.5F;

    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("attack");

    private int attackHitTimer;
    private int attackAnimTimer;
    /** 触发动画时锁定的主目标：命中过滤与伤害结算用它，否则前摇里目标一换人，动画朝旧目标挥、伤害打到新目标。 */
    @Nullable
    private LivingEntity pendingAttackTarget;
    /** 起手瞬间冻结的扇形轴，作为兜底方向（主目标还活着时命中会重新对准它当前位置）。仅服务端使用。 */
    @Nullable
    private Vec3 pendingAttackDirection;
    /** 本次挥砍是否已通过扇面扫到主目标 —— 用于「主目标近战补正」去重，避免同一刀打两下。仅服务端使用。 */
    private boolean primaryHitThisSwing;

    public PursuerEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                         Consumer<AbstractRottenEntity> consumer) {
        super(entityType, level, consumer);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.3F);
        this.setRunSpeed(0.3F);
        this.setIdleAnimSpeed(1.0F);
        this.setWalkAnimSpeed(1.2F);
        this.setDoHurtTime(20);
        this.setDoHurtDistance(1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25F);
    }

    @Override
    protected void registerGoals() {
        // 追击占位只占 MOVE：位移与出手由 AbstractRottenEntity#tickChase 负责，原版 MeleeAttackGoal 的出手在基类写死为 false。
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true)   // 王庭 vs 腐化：跨阵营索敌（同阵营仍被 infcore 过滤）
                .range(40.0)
                .mustSee(false)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 40));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        super.registerGoals();
    }

    /**
     * 出手入口：由 {@link AbstractRottenEntity#tickChase()} 在贴身且视线通时调用。<b>只触发动画 + 起倒计时，不立刻造成伤害</b>
     * （否则会出现「动画刚起手目标已经掉血」）；伤害在 {@link #tickAttack()} 里 triggerAnim 之后 {@link #ATTACK_HIT_DELAY_TICKS} tick 结算。
     */
    @Override
    protected boolean doHurtGoal(LivingEntity target) {
        if (isFakeDying() || isDeadOrDying()) return false;
        // 上一刀动画还没播完：既不重触发（会多结算一份伤害），也不返回 true（会让基类白吃一次出手窗口）。
        if (attackAnimTimer > 0) return false;

        pendingAttackTarget = target;
        pendingAttackDirection = horizontalDirectionTo(target);
        attackHitTimer = ATTACK_HIT_DELAY_TICKS;
        attackAnimTimer = ATTACK_TOTAL_TICKS;
        triggerAnim("attack_controller", "attack");
        return true;
    }

    /** 服务端先推进攻击倒计时再走基类 tick：否则命中会被基类 tick 的先后顺序多拖一 tick。 */
    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            tickAttack();
        }
        super.tick();
    }

    private void tickAttack() {
        // 假死（爆体）/ 真死亡：残留状态必须清干净，否则复活或重进世界后旧的锁存可能在很远的地方炸出一刀。
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
            if (attackAnimTimer == 0) {
                pendingAttackTarget = null;
                pendingAttackDirection = null;
            }
        }
    }

    /**
     * 命中瞬间：按主目标<b>当前位置</b>重新对准扇形（0.65s 前摇里玩家横移 1~2 格就会把它甩出 100° 扇面）→ 结算扇面扫击
     * → 主目标近战补正（仍在 {@code doHurtDistance + 1.5} 格碰撞箱间距内就补一次，用 {@link #primaryHitThisSwing} 去重）。
     * 主目标已死 / 丢失时才退回起手冻结的方向，保证这一爪仍会扫到周围其它目标。
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
            // 垂直沿用扇面同一套容差：补正只解决水平贴脸 / 角度差，不该隔着地板或两层台阶摸到头顶。
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

    /** 在给定方向上结算一次扇形范围伤害。{@code damage(0.0F)} 是有意的：工具类只做几何判定与回调，伤害仍由 {@link #getAttack} 结算。 */
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

    /** 扇形过滤：主目标只要还活着就放行（这一刀已经挥出去了），其余目标走基类的通用判定。 */
    private boolean canHitByAoeAttack(LivingEntity target) {
        if (target == pendingAttackTarget && target.isAlive()) return true;
        return canHitWithRangeAttack(target);
    }

    @Override
    public boolean beforeHit(RangeAttackUtils.Options options, LivingEntity target) {
        return target.isAlive();
    }

    /** 命中后：用原有的 {@link #getAttack} 结算（数值、属性修饰符与难度加成都在里面）；扫到的是主目标则置位补正去重标记。 */
    @Override
    public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
        if (target == pendingAttackTarget) {
            primaryHitThisSwing = true;
        }
        if (target.isAlive()) {
            getAttack(target);
        }
    }

    /** 只追加攻击控制器：{@code stateHandler} 恒返回 STOP，动画全由 triggerAnim 驱动。位置在基类扩展点里（movement 之后、die 之前），让攻击压在移动之上。 */
    @Override
    protected void registerAdditionalControllers(AnimatableManager.ControllerRegistrar registrar) {
        AnimationController<AbstractRottenEntity> attackController =
                new AnimationController<>(this, "attack_controller", ATTACK_TRANSITION_TICKS,
                        state -> PlayState.STOP);
        attackController.triggerableAnim("attack", ANIM_ATTACK);
        registrar.add(attackController);
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 8.0F);
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }
}
