package org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.client.rend.gui.GuiShaderManager;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.utils.TickUtils;
import org.bytechen.rottenruins.utils.combat.RangeAttackCallback;
import org.bytechen.rottenruins.utils.combat.RangeAttackUtils;
import org.bytechen.rottenruins.utils.entity.EntityBreakUtils;
import org.bytechen.rottenruins.utils.entity.EntityParticleUtils;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.UUID;
import java.util.function.Consumer;

public class InfPlayerEntity extends BaseInfectedEntity implements RangeAttackCallback {


    private static final EntityDataAccessor<Integer> MADNESS_COOLDOWN =
            SynchedEntityData.defineId(InfPlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEAP_COOLDOWN =
            SynchedEntityData.defineId(InfPlayerEntity.class, EntityDataSerializers.INT);

    /** 大跳冷却（tick），15s */
    private static final int LEAP_COOLDOWN_MAX = 300;
    /** 大跳触发范围（半径格数） */
    private static final float LEAP_RANGE = 8.0f;
    /** 大跳垂直初速度 */
    private static final float LEAP_UPWARD = 0.7f;
    /** 大跳水平速度系数 */
    private static final float LEAP_H_SPEED = 0.18f;

    private int getMadnessCooldown()  { return entityData.get(MADNESS_COOLDOWN); }
    private void setMadnessCooldown(int v) { entityData.set(MADNESS_COOLDOWN, v); }
    private int getLeapCooldown()     { return entityData.get(LEAP_COOLDOWN); }
    private void setLeapCooldown(int v) { entityData.set(LEAP_COOLDOWN, v); }

    public InfPlayerEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level, Consumer<AbstractRottenEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.32f);
        this.setRunSpeed(0.5F);
        this.setDoHurtTime(20);

        // ── 爆体 ──
        // 所有非使徒生物默认参与爆体（基类：40% 概率、1.5 秒、10 次/秒脉冲、最大 1.4 倍、
        // 逐 tick 抖动），hall_player 不再单独配置数值。
        // 它没有 "die" 动画（hasDieAnim 默认 false）：爆体只缩放、不播死亡动画，
        // idle / walk / run 照常播放（见 isBurstAnimating）。
        this.setBurstRenderMode(BurstRenderMode.SCALE_ONLY);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 15)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE,0.8f);
    }

    @Override
    protected void registerGoals() {
        // 追击占位（只占 MOVE）：位移与出手由 AbstractRottenEntity#tickChase 统一负责。
        // 原版 MeleeAttackGoal 会再算一份路径，而它的出手走 doHurtTarget()，
        // 在基类里被写死为 false —— 那部分一直是空转。
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));

        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true)   // 王庭 vs 腐化：跨阵营索敌（同阵营仍被 infcore 过滤）
                .range(40.0)
                .mustSee(false)
                .filter(this)
                .build());
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(2, new FaceTargetGoal(this,40));
        super.registerGoals();
    }


    /** 剑气触发概率（百分比） */
    private static final int SWORD_AURA_CHANCE = 5;
    /** 剑气缩小速度倍率 */
    private static final float SWORD_AURA_SHRINK = 2.0f;
    /** 剑气伤害倍率 */
    private static final float SWORD_AURA_DAMAGE_MULT = 5.0f;
    /** 剑气缩放倍率 */
    private static final float SWORD_AURA_SCALE = 1.5f;

    /** GUI horror shader effect: total duration (ticks, ~5s) */
    private static final int HORROR_SHADER_DURATION = 100;
    /** GUI horror shader effect: fade-in (ticks) */
    private static final int HORROR_SHADER_FADE_IN = 10;
    /** GUI horror shader effect: fade-out (ticks) */
    private static final int HORROR_SHADER_FADE_OUT = 30;

    // ── 攻击动画 / 命中时机（attack_controller）──
    //
    // hall_player.animation.json 里的 attack（双手）、attack_left（左手）、attack_right（右手）
    // 都是 thenPlay、animation_length = 1.2083s、不循环，三个招式共用同一套命中时刻。
    //
    // 需求是「动画真正开始播放后 0.58s 命中」。这里不能直接拿 0.58 * 20 = 11.6 tick 去倒计时，
    // 因为 GeckoLib 4 的 AnimationController 第三个构造参数是 transitionLength（平滑切换 tick）：
    // triggerAnim 之后的头 ATTACK_TRANSITION_TICKS 个 tick 只做骨骼混合，动画时钟被钉在 0，
    // 过渡结束的那一 tick 才把时钟清零并真正开始播放。也就是说：
    //     triggerAnim ──(transition)──> 动画时钟 0 ──(0.58s)──> 命中
    // 从 triggerAnim 起算的真实延迟必须把 transition 补偿进来，否则会提前 transition 那么久就打中。
    /** 攻击动画平滑切换 tick（0.15s）：给 movement_controller 留出混合时间，出手不突兀。 */
    private static final int ATTACK_TRANSITION_TICKS = 3;
    /** 动画真正开始播放后到命中的时间（秒）。 */
    private static final float ATTACK_HIT_SECONDS = 0.58F;
    /** 命中点落在动画内部的 tick 数：0.58 * 20 = 11.6，四舍五入取 12。 */
    private static final int ATTACK_HIT_TICK_IN_ANIM = Math.round(ATTACK_HIT_SECONDS * 20.0F);
    /** 从 triggerAnim 到结算伤害的真实延迟 = transition 补偿 + 动画内命中点 = 3 + 12 = 15 tick（0.75s）。 */
    private static final int ATTACK_HIT_DELAY_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_HIT_TICK_IN_ANIM;
    /** 攻击动画长度（秒），与 hall_player.animation.json 的 animation_length 保持一致。 */
    private static final float ATTACK_ANIM_LENGTH_SECONDS = 1.2083F;
    /** 动画长度换算成 tick：ceil(1.2083 * 20) = ceil(24.166) = 25，向上取整宁可多等一 tick 也别提前打断。 */
    private static final int ATTACK_ANIM_TICKS = (int) Math.ceil(ATTACK_ANIM_LENGTH_SECONDS * 20.0F);
    /** 一次攻击从 triggerAnim 到动画彻底播完的总 tick = 3 + 25 = 28，用于「上一刀没播完不许重触发」。 */
    private static final int ATTACK_TOTAL_TICKS = ATTACK_TRANSITION_TICKS + ATTACK_ANIM_TICKS;

    private static final RawAnimation ANIM_ATTACK       = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ANIM_ATTACK_LEFT  = RawAnimation.begin().thenPlay("attack_left");
    private static final RawAnimation ANIM_ATTACK_RIGHT = RawAnimation.begin().thenPlay("attack_right");

    /**
     * 三种攻击招式：{@code attack} 双手、{@code attack_left} 左手、{@code attack_right} 右手。
     * 每次出手随机三选一；{@code animationName} 同时也是 triggerableAnim 的注册名。
     */
    private enum AttackVariant {
        BOTH("attack"),
        LEFT("attack_left"),
        RIGHT("attack_right");

        private final String animationName;

        AttackVariant(String animationName) {
            this.animationName = animationName;
        }
    }

    // ── 扇形范围伤害（AoE）参数 ──
    //
    // 命中瞬间不再是「只打 pendingAttackTarget 一个」，而是以起手锁定的方向为轴做一次扇形扫击：
    // 三个招式本来就是横扫 / 单臂挥砍，一刀扫到身边一圈小怪才符合直觉；只打一个目标会出现
    // 「手臂明明从旁边怪身上扫过去、它却一点血没掉」的错位观感。
    /** 双手横扫（attack）的扇形全角：140°，覆盖身前大半圈，对应两手一起抡出去的横扫幅度。 */
    private static final float AOE_ANGLE_BOTH = 140.0F;
    /** 单手挥击（attack_left / attack_right）的扇形全角：90°，单臂能覆盖的范围比双手横扫窄。 */
    private static final float AOE_ANGLE_SINGLE = 90.0F;
    /**
     * 单手招式的扇形中心相对「目标方向」的偏移（度）。
     * <p>+ = 角色左手侧（attack_left），- = 角色右手侧（attack_right）：单臂挥砍真正扫过的区域
     * 偏向出招那一侧，所以把整个扇形绕 Y 轴转过去，而不是仍以目标方向居中——
     * 否则左手挥击会有一半扇面落在身体另一侧，看起来像打到了背后。</p>
     */
    private static final float AOE_SINGLE_SIDE_OFFSET = 30.0F;
    /**
     * 扇形垂直容差（格）：目标碰撞箱与 {@code origin.y ± 2.0} 相交就算命中。
     * <p>用碰撞箱区间而不是目标中心，坡上 / 台阶上、比自己高一头的目标都不会被漏掉。</p>
     */
    private static final float AOE_VERTICAL_RANGE = 2.0F;
    /**
     * 扇形半径在基类触发距离（{@code doHurtDistance}）之上的额外放宽。
     * <p>起手到命中有 0.75s 前摇，目标完全可能挪开半步；放宽命中半径是位移容差，
     * <b>不会</b>放大触发距离——出手条件仍由基类按 {@code doHurtDistance} 卡。</p>
     */
    private static final float AOE_RADIUS_EXTRA = 1.0F;

    /** 命中倒计时：归零时结算一次伤害。仅服务端使用，不同步、不进 NBT（视觉由客户端动画自己播）。 */
    private int attackHitTimer;
    /** 动画总时长倒计时：>0 表示上一刀还在播，期间不允许重新触发。仅服务端使用。 */
    private int attackAnimTimer;
    /**
     * 触发动画时锁定的目标：命中结算用它，而不是命中瞬间的 getTarget()。
     * 否则 0.75s 前摇里目标一换人，动画还在往旧目标的方向挥、伤害却打到新目标身上。
     */
    @Nullable
    private LivingEntity pendingAttackTarget;
    /**
     * 起手时锁定的扇形轴（水平单位向量），命中瞬间用它做扫击判定。
     * <p>与 {@link #pendingAttackTarget} <b>同时</b>锁定：0.75s 前摇里目标换人 / 自己转身，
     * 扇形仍然朝着「当初瞄准的方向」扫出去，动画与伤害判定不会各朝一边。</p>
     * <p>仅服务端使用，不同步、不进 NBT。</p>
     */
    @Nullable
    private Vec3 pendingAttackDirection;
    /** 起手时锁定的扇形全角（度），与 {@link #pendingAttackDirection} 配套。仅服务端使用、不同步。 */
    private float pendingAttackAngleDegrees;


    @Override
    protected boolean getAttack(LivingEntity target) {
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 15.0F);

        if (this.getRandom().nextInt(100) < SWORD_AURA_CHANCE) {
            // 5% 概率：在目标碰撞箱中心生成剑气，1.5 倍大小，5 倍伤害
            target.hurt(this.damageSources().mobAttack(this), baseDamage * SWORD_AURA_DAMAGE_MULT);
            SwordAuraEntity.spawn(level(), target.getBoundingBox().getCenter(),
                    SWORD_AURA_SCALE, 40, SWORD_AURA_SHRINK);
        } else {
            target.hurt(this.damageSources().mobAttack(this), baseDamage);
        }

        // 攻击玩家时发送全屏 GUI 恐怖着色器特效
        if (target instanceof ServerPlayer player && !level().isClientSide()) {
            NetworkHelper.sendGuiShaderToPlayer(player,
                    GuiShaderManager.TYPE_HORROR_VORONOI,
                    UUID.randomUUID(),
                    HORROR_SHADER_DURATION,
                    HORROR_SHADER_FADE_IN,
                    HORROR_SHADER_FADE_OUT);
        }

        return true;
    }

    // ── RangeAttackCallback：扇形扫击的逐目标回调 ──

    /**
     * 扇形扫击的准入判定：目标必须还活着。
     *
     * <p>几何命中与 {@link #canHitByAoeAttack} 已经筛过一遍，这里再判一次存活，
     * 是因为候选目标列表是<b>快照</b>：前一个目标的 {@link #getAttack}（剑气倍伤）
     * 完全可能顺手把后面的目标打死，此时不该再对它结算第二下。</p>
     */
    @Override
    public boolean beforeHit(RangeAttackUtils.Options options, LivingEntity target) {
        return target.isAlive();
    }

    /**
     * 单个目标被扫中之后的<b>唯一伤害出口</b>：统一走原有的 {@link #getAttack}。
     *
     * <p>这样 5% 剑气、剑气 5 倍伤害、命中玩家时的 GUI horror shader 等逻辑一字不动地复用，
     * 不会因为改成 AoE 就丢掉原有表现。</p>
     *
     * <p>注意 {@link #performAoeAttack()} 里 {@code damage(0.0F)} 是<b>有意</b>的：
     * 工具类不自己 hurt，伤害全部从这里逐目标结算，否则同一目标会被结算两次。</p>
     */
    @Override
    public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
        // 工具类的 hurt 没跑（damage = 0），hurtSuccess 恒为 false，不能拿它当"是否命中"用；
        // 能走到 afterHit 就说明几何 + 过滤 + beforeHit 都通过了，这里只确认没被前面的结算打死。
        if (target.isAlive()) {
            getAttack(target);
        }
    }

    /**
     * 出手入口：由 {@link AbstractRottenEntity#tickChase()} 在贴身且视线通时调用。
     *
     * <p>这里<b>只触发动画 + 起倒计时 + 锁定扇形方向，不立刻造成伤害</b>。伤害统一在
     * {@link #tickAttack()} 里、triggerAnim 之后 {@link #ATTACK_HIT_DELAY_TICKS} tick 结算，
     * 对应「动画真正开始播放后 0.58s」；若在这里直接 hurt，就会出现「动画刚起手目标已经掉血」的错位。</p>
     *
     * <p>扇形轴在<b>起手这一 tick</b>就定下来：{@code aim = horizontalDirectionTo(target)}，
     * 双手招式直接以 aim 为轴扫 {@link #AOE_ANGLE_BOTH}；左手 / 右手招式把轴绕 Y 轴偏
     * {@link #AOE_SINGLE_SIDE_OFFSET}° 后再扫 {@link #AOE_ANGLE_SINGLE}，让扇面偏向真正挥出去的那一侧。
     * 命中时不再重新读 getTarget()，避免「动画朝旧目标挥、伤害却扫到新方向」。</p>
     *
     * <p>返回 true 只是告诉基类「这次出手成立」，让基类照常进入 doHurtTime 冷却。</p>
     */
    @Override
    protected boolean doHurtGoal(LivingEntity target) {
        if (isFakeDying() || isDeadOrDying()) return false;
        // 上一刀动画还没播完：既不重触发（会打断动画并让倒计时错位，等于多结算一份伤害），
        // 也不返回 true —— 返回 true 会让基类立刻进入 doHurtTime 冷却，白白吃掉一次出手窗口。
        if (attackAnimTimer > 0) return false;

        AttackVariant[] variants = AttackVariant.values();
        AttackVariant variant = variants[this.getRandom().nextInt(variants.length)];

        // 扇形轴对准目标所在方向，而不是 getYRot()：FaceTargetGoal 是渐进转向，
        // 起手瞬间 yRot 往往还没完全对准，拿它当轴会把主目标甩出扇面。
        Vec3 aim = horizontalDirectionTo(target);
        pendingAttackTarget = target;
        switch (variant) {
            case BOTH -> {
                pendingAttackDirection = aim;
                pendingAttackAngleDegrees = AOE_ANGLE_BOTH;
            }
            // 单手招式：扇面中心朝出招那一侧偏 30°。正角 = 角色左手侧（见 rotateY），右手取负。
            case LEFT -> {
                pendingAttackDirection = rotateY(aim, AOE_SINGLE_SIDE_OFFSET);
                pendingAttackAngleDegrees = AOE_ANGLE_SINGLE;
            }
            case RIGHT -> {
                pendingAttackDirection = rotateY(aim, -AOE_SINGLE_SIDE_OFFSET);
                pendingAttackAngleDegrees = AOE_ANGLE_SINGLE;
            }
        }

        attackHitTimer = ATTACK_HIT_DELAY_TICKS;
        attackAnimTimer = ATTACK_TOTAL_TICKS;
        triggerAnim("attack_controller", variant.animationName);
        return true;
    }

    /**
     * 把水平向量绕 Y 轴旋转 {@code degrees} 度（正角 = 朝角色左侧）。
     *
     * <p>Minecraft 是右手系、Y 轴朝上，水平面里角度从 +X 转向 +Z。绕 Y 轴旋转的公式是：</p>
     * <pre>
     *   x' =  x·cosθ + z·sinθ
     *   z' = -x·sinθ + z·cosθ
     * </pre>
     * <p>代入 θ = +90°、v = +Z（朝南）可验证：结果 +X（朝东）。玩家面朝南时屏幕左侧正是东，
     * 所以正角确实对应「角色左手侧」，与 {@link #AOE_SINGLE_SIDE_OFFSET} 的符号约定一致。</p>
     *
     * <p>Java 三角函数要的是<b>弧度</b>，而这里的参数是度，必须经 {@link Math#toRadians(double)}
     * 换算一次 —— 直接把度数丢给 {@link Math#cos(double)} 会得到一个完全错误的扇形轴。</p>
     *
     * @param direction 水平方向向量（不要求是单位向量，旋转不改变长度）
     * @param degrees   旋转角度（度），正 = 朝角色左侧
     * @return 旋转后的水平向量（y 分量恒为 0）
     */
    private static Vec3 rotateY(Vec3 direction, float degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = direction.x * cos + direction.z * sin;
        double z = -direction.x * sin + direction.z * cos;
        return new Vec3(x, 0.0D, z);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if(target!=this.getTarget())
        {
            setMadnessCooldown(100);
        }

        super.setTarget(target);
    }

    @Override
    public void tick() {
        //Client
        if(this.level().isClientSide())
        {
            clientTick();
        }
        else
        {
            serverTick();
        }
        setMadnessCooldown(getMadnessCooldown() - 1);
        setLeapCooldown(getLeapCooldown() - 1);
        super.tick();
    }

    public void clientTick()
    {
        if(TickUtils.tickCountTimer(this.tickCount,10)&&getMadnessCooldown()>0)
        {
            EntityParticleUtils.spawnParticles(this, ParticleTypes.END_ROD,0.2
                    ,0.2 , 1.0f);
        }
    }

    public void serverTick()
    {
        // 攻击倒计时必须在 super.tick()（基类 tickChase → doHurtGoal 的触发点）之前推进：
        // 触发动画的那一 tick 里 doHurtGoal 才把计时器置满，从下一 tick 起 tickAttack 每 tick 先走一步，
        // 命中恰好落在 triggerAnim + ATTACK_HIT_DELAY_TICKS tick，不会被基类 tick 的先后顺序多拖一 tick。
        tickAttack();
        // 爆体（假死）期间只播缩放：tickAttack 已在假死分支清掉残留出手状态，
        // 这里直接停手，避免还在拆方块 / 大跳。
        if (isFakeDying()) return;
        if (TickUtils.tickCountTimer(tickCount, 20)) {
            EntityBreakUtils.breakBlocksInRange(this, 1, 2f, false);
        }
        // 大跳逻辑：8 格内有目标且冷却完毕时触发
        LivingEntity target = getTarget();
        if (target != null && getLeapCooldown() <= 0 && this.onGround()
                && this.distanceToSqr(target) <= LEAP_RANGE * LEAP_RANGE) {
            performLeap(target);
        }

        if (TickUtils.tickCountTimer(tickCount, 40) && target != null) {
            setMadnessCooldown(100);
        }
    }

    /**
     * 推进攻击倒计时，是这套「动画 + 延迟命中」的唯一伤害出口。
     *
     * <p>时序：doHurtGoal 在 tick T 触发动画并把 attackHitTimer 置为
     * {@link #ATTACK_HIT_DELAY_TICKS}；之后每 tick 进入本方法先减一，正好到 tick T+15 减到 0 并结算。
     * 而 GeckoLib 的动画时钟是在 triggerAnim 后先冻结 {@link #ATTACK_TRANSITION_TICKS} tick、
     * 再从 0 开始播，所以结算时刻 = 动画时钟 0.58s 处，两头对齐。</p>
     *
     * <p>结算不再是「只打 pendingAttackTarget 一个」，而是调用 {@link #performAoeAttack()}：
     * 在起手锁定的方向上做一次扇形扫击（双手 140°；左右手各 90° 且扇面中心偏 30°；
     * 半径 = 触发距离 {@code doHurtDistance} + {@link #AOE_RADIUS_EXTRA}），
     * 被扫到的每个目标各走一次原有的 {@link #getAttack}。</p>
     */
    private void tickAttack() {
        // 假死（爆体）/ 真死亡：已经不该再出手，残留状态必须清干净，
        // 否则复活或重进世界后旧的 pendingAttackTarget / pendingAttackDirection
        // 可能在很远的地方炸出一刀。
        if (isFakeDying() || isDeadOrDying()) {
            attackHitTimer = 0;
            attackAnimTimer = 0;
            pendingAttackTarget = null;
            pendingAttackDirection = null;
            return;
        }

        if (attackHitTimer > 0) {
            attackHitTimer--;
            if (attackHitTimer == 0) {
                // 命中瞬间：朝起手锁定的方向做扇形扫击。原来的「单目标距离重检」逻辑已删除，
                // 距离 / 角度统一交给 RangeAttackUtils 的扇面判定（半径 = doHurtDistance + 1，
                // 既覆盖 0.75s 前摇里的位移容差，又不会把触发距离放大）。
                performAoeAttack();
                pendingAttackTarget = null;
                pendingAttackDirection = null;
            }
        }

        if (attackAnimTimer > 0) {
            attackAnimTimer--;
            if (attackAnimTimer == 0) pendingAttackTarget = null;
        }
    }

    /**
     * 在起手锁定的方向上执行一次扇形扫击（服务端）。
     *
     * <p><b>为什么 damage 传 0</b>：真正的伤害必须走 {@link #getAttack}（5% 剑气、剑气 5 倍伤害、
     * 命中玩家的 GUI horror shader 都在里面）。若这里再给一个正数，工具类会先 hurt 一次、
     * {@link #afterHit} 里又 {@code getAttack} 一次，同一个目标就吃到两倍伤害。
     * 所以几何判定 + 目标过滤交给 {@link RangeAttackUtils}，伤害全部由回调逐目标结算。</p>
     *
     * <p>半径取 {@code max(2, doHurtDistance + AOE_RADIUS_EXTRA)}：下限 2 是为了防止
     * 配置把 doHurtDistance 调得极小时扇形退化成打不到人；上限方向由触发距离决定，
     * 保证「能出手就一定能扫到」。方向为 null（没有锁定过、或已被清理）时直接返回。</p>
     */
    private void performAoeAttack() {
        if (pendingAttackDirection == null) return;

        float radius = Math.max(2.0F, getDoHurtDistance() + AOE_RADIUS_EXTRA);
        RangeAttackUtils.perform(this, RangeAttackUtils.sector()
                .origin(this.getBoundingBox().getCenter())
                .direction(pendingAttackDirection)
                .radius(radius)
                .angleDegrees(pendingAttackAngleDegrees)
                .verticalRange(AOE_VERTICAL_RANGE)
                .damage(0.0F)
                .filter(this::canHitByAoeAttack)
                .callback(this));
    }

    /**
     * 扇形扫击的目标过滤：在基类通用判定之上补一条<b>主目标例外</b>。
     *
     * <p>玩家能不能被范围攻击打中，{@link #canHitWithRangeAttack} 走的是威胁点数 / 还手窗口闸门。
     * 0.75s 前摇里玩家完全可能脱离视线、或威胁点数掉回阈值以下，导致命中瞬间闸门关闭 ——
     * 那就会出现「动画明明都挥到脸上了却一点伤害没有」。既然起手是 AI 已经选中它，
     * 就对<b>这一个</b>主目标只要求存活，不再重跑威胁闸门。</p>
     *
     * <p>其它被扫到的目标一律走通用判定：玩家仍受威胁点数闸门约束，
     * 感染生物之间不互相误伤。</p>
     */
    private boolean canHitByAoeAttack(LivingEntity target) {
        if (target == pendingAttackTarget) {
            return target.isAlive();
        }
        return canHitWithRangeAttack(target);
    }

    /** 大跳至目标位置或目标后方 */
    private void performLeap(LivingEntity target) {
        Vec3 dest;
        if (this.getRandom().nextBoolean()) {
            // 落点 = 目标位置
            dest = target.position();
        } else {
            // 落点 = 目标背后 2.5 格
            Vec3 look = target.getLookAngle().scale(-2.5);
            dest = target.position().add(look.x, 0, look.z);
        }

        Vec3 diff = dest.subtract(this.position());
        // 计算水平速度使落点距离匹配
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double hSpeed = Math.min(hDist * LEAP_H_SPEED, 2.0);
        Vec3 vel = new Vec3(diff.x * LEAP_H_SPEED, LEAP_UPWARD, diff.z * LEAP_H_SPEED);
        this.setDeltaMovement(vel);
        this.hurtMarked = true; // 通知客户端

        // 面向目标
        this.getLookControl().setLookAt(target, 30, 30);
        this.yBodyRot = this.getYRot();

        setLeapCooldown(LEAP_COOLDOWN_MAX);

        // 起跳粒子
        if (level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 20; i++) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        getX() + (random.nextDouble() - 0.5) * 0.6,
                        getY() + 0.1,
                        getZ() + (random.nextDouble() - 0.5) * 0.6,
                        1, 0, 0.05, 0, 0.02);
            }
        }
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MADNESS_COOLDOWN, 0);
        this.entityData.define(LEAP_COOLDOWN, 0);
    }

    protected static final RawAnimation ANIM_IDLE_MADNESS  = RawAnimation.begin().thenPlay("idle_madness");


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "movement_controller", 3, state -> {
            if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;
            if (!isActuallyMoving) { state.setControllerSpeed(getIdleAnimSpeed()); return this.entityData.get(MADNESS_COOLDOWN) > 0 ? state.setAndContinue(ANIM_IDLE_MADNESS) : state.setAndContinue(ANIM_IDLE); }
            if (hasWalkAnimation()) {
                if (isWalking()) { state.setControllerSpeed(getWalkAnimSpeed()); return state.setAndContinue(ANIM_WALK); }
                else { state.setControllerSpeed(getRunAnimSpeed()); return state.setAndContinue(ANIM_RUN); }
            }
            state.setControllerSpeed(getWalkAnimSpeed());
            return state.setAndContinue(ANIM_WALK);
        }));
        // 攻击控制器：stateHandler 恒返回 STOP，动画完全由 triggerAnim 驱动（三招都是 thenPlay、不循环），
        // 播完自动回落到 movement_controller。放在 movement 之后、die 之前：
        // GeckoLib 按注册顺序应用控制器、后者覆盖前者的骨骼，攻击必须压在移动之上，
        // 否则走路动画会把手臂拽回去；die 仍排最后，爆体动画优先级最高。
        AnimationController<AbstractRottenEntity> attackController =
                new AnimationController<>(this, "attack_controller", ATTACK_TRANSITION_TICKS, state -> PlayState.STOP);
        attackController.triggerableAnim("attack", ANIM_ATTACK);
        attackController.triggerableAnim("attack_left", ANIM_ATTACK_LEFT);
        attackController.triggerableAnim("attack_right", ANIM_ATTACK_RIGHT);
        registrar.add(attackController);

        AnimationController<AbstractRottenEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        isBurstAnimating() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }


}
