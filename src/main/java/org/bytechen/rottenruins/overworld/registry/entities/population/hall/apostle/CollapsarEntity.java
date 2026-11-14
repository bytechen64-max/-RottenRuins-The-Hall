package org.bytechen.rottenruins.overworld.registry.entities.population.hall.apostle;

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
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.compat.BCCoreCompat;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.BlackHoleEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.ShockwaveEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.api.infection.IInfectedEntity;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.function.Consumer;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

/**
 * 坍缩使徒（Collapsar）—— 只会飞、不会走，悬在目标上方，靠「坍缩黑洞」（前摇 60 tick → 冲击波 → 黑洞 60 tick）
 * 与「斩击风暴」（5 秒 20 刀，每刀按目标最大生命的 5%）打人，只有 idle 动画。伤害全走 {@link BCCoreCompat} 直接写血量存储：
 * 无视无敌帧 / 护甲 / 抗性，算到 ≤0 补一次终结击。黑洞的伤害逻辑不在实体里（见 BlackHolePhysicsHandler）。
 */
public class CollapsarEntity extends BaseApostleEntity {

    private static final double MAX_HEALTH = 800.0D;
    private static final double ARMOR = 100.0D;
    /** 位移速度（格/tick），也是飞行速度上限：0.16 ≈ 3.2 格/秒。必须与构造函数里的 {@code setWalkSpeed()} 保持一致。 */
    private static final double MOVEMENT_SPEED = 0.16D;
    private static final double KNOCKBACK_RESISTANCE = 1.0D;
    private static final double FOLLOW_RANGE = 64.0D;

    private static final double HOVER_ABOVE_TARGET = 9.0D;
    private static final double MIN_GROUND_CLEARANCE = 5.0D;
    private static final double FLY_SPEED = 1.0D;
    /** 到位判定（格）：进了这个圈就不再加力，避免在悬停点附近过冲抖动 */
    private static final double HOVER_DEADZONE = 0.5D;
    /** 每 tick 叠加的加速度（格/tick²）：与 FLY_DRAG 一起决定巡航速度 {@code 0.03/(1-0.80) = 0.15} 格/tick ≈ 3 格/秒。 */
    private static final double FLY_ACCEL = 0.03D;
    /** 每 tick 保留的速度比例（阻力）。0.80 的刹车距离 ≈ 0.6 格，正好落在 {@link #HOVER_DEADZONE} 附近，到位后不会过冲摆动。 */
    private static final double FLY_DRAG = 0.80D;
    /** 转向速度（度/tick）：4 ≈ 80°/秒，重型单位该有的迟钝感 */
    private static final float TURN_SPEED = 4.0F;
    /** 转向死区（格）：水平距离小于这个值就保持当前朝向，不重新求解 */
    private static final double FACING_DEADZONE = 0.75D;
    private static final int GROUND_HEIGHT_INTERVAL = 10;

    /** 黑洞技能冷却（tick）：10s。两个技能互相不阻塞 —— 各冷却只决定"这个技能多久能再用一次"。 */
    private static final int BLACK_HOLE_COOLDOWN = 200;
    private static final int SLASH_COOLDOWN = 120;
    private static final double CAST_RANGE = 40.0D;

    private static final int BH_WINDUP_TICKS = 60;
    /** 前摇期间锚点是否跟随目标：{@code true} 时黑洞与末地烛粒子都落在目标身上；{@code false} 则在起手瞬间锁定地面范围。 */
    private static final boolean BH_TRACK_TARGET = true;
    private static final int BH_WINDUP_PARTICLES = 28;
    private static final float BH_SHOCKWAVE_RADIUS = 20.0F;
    /** 冲击波扩散速度（格/秒）：半径按 {@code age / 20 * speed} 增长，80 意味着 5 tick 就铺满 20 格。 */
    private static final float BH_SHOCKWAVE_SPEED = 80.0F;
    private static final int BH_SHOCKWAVE_LIFE = 12;
    private static final int BH_SPAWN_DELAY = 4;
    private static final int BH_LIFETIME = 60;
    private static final int BH_HUM_INTERVAL = 20;
    private static final int BH_CHARGE_SOUND_INTERVAL = 10;
    /** 史瓦西半径（格）：透镜球半径是 {@code 6·Rs} ≈ 1.44 格；引力与事件视界半径没有跟着缩（它们决定手感而非观感）。 */
    private static final float BH_RADIUS = 0.24F;
    private static final float BH_GRAVITY_RADIUS = 24.0F;
    /** 事件视界半径（格）：进入后每 tick 被改血 */
    private static final float BH_KILL_RADIUS = 6.0F;
    private static final float BH_DAMAGE_PER_TICK = 3.0F;
    private static final int BH_DAMAGE_TICKS = 60;

    private static final int SLASH_COUNT = 20;
    /** 两次斩击的间隔（tick）：20 次 × 5 = 100 tick = 5s */
    private static final int SLASH_INTERVAL = 5;
    private static final float SLASH_DAMAGE_PERCENT = 5.0F;
    /** 剑气总长度（格）：初版 8 格看着"够不着人"，这里放大 4 倍到 32 */
    private static final float SLASH_HEIGHT = 32.0F;
    private static final float SLASH_RADIUS = 1.0F;
    private static final int SLASH_MAX_AGE = 14;
    /** 剑气收缩速度（>1 收得更快，撑不住 14 tick 会变成一条线） */
    private static final float SLASH_SHRINK_SPEED = 1.6F;
    private static final float SLASH_SCALE = 1.0F;
    private static final double SLASH_SPREAD = 1.6D;
    /** 斩击伤害判定半径（格）：刀长 32 格但伤害只按落点球结算；只打 getTarget() 的话命中感非常差。 */
    private static final double SLASH_HIT_RADIUS = 3.0D;

    /** 尾杀触发血量比例：血量 ≤ 最大生命的这个比例时开始，一场只放一次（800 血 ⇒ 200 血以下进入尾杀）。 */
    private static final float FINISHER_TRIGGER_RATIO = 0.25F;
    private static final int FINISHER_DURATION = 300;
    private static final int FINISHER_STORM_TICKS = 100;
    private static final int FINISHER_STORM_INTERVAL = 2;
    /** 风暴里音效单独按 10 tick 走骨架：冲击波每 2 tick 一个，音效跟着走会每秒 10 次广播、糊成一片还白发 50 个包。 */
    private static final int FINISHER_STORM_SOUND_INTERVAL = 10;

    /** 巨型黑洞尺寸：普通黑洞（{@link #BH_RADIUS} 0.24 / 引力 24 / 杀伤 6）的 5 倍 —— 目标必须在 10 秒内跑出 24 格。 */
    private static final float FINISHER_BH_RADIUS = 1.2F;
    private static final float FINISHER_BH_GRAVITY_RADIUS = 48.0F;
    private static final float FINISHER_BH_KILL_RADIUS = 24.0F;
    private static final float FINISHER_BH_DAMAGE_PER_TICK = 20.0F;
    /** 巨型黑洞存活时间（tick）= 尾杀剩余时长：5 秒处生成、15 秒处与尾杀同时结束；伤害窗口与它同值。 */
    private static final int FINISHER_BH_LIFETIME = FINISHER_DURATION - FINISHER_STORM_TICKS;

    private int blackHoleCooldown = 0;
    private int slashCooldown = 0;

    /** >0 表示正在黑洞技能的前摇 */
    private int windupTicks = 0;
    /** >0 表示冲击波已放、正等黑洞落地 */
    private int blackHoleDelay = 0;
    /** 黑洞技能的召唤锚点（前摇期间跟随本体，结束时冻结） */
    private Vec3 anchor = null;

    private int slashesLeft = 0;
    private int slashTimer = 0;
    private int slashIndex = 0;
    /** >0 表示黑洞还在世，用来驱动低频嗡鸣 */
    private int blackHoleHumTicks = 0;
    private double cachedGroundY = 0.0D;
    private int groundHeightCooldown = GROUND_HEIGHT_INTERVAL;
    /** 自己的悬停移动控制器（用来在到位时喊停，见 HoverMoveControl#halt） */
    private final HoverMoveControl hoverMoveControl;

    /** 尾杀是否已经放过：一场只放一次（存盘，重载后不会重复触发）。 */
    private boolean finisherUsed = false;
    /** >0 表示尾杀进行中（剩余 tick，从 {@link #FINISHER_DURATION} 递减到 0）。 */
    private int finisherTick = 0;
    private boolean finisherHoleSpawned = false;
    /**
     * 尾杀期间"赊"下的致命伤：尾杀由本体 tick() 驱动，一旦 die() 时间轴立即断掉，所以这段时间只记账 —— 血量钉在 1，
     * 等 15 秒走完再一次性结算。
     */
    private boolean finisherPendingDeath = false;
    /** 尾杀期间赊下的那笔致命伤来自谁（结算时用来保住击杀归属）。 */
    private DamageSource finisherPendingSource = null;

    /** 上一次"我们认可"的血量基线，用于识破绕过 {@code hurt()} 的越权改血；-1 = 还没立过基线（首 tick / 刚读盘）。 */
    private float lastSanctionedHealth = -1.0F;
    private boolean loggedOutOfBandDamage = false;

    /** 原版 boss 血条（客户端拿到的是 {@code LerpingBossEvent}）；客户端由 CollapsarBossBar 取消原版绘制改用自己的 mask。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    /** 上次同步出去的血量比例：血量由 VitalProbe 直接写存储，只能每 tick 轮询比较后再发，避免每 tick 一个进度包。 */
    private float lastBossProgress = 1.0F;

    public CollapsarEntity(EntityType<? extends Monster> entityType, Level level,
                           Consumer<AbstractRottenEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.hoverMoveControl = new HoverMoveControl(this);
        this.moveControl = this.hoverMoveControl;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        // 只有 idle 动画：不声明 walk/run，避免 AbstractRottenEntity 去找不存在的动画
        this.setHasWalkAnim(false);
        // 悬停飞行的速度上限（必须与 MOVEMENT_SPEED 常量保持一致）
        this.setWalkSpeed((float) MOVEMENT_SPEED);
        this.setRunSpeed((float) MOVEMENT_SPEED);
        // 使徒不用近战钩子（BaseApostleEntity 已把 doHurtDistance 置 0）
        this.setDoHurtTime(20);
    }

    /** 资源绑定：geo / png / animation 三个文件名都是 collapsar。 */
    public static Consumer<AbstractRottenEntity> resources() {
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

    @Override
    protected void registerGoals() {
        // 索敌：64 格内看见就打（不需要视线 —— 它靠悬停俯视，隔着树叶也应该锁人）
        this.targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true)   // 王庭 vs 腐化：跨阵营索敌
                .range(FOLLOW_RANGE)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        // 这里刻意不加 FaceTargetGoal：它写的是 yHeadRot / yBodyRot / xRot，而 GeckoLib 只认实体 yRot，
        // 加了等于有个看不见的第二方在抢写旋转。朝向统一由 tickFlight() 里的 updateFacing() 负责（唯一写入者）。
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "idle_controller", 5, state -> {
            if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;
            state.setControllerSpeed(getIdleAnimSpeed());
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    /** 自定义血量。覆写 getHealth() 之后，原版 {@code LivingEntity.health} 字段不再参与本实体。 */
    private static final EntityDataAccessor<Float> DATA_HEALTH =
            SynchedEntityData.defineId(CollapsarEntity.class, EntityDataSerializers.FLOAT);

    /**
     * 血量存储的一次函数伪装：同步数据里躺着的是 {@code 100 - 真实血量}（斜率 -1，自逆），扫描器"写 0 弄死"的直觉正好反向。
     * 挡不住会反解 getHealth() 字节码的实现，真正的防线是 {@link #guardHealthCap()}。映射是<b>对合</b>的：读写方向搞反只会静默错，
     * 所以除 getHealth / setHealth / defineSynchedData 外不许再碰 DATA_HEALTH。
     */
    private static final float STORED_HEALTH_OFFSET = 100.0F;

    private static float toStoredHealth(float realHealth) {
        return STORED_HEALTH_OFFSET - realHealth;
    }

    private static float toRealHealth(float storedHealth) {
        return STORED_HEALTH_OFFSET - storedHealth;
    }

    /** 单次受击伤害上限：无论来源多猛（凋灵爆炸、上千点魔法伤害），单次最终伤害最多 20 —— 800 血至少挨 40 刀，不会被单次爆发秒掉。 */
    private static final float MAX_DAMAGE_PER_HIT = 20.0F;

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();                       // 基类的走路/动画同步数据，必须保留
        // 初值是“满血的存储形态”，不是满血本身 —— 800 血写进去的是 -700
        this.entityData.define(DATA_HEALTH, toStoredHealth((float) MAX_HEALTH));
    }

    /** 血量读自定义同步数据，并做一次逆变换还原成真实血量（原版 health 字段被架空）。 */
    @Override
    public float getHealth() {
        return toRealHealth(this.entityData.get(DATA_HEALTH));
    }

    /** 血量按真实值夹在 {@code [0, 最大生命]} 再变换成存储形态。构造期 getMaxHealth() 可能是 0，直接夹会把血夹死，故留兜底。 */
    @Override
    public void setHealth(float health) {
        float max = this.getMaxHealth();
        if (max <= 0.0F) max = (float) MAX_HEALTH;
        this.entityData.set(DATA_HEALTH, toStoredHealth(Mth.clamp(health, 0.0F, max)));
    }

    /**
     * 自定义受击结算：血量在同步数据里，而原版 {@code actuallyHurt} 扣的是 {@code LivingEntity.health} 字段，混着用会
     * "挨了打但血条不掉"或"血条空了却不死"。限伤夹在最后、护甲 / 抗性不参与；{@code LivingHurtEvent} / {@code LivingDamageEvent}
     * <b>必须</b>照常投递，否则友伤规则与外模组伤害数字会静默失效。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isFakeDying()) return false;                        // 假死期间免疫（基类能力）
        if (this.level().isClientSide() || amount <= 0.0F || this.isRemoved()) return false;
        if (this.isInvulnerableTo(source)) return false;

        // 先限伤，再进事件：让监听方看到的就是"这一刀最多 20"。这里不给 BYPASSES_INVULNERABILITY（/kill、出界）
        // 开后门 —— 限伤是本体唯一的减伤规则，开了口子就等于 op 一条命令绕过全部设计。
        float limited = Math.min(amount, MAX_DAMAGE_PER_HIT);

        // 假死（基类能力）：开启时致命一击改为进入假死。Collapsar 默认没开，但接线留着，否则打开后行为会静默变成"直接打死"。
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

        // 受击溅血（基类钩子）：本类完整接管了 hurt、不经过 AbstractRottenEntity#hurt，所以这里显式补一次。
        this.onHurtBlood(source, finalDamage);

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

    /** 血量存盘只保留标准键 {@code Health} 且写伪装值（满血 800 写进去是 -700），否则任何读 NBT 的工具都能看到真实血量。 */
    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putFloat("Health", toStoredHealth(this.getHealth()));
        nbt.putBoolean("HallFinisherUsed", this.finisherUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        // super 会把伪装值当血量读一遍（夹到 0），下一行就覆盖；关键是这一步必须在 super 之后，否则会被盖回去。
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

    /** 还原存档血量：新格式（标准键 {@code Health} 里的伪装值）→ 旧存档明文 {@code HallHealth} → 都没有就满血兜底。 */
    private float readSavedHealth(CompoundTag nbt) {
        if (nbt.contains("Health")) {
            return toRealHealth(nbt.getFloat("Health"));
        }
        if (nbt.contains("HallHealth")) {          // 旧存档：当年写的明文真实血量
            return nbt.getFloat("HallHealth");
        }
        return this.getMaxHealth();
    }

    @Override
    public void tick() {
        // 必须在 super.tick() 之前：原版 baseTick 会检查 isDeadOrDying() 并直接走 tickDeath()，
        // 而 VitalProbe 改血绕过 hurt / applyCustomDamage，"改血到 0"只能在这里顶回去（否则尾杀没播完就被判死）。
        guardHealthCap();
        guardFinisherHealth();

        super.tick();
        if (this.level().isClientSide()) return;

        tickBossEvent();
        tickFlight();
        serverTickSkills();
    }

    /** 尾杀期间的免死保护：把被改血写到 ≤0 的血量顶回 1，并记下这笔致命伤；尾杀结束后照常可削到 0 正常死亡。 */
    private void guardFinisherHealth() {
        if (finisherTick <= 0) return;
        if (this.getHealth() <= 0.0F) {
            this.setHealth(1.0F);
            this.lastSanctionedHealth = 1.0F;
            this.finisherPendingDeath = true;
        }
    }

    /**
     * <b>尾杀期间本体在定义上就还没死。</b>判死入口不止一个，有些还落在我们修完血量之后（baseTick 的 isDeadOrDying() →
     * tickDeath()、别家模组的 LivingTickEvent 回写 0、直接反射调 die()），所以只能把判定本身按下去；finisherTick 归零后恢复。
     */
    @Override
    public boolean isDeadOrDying() {
        return this.finisherTick <= 0 && super.isDeadOrDying();
    }

    /** 尾杀期间拦下真正的死亡（有人直接调 {@code die()}：反射、别家模组的处决逻辑），一律转成赊账，击杀归属不丢。 */
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
     * <b>限伤兜底</b>：把绕过 {@code hurt()} 的越权改血顶回去。限伤只在 hurt() 里生效，而反解 getHealth() 字节码直接写存储的
     * 实现一次就能把 800 血改到 0，所以防线只能是轮询不变量；对任何来源都不开口子，连 BYPASSES_INVULNERABILITY 也按 20 结算。
     */
    private void guardHealthCap() {
        if (this.isRemoved()) return;

        float now = this.getHealth();
        if (this.lastSanctionedHealth < 0.0F) {          // 首 tick / 刚读盘：先立基线
            this.lastSanctionedHealth = now;
            return;
        }

        float before = this.lastSanctionedHealth;

        // 反方向越权：伪装映射让"写负数"正好落进这一格 —— 不顶回去，一次扫描写入就能把本体变成几百万血的肉山。
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

    private void logOutOfBand(float before, float written, String what, float corrected) {
        if (this.loggedOutOfBandDamage) return;
        this.loggedOutOfBandDamage = true;
        HallMod.LOGGER.warn("[Collapsar] 检测到绕过 hurt() 的越权改血（{}）：{} → {}，已顶回 {}。"
                        + "本行只报一次，兜底此后每 tick 继续生效。",
                what, before, written, corrected);
    }

    /** 血量比例变了才发包（改血走 VitalProbe，绕过了 setHealth，所以只能轮询）。 */
    private void tickBossEvent() {
        float max = this.getMaxHealth();
        float progress = max <= 0.0F ? 0.0F : Mth.clamp(this.getHealth() / max, 0.0F, 1.0F);
        if (progress != this.lastBossProgress) {
            this.lastBossProgress = progress;
            this.bossEvent.setProgress(progress);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /** 飞行：永远想去"目标上方 HOVER_ABOVE_TARGET 格"且不低于地面 MIN_GROUND_CLEARANCE 格；没有目标就原地保持高度，不漫游。 */
    private void tickFlight() {
        // 地面高度每 10 tick 重算一次并缓存：高度图查询要碰区块，每 tick 都做没必要
        if (--groundHeightCooldown <= 0) {
            groundHeightCooldown = GROUND_HEIGHT_INTERVAL;
            cachedGroundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    this.getBlockX(), this.getBlockZ());
        }
        double minY = cachedGroundY + MIN_GROUND_CLEARANCE;

        LivingEntity target = this.getTarget();
        // 尾杀期间原地悬停：desired 取当前位置 ⇒ 走进下面的死区分支并 halt()，只保留"不低于地面"那条下限。
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
            // 到位就停止加力，靠 travel() 里的阻力自然停住：不 halt 的话 MoveControl 会一直朝已到达的点叠加加速度（过冲抖动）。
            this.hoverMoveControl.halt();
        }

        // 朝向：唯一写入者（见 registerGoals 里关于 FaceTargetGoal 的说明）
        updateFacing(target);

        // 无条件停掉路径导航：AbstractRottenEntity#tick 会每 tick 调 navigation.moveTo(target)，那条路径由
        // FlyingPathNavigation 自己驱动 MoveControl —— 会与上面的悬停指令抢控制权。悬停单位不需要地面寻路。
        this.getNavigation().stop();
    }

    /**
     * 慢速转向。GeckoLib 的模型整体按实体 {@code yRot} 旋转；以前在 HoverMoveControl 里用 atan2 求朝向，悬停到位后
     * dx/dz 只剩噪声，整个模型每 tick 乱转头。现在只在有目标时朝目标转，水平距离小于 {@link #FACING_DEADZONE} 格不转，
     * 转向速度限死 {@link #TURN_SPEED} 度/tick 且用 wrapDegrees 走最短弧。
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

    private void serverTickSkills() {
        // 黑洞在世时的低频嗡鸣：放在 AI 守卫之前，这样"黑洞还在、本体被冻结"时也照样有声音（否则玩家跑远再回来会断一拍）
        if (blackHoleHumTicks > 0) {
            if (blackHoleHumTicks % BH_HUM_INTERVAL == 0 && anchor != null) {
                ApostleSounds.blackHoleHum(this.level(), anchor, blackHoleHumTicks);
            }
            blackHoleHumTicks--;
        }

        // 超出模拟距离时基类会把 AI 关掉：这时既不索敌也不该放技能，否则没人看的地方会一直空放技能、白烧 CPU。
        if (this.isNoAi()) return;

        // 尾杀优先于一切：15 秒内由它接管本 tick，黑洞与 20 连斩都不再起手（进行中的会在 startFinisher 里被放弃）。
        if (tickFinisher()) return;

        if (blackHoleCooldown > 0) blackHoleCooldown--;
        if (slashCooldown > 0) slashCooldown--;

        // 两个技能各自推进，没有"同一时间只能放一个"的公共间隔：黑洞前摇与 20 连斩可以完全重叠，威胁是叠乘而不是相加。
        tickBlackHoleChannel();
        tickSlashChannel();

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

    private void tickSlashChannel() {
        if (slashesLeft > 0) {
            tickSlashSequence();
        }
    }

    /** 尾杀时间轴（0~5s 冲击波风暴每 2 tick 一发；5~15s 巨型黑洞每 tick 改血 20）；返回 true = 本 tick 由尾杀接管。 */
    private boolean tickFinisher() {
        if (finisherTick <= 0) {
            if (finisherUsed || !finisherReady()) return false;
            startFinisher();
        }

        int elapsed = FINISHER_DURATION - finisherTick;

        if (elapsed < FINISHER_STORM_TICKS) {
            if (elapsed % FINISHER_STORM_INTERVAL == 0) {
                spawnFinisherShockwave();
            }
            // 音效单独降频（每 10 tick 一记），别让 50 发冲击波变成 50 次广播
            if (elapsed % FINISHER_STORM_SOUND_INTERVAL == 0) {
                ApostleSounds.finisherStormPulse(this.level(), this.position(),
                        elapsed / (float) FINISHER_STORM_TICKS);
            }
        } else if (!finisherHoleSpawned) {
            spawnFinisherBlackHole();
        }

        if (--finisherTick <= 0) {
            finisherTick = 0;
            finisherHoleSpawned = false;
            ApostleSounds.finisherEnd(this.level(), this.position());

            // 赊下的致命伤在这里结算：优先用被拦下那次 die() 的来源，其次 lastDamageSource，最后 genericKill（保住击杀归属）。
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

    /** 起手：放弃进行中的黑洞前摇与连斩，锁住尾杀标志（{@code finisherUsed} 一旦置位就存盘，一场战斗只有一次尾杀）。 */
    private void startFinisher() {
        finisherUsed = true;
        finisherTick = FINISHER_DURATION;
        finisherHoleSpawned = false;
        finisherPendingDeath = false;
        finisherPendingSource = null;
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

    /** 巨型黑洞：目标位置落地，每 tick 20 点改血。伤害由 BlackHolePhysicsHandler 施加（实体本身不含伤害逻辑），护甲/无敌帧不参与。 */
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

    private void startBlackHoleCast() {
        windupTicks = BH_WINDUP_TICKS;
        anchor = anchorForTarget();
        blackHoleCooldown = BLACK_HOLE_COOLDOWN;
        ApostleSounds.blackHoleCastStart(this.level(), anchor);
    }

    /** 技能锚点：目标碰撞箱中心（黑洞与末地烛前摇粒子用同一个锚点，玩家看到脚下在充能就知道那里会开洞）。 */
    private Vec3 anchorForTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            return target.getBoundingBox().getCenter();
        }
        return this.position();
    }

    /** 前摇：末地烛粒子聚在目标身上 + 每 10 tick 一次蓄力音；结束时先在锚点放冲击波，再排队生成黑洞。 */
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
            // 一次 sendParticles 让原版在客户端展开 count 个粒子 —— 千万不要写 for 循环逐粒子发包。
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

    private void startSlashSequence() {
        slashesLeft = SLASH_COUNT;
        slashIndex = 0;
        slashTimer = 0;                            // 第一刀立刻出，观感上"起手就有"
        slashCooldown = SLASH_COOLDOWN;
        ApostleSounds.slashCastStart(this.level(), this.position());
    }

    private void tickSlashSequence() {
        LivingEntity target = this.getTarget();

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

    /** 单次斩击：一道使徒样式剑气 + 按目标最大生命值 5% 改血。伤害按"落点球"结算，只打 getTarget() 的话命中感非常差。 */
    private void performSlash(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 offset = new Vec3(
                (this.random.nextDouble() - 0.5D) * 2.0D * SLASH_SPREAD,
                (this.random.nextDouble() - 0.5D) * SLASH_SPREAD,
                (this.random.nextDouble() - 0.5D) * 2.0D * SLASH_SPREAD);
        Vec3 hitPos = center.add(offset);

        SwordAuraEntity.spawnApostleSlash(serverLevel, hitPos,
                SLASH_SCALE, SLASH_HEIGHT, SLASH_RADIUS,
                SLASH_MAX_AGE, SLASH_SHRINK_SPEED);

        // 伤害：落点球内的每个非感染生物各按自身最大生命 5% 改血（BCCoreCompat 负责改血，没装时自动回退原版 hurt）
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

    /** 使徒同类判定：哪些王庭生物不该被自己的技能打到（腐化系不算同族）。 */
    private static boolean isHallInfected(LivingEntity entity) {
        if (!(entity instanceof IInfectedEntity infected)) return false;
        return RottenFaction.HALL.matches(infected.getInfectionType());
    }

    /** 技能伤害源：间接魔法，归属本体 —— 与原版唤魔者尖牙一致。 */
    private DamageSource skillDamageSource() {
        return this.damageSources().indirectMagic(this, this);
    }

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
        // 阻力只在这里施加一次：之前 MoveControl 的 WAIT 分支也乘过一次，两层衰减叠在一起，起停曲线又急又不可预期。
        this.setDeltaMovement(this.getDeltaMovement().scale(FLY_DRAG));
    }

    @Override
    public void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.core.BlockPos pos) {
        // 飞行单位不吃摔落伤害
    }

    /**
     * 悬停移动控制：朝目标点匀速加力并限速。这里不写朝向（交给 updateFacing）也不写阻力（交给 travel()）。
     * 不做 {@code canReach} 预判是有意的："目标在墙后面"会让它原地发呆，飞行单位蹭到方块下一 tick 自己纠正。
     */
    private static class HoverMoveControl extends MoveControl {
        private final CollapsarEntity mob;

        HoverMoveControl(CollapsarEntity mob) {
            super(mob);
            this.mob = mob;
        }

        /** 停止加力：{@code operation} 是 MoveControl 的 protected 字段，包一个方法出来比在 tickFlight 里做类型转换干净。 */
        void halt() {
            this.operation = Operation.WAIT;
        }

        @Override
        public void tick() {
            if (this.operation != Operation.MOVE_TO) {
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
        // 只在真死之后允许移除（活着时吞掉所有 remove 调用）。这里必须走 getHealth()：存储里那份是伪装值，
        // 直接读 DATA_HEALTH 判断的是"真实血量 ≥ 100"，语义整个反过来。
        if (this.getHealth() <= 0.0F) {
            super.remove(removalReason);
        }
    }
}
