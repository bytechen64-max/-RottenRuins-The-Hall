package org.bytechen.hall.overworld.registry.entities.population.apostle;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.compat.BCCoreCompat;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.function.Consumer;

/**
 * 坍缩使徒（Collapsar）—— 使徒分类的第一个单位。
 *
 * <h3>定位</h3>
 * 只会飞、不会走，永远悬在目标上方一段高度（同时不低于地面若干格），
 * 靠两个技能打人：<b>坍缩黑洞</b>与<b>斩击风暴</b>。只有 idle 动画，所以它没有
 * 走路/跑步/死亡动画控制器 —— 移动表现完全由飞行插值负责。
 *
 * <h3>技能一：坍缩黑洞</h3>
 * <pre>
 *   前摇 60 tick（3s）：在自身位置持续喷末地烛粒子（一次 sendParticles，不做逐粒子发包）
 *   前摇结束：先在原地放一个半径 20 格、扩散极快的冲击波（纯视觉）
 *   4 tick 后：黑洞出现，持续 60 tick
 *       伤害：引力范围内每 tick 3 点改血；伤害只在黑洞 60 tick 内存续
 *       注意：伤害逻辑<b>不在实体里</b> —— 见 BlackHolePhysicsHandler#applyHorizonDamage
 * </pre>
 *
 * <h3>技能二：斩击风暴</h3>
 * <pre>
 *   5 秒内共 20 次斩击（每 5 tick 一次），每次都是：
 *     在目标身上生成一道"使徒样式"的剑气（高 8 格、底面半径 1 格，白色外层 + 黑色内芯 outline）
 *     并按目标<b>最大生命值的 5%</b>改血 —— 20 刀刚好打满 100%，不看血量上限
 * </pre>
 *
 * <h3>为什么伤害全部走 {@link BCCoreCompat}</h3>
 * 改血是直接写血量存储：不受无敌帧、护甲、伤害抗性影响，且算到 ≤0 时由 BCCoreCompat
 * 补一次终结击，不会留下"看着是 0 其实是 1e-7"的残血。没装 VitalProbe 时自动回退原版 hurt。
 */
public class CollapsarEntity extends BaseApostleEntity {

    // ══════════════════════════════════════════════════════════════
    // 属性
    // ══════════════════════════════════════════════════════════════

    private static final double MAX_HEALTH = 800.0D;
    private static final double ARMOR = 100.0D;
    /**
     * 位移速度（格/tick），也是飞行速度上限：0.16 ≈ 3.2 格/秒。
     * <p>比玩家走路（约 4.3 格/秒）还慢一点 —— 使徒靠技能而不是靠贴身，
     * 飞行只需要"慢慢挪到目标头顶"。</p>
     * <p><b>注意</b>：这个值会被构造函数里的 {@code setWalkSpeed()} 覆盖
     * （AbstractHallEntity#applyMoveSpeed 把 walkSpeed 写进属性 baseValue），
     * 所以两处必须保持一致。</p>
     */
    private static final double MOVEMENT_SPEED = 0.16D;
    private static final double KNOCKBACK_RESISTANCE = 1.0D;
    /** 索敌半径 */
    private static final double FOLLOW_RANGE = 64.0D;

    // ══════════════════════════════════════════════════════════════
    // 飞行
    // ══════════════════════════════════════════════════════════════

    /** 悬停高度：目标脚底往上多少格 */
    private static final double HOVER_ABOVE_TARGET = 9.0D;
    /** 离地高度：无论目标在哪，至少离地面这么高 */
    private static final double MIN_GROUND_CLEARANCE = 5.0D;
    /** 飞行插值速度倍率（给 MoveControl 用） */
    private static final double FLY_SPEED = 1.0D;
    /** 到位判定（格）：进了这个圈就不再加力，避免在悬停点附近过冲抖动 */
    private static final double HOVER_DEADZONE = 0.5D;
    /**
     * 每 tick 朝目标方向叠加的加速度（格/tick²）。
     * <p>与 {@link #FLY_DRAG} 一起决定实际巡航速度：{@code 0.03 / (1 - 0.80) = 0.15}
     * 格/tick ≈ 3 格/秒，正好略低于 {@link #MOVEMENT_SPEED} 的上限 ——
     * 也就是说"飞多快"由这两个数决定，{@code MOVEMENT_SPEED} 只是兜底的天花板。</p>
     */
    private static final double FLY_ACCEL = 0.03D;
    /**
     * 每 tick 保留的速度比例（阻力）。
     * <p>0.80 的刹车距离 ≈ {@code v·d/(1-d)} ≈ 0.6 格，正好落在
     * {@link #HOVER_DEADZONE} 附近：到位后不再过冲回头，悬停点附近不会来回摆。</p>
     */
    private static final double FLY_DRAG = 0.80D;
    /** 转向速度（度/tick）：4 ≈ 80°/秒，重型单位该有的迟钝感 */
    private static final float TURN_SPEED = 4.0F;
    /** 转向死区（格）：水平距离小于这个值就保持当前朝向，不重新求解 */
    private static final double FACING_DEADZONE = 0.75D;
    /** 地面高度重算间隔（tick） */
    private static final int GROUND_HEIGHT_INTERVAL = 10;

    // ══════════════════════════════════════════════════════════════
    // 技能调度
    // ══════════════════════════════════════════════════════════════

    /**
     * 黑洞技能冷却（tick）：10s。
     * <p>两个技能<b>互相不阻塞</b>（见 {@code serverTickSkills}）：各自的冷却只决定
     * "这个技能多久能再用一次"，黑洞前摇期间斩击照打、黑洞还在吸的时候也能再起前摇。</p>
     */
    private static final int BLACK_HOLE_COOLDOWN = 200;
    /** 斩击技能冷却（tick）：6s。 */
    private static final int SLASH_COOLDOWN = 120;
    /** 目标离本体多远才值得开技能（不能用技能打 60 格外的空气） */
    private static final double CAST_RANGE = 40.0D;

    // ── 黑洞技能 ──

    /** 前摇时长（tick）：3s */
    private static final int BH_WINDUP_TICKS = 60;
    /**
     * 前摇期间锚点是否跟随目标。
     * <p>{@code true}：黑洞落在<b>目标位置</b>，末地烛粒子也一直出现在目标身上
     * （玩家能明确看到"我脚下正在充能"）。{@code false} 则在起手瞬间锁定地面范围，
     * 玩家可以跑出去躲开 —— 想改手感就把这个开关翻过来。</p>
     */
    private static final boolean BH_TRACK_TARGET = true;
    /** 前摇每 tick 的末地烛粒子数（一次发包，count 由原版在客户端展开） */
    private static final int BH_WINDUP_PARTICLES = 28;
    /** 冲击波半径（格） */
    private static final float BH_SHOCKWAVE_RADIUS = 20.0F;
    /**
     * 冲击波扩散速度（格/秒）。半径按 {@code age / 20 * speed} 增长，
     * 80 意味着 5 tick（0.25 秒）就铺满 20 格 —— "扩散快"。
     */
    private static final float BH_SHOCKWAVE_SPEED = 80.0F;
    /** 冲击波存活（tick） */
    private static final int BH_SHOCKWAVE_LIFE = 12;
    /** 冲击波之后隔几 tick 出黑洞（先看到波，再看到洞） */
    private static final int BH_SPAWN_DELAY = 4;
    /** 黑洞存活（tick） */
    private static final int BH_LIFETIME = 60;
    /** 黑洞低频嗡鸣的间隔（tick） */
    private static final int BH_HUM_INTERVAL = 20;
    /** 前摇蓄力脉冲的间隔（tick） */
    private static final int BH_CHARGE_SOUND_INTERVAL = 10;
    /**
     * 史瓦西半径（格）：透镜盘面大小。
     * <p>初版 1.2，观感上太大，这里缩到 1/5（0.24）。渲染器的透镜球半径是 {@code 6·Rs}
     * ≈ 1.44 格。{@code bendForRadius} 会同步把弯曲强度放大，所以缩小后
     * "折射的归一化强度"不变，只是整体变小。</p>
     * <p>注意：引力半径与事件视界半径<b>没有</b>跟着缩 —— 它们决定技能手感而不是观感，
     * 见 {@link #BH_GRAVITY_RADIUS} / {@link #BH_KILL_RADIUS}。</p>
     */
    private static final float BH_RADIUS = 0.24F;
    /** 引力作用半径（格） */
    private static final float BH_GRAVITY_RADIUS = 24.0F;
    /** 事件视界半径（格）：进入后每 tick 被改血 */
    private static final float BH_KILL_RADIUS = 6.0F;
    /** 每 tick 改血量（点） */
    private static final float BH_DAMAGE_PER_TICK = 3.0F;
    /** 改血伤害持续（tick）：60 */
    private static final int BH_DAMAGE_TICKS = 60;

    // ── 斩击技能 ──

    /** 一次技能的总斩击次数 */
    private static final int SLASH_COUNT = 20;
    /** 两次斩击的间隔（tick）：20 次 × 5 = 100 tick = 5s */
    private static final int SLASH_INTERVAL = 5;
    /** 单次斩击伤害（目标最大生命值的百分比） */
    private static final float SLASH_DAMAGE_PERCENT = 5.0F;
    /** 剑气总长度（格）。初版 8 格看着"够不着人"，这里放大 4 倍到 32 */
    private static final float SLASH_HEIGHT = 32.0F;
    /** 剑气底面半径（格） */
    private static final float SLASH_RADIUS = 1.0F;
    /** 单道剑气的存活（tick） */
    private static final int SLASH_MAX_AGE = 14;
    /** 剑气收缩速度（>1 收得更快，撑不住 14 tick 会变成一条线） */
    private static final float SLASH_SHRINK_SPEED = 1.6F;
    /** 剑气初始缩放 */
    private static final float SLASH_SCALE = 1.0F;
    /** 落点随机偏移半径（格）：20 刀不要全砸在同一个像素上 */
    private static final double SLASH_SPREAD = 1.6D;
    /**
     * 斩击伤害判定半径（格）。
     * <p>刀长 32 格，但伤害只按"落点周围这个球"结算：范围内的每个非感染生物
     * 各按<b>自身</b>最大生命的 5% 改血。这样一刀下去是"劈在谁身上谁掉血"，
     * 不会出现"剑气明明穿过去了，却只有远处的当前目标在掉血"的脱靶感。</p>
     */
    private static final double SLASH_HIT_RADIUS = 3.0D;

    // ══════════════════════════════════════════════════════════════
    // 尾杀「坍缩」参数
    // ══════════════════════════════════════════════════════════════

    /**
     * 尾杀触发血量比例：血量 ≤ 最大生命的这个比例时开始，<b>一场只放一次</b>。
     * <p>800 血 ⇒ 200 血以下进入尾杀。</p>
     */
    private static final float FINISHER_TRIGGER_RATIO = 0.25F;
    /** 尾杀总时长（tick）：15 秒。 */
    private static final int FINISHER_DURATION = 300;
    /** 第一阶段（冲击波风暴）时长（tick）：5 秒。 */
    private static final int FINISHER_STORM_TICKS = 100;
    /** 风暴间隔：每 2 tick 一发，5 秒共 50 发。 */
    private static final int FINISHER_STORM_INTERVAL = 2;
    /**
     * 风暴里音效的间隔（tick）。
     * <p>冲击波每 2 tick 生成一个，但音效不能跟着每 2 tick 来 —— 那是每秒 10 次
     * {@code playSound} 广播，糊成一片还白发 50 个包。音频单独按 10 tick 走骨架。</p>
     */
    private static final int FINISHER_STORM_SOUND_INTERVAL = 10;

    /**
     * 巨型黑洞尺寸 —— 相对普通黑洞技能（{@link #BH_RADIUS} 0.24 / 引力 24 / 杀伤 6）。
     * <p>"巨大"按 5 倍视半径给：视觉半径 1.2 格、引力 48 格、杀伤半径 24 格。
     * 杀伤半径是唯一会要命的那条线，24 格意味着尾杀第二阶段一开始，
     * 目标必须在 10 秒内跑出 24 格 —— 这是设计上的逃跑窗口。</p>
     */
    private static final float FINISHER_BH_RADIUS = 1.2F;
    private static final float FINISHER_BH_GRAVITY_RADIUS = 48.0F;
    private static final float FINISHER_BH_KILL_RADIUS = 24.0F;
    /** 尾杀黑洞每 tick 的改血量（VitalProbe 改血，不走原版 hurt）。 */
    private static final float FINISHER_BH_DAMAGE_PER_TICK = 20.0F;
    /**
     * 巨型黑洞存活时间（tick）= 尾杀剩余时长：5 秒处生成、15 秒处与尾杀同时结束。
     * <p>伤害窗口与存活时间同值，也就是"黑洞在，就每 tick 削 20"。</p>
     */
    private static final int FINISHER_BH_LIFETIME = FINISHER_DURATION - FINISHER_STORM_TICKS;

    // ══════════════════════════════════════════════════════════════
    // 运行时状态（仅服务端使用）
    // ══════════════════════════════════════════════════════════════

    private int blackHoleCooldown = 0;
    private int slashCooldown = 0;

    /** >0 表示正在黑洞技能的前摇 */
    private int windupTicks = 0;
    /** >0 表示冲击波已放、正等黑洞落地 */
    private int blackHoleDelay = 0;
    /** 黑洞技能的召唤锚点（前摇期间跟随本体，结束时冻结） */
    private Vec3 anchor = null;

    /** >0 表示斩击序列还剩多少次 */
    private int slashesLeft = 0;
    private int slashTimer = 0;
    /** 斩击序列的已出刀数（音效重音用） */
    private int slashIndex = 0;
    /** >0 表示黑洞还在世，用来驱动低频嗡鸣 */
    private int blackHoleHumTicks = 0;
    /** 缓存的脚下地面高度（每 GROUND_HEIGHT_INTERVAL tick 刷新） */
    private double cachedGroundY = 0.0D;
    private int groundHeightCooldown = GROUND_HEIGHT_INTERVAL;
    /** 自己的悬停移动控制器（用来在到位时喊停，见 HoverMoveControl#halt） */
    private final HoverMoveControl hoverMoveControl;

    // ── 尾杀状态 ──

    /** 尾杀是否已经放过：一场只放一次（存盘，重载后不会重复触发）。 */
    private boolean finisherUsed = false;
    /** >0 表示尾杀进行中（剩余 tick，从 {@link #FINISHER_DURATION} 递减到 0）。 */
    private int finisherTick = 0;
    /** 巨型黑洞是否已经生成（进入第二阶段那一刻放一次）。 */
    private boolean finisherHoleSpawned = false;
    /**
     * 尾杀期间"赊"下的致命伤。
     * <p>尾杀由本体自己的 {@code tick()} 驱动，一旦 {@code die()} 时间轴立即断掉
     * （表现就是"尾杀还没播完就没了"）。所以这段时间把致命伤记账：血量钉在 1，
     * 等 15 秒走完再一次性结算。</p>
     */
    private boolean finisherPendingDeath = false;
    /** 尾杀期间赊下的那笔致命伤来自谁（结算时用来保住击杀归属）。 */
    private DamageSource finisherPendingSource = null;

    // ── 限伤兜底 ──

    /**
     * 上一次"我们认可"的血量基线，用于识破绕过 {@code hurt()} 的越权改血。
     * <p>-1 表示还没立过基线（首 tick / 刚读盘）。</p>
     */
    private float lastSanctionedHealth = -1.0F;
    /** 越权改血只报一次日志，避免刷屏。 */
    private boolean loggedOutOfBandDamage = false;

    // ══════════════════════════════════════════════════════════════
    // Boss 血条
    // ══════════════════════════════════════════════════════════════

    /**
     * 原版 boss 血条（客户端拿到的是 {@code LerpingBossEvent}）。
     * <p>碰撞箱之外的显示逻辑全部交给它：谁看得到、什么时候出现/消失、血量插值，
     * 都是原版既有行为。客户端那边由 {@code CollapsarBossBar} 在
     * {@code CustomizeGuiOverlayEvent.BossEventProgress} 里取消原版绘制，
     * 改用 {@code collapsar_health_outline} + 宇宙着色器渲染的 mask 内容。</p>
     */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    /**
     * 上次同步出去的血量比例。
     * <p>血量是由 VitalProbe 直接写存储的（不一定经过 {@code setHealth}），所以只能每 tick
     * 轮询比较；比较后再发，避免每 tick 一个进度包 —— 血迹只在掉血时才会更新。</p>
     */
    private float lastBossProgress = 1.0F;

    public CollapsarEntity(EntityType<? extends Monster> entityType, Level level,
                           Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.hoverMoveControl = new HoverMoveControl(this);
        this.moveControl = this.hoverMoveControl;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        // 只有 idle 动画：不声明 walk/run，避免 AbstractHallEntity 去找不存在的动画
        this.setHasWalkAnim(false);
        // 悬停飞行的速度上限（与 MOVEMENT_SPEED 常量保持一致，见那里的说明）
        this.setWalkSpeed((float) MOVEMENT_SPEED);
        this.setRunSpeed((float) MOVEMENT_SPEED);
        // 使徒不用近战钩子（BaseApostleEntity 已把 doHurtDistance 置 0）
        this.setDoHurtTime(20);
    }

    /** 资源绑定：geo / png / animation 三个文件名都是 collapsar。 */
    public static Consumer<AbstractHallEntity> resources() {
        return entity -> {
            entity.model = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "geo/collapsar.geo.json");
            entity.texture = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/entity/collapsar.png");
            entity.animation = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "animations/collapsar.animation.json");
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)         // 技能伤害不走这里，仅用于占位
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    // ══════════════════════════════════════════════════════════════
    // Goals
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void registerGoals() {
        // 索敌：64 格内看见就打（不需要视线 —— 它靠悬停俯视，隔着树叶也应该锁人）
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(FOLLOW_RANGE)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        // 这里刻意<b>不</b>加 FaceTargetGoal：它写的是 yHeadRot / yBodyRot / xRot，
        // 而 GeckoLib 模型的朝向只认实体 yRot，头/身旋转量没有任何模型去读 ——
        // 加了等于有个看不见的第二方在抢写旋转。朝向统一由 tickFlight() 里的
        // updateFacing() 负责（唯一写入者，慢速转向）。
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    // ══════════════════════════════════════════════════════════════
    // 动画：只有 idle
    // ══════════════════════════════════════════════════════════════

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "idle_controller", 5, state -> {
            if (isDeadOrDying() || isFakeDying()) return PlayState.STOP;
            state.setControllerSpeed(getIdleAnimSpeed());
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    // ══════════════════════════════════════════════════════════════
    // 自定义血量（同步数据）+ 自定义受击结算
    // ══════════════════════════════════════════════════════════════

    /** 自定义血量。覆写 getHealth() 之后，原版 {@code LivingEntity.health} 字段不再参与本实体。 */
    private static final EntityDataAccessor<Float> DATA_HEALTH =
            SynchedEntityData.defineId(CollapsarEntity.class, EntityDataSerializers.FLOAT);

    /**
     * 血量存储的<b>一次函数伪装</b>：同步数据里躺着的不是真实血量。
     *
     * <pre>
     *   存储值   = 100 - 真实血量
     *   真实血量 = 100 - 存储值      // 斜率 -1，所以它自己就是自己的逆函数
     * </pre>
     *
     * <h3>为什么这个方向</h3>
     * 外面那些"字段扫描改血"的实现通常是找到血量字段直接写 0（原版语义里 0 就是死）。
     * 换到这个极性之后：
     * <ul>
     *   <li>写 <b>0</b> ⇒ 真实血量变成 <b>100</b>，本体不死；</li>
     *   <li>写<b>负数</b> ⇒ 真实血量反而更高（更肉）；</li>
     *   <li>想按“写个大数把它弄死”的直觉来，反而需要一个很大的<b>正数</b>，
     *       而扫描器一般是拿当前值往下减，方向天然反着。</li>
     * </ul>
     * 它们扫到的这个 float 既不是血量的量级、方向也是反的，对不懂换算的实现等于无效。
     *
     * <h3>它挡不住谁</h3>
     * 会反解 {@code getHealth()} 字节码的那一档照样能把这条一次函数翻回来
     * （VitalProbe 的 {@code analysis/Arith} + {@code OutExpr} 就是专门做算术还原的）。
     * 所以真正的防线仍然是 {@link #guardHealthCap()}：对任何来源都按每 tick 20 兜底。
     *
     * <h3>改代码时的注意</h3>
     * 这个映射是<b>对合</b>的（套两次回到原值），也就是说读写方向搞反<b>不会报错</b>、
     * 只会静默错。因此除 {@link #getHealth()} / {@link #setHealth(float)} 与
     * {@link #defineSynchedData()} 的初值以外，<b>任何地方都不许再直接碰 DATA_HEALTH</b>。
     */
    private static final float STORED_HEALTH_OFFSET = 100.0F;

    /** 真实血量 → 存储值。 */
    private static float toStoredHealth(float realHealth) {
        return STORED_HEALTH_OFFSET - realHealth;
    }

    /** 存储值 → 真实血量。 */
    private static float toRealHealth(float storedHealth) {
        return STORED_HEALTH_OFFSET - storedHealth;
    }

    /**
     * 单次受击伤害上限。
     * <p>无论来源多猛（凋灵爆炸、上千点的魔法伤害、词条怪的一刀），单次最终伤害最多 20。
     * 800 血 ⇒ 至少挨 40 刀，血量曲线完全由<b>次数</b>决定，不会被单次爆发秒掉。</p>
     */
    private static final float MAX_DAMAGE_PER_HIT = 20.0F;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();                       // 基类的走路/动画同步数据，必须保留
        // 初值是“满血的存储形态”，不是满血本身 —— 800 血写进去的是 -700
        this.entityData.define(DATA_HEALTH, toStoredHealth((float) MAX_HEALTH));
    }

    /** 血量读自定义同步数据，并做一次逆变换还原成真实血量（原版 health 字段被架空）。 */
    @Override
    public float getHealth() {
        return toRealHealth(this.entityData.get(DATA_HEALTH));
    }

    /**
     * 血量按<b>真实值</b>夹在 {@code [0, 最大生命]}，再变换成存储形态写进同步数据。
     * <p>属性在极早期（构造期）可能还没挂上，那时 {@code getMaxHealth()} 是 0，
     * 直接夹会把血夹成 0 并且再也回不来，所以留一道兜底。</p>
     */
    @Override
    public void setHealth(float health) {
        float max = this.getMaxHealth();
        if (max <= 0.0F) max = (float) MAX_HEALTH;
        this.entityData.set(DATA_HEALTH, toStoredHealth(Mth.clamp(health, 0.0F, max)));
    }

    /**
     * 自定义受击结算。
     *
     * <h3>为什么不走 {@code super.hurt}</h3>
     * 本实体的血量存在同步数据里，而原版 {@code actuallyHurt} 扣的是 {@code LivingEntity.health} 字段 ——
     * 两者不是一个地方。混着用会出现"挨了打但血条不掉"或"血条空了却不死"。
     * 所以这里完整接管，并按原版语义补齐该有的副作用：
     * <ol>
     *   <li>无敌帧与原版同构：20 tick 窗口内只有<b>更高</b>的单次伤害能补差；</li>
     *   <li><b>限伤 {@value #MAX_DAMAGE_PER_HIT}</b>：夹在最后，任何来源都翻不过去；</li>
     *   <li>{@code LivingHurtEvent} / {@code LivingDamageEvent} 照常投递 ——
     *       本模组的"感染生物之间无友伤"、异常伤害加成、外模组的伤害数字都监听它们，
     *       不投递会让这些规则在本体上静默失效；</li>
     *   <li>受击表现（{@code hurtTime}/事件码 2/记仇）与掉落、击杀归属仍走原版；
     *       只有"护甲 / 吸收 / 抗性"这一串不参与 —— 这个 boss 的减伤只有限伤 20 一条。</li>
     * </ol>
     *
     * <h3>血量数值的坐标空间</h3>
     * 本方法（以及 {@link #applyCustomDamage}、{@link #guardHealthCap}）里出现的每一个血量
     * 数字都是<b>真实血量</b>。存储里那份是伪装过的一次函数值（见 {@link #STORED_HEALTH_OFFSET}），
     * 变换只发生在 {@link #getHealth()} / {@link #setHealth(float)} 内部 ——
     * 所以这些方法里<b>一个 DATA_HEALTH 都不应该出现</b>，出现了就是写错了空间。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isFakeDying()) return false;                        // 假死期间免疫（基类能力）
        if (this.level().isClientSide() || amount <= 0.0F || this.isRemoved()) return false;
        if (this.isInvulnerableTo(source)) return false;

        // 先限伤，再进事件：让监听方看到的就是"这一刀最多 20"的数值。
        // 注意这里<b>不</b>给 BYPASSES_INVULNERABILITY（/kill、出界）开后门 ——
        // 限伤对本体是唯一减伤规则，开了口子就等于"op 一条命令绕过全部设计"。
        float limited = Math.min(amount, MAX_DAMAGE_PER_HIT);

        // 假死（基类能力）：开启时，致命一击改为进入假死而不是掉血。
        // Collapsar 默认没开这个开关，但接线留着 —— 否则哪天有人打开它，
        // 行为会在这里静默变成"直接打死"。
        if (this.fakeDeathEnabled && this.getHealth() - limited <= 0.0F && this.tryFakeDeath()) {
            return false;
        }

        LivingHurtEvent hurtEvent = new LivingHurtEvent(this, source, limited);
        if (MinecraftForge.EVENT_BUS.post(hurtEvent)) return false;   // 可被取消（友伤规则走这条）
        limited = Math.min(hurtEvent.getAmount(), MAX_DAMAGE_PER_HIT);

        if (this.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (limited <= this.lastHurt) return false;
            float delta = limited - this.lastHurt;
            this.lastHurt = limited;
            applyCustomDamage(delta, source);
            return true;
        }

        this.lastHurt = limited;
        this.invulnerableTime = 20;
        applyCustomDamage(limited, source);
        return true;
    }

    /** 真正扣血与死亡判定（伤害已经过限伤与事件）。 */
    private void applyCustomDamage(float damage, DamageSource source) {
        LivingDamageEvent damageEvent = new LivingDamageEvent(this, source, damage);
        MinecraftForge.EVENT_BUS.post(damageEvent);
        float finalDamage = Math.min(damageEvent.getAmount(), MAX_DAMAGE_PER_HIT);

        this.setHealth(this.getHealth() - finalDamage);
        this.lastSanctionedHealth = this.getHealth();             // 这一笔是我们自己打的，记为合规

        // 受击表现（原版 hurt 里做的三件事）
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        this.hurtMarked = true;                                   // 让客户端刷新速度/位置
        this.level().broadcastEntityEvent(this, (byte) 2);        // 客户端播受击音效 + 播放受击动画
        this.getCombatTracker().recordDamage(source, finalDamage);

        // 记仇：基类的索敌依赖它，击杀归属也依赖它
        if (source.getEntity() instanceof LivingEntity attacker) {
            this.setLastHurtByMob(attacker);
            if (attacker instanceof Player player) {
                this.setLastHurtByPlayer(player);
            }
        }

        if (this.getHealth() <= 0.0F) {
            // 尾杀期间免死：记账 + 血量钉 1，等 15 秒播完再死（结算在 tickFinisher 收尾）
            if (finisherTick > 0) {
                this.setHealth(1.0F);
                this.finisherPendingDeath = true;
                return;
            }
            this.setHealth(0.0F);
            this.die(source);                                     // 掉落/击杀归属/死亡音效都在原版 die 里
        }
    }

    /**
     * 血量存盘：<b>只保留标准键 {@code Health}，且写的是伪装值。</b>
     *
     * <p>以前这里是两条明文：原版 {@code super.addAdditionalSaveData} 会把真实血量写进
     * 标准键 {@code Health}，我们另外又补了一条 {@code HallHealth} —— 任何读 NBT 的工具
     * （存档编辑器、其它模组的调试命令）都能直接看到真实血量。</p>
     *
     * <p>现在反过来利用原版那条：覆盖 {@code Health} 为伪装空间的值（满血 800 写进去是 -700），
     * 并且<b>不再写自定义键</b>。于是 NBT 里既没有"一看就是血量"的自定义键，
     * 也没有一个落在 [0, 800] 区间、方向和量级都对得上的数字。读的时候在
     * {@link #readSavedHealth} 里还原，并兼容旧存档的明文 {@code HallHealth}。</p>
     *
     * <p>尾杀"一场一次"的标志另存一条布尔 —— 它不含血量信息，留着无所谓。</p>
     */
    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putFloat("Health", toStoredHealth(this.getHealth()));
        nbt.putBoolean("HallFinisherUsed", this.finisherUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        // super 会把上面的伪装值当血量读一遍（setHealth 夹到 0）—— 无所谓，下一行就覆盖。
        // 关键是这一步必须在 super 之后，否则会被它盖回去。
        super.readAdditionalSaveData(nbt);
        this.setHealth(readSavedHealth(nbt));
        this.finisherUsed = nbt.getBoolean("HallFinisherUsed");
        // 进度本身不存：读盘时尾杀中断（剩下的以普通技能继续），只有"放过一次"是持久的
        this.finisherTick = 0;
        this.finisherHoleSpawned = false;
        this.finisherPendingDeath = false;
        this.finisherPendingSource = null;
        this.lastSanctionedHealth = this.getHealth();   // 读盘后重新立基线，别把存档血量当越权
    }

    /**
     * 还原存档里的血量。
     * <p>顺序：新格式（标准键 {@code Health} 里存的伪装值）→ 旧存档的明文 {@code HallHealth}
     * → 都没有就满血兜底（刚生成、或从更老的存档升级上来）。</p>
     */
    private float readSavedHealth(CompoundTag nbt) {
        if (nbt.contains("Health")) {
            return toRealHealth(nbt.getFloat("Health"));
        }
        if (nbt.contains("HallHealth")) {          // 旧存档：当年写的明文真实血量
            return nbt.getFloat("HallHealth");
        }
        return this.getMaxHealth();
    }

    // ══════════════════════════════════════════════════════════════
    // Tick
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        // 必须在 super.tick() 之前：原版 baseTick 会检查 isDeadOrDying()（= getHealth() <= 0）
        // 并直接走 tickDeath()。而 VitalProbe 改血是直接写血量存储，绕过我们的 hurt /
        // applyCustomDamage，所以"改血到 0"这条路径只能在这里顶回去 ——
        // 否则就是"尾杀还没播完，本体已经被改血 0 判死了"。
        guardHealthCap();
        guardFinisherHealth();

        super.tick();
        if (this.level().isClientSide()) return;

        tickBossEvent();
        tickFlight();
        serverTickSkills();
    }

    /**
     * 尾杀期间的免死保护：把被改血写到 ≤0 的血量顶回 1，并记下这笔致命伤。
     * <p>只在尾杀进行中生效；尾杀结束后血量照常可以被削到 0 并正常死亡。</p>
     */
    private void guardFinisherHealth() {
        if (finisherTick <= 0) return;
        if (this.getHealth() <= 0.0F) {
            this.setHealth(1.0F);
            this.lastSanctionedHealth = 1.0F;
            this.finisherPendingDeath = true;
        }
    }

    /**
     * <b>尾杀期间本体在定义上就还没死。</b>
     *
     * <h3>为什么单靠"每 tick 开头修血量"堵不住</h3>
     * 判死的入口不止一个，而且有些落在我们修完血量<b>之后</b>：
     * <ul>
     *   <li>原版 {@code LivingEntity.baseTick()} 里 {@code isDeadOrDying() → tickDeath()}，
     *       这段跑在 {@code super.tick()} 里，也就是我们 {@code tick()} 开头那次顶血之后；</li>
     *   <li>别家模组的 {@code LivingTickEvent} / 实体 tick 回调同样在 {@code super.tick()} 内，
     *       它们在里面把血量写成 0，紧接着原版的判死就会看到 0；</li>
     *   <li>直接反射/内部调用 {@code die(source)} 的实现，根本不看血量。</li>
     * </ul>
     * 所以真正的堵法是把这个判定本身按下去 —— 只要尾杀还在跑，
     * {@code isDeadOrDying()} 一律回答"没死"，原版那条 {@code tickDeath()} 分支就进不去；
     * 而实体的 {@code tick()} 也不会因为"已死"而停掉，尾杀的时间轴才播得完。
     *
     * <p>尾杀一结束，这里立刻恢复成原版的真实判定（{@code finisherTick} 归零那一刻），
     * 于是那笔赊账会在收尾处以 {@code die()} 结算。</p>
     */
    @Override
    public boolean isDeadOrDying() {
        return this.finisherTick <= 0 && super.isDeadOrDying();
    }

    /**
     * 尾杀期间拦下真正的死亡。
     * <p>{@link #isDeadOrDying()} 挡住的是"原版按血量判死"这条路；这条挡的是
     * <b>有人直接调 {@code die()}</b>（反射、别的模组的处决逻辑、原版的某些一击必杀路径）。
     * 一律转成赊账：血量钉 1、记住来源，等 15 秒收尾再结算，击杀归属不丢。</p>
     */
    @Override
    public void die(DamageSource source) {
        if (this.finisherTick > 0) {
            this.setHealth(1.0F);
            this.lastSanctionedHealth = 1.0F;
            this.finisherPendingDeath = true;
            this.finisherPendingSource = source;
            return;
        }
        super.die(source);
    }

    /**
     * <b>限伤兜底</b>：把绕过 {@code hurt()} 的越权改血顶回去。
     *
     * <h3>为什么必须有这一层</h3>
     * {@link #MAX_DAMAGE_PER_HIT} 只在 {@code hurt()} 里生效。而有些改血实现
     * —— VitalProbe 就是这一类：反解 {@code getHealth()} 的字节码 → 定位真实存储 → 直接写
     * —— 根本不经过 {@code hurt()}，一次写入就能把 800 血改到 0。
     * <b>换存储形态挡不住</b>：字段、SynchedEntityData、HashMap 它都认（连命名映射
     * 都有 {@code PlanApplier$NamedMap} 这种写回方案），所以防线只能是"轮询不变量"。
     *
     * <p>规则：每 tick 与基线比对一次。血量掉得比一次限伤上限还多，就认定是越权写入，
     * 把多出来的部分补回去。效果是无论对面一次写多少，本体每 tick 最多掉
     * {@link #MAX_DAMAGE_PER_HIT} —— 800 血至少 40 tick，与正常限伤同一手感。</p>
     *
     * <p>合规路径不会误伤：{@link #applyCustomDamage} 每笔自己打的伤害都会刷新基线；
     * 加血（drop &lt; 0）一律放过。副作用是<b>本模组自己的改血工具（虚空剑）在本体身上
     * 也会被压到 20/tick</b> —— 这正是限伤的定义。这里对任何来源都不开口子，
     * 连 {@code BYPASSES_INVULNERABILITY}（/kill、出界）也照样按 20 结算。</p>
     */
    private void guardHealthCap() {
        if (this.isRemoved()) return;

        float now = this.getHealth();
        if (this.lastSanctionedHealth < 0.0F) {          // 首 tick / 刚读盘：先立基线
            this.lastSanctionedHealth = now;
            return;
        }

        float before = this.lastSanctionedHealth;

        // 反方向越权：一次写入把真实血量抬到上限之上。伪装映射（存储 = 100 - 真实）让
        // "写负数"正好落进这一格 —— 不顶回去的话，一次扫描写入就能把本体变成几百万血的肉山。
        // 合规路径写不出这种值：setHealth 自己会夹到 max，所以不会误伤。
        float max = this.getMaxHealth();
        if (now > max) {
            this.setHealth(max);
            this.lastSanctionedHealth = max;
            logOutOfBand(before, now, "被抬到上限之上", max);
            return;
        }

        if (before - now <= MAX_DAMAGE_PER_HIT) {        // 正常范围（含加血）
            this.lastSanctionedHealth = now;
            return;
        }

        float corrected = Math.max(before - MAX_DAMAGE_PER_HIT, 0.0F);
        this.setHealth(corrected);
        this.lastSanctionedHealth = corrected;
        logOutOfBand(before, now, "掉血超过单次限伤", corrected);
    }

    /** 越权改血只报一次，避免刷屏。 */
    private void logOutOfBand(float before, float written, String what, float corrected) {
        if (this.loggedOutOfBandDamage) return;
        this.loggedOutOfBandDamage = true;
        HallMod.LOGGER.warn("[Collapsar] 检测到绕过 hurt() 的越权改血（{}）：{} → {}，已顶回 {}。"
                        + "本行只报一次，兜底此后每 tick 继续生效。",
                what, before, written, corrected);
    }

    // ══════════════════════════════════════════════════════════════
    // Boss 血条
    // ══════════════════════════════════════════════════════════════

    /** 血量比例变了才发包（改血走 VitalProbe，绕过了 setHealth，所以只能轮询）。 */
    private void tickBossEvent() {
        float max = this.getMaxHealth();
        float progress = max <= 0.0F ? 0.0F : Mth.clamp(this.getHealth() / max, 0.0F, 1.0F);
        if (progress != this.lastBossProgress) {
            this.lastBossProgress = progress;
            this.bossEvent.setProgress(progress);
        }
    }

    /** 玩家进入追踪范围 → 加入这条 boss 血条的观众。 */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    /** 玩家离开追踪范围 → 移出观众，血条随之消失。 */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /**
     * 飞行：永远想去"目标上方 HOVER_ABOVE_TARGET 格"，并且不低于地面 MIN_GROUND_CLEARANCE 格。
     * <p>没有目标时就停在原地保持高度，不做漫游 —— 使徒不需要随机游荡。
     */
    private void tickFlight() {
        // 地面高度每 10 tick 重算一次并缓存：高度图查询要碰区块，每 tick 都做没必要
        if (--groundHeightCooldown <= 0) {
            groundHeightCooldown = GROUND_HEIGHT_INTERVAL;
            cachedGroundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    this.getBlockX(), this.getBlockZ());
        }
        double minY = cachedGroundY + MIN_GROUND_CLEARANCE;

        LivingEntity target = this.getTarget();
        // 尾杀期间原地悬停：坍缩中的本体不再追击，desired 取当前位置 ⇒ 走进下面的
        // 死区分支并 halt()，只保留"不低于地面"那条下限。
        Vec3 desired = (finisherTick > 0)
                ? this.position()
                : (target != null && target.isAlive())
                        ? new Vec3(target.getX(), target.getY() + HOVER_ABOVE_TARGET, target.getZ())
                        : this.position();

        if (desired.y < minY) {
            desired = new Vec3(desired.x, minY + 1.0D, desired.z);
        }

        if (desired.distanceToSqr(this.position()) > HOVER_DEADZONE * HOVER_DEADZONE) {
            this.hoverMoveControl.setWantedPosition(desired.x, desired.y, desired.z, FLY_SPEED);
        } else {
            // 到位：停止加力，靠 travel() 里的阻力自然停住。
            // 以前这里什么都不做，MoveControl 会一直朝"已经到达的点"叠加加速度，
            // 于是悬停点附近出现来回过冲的抖动。
            this.hoverMoveControl.halt();
        }

        // 朝向：唯一写入者（见 registerGoals 里关于 FaceTargetGoal 的说明）
        updateFacing(target);

        // 无条件停掉路径导航：AbstractHallEntity#tick 在目标超出贴身距离时
        // 会每 tick 调一次 navigation.moveTo(target)，而那条路径由 FlyingPathNavigation
        // 自己驱动 MoveControl —— 与上面的悬停指令抢控制权。悬停单位不需要地面寻路。
        this.getNavigation().stop();
    }

    /**
     * 慢速转向。
     *
     * <h3>为什么必须在这里做，而且必须带死区</h3>
     * GeckoLib 的模型整体是按实体 {@code yRot} 旋转的（不是 yHeadRot/yBodyRot）。
     * 之前这段逻辑写在 {@code HoverMoveControl} 里，用 {@code atan2(dz, dx)} 求朝向 ——
     * 悬停到位后 dx/dz 只剩噪声量级，{@code atan2} 的结果每 tick 都在乱跳，
     * 渲染出来就是"站在原地莫名其妙乱转头"。现在：
     * <ul>
     *   <li>只在<b>有目标</b>时朝目标转；没目标时按<b>实际速度</b>方向转，且在速度足够大时才转；</li>
     *   <li>水平距离小于 {@link #FACING_DEADZONE} 格时不转（噪声全被挡在这里）；</li>
     *   <li>转向速度限死在 {@link #TURN_SPEED} 度/tick，且用 wrapDegrees 走最短弧，
     *       不会在 ±180° 处抽一下。</li>
     * </ul>
     */
    private void updateFacing(LivingEntity target) {
        Float desiredYaw = null;

        if (target != null && target.isAlive()) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            if (dx * dx + dz * dz >= FACING_DEADZONE * FACING_DEADZONE) {
                desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            }
        } else {
            Vec3 velocity = this.getDeltaMovement();
            if (velocity.horizontalDistanceSqr() >= 1.0E-3D) {
                desiredYaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0 / Math.PI)) - 90.0F;
            }
        }

        if (desiredYaw == null) return;

        float current = this.getYRot();
        float step = Mth.clamp(Mth.wrapDegrees(desiredYaw - current), -TURN_SPEED, TURN_SPEED);
        float next = current + step;
        this.setYRot(next);
        // 头 / 身跟着走，避免任何读到它们的逻辑拿到上一帧的旧值
        this.yHeadRot = next;
        this.yBodyRot = next;
    }

    // ══════════════════════════════════════════════════════════════
    // 技能调度
    // ══════════════════════════════════════════════════════════════

    private void serverTickSkills() {
        // 黑洞在世时的低频嗡鸣：放在 AI 守卫之前，这样"黑洞还在、本体被冻结"时
        // 也照样有声音（否则玩家跑远再回来会听到嗡鸣断一拍）
        if (blackHoleHumTicks > 0) {
            if (blackHoleHumTicks % BH_HUM_INTERVAL == 0 && anchor != null) {
                ApostleSounds.blackHoleHum(this.level(), anchor, blackHoleHumTicks);
            }
            blackHoleHumTicks--;
        }

        // 超出模拟距离时基类会把 AI 关掉（stopAiAtDist）。这时既不索敌也不该放技能 ——
        // 否则没人看的地方会一直空放黑洞与 20 连斩，白烧 CPU。
        if (this.isNoAi()) return;

        // 尾杀优先于一切：血量过线就进入尾杀，15 秒内由它接管本 tick，
        // 黑洞与 20 连斩都不再起手（进行中的那一个也会在 startFinisher 里被放弃）。
        if (tickFinisher()) return;

        if (blackHoleCooldown > 0) blackHoleCooldown--;
        if (slashCooldown > 0) slashCooldown--;

        // 两个技能各自推进 —— 这里<b>没有</b>"同一时间只能放一个"的公共间隔了。
        // 于是"黑洞前摇中"与"20 连斩进行中"可以完全重叠：前者在目标脚下蓄能，
        // 后者一边劈一边追，威胁是叠乘而不是相加。
        tickBlackHoleChannel();
        tickSlashChannel();

        // 起手判定：只看各自冷却 + 自己是否已在施法，互不阻塞
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.distanceToSqr(target) > CAST_RANGE * CAST_RANGE) return;

        if (blackHoleCooldown <= 0 && windupTicks <= 0 && blackHoleDelay <= 0) {
            startBlackHoleCast();
        }
        if (slashCooldown <= 0 && slashesLeft <= 0) {
            startSlashSequence();
        }
    }

    /** 黑洞技能自己的时间轴：前摇 → 冲击波 → 黑洞落地。 */
    private void tickBlackHoleChannel() {
        if (windupTicks > 0) {
            tickBlackHoleWindup();
            return;
        }
        if (blackHoleDelay > 0 && --blackHoleDelay == 0) {
            spawnBlackHole();
        }
    }

    /** 斩击技能自己的时间轴：20 刀序列。 */
    private void tickSlashChannel() {
        if (slashesLeft > 0) {
            tickSlashSequence();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 技能三（尾杀）：坍缩
    // ══════════════════════════════════════════════════════════════

    /**
     * 尾杀时间轴。返回 {@code true} 表示本 tick 由尾杀接管，普通技能不要再起手。
     *
     * <pre>
     *   0s ───────────────── 5s ────────────────────────── 15s
     *   冲击波风暴               巨型黑洞
     *   每 2 tick 一发           每 tick 改血 20（VitalProbe）
     *   共 50 发                存活到 15s，与尾杀同时结束
     * </pre>
     *
     * <p>冲击波的半径/速度/寿命直接复用黑洞技能那三个常量
     * （{@link #BH_SHOCKWAVE_RADIUS}/{@link #BH_SHOCKWAVE_SPEED}/{@link #BH_SHOCKWAVE_LIFE}），
     * 以后调整黑洞技能的手感，尾杀会跟着一起变，不会出现两套手感。</p>
     */
    private boolean tickFinisher() {
        if (finisherTick <= 0) {
            if (finisherUsed || !finisherReady()) return false;
            startFinisher();
        }

        int elapsed = FINISHER_DURATION - finisherTick;

        if (elapsed < FINISHER_STORM_TICKS) {
            // ── 第一阶段：冲击波风暴 ──
            if (elapsed % FINISHER_STORM_INTERVAL == 0) {
                spawnFinisherShockwave();
            }
            // 音效单独降频（每 10 tick 一记），别让 50 发冲击波变成 50 次广播
            if (elapsed % FINISHER_STORM_SOUND_INTERVAL == 0) {
                ApostleSounds.finisherStormPulse(this.level(), this.position(),
                        elapsed / (float) FINISHER_STORM_TICKS);
            }
        } else if (!finisherHoleSpawned) {
            // ── 第二阶段：第 5 秒整开出巨型黑洞 ──
            spawnFinisherBlackHole();
        }

        if (--finisherTick <= 0) {
            finisherTick = 0;
            finisherHoleSpawned = false;
            ApostleSounds.finisherEnd(this.level(), this.position());

            // 尾杀期间赊下的致命伤在这里结算：15 秒一定播完，人再死。
            // 优先用被拦下那次 die() 的来源，其次用 lastDamageSource，最后才是 genericKill ——
            // 击杀归属/掉落尽量还给最后一击的玩家。
            if (finisherPendingDeath) {
                finisherPendingDeath = false;
                DamageSource source = finisherPendingSource != null
                        ? finisherPendingSource
                        : (this.getLastDamageSource() != null
                                ? this.getLastDamageSource()
                                : this.damageSources().genericKill());
                finisherPendingSource = null;
                this.setHealth(0.0F);
                this.die(source);
            }
        }
        return true;
    }

    private boolean finisherReady() {
        float max = this.getMaxHealth();
        return max > 0.0F && this.getHealth() / max <= FINISHER_TRIGGER_RATIO;
    }

    /**
     * 起手：放弃正在进行的黑洞前摇与连斩，锁住尾杀标志。
     * <p>{@code finisherUsed} 一旦置位就存盘 —— 重载区块、退出重进都不会再触发第二次，
     * 一场战斗只有一次尾杀。</p>
     */
    private void startFinisher() {
        finisherUsed = true;
        finisherTick = FINISHER_DURATION;
        finisherHoleSpawned = false;
        finisherPendingDeath = false;
        finisherPendingSource = null;
        // 放弃进行中的普通技能：尾杀期间只走自己的时间轴
        windupTicks = 0;
        blackHoleDelay = 0;
        blackHoleHumTicks = 0;
        slashesLeft = 0;
        slashTimer = 0;
        slashIndex = 0;
        anchor = null;

        ApostleSounds.finisherStart(this.level(), this.position());
    }

    /** 风暴的一发：半径 20 的冲击波，以本体为圆心（"本体在坍缩"的视觉表达）。 */
    private void spawnFinisherShockwave() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        ShockwaveEntity.spawn(serverLevel, this.position(),
                BH_SHOCKWAVE_SPEED, BH_SHOCKWAVE_RADIUS, BH_SHOCKWAVE_LIFE, 1.0F);
    }

    /**
     * 巨型黑洞：目标位置落地，每 tick 20 点改血，存活到 15 秒。
     *
     * <p>伤害由 {@code BlackHolePhysicsHandler} 施加（黑洞实体本身不含伤害逻辑），
     * 走 VitalProbe 改血，所以护甲/抗性/无敌帧都不参与 —— 站在杀伤半径里就是每 tick 20，
     * 唯一的解法是在 10 秒内跑出 {@link #FINISHER_BH_KILL_RADIUS} 格。</p>
     *
     * <p>施法者自己不会被打：物理处理器的筛选会跳过 hall 感染生物（也就是本体）。</p>
     */
    private void spawnFinisherBlackHole() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 center = anchorForTarget();
        BlackHoleEntity.spawnSkill(serverLevel, center,
                FINISHER_BH_LIFETIME,
                FINISHER_BH_RADIUS,
                FINISHER_BH_GRAVITY_RADIUS,
                FINISHER_BH_KILL_RADIUS,
                FINISHER_BH_DAMAGE_PER_TICK,
                FINISHER_BH_LIFETIME);

        finisherHoleSpawned = true;
        anchor = center;
        blackHoleHumTicks = FINISHER_BH_LIFETIME;   // 嗡鸣跟着巨型黑洞走
        ApostleSounds.finisherCollapse(serverLevel, center);
    }

    // ── 技能一：坍缩黑洞 ──

    private void startBlackHoleCast() {
        windupTicks = BH_WINDUP_TICKS;
        anchor = anchorForTarget();
        blackHoleCooldown = BLACK_HOLE_COOLDOWN;
        ApostleSounds.blackHoleCastStart(this.level(), anchor);
    }

    /**
     * 技能锚点：目标身上。
     * <p>黑洞要"释放在目标位置"，末地烛前摇粒子也在目标位置，所以两者用同一个锚点 ——
     * 玩家看到脚下在充能，就知道接下来那里会开一个洞。</p>
     */
    private Vec3 anchorForTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            return target.getBoundingBox().getCenter();
        }
        return this.position();
    }

    /**
     * 前摇：末地烛粒子聚在目标身上 + 每 10 tick 一次蓄力音；
     * 结束时先在锚点放冲击波，再排队生成黑洞。
     */
    private void tickBlackHoleWindup() {
        // 锚点跟随目标（BH_TRACK_TARGET）：粒子与最终落点都在目标身上
        if (BH_TRACK_TARGET) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                anchor = target.getBoundingBox().getCenter();
            }
        }
        if (anchor == null) anchor = this.position();

        if (this.level() instanceof ServerLevel serverLevel) {
            // 一次 sendParticles 让原版在客户端展开 count 个粒子 ——
            // 千万不要写 for 循环逐粒子 sendParticles（那是每粒子一个包）
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    anchor.x, anchor.y + 0.2D, anchor.z,
                    BH_WINDUP_PARTICLES,
                    1.0D, 1.0D, 1.0D,
                    0.02D);
        }

        int elapsed = BH_WINDUP_TICKS - windupTicks;
        if (elapsed % BH_CHARGE_SOUND_INTERVAL == 0) {
            ApostleSounds.blackHoleCharge(this.level(), anchor,
                    elapsed / (float) BH_WINDUP_TICKS);
        }

        if (--windupTicks <= 0) {
            // 锚点在这里冻结：黑洞就落在这 3 秒前摇结束时目标所在的位置
            spawnShockwave();
            blackHoleDelay = BH_SPAWN_DELAY;
        }
    }

    /** 冲击波：纯视觉，半径 20 格、扩散极快 —— 它只是黑洞的前奏。 */
    private void spawnShockwave() {
        if (!(this.level() instanceof ServerLevel serverLevel) || anchor == null) return;
        ShockwaveEntity.spawn(serverLevel, anchor,
                BH_SHOCKWAVE_SPEED, BH_SHOCKWAVE_RADIUS, BH_SHOCKWAVE_LIFE, 1.0F);
        ApostleSounds.blackHoleShockwave(serverLevel, anchor);
    }

    /** 黑洞实体本身不含伤害逻辑：伤害由 BlackHolePhysicsHandler 按这里的参数施加。 */
    private void spawnBlackHole() {
        if (!(this.level() instanceof ServerLevel serverLevel) || anchor == null) return;
        BlackHoleEntity.spawnSkill(serverLevel, anchor,
                BH_LIFETIME, BH_RADIUS, BH_GRAVITY_RADIUS, BH_KILL_RADIUS,
                BH_DAMAGE_PER_TICK, BH_DAMAGE_TICKS);
        ApostleSounds.blackHoleOpen(serverLevel, anchor);
        blackHoleHumTicks = BH_LIFETIME;    // 嗡鸣跟着黑洞寿命走
    }

    // ── 技能二：斩击风暴 ──

    private void startSlashSequence() {
        slashesLeft = SLASH_COUNT;
        slashIndex = 0;
        slashTimer = 0;                            // 第一刀立刻出，观感上"起手就有"
        slashCooldown = SLASH_COOLDOWN;
        ApostleSounds.slashCastStart(this.level(), this.position());
    }

    private void tickSlashSequence() {
        LivingEntity target = this.getTarget();

        // 目标死了/丢了就中断序列，不浪费剩余刀数
        if (target == null || !target.isAlive()) {
            slashesLeft = 0;
            return;
        }

        if (slashTimer > 0) {
            slashTimer--;
            return;
        }

        performSlash(target);
        slashIndex++;
        slashesLeft--;

        if (slashesLeft <= 0) {
            slashTimer = 0;
            ApostleSounds.slashFinish(this.level(), target.getBoundingBox().getCenter());
        } else {
            slashTimer = SLASH_INTERVAL;
        }
    }

    /**
     * 单次斩击：生成一道使徒样式剑气 + 按目标最大生命值 5% 改血。
     *
     * <p>伤害按"落点球"结算而不是只打当前目标：刀长 32 格，只对 {@code getTarget()}
     * 生效的话，画面上剑气穿过旁边一群怪却只有远处那一个掉血，命中感非常差。
     * 现在落点 {@value #SLASH_HIT_RADIUS} 格内的每个非感染生物都会各挨一刀。</p>
     */
    private void performSlash(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 落点：目标碰撞箱中心 + 少量随机偏移
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 offset = new Vec3(
                (this.random.nextDouble() - 0.5D) * 2.0D * SLASH_SPREAD,
                (this.random.nextDouble() - 0.5D) * SLASH_SPREAD,
                (this.random.nextDouble() - 0.5D) * 2.0D * SLASH_SPREAD);
        Vec3 hitPos = center.add(offset);

        // 视觉：细长剑气（高 32 格 / 底面半径 1 格），白色外层 + 黑色内芯 outline
        SwordAuraEntity.spawnApostleSlash(serverLevel, hitPos,
                SLASH_SCALE, SLASH_HEIGHT, SLASH_RADIUS,
                SLASH_MAX_AGE, SLASH_SHRINK_SPEED);

        // 伤害：落点球内的每个非感染生物，各按自身最大生命 5% 改血
        // （走 VitalProbe 改血；没装 VitalProbe 时 BCCoreCompat 自动回退原版 hurt）
        AABB hitBox = new AABB(hitPos, hitPos).inflate(SLASH_HIT_RADIUS);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e.isAlive() && !isHallInfected(e));
        for (LivingEntity victim : victims) {
            BCCoreCompat.damagePercentOfMax(victim, SLASH_DAMAGE_PERCENT, skillDamageSource());
            BCCoreCompat.damage(victim, 10, skillDamageSource());
        }
        // 兜底：目标万一不在落点球里（被传送、卡进方块），也必须吃到这一刀
        if (target.isAlive() && !victims.contains(target)) {
            BCCoreCompat.damagePercentOfMax(target, SLASH_DAMAGE_PERCENT, skillDamageSource());
        }

        ApostleSounds.slashHit(serverLevel, hitPos, slashIndex);
    }

    /** 使徒同类判定：哪些生物不该被自己的技能打到。 */
    private static boolean isHallInfected(LivingEntity entity) {
        if (!(entity instanceof IInfectedEntity infected)) return false;
        ResourceLocation type = infected.getInfectionType();
        return type != null
                && HallMod.MODID.equals(type.getNamespace())
                && "hall".equals(type.getPath());
    }

    /** 技能伤害源：间接魔法，归属本体 —— 与原版唤魔者尖牙一致。 */
    private DamageSource skillDamageSource() {
        return this.damageSources().indirectMagic(this, this);
    }

    // ══════════════════════════════════════════════════════════════
    // 飞行移动控制
    // ══════════════════════════════════════════════════════════════

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (!this.isEffectiveAi()) {
            super.travel(travelVector);
            return;
        }
        this.moveRelative(0.02F, travelVector);
        this.move(MoverType.SELF, this.getDeltaMovement());
        // 阻力只在这里施加一次。之前 MoveControl 的 WAIT 分支里也乘了一次 0.8，
        // 两个 0.9/0.8 叠在一起，起停曲线就变得又急又不可预期。
        this.setDeltaMovement(this.getDeltaMovement().scale(FLY_DRAG));
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.core.BlockPos pos) {
        // 飞行单位不吃摔落伤害
    }

    /**
     * 悬停移动控制：朝目标点匀速加力并限速。
     *
     * <p><b>这里不写朝向、也不写阻力</b>：</p>
     * <ul>
     *   <li>朝向交给 {@code CollapsarEntity#updateFacing}。以前在这里用
     *       {@code atan2(dz, dx)} 设 yRot，悬停到位后 dx/dz 只剩噪声，
     *       结果整只模型在原地乱转；</li>
     *   <li>阻力交给 {@code travel()}，只保留一处衰减，加速度才有确定的手感。</li>
     * </ul>
     *
     * <p>不做 {@code canReach} 可达性预判是有意的：使徒必须追人，而"目标点在墙后面"
     * 时的可达性预判会让它原地发呆。飞行单位蹭到方块也无所谓，下一 tick 会自己纠正。</p>
     */
    private static class HoverMoveControl extends MoveControl {
        private final CollapsarEntity mob;

        HoverMoveControl(CollapsarEntity mob) {
            super(mob);
            this.mob = mob;
        }

        /**
         * 停止加力。
         * <p>{@code operation} 是 {@code MoveControl} 的 protected 字段，外部（含本实体类）
         * 直接读不到，所以在这里包一个方法出来 —— 比在 tickFlight 里做类型转换干净。</p>
         */
        void halt() {
            this.operation = Operation.WAIT;
        }

        @Override
        public void tick() {
            if (this.operation != Operation.MOVE_TO) {
                // 没指令：什么都不做，让 travel() 的阻力把它停下来
                return;
            }

            double dx = this.wantedX - this.mob.getX();
            double dy = this.wantedY - this.mob.getY();
            double dz = this.wantedZ - this.mob.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;

            if (distSqr < 2.5000003E-7D) {
                this.operation = Operation.WAIT;
                return;
            }

            double dist = Math.sqrt(distSqr);
            Vec3 dir = new Vec3(dx / dist, dy / dist, dz / dist);

            double maxSpeed = Math.max(0.01D, this.speedModifier)
                    * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
            Vec3 delta = this.mob.getDeltaMovement().add(dir.scale(FLY_ACCEL));
            double speed = delta.length();
            if (speed > maxSpeed) {
                delta = delta.scale(maxSpeed / speed);
            }
            this.mob.setDeltaMovement(delta);
        }
    }

    @Override
    public void remove(RemovalReason removalReason) {
        // 只在真死之后允许移除（原设计意图：活着时吞掉所有 remove 调用）。
        // 这里必须走 getHealth()：存储里那份是伪装值，直接读 DATA_HEALTH 判断的
        // 是“真实血量 ≥ 100”，语义整个反过来。
        if (this.getHealth() <= 0.0F) {
            super.remove(removalReason);
        }
    }
}
