package org.bytechen.rottenruins.overworld.registry.entities.population.defensive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.rottenruins.utils.entity.EntityParticleUtils;
import org.bytechen.infcore.api.IInfectedEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 缠绕之柱 —— 防御型生物（{@code defensive} 分类）。
 *
 * <h3>行为总览</h3>
 * <ol>
 *   <li><b>不移动</b>：移动速度 0、不注册任何位移 Goal、{@link #travel} 只吃重力不吃输入，
 *       并且基类 tick 里那条「目标不在近战距离就寻路过去」的分支被
 *       {@link #NO_CHASE_DISTANCE} 堵死。它唯一的位移来源是下面这两段脚本化的下潜 / 上浮。</li>
 *   <li><b>只有 idle 动画</b>：{@link #registerControllers} 只挂一条 idle 控制器；
 *       资源里也确实只有 {@code idle} 这一段。</li>
 *   <li><b>抓取</b>：2 格内的生物会被抓住，并被钉在<b>被抓那一瞬间</b>的位置上；
 *       之后每秒（20 tick）扣 5 点伤害，同时用 {@link EntityParticleUtils}
 *       往它身上撒红色 dust。</li>
 *   <li><b>伤害传导（防御机制）</b>：身上有抓取物时受击，按 40% / 30% / 30% 三档分配：
 *       <ul>
 *         <li>40%：自身 0 伤害，<b>全部</b>传导给抓取物；</li>
 *         <li>30%：自身与抓取物各承担 <b>50%</b>；</li>
 *         <li>30%：自身承担 <b>100%</b>。</li>
 *       </ul>
 *       概率用 {@link ThreadLocalRandom} 抽取，原因见 {@link #hurt}。</li>
 *   <li><b>潜地偷袭</b>：没有抓取物时，只要 25 格内发现可抓的生物，就先把自身
 *       沉进脚下的方块（边沉边撒「脚下这块方块」的 break 粒子），完全没入后隐身，
 *       再从目标<b>脚下方块</b>里冒出来抓人。</li>
 * </ol>
 *
 * <h3>资源</h3>
 * <p>模型 {@code geo/winding_column.geo.json}、贴图 {@code textures/entity/winding_column.png}、
 * 动画 {@code animations/winding_column.animation.json}（只有 idle）。</p>
 */
public class WindingColumnEntity extends AbstractHallEntity {

    // ==================== 抓取 ====================

    /** 抓取半径（格）：目标进入这个距离就会被抓住。 */
    private static final double GRAB_RADIUS = 2.0D;
    private static final double GRAB_RADIUS_SQR = GRAB_RADIUS * GRAB_RADIUS;

    /** 潜地索敌半径（格）。 */
    private static final double DETECT_RADIUS = 25.0D;
    private static final double DETECT_RADIUS_SQR = DETECT_RADIUS * DETECT_RADIUS;

    /** 索敌扫描间隔（tick）。 */
    private static final int SCAN_INTERVAL = 10;

    /**
     * 抓取期间限制目标移速用的瞬态属性修饰符。
     *
     * <p>用瞬态（{@code addTransientModifier}）而不是 {@code addPermanentModifier}：
     * 瞬态修饰符不写进存档，万一实体被卸载 / 回档时没走到 {@link #release()}，
     * 也不会在它身上留下永久的 -100% 移速。</p>
     */
    private static final UUID GRAB_SPEED_LOCK_ID = UUID.fromString("7d2f9c64-1e08-4a53-9b7d-3c5e1f80a2d6");
    private static final String GRAB_SPEED_LOCK_NAME = "hall_winding_column_grab_lock";

    /**
     * 基类 {@code tick()} 里「目标不在近战距离就 {@code navigation.moveTo}」的判定距离。
     *
     * <p>这是让本生物<b>真的不会走</b>的关键：那段代码只要
     * {@code 碰撞箱距离² > doHurtDistance²} 就会寻路过去，而移动速度为 0 并不能阻止寻路
     * （路径本身仍然会驱动 {@code travel}）。把判定距离取得比索敌半径还大
     * （64 格 → 4096 ≫ 25² = 625）之后，那个分支在本生物身上<b>永远不可达</b>。</p>
     */
    private static final float NO_CHASE_DISTANCE = 64.0F;

    // ==================== 吸血 ====================

    /** 每次结算伤害的间隔（tick）：20 tick = 1 秒。 */
    private static final int DRAIN_INTERVAL = 20;
    /** 每次结算的伤害点数（需求固定 5 点，不吃属性 / 难度倍率）。 */
    private static final float DRAIN_DAMAGE = 5.0F;

    // ==================== 伤害传导概率（百分比，合计 100） ====================

    /** 40%：自身 0 伤害，全额传导到抓取物。 */
    private static final int CHANCE_FULL_TRANSFER = 40;
    /** 30%：自身 50% / 抓取物 50%（上界 = 40 + 30）。 */
    private static final int CHANCE_HALF_SPLIT = CHANCE_FULL_TRANSFER + 30;

    // ==================== 潜地 / 上浮 ====================

    /** 下潜深度（格）：柱体高 2.8，沉 3 格才真正看不见。 */
    private static final double BURROW_DEPTH = 3.0D;
    /** 下潜耗时（tick）。 */
    private static final int DIVE_TICKS = 16;
    /** 完全没入后的潜伏耗时（tick）。 */
    private static final int HIDDEN_TICKS = 12;
    /**
     * 潜伏时最多再等目标落地多久（tick）。
     *
     * <p>目标在空中时不能从它脚下冒出来（下方没有方块，也没法把它钉在正确的高度），
     * 所以潜伏会一直等到它落地；超过这个时间就放弃这次偷袭。</p>
     */
    private static final int HIDDEN_MAX_WAIT = 60;
    /** 放弃偷袭后的索敌冷却（tick）：避免对着飞行中的目标每两秒潜一次地。 */
    private static final int AMBUSH_COOLDOWN = 100;
    /** 上浮耗时（tick）。 */
    private static final int RISE_TICKS = 16;
    /** {@link #restorePhysics()} 向上找空位时最多扫几格（= 埋深 + 1 格余量）。 */
    private static final int RESTORE_SCAN = 4;

    // ==================== 粒子 ====================

    /** 红色 dust（抓取物身上持续渗出）。 */
    private static final ParticleOptions BLOOD_DUST =
            new DustParticleOptions(new Vector3f(0.86F, 0.06F, 0.08F), 1.3F);

    /** 抓取物身上 dust 的密度 / 初速度 / 范围系数（参数含义见 {@link EntityParticleUtils}）。 */
    private static final double DRAIN_DUST_DENSITY = 4.0D;
    private static final double DRAIN_DUST_SPEED = 0.08D;
    private static final float DRAIN_DUST_RANGE = 0.9F;
    /** 每几 tick 撒一次 dust —— 1 秒一次太稀，看不出「被持续抽取」。 */
    private static final int DRAIN_DUST_INTERVAL = 5;

    /**
     * 抓取物被拽回的最大距离（格）。
     *
     * <p>钉住是每 tick 把目标拉回抓取点，正常走动在此期间只会有零点几格的漂移；
     * 一旦目标<b>瞬移</b>（末影珍珠 / 传送指令 / 换维度），就不该把它从几百格外拽回来，
     * 而是直接松手 —— 否则这个「陷阱」会变成一张无限距离的捕人网。</p>
     */
    private static final double MAX_PIN_DISTANCE = 8.0D;
    private static final double MAX_PIN_DISTANCE_SQR = MAX_PIN_DISTANCE * MAX_PIN_DISTANCE;

    // ==================== 状态 ====================

    /** 潜地流程的阶段。只在服务端推进；读档时一律回到 {@link Phase#IDLE}。 */
    private enum Phase {
        /** 站在地上，等目标进入索敌半径。 */
        IDLE,
        /** 正在往脚下的方块里沉。 */
        DIVE,
        /** 完全没入、隐身，等待扑向目标。 */
        HIDDEN,
        /** 正在从目标脚下方块里冒出来。 */
        RISE
    }

    private Phase phase = Phase.IDLE;
    /** 当前阶段剩余 tick。 */
    private int phaseTicks;
    /** 下潜 / 上浮的落点（x / z 在「跟随目标」时每 tick 会被覆盖）。 */
    private double moveX, moveZ;
    /** 下潜 / 上浮的 Y 区间。 */
    private double moveFromY, moveToY;
    /** 潜地流程正在追的目标。 */
    @Nullable
    private LivingEntity victim;
    /** 上浮时是否贴着目标走（「从目标脚下方块冒出来」是收口条件，不能让它半路跑掉）。 */
    private boolean followVictim;

    /**
     * 是否「锚定」：穿透 + 零重力，位置完全由脚本决定。
     * <p>埋在方块里、以及抓着目标时都是锚定状态 —— 后者是为了不把被钉住的目标
     * 每 tick 往外挤一点（两个碰撞箱完全重叠时，有物理的那一方会互相推）。</p>
     */
    private boolean anchored;

    /** 已抓取的目标（服务端逻辑用，不做同步数据）。 */
    @Nullable
    private LivingEntity grabbed;
    /** 抓取目标的 UUID —— 存档只存这个，读档后再解析回实体引用。 */
    @Nullable
    private UUID grabbedId;
    /** 抓取瞬间的目标坐标：钉住它的就是这三个数。 */
    private double grabX, grabY, grabZ;
    /** 距下次结算伤害的 tick 计数。 */
    private int drainTimer;
    /** 索敌扫描冷却。 */
    private int scanCooldown;
    /** 放弃偷袭后的索敌冷却（见 {@link #AMBUSH_COOLDOWN}）。 */
    private int seekCooldown;

    public WindingColumnEntity(EntityType<? extends Monster> entityType, Level level,
                               Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);

        // 只有 idle 一段动画，没有 walk / run
        this.setHasWalkAnim(false);
        // 移动速度 0：配合「不注册位移 Goal」+ NO_CHASE_DISTANCE，它就是一根柱子
        this.setWalkSpeed(0.0F);
        this.setRunSpeed(0.0F);
        this.setIdleAnimSpeed(1.0F);
        // 参与爆体（基类默认开启，40% 概率）。它没有 die 动画 → 爆体期间 idle 照常播，
        // 只会高频缩放抽搐；抓取物在 onBurstStart 里松手（见下方 override）。
        // 见 NO_CHASE_DISTANCE
        this.setDoHurtDistance(NO_CHASE_DISTANCE);
        this.scanCooldown = Math.abs(this.getId()) % SCAN_INTERVAL;
    }

    // ==================== 属性 ====================

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                // 靠「伤害传导」活着的防御生物：血厚、护甲高、完全免疫击退
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, DETECT_RADIUS);
    }

    // ==================== 行为注册 ====================

    /**
     * 一个 Goal 都不注册。
     *
     * <p>索敌自己写在 {@link #tickSeeking} 里。基类那套 {@code InfectedTargetGoal}
     * 会把目标写进 {@code getTarget()}，而 {@code AbstractHallEntity#tick} 一旦看到
     * 目标在 {@code doHurtDistance} 之外就会寻路过去 —— 虽然已经被
     * {@link #NO_CHASE_DISTANCE} 堵死，但少一条路径就少一个「它怎么动了」的来源。</p>
     */
    @Override
    protected void registerGoals() {
        // 刻意留空
    }

    /** 只有 idle 一段动画。 */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!this.defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "idle_controller", 5, state -> {
            if (this.isDeadOrDying() || this.isBurstAnimating()) return PlayState.STOP;
            state.setControllerSpeed(this.getIdleAnimSpeed());
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    /** 除了抓取 / 吸血之外没有任何「普通攻击」。 */
    @Override
    protected boolean getAttack(LivingEntity target) {
        return false;
    }

    /** 不能被推着走。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // 柱体不吃任何挤压
    }

    /** 绝不自走：水平位移只由脚本化的下潜 / 上浮决定，竖直方向保留重力好让它落到地面。 */
    @Override
    public void travel(Vec3 travelVector) {
        super.travel(Vec3.ZERO);
    }

    /**
     * 掐掉一切外力塞给本实体的速度：爆炸冲击、活塞、水推、别的模组的推力……
     *
     * <p>「不能移动」不能只靠移动速度 0 + 空 Goal 表 + {@link #travel} 只吃输入：
     * 原版（以及 Forge 打过补丁的）爆炸是<b>直接给受击者写 {@code deltaMovement}</b> 的，
     * 那个速度不经过输入也不经过寻路，{@code travel} 里照样会被 {@code move()} 应用。
     * 竖直分量尤其致命 —— 爆炸在脚下时它是正的，按 {@code v²/(2·0.08)} 算能从地面
     * 弹到上百格高，表现就是「被炸一下就垂直升天」。锚定（零重力）的时候更糟：
     * 连落回来都不会。</p>
     *
     * <p>所以这里每 tick 在 {@code travel} 之前把速度清干净，只保留<b>向下</b>的分量
     * （未锚定时），好让它仍然能正常落地；被清掉时顺手置一次 {@code hurtMarked}，
     * 让客户端也收到速度归零的包，不在本地先飘一下。</p>
     */
    private void suppressExternalMotion() {
        Vec3 delta = this.getDeltaMovement();
        // 锚定时连下落也不要（潜地 / 抓着目标时位置完全由脚本决定）
        double keepY = this.anchored ? 0.0D : Math.min(delta.y, 0.0D);
        if (delta.x == 0.0D && delta.z == 0.0D && delta.y == keepY) {
            return; // 本来就只有向下的重力，不用管
        }
        this.setDeltaMovement(0.0D, keepY, 0.0D);
        this.hurtMarked = true;
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        // 必须在 super.tick() 之前：aiStep → travel 会拿当时的速度去 move()，
        // 晚一步就来不及了（爆炸正是先在别人 tick 里写好速度、再在本 tick 里被应用的）。
        if (!this.level().isClientSide()) {
            this.suppressExternalMotion();
        }
        super.tick();
        if (this.level().isClientSide()) return;
        if (this.isFakeDying()) return;

        LivingEntity held = this.currentGrabbed();
        if (held != null) {
            this.tickGrab(held);
            return;
        }

        if (this.phase == Phase.IDLE) {
            // 抓取结束后从「锚定」回到正常物理（放不下就下个周期再试）
            if (this.anchored && this.tickCount % 20 == 0) {
                this.restorePhysics();
            }
            this.tickSeeking();
            return;
        }

        switch (this.phase) {
            case DIVE -> this.tickDive();
            case HIDDEN -> this.tickHidden();
            case RISE -> this.tickRise();
            default -> { /* IDLE 已在上面处理 */ }
        }
    }

    // ==================== 潜地偷袭 ====================

    /** 没有抓取物时的常态：扫描 → 发现目标 → 开始下潜。 */
    private void tickSeeking() {
        // 刚放弃过一次偷袭（目标一直在空中）：先冷却，别立刻再潜一次
        if (this.seekCooldown > 0) {
            this.seekCooldown--;
            this.victim = null;
            return;
        }

        if (this.scanCooldown > 0) {
            this.scanCooldown--;
        } else {
            this.scanCooldown = SCAN_INTERVAL;
            this.victim = this.findVictim();
        }

        LivingEntity target = this.victim;
        if (target == null || !this.isValidVictim(target)
                || this.distanceToSqr(target) > DETECT_RADIUS_SQR) {
            this.victim = null;
            return;
        }

        this.beginDive(target);
    }

    /** 25 格内最近的可抓目标。 */
    @Nullable
    private LivingEntity findVictim() {
        AABB area = this.getBoundingBox().inflate(DETECT_RADIUS);
        List<LivingEntity> candidates =
                this.level().getEntitiesOfClass(LivingEntity.class, area, this::isValidVictim);

        LivingEntity closest = null;
        double closestSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distSqr = this.distanceToSqr(candidate);
            if (distSqr > DETECT_RADIUS_SQR || distSqr >= closestSqr) continue;
            closestSqr = distSqr;
            closest = candidate;
        }
        return closest;
    }

    /**
     * 能否抓取这一只。
     *
     * <p>排除：自己、已死亡 / 已移除、不可攻击、王庭自家生物（见 {@link #isHallKin}）、
     * 创造 / 旁观玩家。</p>
     *
     * <p>与其它王庭生物不同，这里<b>没有</b>套 {@code AbstractHallEntity#canTarget}
     * 的威胁点数闸门：它是一根「谁踩进来就抓谁」的防御柱，不是会主动出击的巡逻怪。</p>
     */
    private boolean isValidVictim(LivingEntity candidate) {
        if (candidate == this) return false;
        if (candidate.isRemoved() || !candidate.isAlive()) return false;
        if (!candidate.isAttackable()) return false;
        if (candidate instanceof Player player) return !isIgnoredPlayer(player);
        return !isHallKin(candidate);
    }

    /** 是否王庭自家生物（同族不互抓）。 */
    private static boolean isHallKin(LivingEntity candidate) {
        if (candidate instanceof AbstractHallEntity) return true;
        if (candidate instanceof IInfectedEntity infected) {
            ResourceLocation type = infected.getInfectionType();
            return type != null && HallMod.MODID.equals(type.getNamespace());
        }
        return false;
    }

    /** 开始往脚下的方块里沉。 */
    private void beginDive(LivingEntity target) {
        this.victim = target;
        this.moveX = this.getX();
        this.moveZ = this.getZ();
        this.moveFromY = this.getY();
        this.moveToY = this.getY() - BURROW_DEPTH;
        this.phase = Phase.DIVE;
        this.phaseTicks = DIVE_TICKS;
        this.setAnchored(true);
        this.setInvisible(false);
    }

    /** 一边下沉一边撒「脚下这块方块」的 break 粒子。 */
    private void tickDive() {
        this.phaseTicks--;
        double progress = 1.0D - this.phaseTicks / (double) DIVE_TICKS;
        this.setPos(this.moveX, Mth.lerp(progress, this.moveFromY, this.moveToY), this.moveZ);
        this.setDeltaMovement(Vec3.ZERO);

        if (this.tickCount % 2 == 0) {
            this.spawnBlockBurst(this.moveX, this.moveFromY, this.moveZ, 10);
        }

        if (this.phaseTicks <= 0) {
            // 完全没入：隐身，免得方块边缘露出一截
            this.setInvisible(true);
            this.phase = Phase.HIDDEN;
            this.phaseTicks = HIDDEN_TICKS;
        }
    }

    /**
     * 完全没入后的潜伏：盯着目标，<b>等它站在地面上</b>再从它脚下的方块里钻出来。
     *
     * <p>「从目标脚下方块冒出来」只有在目标确实踩在地面上时才成立：
     * 目标在空中（跳跃中 / 飞行 / 悬在池子上方）时，柱子下面是空气，
     * 冒出来既没有方块粒子，也会把目标钉在半空里。所以这里要等它落地，
     * 等太久（{@link #HIDDEN_MAX_WAIT}）就放弃这次偷袭，浮回原地并挂个冷却，
     * 免得对着飞天的目标每两秒潜一次地。</p>
     */
    private void tickHidden() {
        // 潜伏时位置完全锁死（爆炸 / 活塞都不该把它从地底顶出来），
        // 所以这里主动把坐标按回埋点，而不是只依赖 suppressExternalMotion。
        this.setPos(this.moveX, this.moveToY, this.moveZ);
        this.phaseTicks--;

        LivingEntity target = this.victim;
        if (target == null || !this.isValidVictim(target)
                || this.distanceToSqr(target) > DETECT_RADIUS_SQR) {
            // 目标跑了 / 没了：原地浮回去，不抓人
            this.victim = null;
            this.beginRise(this.moveX, this.moveFromY, this.moveZ, false);
            return;
        }

        // 需求：只有在目标站在地面上时，才从它脚下冒出来
        if (!this.isStandingOnGround(target)) {
            if (this.phaseTicks > -HIDDEN_MAX_WAIT) {
                return; // 还没落地 —— 在地底继续等
            }
            this.abortAmbush(); // 等太久了，放弃这次偷袭
            return;
        }

        if (this.phaseTicks <= 0) {
            // 从「目标脚下的那一层方块」冒出来
            this.beginRise(target.getX(), target.getY(), target.getZ(), true);
        }
    }

    /**
     * 目标是否「站在地面上」：脚下有碰撞、且脚底那一层是实心方块。
     *
     * <p>后者是给粒子用的 —— 「下方出现方块对应粒子」需要一个真方块；
     * 目标站在空气上（例如浮空方块边缘外、或者踩着临时的碰撞箱）时不该当成地面。</p>
     */
    private boolean isStandingOnGround(LivingEntity target) {
        if (!target.onGround()) return false;
        return !this.level()
                .getBlockState(BlockPos.containing(target.getX(), target.getY() - 0.5D, target.getZ()))
                .isAir();
    }

    /** 放弃这次偷袭：原地浮回去，并给索敌挂一个冷却（否则会对着空中目标反复潜地）。 */
    private void abortAmbush() {
        this.victim = null;
        this.seekCooldown = AMBUSH_COOLDOWN;
        this.beginRise(this.moveX, this.moveFromY, this.moveZ, false);
    }

    /**
     * 开始上浮。
     *
     * @param x        冒出点 X
     * @param surfaceY 冒出的终点 Y（= 目标脚底 / 原地）
     * @param z        冒出点 Z
     * @param follow   是否一边上浮一边贴着目标走
     */
    private void beginRise(double x, double surfaceY, double z, boolean follow) {
        this.followVictim = follow;
        this.moveX = x;
        this.moveZ = z;
        this.moveToY = surfaceY;
        this.moveFromY = surfaceY - BURROW_DEPTH;
        this.phase = Phase.RISE;
        this.phaseTicks = RISE_TICKS;
        this.setPos(x, this.moveFromY, z);
        this.setDeltaMovement(Vec3.ZERO);
        // 冒头前的预兆：地面上先炸开一片该方块的碎屑
        this.spawnBlockBurst(x, surfaceY, z, 24);
        this.level().playSound(null, x, surfaceY, z,
                SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    /** 上浮收口：露出地面，并抓住脚下那个目标。 */
    private void tickRise() {
        this.phaseTicks--;

        LivingEntity target = this.victim;
        if (this.followVictim && target != null && this.isValidVictim(target)) {
            // 水平方向跟着走 —— 「从它脚下方块冒出来」是收口条件，不能让它半路跑掉。
            // 竖直方向只在它<b>仍然站在地面上</b>时才跟：目标跳起来的瞬间不该把冒出点也抬到空中
            // （否则柱子会从空气里冒出来，下面一格方块粒子都没有）。
            this.moveX = target.getX();
            this.moveZ = target.getZ();
            if (this.isStandingOnGround(target)) {
                this.moveToY = target.getY();
                this.moveFromY = this.moveToY - BURROW_DEPTH;
            }
        }

        double progress = 1.0D - this.phaseTicks / (double) RISE_TICKS;
        this.setPos(this.moveX, Mth.lerp(progress, this.moveFromY, this.moveToY), this.moveZ);
        this.setDeltaMovement(Vec3.ZERO);

        if (this.tickCount % 2 == 0) {
            this.spawnBlockBurst(this.moveX, this.moveToY, this.moveZ, 8);
        }

        if (this.phaseTicks > 0) return;

        // 收口
        this.setInvisible(false);
        this.phase = Phase.IDLE;

        if (this.followVictim && target != null && this.isValidVictim(target)
                && this.distanceToSqr(target) <= GRAB_RADIUS_SQR) {
            this.victim = null;
            this.grab(target);
            return;
        }

        // 没抓到（目标半路传送走 / 死了）：回落到正常物理，站着等下一次机会
        this.victim = null;
        this.followVictim = false;
        this.restorePhysics();
    }

    // ==================== 抓取 / 吸血 ====================

    /** 抓住目标：钉在该坐标，并开始每秒扣血。 */
    private void grab(LivingEntity target) {
        this.grabbed = target;
        this.grabbedId = target.getUUID();
        this.grabX = target.getX();
        this.grabY = target.getY();
        this.grabZ = target.getZ();
        this.drainTimer = 0;
        // 只锁「移动」，不关 AI：被抓的生物必须还能还手（近战 / 远程 / 自爆都随它）
        this.lockMovement(target);
        // 抓着人的柱体同样保持锚定（穿透 + 零重力），否则两个重叠的碰撞箱会互相挤
        this.setAnchored(true);
        this.setTarget(target);

        // 抓取瞬间的反馈
        this.spawnDrainDust(target);
        this.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /**
     * 每 tick 维护「被抓住」这件事：把目标钉回抓取点、锁住移速、每秒扣 5 点血。
     *
     * <p>钉住靠三件事叠加，<b>全都不动 AI</b>：
     * <ol>
     *   <li>{@link #lockMovement}：移速锁 0（瞬态属性修饰符），它自己想走也走不动；</li>
     *   <li>{@code navigation.stop()} + 速度清零：把已排上的路径和残余动量清掉；</li>
     *   <li>{@code setPos(抓取点)} + {@code hurtMarked}：最后一道，坐标直接按回去并逼客户端同步。</li>
     * </ol>
     * 所以被抓的生物仍然能索敌、能打柱子（也会被柱子打），只是挪不动位置。</p>
     */
    private void tickGrab(LivingEntity held) {
        this.grabbed = held;

        // 目标瞬移跑了 → 松手，而不是把它从几百格外拽回来
        if (this.distanceToSqr(held) > MAX_PIN_DISTANCE_SQR) {
            this.release();
            return;
        }

        held.setPos(this.grabX, this.grabY, this.grabZ);
        held.setDeltaMovement(Vec3.ZERO);
        held.hurtMarked = true;
        // 每 tick 复查一次：万一移速锁被别的模组 / 指令洗掉了，立刻补上
        this.lockMovement(held);
        if (held instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        // 身上持续渗血红色 dust
        if (++this.drainTimer % DRAIN_DUST_INTERVAL == 0) {
            this.spawnDrainDust(held);
        }

        // 每秒 5 点
        if (this.drainTimer % DRAIN_INTERVAL == 0) {
            held.hurt(this.damageSources().mobAttack(this), DRAIN_DAMAGE);
            // 这一下打死了：同一 tick 就松手，把移速锁还掉（史莱姆 / 岩浆怪是
            // 「死后 20 tick 才 remove 并分裂」，子体不该继承任何残留状态）
            if (!held.isAlive()) {
                this.release();
            }
        }
    }

    /**
     * 把目标的移动速度锁成 0（瞬态属性修饰符），并保持锁住。
     *
     * <p>为什么不用 {@code setNoAi(true)}：{@code Mob#isEffectiveAi()} 一旦为 false，
     * {@code GoalSelector} 整个不会跑 —— 被抓的生物连还手都做不到；更糟的是
     * 史莱姆 / 岩浆怪分裂时会把父体的 {@code isNoAi()} 复制给子体
     * （{@code Slime#remove → slime.setNoAi(flag)}），子体不但继承「不会动」，
     * 连 {@code travel} 都不会执行、<b>连重力都没有</b>，直接悬在半空。</p>
     *
     * <p>这里只掐「移动速度」这一项：AI 照常，攻击 / 喝药 / 自爆 / 逃跑都还在，
     * 只是步子迈不出去。真正把位置钉死的是 {@link #tickGrab} 里的坐标回按。</p>
     */
    private void lockMovement(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.getModifier(GRAB_SPEED_LOCK_ID) != null) return; // 已经锁上了
        speed.addTransientModifier(new AttributeModifier(
                GRAB_SPEED_LOCK_ID, GRAB_SPEED_LOCK_NAME, -1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 解除 {@link #lockMovement} 加的移速锁。 */
    private static void unlockMovement(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(GRAB_SPEED_LOCK_ID) != null) {
            speed.removeModifier(GRAB_SPEED_LOCK_ID);
        }
    }

    /** 释放（目标死了 / 消失 / 换维度）。只清状态，物理回复交给 tick。 */
    private void release() {
        LivingEntity held = this.grabbed;
        // 无条件解锁：目标死了也要解，否则尸体上会留着 -100% 移速
        if (held != null) {
            unlockMovement(held);
        }
        this.grabbed = null;
        this.grabbedId = null;
        this.drainTimer = 0;
        this.phase = Phase.IDLE;
    }

    /**
     * 死亡前必须松手：否则被抓的生物会一直带着移速锁
     * （瞬态修饰符虽然不跨存档，但这一局里它就走不动了）。
     */
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            this.release();
        }
        super.die(source);
    }

    /**
     * 爆体开始 = 这一局必死（爆体结束必定 {@code remove(KILLED)}，不再走 {@link #die}）。
     * 所以松手必须提前到这里，否则被抓的生物会一直带着移速锁直到爆体结束。
     */
    @Override
    protected void onBurstStart() {
        if (!this.level().isClientSide()) {
            this.release();
        }
        super.onBurstStart();
    }

    /**
     * 当前抓取物（必要时用 UUID 重新解析）。
     * 返回值已过滤掉死亡 / 移除 / 换维度的目标 —— 有值时可以直接用。
     */
    @Nullable
    private LivingEntity currentGrabbed() {
        LivingEntity held = this.grabbed;
        if (held != null) {
            if (!held.isAlive() || held.isRemoved() || held.level() != this.level()) {
                this.release();
                return null;
            }
            return held;
        }
        if (this.grabbedId == null) return null;

        // 读档后实体引用还没接回来：用 UUID 找一次
        Entity resolved = this.level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(this.grabbedId) : null;
        if (resolved instanceof LivingEntity living && living.isAlive()) {
            this.grabbed = living;
            return living;
        }
        this.release();
        return null;
    }

    // ==================== 伤害传导（防御机制） ====================

    /**
     * 受击处理：身上有抓取物时按 40 / 30 / 30 分配伤害。
     *
     * <p><b>为什么用 {@link ThreadLocalRandom}</b>：这里的随机必须线程安全。实体 tick 正常
     * 都在服务端主线程跑，但 {@code hurt} 是公开入口，会被别的模组（异步伤害队列、
     * 区块加载线程、网络包处理）在别的线程调到；而 {@link java.util.Random} 的种子是
     * 可变共享状态 —— 两个线程同时取数会撕裂种子，极端情况下同一段种子反复得到同一档，
     * 40/30/30 就退化成 100/0/0 之类。{@code ThreadLocalRandom} 每线程独立、无锁，
     * 不会被并发污染，也不需要同步块。</p>
     *
     * <p>环境伤害（火 / 摔落 / 虚空）也走这套分配：需求只写了「被攻击」，但
     * 「身上挂着人时自己不出血」这件事在哪种伤害上都一样，统一处理更不容易出歧义。</p>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount <= 0.0F || this.level().isClientSide()) {
            return super.hurt(source, amount);
        }
        LivingEntity held = this.currentGrabbed();
        if (held == null) {
            return super.hurt(source, amount);
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < CHANCE_FULL_TRANSFER) {
            // 40%：自身 0 伤害，全部传导
            this.transferDamage(held, amount);
            return false;
        }
        if (roll < CHANCE_HALF_SPLIT) {
            // 30%：各承担 50%
            float half = amount * 0.5F;
            this.transferDamage(held, half);
            return super.hurt(source, half);
        }
        // 30%：自己全吃
        return super.hurt(source, amount);
    }

    /** 把伤害传导给抓取物（并压掉传导带来的击退 —— 它本来就被钉着）。 */
    private void transferDamage(LivingEntity held, float amount) {
        if (amount <= 0.0F) return;
        held.hurt(this.damageSources().mobAttack(this), amount);
        held.setDeltaMovement(Vec3.ZERO);
        held.hurtMarked = true;
        this.spawnDrainDust(held);
        // 传导过去的伤害把它打死了：立刻松手把移速锁还掉（见 release 里的说明）
        if (!held.isAlive()) {
            this.release();
        }
    }

    // ==================== 粒子 ====================

    /** 在抓取物身上撒红色 dust（服务端发送，所有玩家可见）。 */
    private void spawnDrainDust(LivingEntity target) {
        EntityParticleUtils.spawnParticles(target, BLOOD_DUST,
                DRAIN_DUST_DENSITY, DRAIN_DUST_SPEED, DRAIN_DUST_RANGE);
    }

    /**
     * 在 (x, y, z) 处撒「该处方块」的碎屑粒子。
     *
     * <p>贴图里没有专门的粒子资源，所以直接取世界里的方块状态交给
     * {@code ParticleTypes.BLOCK} —— 沉进泥土就是泥土色、沉进石头就是石头色，
     * 这是「下方出现方块对应粒子」最省事也最准确的做法。</p>
     */
    private void spawnBlockBurst(double x, double y, double z, int count) {
        BlockState state = this.groundStateAt(x, y, z);
        if (state.isAir()) return;

        EntityParticleUtils.ParticleConfig config = EntityParticleUtils.builder()
                .particle(new BlockParticleOption(ParticleTypes.BLOCK, state))
                .count(count)
                .rangeScale(1.0F)
                .velocityRandomRange(0.35D, 0.22D, 0.35D)
                .build();
        EntityParticleUtils.spawnParticlesInArea(this.level(),
                AABB.ofSize(new Vec3(x, y, z), 1.2D, 0.25D, 1.2D), config);
    }

    /** 取 (x, y, z) 脚下方块的状态：空气就往下找，最多 4 格。 */
    private BlockState groundStateAt(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y - 0.1D, z);
        for (int i = 0; i < 4; i++) {
            BlockState state = this.level().getBlockState(pos);
            if (!state.isAir()) return state;
            pos = pos.below();
        }
        return this.level().getBlockState(pos);
    }

    // ==================== 物理状态切换 ====================

    /**
     * 锚定 / 解除锚定。
     *
     * <p>锚定 = 穿透（不挤别人、不吃挤压与窒息）+ 零重力，位置完全由脚本决定。
     * 埋在方块里、以及抓着目标时都要锚定：后者是为了不让两个完全重叠的碰撞箱
     * 每 tick 互相推挤。</p>
     */
    private void setAnchored(boolean anchored) {
        this.anchored = anchored;
        this.noPhysics = anchored;
        this.setNoGravity(anchored);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 从「锚定」回到正常物理。
     *
     * <p>要点是<b>只做一次定位</b>：先看原地站不站得下，站得下就直接恢复；站不下就往上
     * 找一个放得下的高度（最多 {@link #RESTORE_SCAN} 格，够从 {@link #BURROW_DEPTH} 的
     * 埋深里顶出来）；都找不到就<b>一格都不挪</b>，保持锚定等下次。</p>
     *
     * <p>这里绝不能写成「找不到就 {@code setPos(y + 1)} 的循环」：那样一个卡在方块里的
     * 柱子会每 20 tick 往上爬几格，看上去就是<b>从地面一路升天</b> —— 和爆炸冲击
     * 造成的是同一类现象，只是来源不同。</p>
     */
    private void restorePhysics() {
        // 原地就站得下
        if (this.level().noCollision(this)) {
            this.anchored = false;
            this.noPhysics = false;
            this.setNoGravity(false);
            return;
        }

        // 卡在方块里：找最近一个放得下的高度，一次性挪过去
        for (int dy = 1; dy <= RESTORE_SCAN; dy++) {
            AABB candidate = this.getBoundingBox().move(0.0D, dy, 0.0D);
            if (!this.level().noCollision(this, candidate)) continue;
            this.anchored = false;
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setPos(this.getX(), this.getY() + dy, this.getZ());
            return;
        }

        // 实在放不下（被埋在实心区里）：保持穿透 + 零重力，至少不会被憋死，也不会自己往上飘。
        // 等周围被挖开 / 炸开之后，下一次 restorePhysics 自然就能成功。
        this.setAnchored(true);
    }

    // ==================== 存档 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.grabbedId != null) {
            nbt.putUUID("GrabbedEntity", this.grabbedId);
            nbt.putDouble("GrabX", this.grabX);
            nbt.putDouble("GrabY", this.grabY);
            nbt.putDouble("GrabZ", this.grabZ);
            nbt.putInt("DrainTimer", this.drainTimer);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("GrabbedEntity")) {
            this.grabbedId = nbt.getUUID("GrabbedEntity");
            this.grabX = nbt.getDouble("GrabX");
            this.grabY = nbt.getDouble("GrabY");
            this.grabZ = nbt.getDouble("GrabZ");
            this.drainTimer = nbt.getInt("DrainTimer");
        }
        // 潜地流程是「演出」，不跨存档：读回来一律站在地面上重新开始。
        // 先按「锚定」载入：如果存档时正埋在方块里，第一次 IDLE tick 的
        // restorePhysics 会把它一路顶回地面；本来就在地上时它是无操作。
        this.phase = Phase.IDLE;
        this.phaseTicks = 0;
        this.victim = null;
        this.followVictim = false;
        this.setInvisible(false);
        this.setAnchored(true);
    }
}
