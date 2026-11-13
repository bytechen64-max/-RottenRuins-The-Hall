package org.bytechen.rottenruins.overworld.registry.entities.base;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.entity.IAutoRenderableEntity;
import org.bytechen.rottenruins.network.c2s.IKeyframeHandler;
import org.bytechen.rottenruins.network.s2c.BloodBurstPacket;
import org.bytechen.rottenruins.network.s2c.MeatBurstPacket;
import org.bytechen.rottenruins.utils.entity.EntityParticleUtils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.capability.threat.ThreatHelper;
import org.bytechen.rottenruins.overworld.registry.SoundEventRegistry;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.infcore.api.IKillCounter;
import org.bytechen.infcore.api.goal.ITargetFilter;
import org.bytechen.infcore.core.capability.CapabilityRegistry;
import org.bytechen.infcore.core.capability.KillCountCapability;
import org.bytechen.infcore.core.evolution.EvolutionManager;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public abstract class AbstractRottenEntity extends Monster implements IAutoRenderableEntity, IKeyframeHandler, IInfectedEntity, IKillCounter, ITargetFilter {


    /** 阵营：王庭 / 腐化。<b>必须是可覆写方法而不是实例字段</b>：{@link net.minecraft.world.entity.Mob} 构造器会在子类字段初始化之前调用 {@code registerGoals()} 进而调到这里，用字段会读到 null 并 NPE（表现为刷怪蛋放不出生物）。 */
    protected RottenFaction faction() { return RottenFaction.HALL; }

    public RottenFaction getFaction() { return faction(); }

    public ResourceLocation model, texture, animation;
    protected float idleAnimSpeed = 1.0f;
    protected float walkAnimSpeed = 1.0f;
    protected float runAnimSpeed = 1.0f;
    protected float dieAnimSpeed = 1.0f;

    private static final EntityDataAccessor<Boolean> IS_WALKING =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_WALK_ANIM_SYNCED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> WALK_SPEED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RUN_SPEED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WALK_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RUN_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> IDLE_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIE_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_FAKE_DYING =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FAKE_DEATH_DURATION =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURST_SCALE_DURATION =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BURST_SCALE_RATE =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BURST_MAX_SCALE =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BURST_SCALE_ENABLED =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BURST_TWITCH_AMPLITUDE =
            SynchedEntityData.defineId(AbstractRottenEntity.class, EntityDataSerializers.FLOAT);

    private int fakeDeathTimer = 0;
    /** 爆体总时长（tick）：30 = 1.5 秒。 */
    protected int fakeDeathDuration = 30;
    /** 爆体缩放窗口（tick）：与爆体总时长一致，全程抽搐。 */
    protected int burstScaleDuration = 30;
    /** 每秒脉冲次数：10 次/秒 × 1.5 秒 = 15 级，小而密。 */
    protected float burstScaleRate = 10.0f;
    /** 爆体触发概率（百分比）：默认 40；0 表示 100%（旧语义，用于"必定爆体"）。 */
    protected int fakeDeathChance = 40;
    /** 是否启用爆体：默认开启 —— 所有王庭生物都会爆体，使徒（BaseApostleEntity）在构造器里关闭。 */
    protected boolean fakeDeathEnabled = true;
    protected boolean hasWalkAnim = false;
    protected float walkSpeed = 0.18f;   // 兜底：接近玩家 0.25 的 0.7x，避免子类漏设时异常
    protected float runSpeed = 0.24f;    // 追击速度兜底（不是动画速度，单位见 MOVEMENT_SPEED）
    protected boolean stopAiAtDist = true;
    protected boolean defaultControllersEnabled = true;

    protected int doHurtTime = 20;
    public int doHurtCooldown = 0;
    protected float doHurtDistance = 3.0f;

    /** 追击时交给寻路的速度倍率（对应原版 {@code MeleeAttackGoal} 的 {@code speedModifier}），由子类在构造器里用 {@link #setChaseSpeed(float)} 声明。 */
    protected float chaseSpeed = 1.0F;

    /** 被玩家攻击后的"还手窗口"（tick）：窗口内该玩家无视威胁点数直接锁为攻击者 —— 威胁点数不足时王庭生物<b>不主动索敌</b>玩家，但被打过来<b>必须会还手</b>（主动索敌由 {@link #canTarget} 拦截，还手靠这个窗口放行）。 */
    private static final int RETALIATION_WINDOW_TICKS = 100;

    /** 最近攻击本实体的玩家（只用于还手窗口判定） */
    @Nullable
    private Player lastAttacker;

    private int retaliationTicks;

    /** 创造 / 旁观玩家一律不索敌也不还手（同原版 {@code NearestAttackableTargetGoal}）；{@code canAttackType} 挡不住同类玩家，必须自己判。 */
    public static boolean isIgnoredPlayer(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    /** 玩家贴脸到这个距离内时，即使威胁点数不够也会被主动索敌，避免王庭生物"装瞎"。 */
    protected static final double NEARBY_PLAYER_AGGRO_DISTANCE = 12.0D;

    /** 威胁点数索敌过滤器（作为 {@code InfectedTargetGoal} 的 filter）：玩家要威胁点数 ≥ {@link ThreatHelper#TARGET_THREAT_THRESHOLD} 且非创造/旁观；非玩家生物<b>不论威胁点数一律照常索敌</b>。 */
    @Override
    public boolean canTarget(LivingEntity owner, LivingEntity target) {
        if (!(target instanceof Player player)) return true;
        if (isIgnoredPlayer(player)) return false;
        return canAttackPlayer(player)
                || owner.distanceToSqr(player) <= NEARBY_PLAYER_AGGRO_DISTANCE * NEARBY_PLAYER_AGGRO_DISTANCE;
    }

    /** 最后一道闸：{@link #canTarget} 只挡得住 {@code InfectedTargetGoal} 这条索敌路径，其它直接写 {@code target} 的地方在这里被折成 {@code null}（创造/旁观玩家、威胁点数不足又不在还手窗口内的玩家）。 */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof Player player
                && !level().isClientSide
                && (isIgnoredPlayer(player) || !canAttackPlayer(player))) {
            // 放弃这个目标，同时清掉怒气计时，避免原版反复重试
            this.setLastHurtByMob(null);
            super.setTarget(null);
            return;
        }
        super.setTarget(target);
    }

    protected boolean isRetaliating() {
        return this.retaliationTicks > 0;
    }

    protected boolean isRetaliationTarget(Player player) {
        return this.retaliationTicks > 0 && this.lastAttacker == player;
    }

    /** 威胁点数达标 → 正常索敌；否则只在"还手窗口"内、且正是这名玩家 → 还手；贴脸距离内也算。 */
    protected boolean canAttackPlayer(Player player) {
        return ThreatHelper.getThreat(player) >= ThreatHelper.TARGET_THREAT_THRESHOLD
                || isRetaliationTarget(player)
                || this.distanceToSqr(player) <= NEARBY_PLAYER_AGGRO_DISTANCE * NEARBY_PLAYER_AGGRO_DISTANCE;
    }

    /** 范围攻击是否允许把 {@code target} 计入命中列表（给 {@code RangeAttackUtils} 的 {@code Builder#filter} 用）：<b>不能直接复用 {@link #canTarget}</b> —— 范围攻击是"无差别扫一片"，那样挥一刀会把旁边同阵营的感染生物一起扫进去；玩家仍走威胁点数 / 还手窗口闸门，{@link IInfectedEntity} 不互相误伤。 */
    protected boolean canHitWithRangeAttack(LivingEntity target) {
        if (target == this || !target.isAlive() || !target.isAttackable()) return false;
        if (!this.canAttackType(target.getType())) return false;
        if (target instanceof Player player) return this.canAttackPlayer(player);
        if (target instanceof IInfectedEntity) return false;
        return true;
    }

    /** 自身碰撞箱中心指向目标碰撞箱中心的<b>水平</b>单位向量：扇形轴必须对准目标方向而不是直接用 {@link #getYRot()}（转向是渐进的，起手瞬间会把主目标甩出扇面）；水平位置重合时退回自身朝向，保证不是零向量。 */
    protected Vec3 horizontalDirectionTo(LivingEntity target) {
        Vec3 delta = target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return Vec3.directionFromRotation(0.0F, this.getYRot());
        }
        return horizontal.normalize();
    }

    @Override
    public ResourceLocation getInfectionType() {
        return this.faction().infectionType();
    }

    /** 击杀 → 感染：infcore 在 {@code LivingDeathEvent} 里调用，规则 type 取 {@code getFaction().infectionRuleType()}，具体转化交给 {@link EvolutionManager} 判定（王庭侧没有专属形态的再按碰撞体积走兜底档位，腐化侧只做表内映射）。 */
    @Override
    public void onKilledEntity(Entity killed) {
        IKillCounter.super.onKilledEntity(killed); // 击杀计数 +1

        if (!(killed instanceof LivingEntity victim)) return;
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;

        // 先快照坐标 / 碰撞箱：转化成功时 infcore 会 discard 受害者并生成新的感染实体，之后既读不到它也没有追踪玩家列表（所以溅血按坐标广播）。
        AABB victimBox = victim.getBoundingBox();
        Vec3 victimCenter = victimBox.getCenter();
        // 击杀时拿不到"最后一击"的实际伤害，用生命上限的一半作为体量代理（大怪喷得多、小怪喷得少）。
        float proxyDamage = victim.getMaxHealth() * 0.5F;

        // 只有真的"造成感染"（转化成功）才喷血 —— applyEvolution 的返回值就是判定结果。
        if (EvolutionManager.applyEvolution(serverLevel, victim, this.faction().infectionRuleType())) {
            this.onInfectionBlood(serverLevel, victimCenter, victimBox, proxyDamage);
        }
    }

    /** 感染击杀溅血：被杀目标已被转化替换，只能用手上快照的坐标与碰撞箱按 {@link BloodBurstPacket#sendBurstAt} 广播；子类可覆写改变或禁用（某些 boss 不出血）。{@code damage} 是生命上限一半的数量代理值。 */
    protected void onInfectionBlood(ServerLevel level, Vec3 center, AABB box, float damage) {
        BloodBurstPacket.sendBurstAt(level, center, box, this, damage);
    }

    @Override
    public int getKillCount() {
        return this.getCapability(CapabilityRegistry.KILL_COUNT)
                .map(KillCountCapability::getKillCount)
                .orElse(0);
    }

    @Override
    public void setKillCount(int count) {
        this.getCapability(CapabilityRegistry.KILL_COUNT)
                .ifPresent(kc -> kc.setKillCount(count));
    }

    @Override
    public void addKillCount(int amount) {
        this.getCapability(CapabilityRegistry.KILL_COUNT)
                .ifPresent(kc -> kc.addKillCount(amount));
    }

    public enum BurstRenderMode { ANIMATION_ONLY, SCALE_ONLY, ANIMATION_AND_SCALE, NONE }
    /** 爆体时的渲染模式，由子类在构造器里设置（服务端 / 客户端执行同一构造器，不做同步，运行期不要只在一端改）。默认动画+缩放；没有 die 动画时 {@link #shouldBurstPlayAnim()} 会自动退回"只缩放"且 idle/walk/run 照常播。 */
    protected BurstRenderMode burstRenderMode = BurstRenderMode.ANIMATION_AND_SCALE;

    /** 该生物的 animation json 里是否<b>真的存在</b> {@code "die"} 动画；默认 false（目前项目内所有动画文件都没有）。为 false 时爆体不尝试播放死亡动画，{@link #isBurstAnimating()} 恒为 false，idle / walk / run 不会被爆体停掉。 */
    protected boolean hasDieAnim = false;

    public void setHasDieAnim(boolean has) { this.hasDieAnim = has; }
    public boolean hasDieAnim() { return this.hasDieAnim; }

    public boolean isActuallyMoving = false;
    private int movingCooldown = 0;
    private long clientBurstStartTime = -1;

    /** 找到路之后「下一次重算路径」的间隔（tick），量级同原版 {@code MeleeAttackGoal} 的 {@code 4 + rand(7)}。 */
    private static final int REPATH_INTERVAL = 10;
    /** 寻路失败后的首次退避（tick），之后每失败一次翻倍。 */
    private static final int REPATH_FAIL_BASE = 40;
    /** 退避上限（tick）：完全不可达的目标最多 10 秒重算一次。 */
    private static final int REPATH_FAIL_MAX = 200;

    /** 重新寻路的冷却（tick）。 */
    private int repathCooldown;
    /** 连续寻路失败次数（用来做指数退避）。 */
    private int repathFailures;



    protected static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation ANIM_RUN  = RawAnimation.begin().thenLoop("run");
    protected static final RawAnimation ANIM_DIE  = RawAnimation.begin().thenPlay("die");

    private final AnimatableInstanceCache defaultCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private AABB cachedBoundingBox = null;
    private long lastBoundingBoxUpdateTick = 0;
    private static final int BOUNDING_BOX_CACHE_DURATION = 5;

    protected AbstractRottenEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        if (!level.isClientSide) {
            HallEntityManager.register(this);
        }
        applyMoveSpeed();
    }

    public AbstractRottenEntity(EntityType<? extends Monster> entityType, Level level, Consumer<AbstractRottenEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
    }



    public static AttributeSupplier.Builder createBaseAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_WALKING, true);
        this.entityData.define(HAS_WALK_ANIM_SYNCED, false);
        this.entityData.define(WALK_SPEED, 0.6f);
        this.entityData.define(RUN_SPEED, 1.2f);
        this.entityData.define(WALK_ANIM_SPEED, 1.0f);
        this.entityData.define(RUN_ANIM_SPEED, 1.0f);
        this.entityData.define(IDLE_ANIM_SPEED, 1.0f);
        this.entityData.define(IS_FAKE_DYING, false);
        this.entityData.define(FAKE_DEATH_DURATION, 30);
        this.entityData.define(BURST_SCALE_DURATION, 30);
        this.entityData.define(BURST_SCALE_RATE, 10.0f);
        this.entityData.define(BURST_MAX_SCALE, 1.4f);
        this.entityData.define(BURST_SCALE_ENABLED, true);
        this.entityData.define(BURST_TWITCH_AMPLITUDE, 0.05f);
        this.entityData.define(DIE_ANIM_SPEED, 1.0f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFakeDying()) return false;
        // 爆体判定只在真正的死亡点（die）掷一次，不要在这里预判：
        // 否则致命伤会先在这里掷一次、失败后再进 die() 掷第二次，40% 概率
        // 会变成 1-(1-40%)² = 64%，与配置不符。
        float healthBefore = this.getHealth();
        float absorptionBefore = this.getAbsorptionAmount();
        boolean result = super.hurt(source, amount);

        // 受击溅血：入口就写在基类自己的 hurt 里（不走事件），所以只有王庭生物会触发；
        // 原版 / 其它模组的生物被打根本不经过这里 —— 这正是"其他生物被攻击不会出血"的结构保证。
        // 用血条实际变化量 + 吸收盾消耗量作为"受到的伤害"，而不是护甲减免前的 amount。
        if (result) {
            float dealt = healthBefore - this.getHealth()
                    + Math.max(0.0F, absorptionBefore - this.getAbsorptionAmount());
            // 爆体（假死）触发的那一击会把血量拉回满值，血条变化量是负的 ——
            // 但这一击确实打中了，用原始伤害做个数量代理，别让"挨了致命一击"反而不喷血。
            if (dealt <= 0.0F && this.isFakeDying()) dealt = amount;
            this.onHurtBlood(source, dealt);
        }

        // 被玩家打 → 开还手窗口 + 立刻反锁攻击者
        if (!level().isClientSide) {
            Entity attacker = source.getEntity();
            // 创造 / 旁观玩家不参与还手，否则打一下就会永久被锁为目标
            if (attacker instanceof Player player && !isIgnoredPlayer(player)) {
                this.lastAttacker = player;
                this.retaliationTicks = RETALIATION_WINDOW_TICKS;
                // 原版只有 HurtByTargetGoal 才会读 lastHurtByMob，而王庭生物都没注册这个 goal ——
                // 也就是说"被打了自动还手"在 1.20.1 里不是免费送的，必须在这里显式锁目标。
                // 放在 super.hurt 之后：伤害已结算，且 setTarget 的威胁闸门读到的是最新状态。
                this.setTarget(player);
            }
        }
        return result;
    }

    /** 受击溅血：在被打的<b>王庭生物自己身上</b>喷一簇 {@code blood} 血花。需求只允许两个出血点（本方法 + {@link #onInfectionBlood}），入口写在基类而非 Forge 事件 —— "其它生物被攻击不会出血"是<b>结构上</b>保证的。{@code damage} 是血条变化 + 吸收盾消耗，不是护甲减免前的原始值。 */
    protected void onHurtBlood(DamageSource source, float damage) {
        if (this.level().isClientSide()) return;
        if (damage <= 0.0F) return;

        Entity attacker = this.resolveBloodSource(source);
        if (attacker == null || attacker == this) return;

        BloodBurstPacket.sendBurst(this, attacker, damage);
    }

    /** 受击血花的"攻击源"解析：优先 {@code getEntity()}（真正的攻击者），没有时退回 {@code getDirectEntity()}（弹道方向）；摔伤 / 火 / 虚空没有攻击源，返回 null 不喷血。 */
    protected Entity resolveBloodSource(DamageSource source) {
        if (source.getEntity() != null) return source.getEntity();
        return source.getDirectEntity();
    }

    @Override
    public boolean isDeadOrDying() { return !isFakeDying() && super.isDeadOrDying(); }

    @Override
    public void die(DamageSource source) {
        if (isFakeDying()) return;
        if (!level().isClientSide && fakeDeathEnabled && tryFakeDeath()) return;
        super.die(source);
    }

    @Override
    public void tick() {
        if (firstTick) applyMoveSpeed();
        if (!this.level().isClientSide()) updateWalkRunState();
        if (this.level().isClientSide()) updateMovingState();
        super.tick();

        if (isFakeDying()) {
            if (!this.level().isClientSide() && fakeDeathTimer > 0) {
                fakeDeathTimer--;
                if (fakeDeathTimer == 0) {
                    onBurstEnd();
                    this.entityData.set(IS_FAKE_DYING, false);
                    this.remove(RemovalReason.KILLED);
                }
            }
            return;
        }

        if (!this.level().isClientSide()) {
            if (doHurtCooldown > 0) doHurtCooldown--;
            if (retaliationTicks > 0) retaliationTicks--;
            this.tickChase();
        }
    }

    /**
     * 追击：贴身就出手，够不着就寻路过去 —— 带节流与失败退避。
     * <p><b>不能每 tick 直接 {@code moveTo}</b>：目标卡在密闭空间 / 墙后 / 高台上时 {@code createPath} 返回 null、{@code moveTo} 失败，
     * {@code isDone()} 下一 tick 仍为 true，于是每 tick 重跑一次完整 A*（20 次/秒），几只叠加就能把 TPS 打穿。
     * <p>找到路：{@link #REPATH_INTERVAL} + 少量随机后重算；找不到路：{@link #REPATH_FAIL_BASE} 起指数退避到 {@link #REPATH_FAIL_MAX}。
     * 子类自己也调 {@code moveTo}（爬行 / 冲撞）时请复用同一个冷却，别再每 tick 重算。
     */
    protected void tickChase() {
        if (this.repathCooldown > 0) this.repathCooldown--;

        // 附近没有玩家时基类 aiStep 会把 AI 关掉（stopAiAtDist），此时 travel 不跑、寻路出来的路径没人消费，直接跳过。
        if (this.isNoAi()) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            this.repathFailures = 0;
            return;
        }

        if (getBoundingBoxDistanceSqrExact(target) <= doHurtDistance * doHurtDistance
                && this.hasLineOfSight(target)) {
            if (doHurtCooldown == 0 && doHurtGoal(target)) doHurtCooldown = doHurtTime;
            this.repathFailures = 0;
            return;
        }

        if (this.repathCooldown > 0 || !this.getNavigation().isDone()) return;

        if (this.getNavigation().moveTo(target, this.chaseSpeed)) {
            this.repathFailures = 0;
            this.repathCooldown = REPATH_INTERVAL + this.getRandom().nextInt(5);
        } else {
            this.repathFailures = Math.min(this.repathFailures + 1, 5);
            this.repathCooldown = Math.min(REPATH_FAIL_BASE << (this.repathFailures - 1), REPATH_FAIL_MAX);
        }
    }

    private void updateWalkRunState() {
        // 没有 walk 动画时，始终按 walk 速度移动（与 GodSickNeo AbstractGSEntity 一致）
        if (!hasWalkAnimation()) {
            if (!isWalking()) {
                this.entityData.set(IS_WALKING, true);
                applyMoveSpeed();
            }
            return;
        }
        LivingEntity target = this.getTarget();
        boolean shouldWalk = target == null || !target.isAlive();
        if (this.isWalking() != shouldWalk) { this.entityData.set(IS_WALKING, shouldWalk); applyMoveSpeed(); }
    }

    public void applyMoveSpeed() {
        AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getBaseValue() != getCurrentMoveSpeed()) attr.setBaseValue(getCurrentMoveSpeed());
    }

    private void updateMovingState() {
        if (getDeltaMovement().horizontalDistanceSqr() > 1e-4) { movingCooldown = 5; isActuallyMoving = true; }
        else { if (movingCooldown > 0) movingCooldown--; else isActuallyMoving = false; }
    }

    protected boolean doHurtGoal(LivingEntity target) { return getAttack(target); }
    protected abstract boolean getAttack(LivingEntity target);

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.tickCount % 20 == 0
                && this.level() instanceof ServerLevel serverLevel && stopAiAtDist) {
            int simDistance = serverLevel.getServer().getPlayerList().getSimulationDistance();
            double activeRange = (simDistance - 1) * 16.0D;
            if (serverLevel.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), activeRange)) {
                if (this.isNoAi()) this.setNoAi(false);
            } else if (!serverLevel.players().isEmpty()) {
                // 有玩家在线但激活范围内没有 → 太远，暂停 AI
                if (!this.isNoAi()) this.setNoAi(true);
            }
            // 无人在线时保持 AI 运行，不影响其他怪物正常刷新
        }
        super.aiStep();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "movement_controller", 3, state -> {
            // 爆体只在"死亡动画真的接管"时停掉移动/待机；没有 die 动画时 idle 照常播。
            if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;
            if (!isActuallyMoving) { state.setControllerSpeed(getIdleAnimSpeed()); return state.setAndContinue(ANIM_IDLE); }
            if (hasWalkAnimation()) {
                if (isWalking()) { state.setControllerSpeed(getWalkAnimSpeed()); return state.setAndContinue(ANIM_WALK); }
                else { state.setControllerSpeed(getRunAnimSpeed()); return state.setAndContinue(ANIM_RUN); }
            }
            state.setControllerSpeed(getWalkAnimSpeed());
            return state.setAndContinue(ANIM_WALK);
        }));
        // 子类扩展点，必须夹在移动与死亡之间：GeckoLib 按注册顺序应用控制器，后注册的覆盖先注册的骨骼，
        // 攻击要盖过移动、死亡要盖过攻击。
        registerAdditionalControllers(registrar);
        AnimationController<AbstractRottenEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        isBurstAnimating() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }

    /** 控制器扩展点：在 {@code movement_controller} 之后、{@code die_controller} 之前调用，基类什么都不做；只追加一条攻击 / 技能控制器时覆写这里即可，不必重写整个 {@link #registerControllers}（容易忘掉移动或死亡控制器）。 */
    protected void registerAdditionalControllers(AnimatableManager.ControllerRegistrar registrar) {
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return defaultCache; }
    @Override public void onKeyframeTrigger(String key) {}

    public boolean isFakeDying() { return this.entityData.get(IS_FAKE_DYING); }
    public boolean shouldBurstScale() { return burstRenderMode == BurstRenderMode.SCALE_ONLY || burstRenderMode == BurstRenderMode.ANIMATION_AND_SCALE; }

    /** 爆体时是否播死亡动画 —— <b>必须同时</b>满足：animation json 里真的有 {@code die} 动画、且渲染模式包含动画。没有 die 动画时恒为 false，既不会播不存在的动画，也<b>不会</b>停掉 idle / walk / run。 */
    public boolean shouldBurstPlayAnim() {
        return hasDieAnim && (burstRenderMode == BurstRenderMode.ANIMATION_ONLY || burstRenderMode == BurstRenderMode.ANIMATION_AND_SCALE);
    }

    /** 爆体期间是否由死亡动画接管（此时 idle / walk / run 要停下让位）：判的是"死亡动画真的会播"而不是 {@link #isFakeDying()} 本身，所以没有 die 动画时 idle 一律继续播。 */
    public boolean isBurstAnimating() {
        return isFakeDying() && shouldBurstPlayAnim();
    }

    /** 单个脉冲里"鼓起"所占的比例，其余时间用于回落（见 {@link #burstPulseShape(float)}）。 */
    private static final float BURST_PULSE_RISE = 0.3f;

    /** 爆体缩放是否生效：已<b>开始爆体</b> + 没关掉缩放 + 渲染模式含缩放，三个条件同时满足；爆体之前恒为 {@code false}，模型保持原始大小。 */
    public boolean isBurstScalingActive() {
        return isFakeDying() && isBurstScaleEnabled() && shouldBurstScale();
    }

    /** 爆体进度（0 = 刚开始，1 = 缩放窗口结束）：客户端用收到 {@code IS_FAKE_DYING} 时的 gameTime、服务端用假死计时器，因此渲染端<b>不需要</b>每 tick 同步"已经过多少 tick"。 */
    public float getBurstProgress(float partialTick) {
        int duration = getResolvedBurstScaleDuration();
        if (duration <= 0) return 1.0f;
        float elapsed;
        if (level().isClientSide()) {
            if (clientBurstStartTime < 0) return 0.0f;
            elapsed = (level().getGameTime() - clientBurstStartTime) + partialTick;
        } else {
            elapsed = (getFakeDeathDuration() - fakeDeathTimer) + partialTick;
        }
        return Mth.clamp(elapsed / (float) duration, 0.0f, 1.0f);
    }

    /** 线性爆体强度（0..1）。只想要"推进度"的调用方（动画 / 粒子等）可以用它。 */
    public float getBurstIntensity(float partialTick) {
        if (!isBurstScalingActive()) return 0.0f;
        return getBurstProgress(partialTick);
    }

    /**
     * 爆体时的模型缩放倍率（1.0 = 原始大小）：高频阶梯脉冲（N = {@link #getBurstPulseCount()} 级，每级只回缩到上一级的峰值）+ 逐 tick 随机抖动。
     * <p>抖动由 tick + 实体 id 哈希出确定性噪声：同一 tick 内所有帧同值、下一 tick 跳变 = 20Hz 抽搐，幅度随进度 0.5×→1.5×，且正向峰值也不许超过 maxScale（默认 1.4）。
     * <p><b>纯视觉</b>：只在客户端渲染时使用，碰撞箱与命中判定保持原样。
     */
    public float getBurstScale(float partialTick) {
        if (!isBurstScalingActive()) return 1.0f;

        float progress = getBurstProgress(partialTick);
        float maxScale = Math.max(1.0f, getBurstMaxScale());
        float scale;

        if (progress >= 1.0f) {
            scale = maxScale;                    // 窗口走完：停在最大倍率（抖动继续到爆体结束）
        } else {
            int pulses = getBurstPulseCount();
            float pulsePos = progress * pulses;  // 0..N
            int index = (int) pulsePos;
            if (index >= pulses) {
                scale = maxScale;
            } else {
                float local = pulsePos - index;  // 当前脉冲内的 0..1
                float step = (maxScale - 1.0f) / pulses;
                float baseline = 1.0f + step * index;
                scale = baseline + step * burstPulseShape(local);
            }
        }

        // 鬼畜抖动：逐 tick 变化（partialTick 不参与），同一 tick 的 3 帧共用同一个值。
        float twitch = getBurstTwitchAmplitude();
        if (twitch > 0.0f) {
            long seed = (long) this.getId() * 0xBF58476D1CE4E5B9L
                    + level().getGameTime() * 0x9E3779B97F4A7C15L;
            float noise = burstTwitchNoise(seed) * 2.0f - 1.0f;   // -1..1
            scale += noise * twitch * (0.5f + progress);          // 幅度 0.5×→1.5×
        }
        // 硬上限：抖动的正向峰值也不许超过 maxScale（默认 1.4 倍）。
        return Mth.clamp(scale, 1.0f, maxScale);
    }

    /** 由 long 种子生成 [0,1) 的确定性噪声（SplitMix64）。用哈希而不是 {@code Random}：渲染每帧都可能调用，不能推进共享随机状态；同一 tick + 同一实体必须给出同一个值，才能形成"每 tick 跳一下"的抽搐而不是每帧乱闪。 */
    private static float burstTwitchNoise(long seed) {
        seed ^= seed >>> 30;
        seed *= 0xBF58476D1CE4E5B9L;
        seed ^= seed >>> 27;
        seed *= 0x94D049BB133111EBL;
        seed ^= seed >>> 31;
        return (seed >>> 40) / (float) (1 << 24);
    }

    /** 单个脉冲的归一化形状 0 → 1 → 0：前 {@value #BURST_PULSE_RISE} 快速鼓起（smoothstep），余下缓缓落回；两端都取 0，所以帧间"跳一级"只发生在脉冲边界。 */
    private static float burstPulseShape(float t) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        if (t < BURST_PULSE_RISE) {
            float x = t / BURST_PULSE_RISE;
            return x * x * (3.0f - 2.0f * x);
        }
        float x = (t - BURST_PULSE_RISE) / (1.0f - BURST_PULSE_RISE);
        return 1.0f - x * x * (3.0f - 2.0f * x);
    }

    /** 缩放窗口里实际使用的时长（tick）；{@code burstScaleDuration <= 0} 时退回爆体总时长。 */
    public int getResolvedBurstScaleDuration() {
        int duration = getBurstScaleDuration();
        if (duration > 0) return duration;
        return Math.max(1, getFakeDeathDuration());
    }

    /** 缩放窗口内的脉冲级数：{@link #getBurstScaleRate()} 是<b>每秒</b>脉冲次数，所以 {@code N = round(rate * duration / 20)}。默认 10 次/秒 × 1.5 秒 = 15 级（每级约 2 tick）。 */
    public int getBurstPulseCount() {
        int duration = getResolvedBurstScaleDuration();
        float rate = getBurstScaleRate();
        if (rate <= 0.0f) return 1;
        return Math.max(1, Math.round(rate * duration / 20.0f));
    }

    public boolean tryFakeDeath() {
        if (!fakeDeathEnabled || isFakeDying()) return false;
        if (fakeDeathChance > 0 && this.getRandom().nextInt(100) >= fakeDeathChance) return false;
        startFakeDeath();
        return true;
    }

    protected void startFakeDeath() {
        if (isFakeDying()) return;
        this.entityData.set(IS_FAKE_DYING, true);
        this.fakeDeathTimer = this.getFakeDeathDuration();
        this.setHealth(this.getMaxHealth());
        if (shouldBurstPlayAnim()) this.triggerAnim("die_controller", "die_trigger");
        onBurstStart();
    }

    protected void onBurstStart() {}

    /**
     * 爆体结束回调：在 {@link #tick()} 里 fake death 计时器归零那一 tick 调用，紧跟着实体才会 {@code remove(RemovalReason.KILLED)}。
     * <p><b>发包顺序很重要：</b>本方法在 {@code remove(...)} <b>之前</b>执行（同一连接保序），客户端处理肉屑包时还能用实体 id 解析到实体读到碰撞箱；
     * 挪到 {@code remove(...)} 之后客户端多半已经找不到实体，肉屑就不会出现。
     * <p><b>子类覆写本方法时必须调用 {@code super.onBurstEnd()}</b>，否则肉屑爆发 + 血腥爆炸 + 红色 dust + 爆体音效 + 那一次死亡掉落就都丢了。
     */
    protected void onBurstEnd() {
        // 只在服务端触发、同 tick 发出：肉屑包 + 血腥爆炸 + 红色 dust + 爆体音效，最后补一次死亡掉落。
        if (!this.level().isClientSide()) {
            MeatBurstPacket.sendBurst(this);
            EntityParticleUtils.spawnExplosionParticles(this);
            // 数值照抄 corruption BaseOriginalEntity 的 spawnParticles(redDust, 8, 0.1, 1.2)：纯红、scale 2.0，给爆炸加一层红色粉尘。
            EntityParticleUtils.spawnParticles(this,
                    new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 2.0F),
                    8.0D, 0.1D, 1.2F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEventRegistry.RIP_BODY.get(), SoundSource.HOSTILE, 1.0F, 1.0F);

            // 爆体路径不经过 super.die()，不在这里补就永远没有战利品表 / 装备 / 经验；每次爆体只调用一次，不会和正常死亡重复。
            this.dropAllDeathLoot(this.getBurstLootDamageSource());
        }
    }

    /** 爆体掉落使用的伤害源：优先最近一次伤害（带着击杀者信息，战利品的 {@code killed_by_player}、抢夺附魔、{@code LivingDropsEvent} 才正常），取不到就退回泛用伤害源。 */
    private DamageSource getBurstLootDamageSource() {
        DamageSource source = this.getLastDamageSource();
        return source != null ? source : this.damageSources().generic();
    }

    @Override public void travel(Vec3 travelVector) { if (isFakeDying()) { super.travel(Vec3.ZERO); return; } super.travel(travelVector); }

    public boolean isWalking() { return this.entityData.get(IS_WALKING); }
    public boolean hasWalkAnimation() { return this.entityData.get(HAS_WALK_ANIM_SYNCED); }
    public void setHasWalkAnim(boolean value) { this.hasWalkAnim = value; this.entityData.set(HAS_WALK_ANIM_SYNCED, value); applyMoveSpeed(); }
    public float getWalkSpeed() { return this.entityData.get(WALK_SPEED); }
    public void setWalkSpeed(float speed) { this.walkSpeed = speed; this.entityData.set(WALK_SPEED, speed); applyMoveSpeed(); }
    public float getRunSpeed() { return this.entityData.get(RUN_SPEED); }
    public void setRunSpeed(float speed) { this.runSpeed = speed; this.entityData.set(RUN_SPEED, speed); applyMoveSpeed(); }
    public float getWalkAnimSpeed() { return this.entityData.get(WALK_ANIM_SPEED); }
    public void setWalkAnimSpeed(float speed) { this.walkAnimSpeed = speed; this.entityData.set(WALK_ANIM_SPEED, speed); }
    public float getRunAnimSpeed() { return this.entityData.get(RUN_ANIM_SPEED); }
    public void setRunAnimSpeed(float speed) { this.runAnimSpeed = speed; this.entityData.set(RUN_ANIM_SPEED, speed); }
    public float getIdleAnimSpeed() { return this.entityData.get(IDLE_ANIM_SPEED); }
    public void setIdleAnimSpeed(float speed) { this.idleAnimSpeed = speed; this.entityData.set(IDLE_ANIM_SPEED, speed); }
    public float getDieAnimSpeed() { return this.entityData.get(DIE_ANIM_SPEED); }
    public void setDieAnimSpeed(float speed) { this.dieAnimSpeed = speed; this.entityData.set(DIE_ANIM_SPEED, speed); }
    public float getCurrentMoveSpeed() {
        // 没有 walk 动画时始终用 walk 速度（与 GodSickNeo AbstractGSEntity 一致）
        if (!hasWalkAnimation()) {
            return getWalkSpeed();
        }
        return isWalking() ? getWalkSpeed() : getRunSpeed();
    }
    public void setFakeDeathEnabled(boolean enabled) { this.fakeDeathEnabled = enabled; }
    public void setFakeDeathChance(int chance) { this.fakeDeathChance = chance; }
    public void setFakeDeathDuration(int duration) { this.fakeDeathDuration = duration; this.entityData.set(FAKE_DEATH_DURATION, duration); }
    public int getFakeDeathDuration() { return this.entityData.get(FAKE_DEATH_DURATION); }
    public void setBurstScaleDuration(int duration) { this.burstScaleDuration = duration; this.entityData.set(BURST_SCALE_DURATION, duration); }
    public int getBurstScaleDuration() { return this.entityData.get(BURST_SCALE_DURATION); }
    public void setBurstScaleRate(float rate) { this.burstScaleRate = rate; this.entityData.set(BURST_SCALE_RATE, rate); }
    public float getBurstScaleRate() { return this.entityData.get(BURST_SCALE_RATE); }
    /** 鬼畜抖动幅度（倍率的绝对值，实际抖动 = 该值 × 0..1 噪声 × 0.5..1.5）；0 = 关闭抖动、只保留高频阶梯脉冲；默认 0.04，NaN / 负数回退 0。 */
    public void setBurstTwitchAmplitude(float amplitude) {
        float v = Float.isFinite(amplitude) ? Math.max(0.0f, amplitude) : 0.0f;
        this.entityData.set(BURST_TWITCH_AMPLITUDE, v);
    }
    public float getBurstTwitchAmplitude() {
        float v = this.entityData.get(BURST_TWITCH_AMPLITUDE);
        return Float.isFinite(v) ? Math.max(0.0f, v) : 0.0f;
    }
    /** 爆体缩放的最大倍率（1.0 = 原始大小；默认夹在 1.4 倍；NaN 回退默认值）。 */
    public void setBurstMaxScale(float scale) { float v = Float.isFinite(scale) ? Math.max(1.0f, scale) : 1.4f; this.entityData.set(BURST_MAX_SCALE, v); }
    public float getBurstMaxScale() { float v = this.entityData.get(BURST_MAX_SCALE); return Float.isFinite(v) ? v : 1.4f; }
    /** 是否允许这颗生物在爆体时缩放（基类默认开启）。 */
    public void setBurstScaleEnabled(boolean enabled) { this.entityData.set(BURST_SCALE_ENABLED, enabled); }
    public boolean isBurstScaleEnabled() { return this.entityData.get(BURST_SCALE_ENABLED); }
    /** 爆体渲染模式 —— 由子类在构造器里声明（两端一致，不做同步）。 */
    public void setBurstRenderMode(BurstRenderMode mode) { this.burstRenderMode = mode != null ? mode : BurstRenderMode.NONE; }
    public BurstRenderMode getBurstRenderMode() { return this.burstRenderMode; }
    public void setDoHurtDistance(float distance) { this.doHurtDistance = distance; }
    public float getDoHurtDistance() { return doHurtDistance; }
    public void setChaseSpeed(float speed) { this.chaseSpeed = speed; }
    public float getChaseSpeed() { return this.chaseSpeed; }
    public int getDoHurtTime() { return doHurtTime; }
    public void setDoHurtTime(int doHurtTime) { this.doHurtTime = doHurtTime; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && IS_FAKE_DYING.equals(key)) {
            if (isFakeDying()) this.clientBurstStartTime = level().getGameTime(); else this.clientBurstStartTime = -1;
        }
    }

    @Override public ResourceLocation model()     { return this.model; }
    @Override public ResourceLocation texture()   { return this.texture; }
    @Override public ResourceLocation animation() { return this.animation; }

    public double getBoundingBoxDistanceSqrExact(Entity entity) {
        AABB a = getCachedBoundingBox(), b = entity.getBoundingBox();
        double dx = 0, dy = 0, dz = 0;
        if (a.maxX < b.minX) dx = b.minX - a.maxX; else if (b.maxX < a.minX) dx = a.minX - b.maxX;
        if (a.maxY < b.minY) dy = b.minY - a.maxY; else if (b.maxY < a.minY) dy = a.minY - b.maxY;
        if (a.maxZ < b.minZ) dz = b.minZ - a.maxZ; else if (b.maxZ < a.minZ) dz = a.minZ - b.maxZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private AABB getCachedBoundingBox() {
        long t = this.level().getGameTime();
        if (cachedBoundingBox == null || t - lastBoundingBoxUpdateTick > BOUNDING_BOX_CACHE_DURATION) { cachedBoundingBox = this.getBoundingBox(); lastBoundingBoxUpdateTick = t; }
        return cachedBoundingBox;
    }

    /** 剔除盒按最大爆体倍率一起放大：缩放是纯视觉的，只放大模型不放大剔除盒时，贴近屏幕边缘会出现"模型还在视野里、实体却被整只裁掉"的闪烁；缩放以脚底为原点，所以水平两侧各扩一半、竖直只向上扩。<b>只影响剔除，不影响碰撞箱 / 命中判定。</b> */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (!isBurstScalingActive()) return box;
        float scale = Math.max(1.0f, getBurstMaxScale());
        if (scale <= 1.0f) return box;
        double dx = box.getXsize() * (scale - 1.0) * 0.5;
        double dz = box.getZsize() * (scale - 1.0) * 0.5;
        double dy = box.getYsize() * (scale - 1.0);
        return box.expandTowards(dx, dy, dz).expandTowards(dx, 0.0, dz);
    }

    public boolean randomPercentage(int percentage) { return this.getRandom().nextInt(100) < percentage; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean doHurtTarget(Entity entity) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("IsWalking", isWalking());
        nbt.putBoolean("HasWalkAnim", this.hasWalkAnim);
        nbt.putFloat("WalkSpeed", this.walkSpeed);
        nbt.putFloat("RunSpeed", this.runSpeed);
        nbt.putFloat("WalkAnimSpeed", this.walkAnimSpeed);
        nbt.putFloat("RunAnimSpeed", this.runAnimSpeed);
        nbt.putFloat("IdleAnimSpeed", this.idleAnimSpeed);
        nbt.putFloat("DieAnimSpeed", this.dieAnimSpeed);
        nbt.putBoolean("IsFakeDying", isFakeDying());
        nbt.putInt("FakeDeathTimer", this.fakeDeathTimer);
        nbt.putInt("FakeDeathDuration", this.fakeDeathDuration);
        nbt.putInt("BurstScaleDuration", getBurstScaleDuration());
        nbt.putFloat("BurstScaleRate", getBurstScaleRate());
        nbt.putFloat("BurstMaxScale", getBurstMaxScale());
        nbt.putBoolean("BurstScaleEnabled", isBurstScaleEnabled());
        nbt.putFloat("BurstTwitchAmplitude", getBurstTwitchAmplitude());
        nbt.putInt("DoHurtTime", this.doHurtTime);
        nbt.putFloat("DoHurtDistance", this.doHurtDistance);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("IsWalking")) this.entityData.set(IS_WALKING, nbt.getBoolean("IsWalking"));
        if (nbt.contains("HasWalkAnim")) { this.hasWalkAnim = nbt.getBoolean("HasWalkAnim"); this.entityData.set(HAS_WALK_ANIM_SYNCED, this.hasWalkAnim); }
        if (nbt.contains("IsFakeDying")) this.entityData.set(IS_FAKE_DYING, nbt.getBoolean("IsFakeDying"));
        if (nbt.contains("FakeDeathTimer")) this.fakeDeathTimer = nbt.getInt("FakeDeathTimer");
        if (nbt.contains("FakeDeathDuration")) this.setFakeDeathDuration(nbt.getInt("FakeDeathDuration"));
        if (nbt.contains("BurstScaleDuration")) this.setBurstScaleDuration(nbt.getInt("BurstScaleDuration"));
        if (nbt.contains("BurstScaleRate")) this.setBurstScaleRate(nbt.getFloat("BurstScaleRate"));
        if (nbt.contains("BurstMaxScale")) this.setBurstMaxScale(nbt.getFloat("BurstMaxScale"));
        if (nbt.contains("BurstScaleEnabled")) this.setBurstScaleEnabled(nbt.getBoolean("BurstScaleEnabled"));
        if (nbt.contains("BurstTwitchAmplitude")) this.setBurstTwitchAmplitude(nbt.getFloat("BurstTwitchAmplitude"));
        if (nbt.contains("WalkSpeed")) { this.walkSpeed = nbt.getFloat("WalkSpeed"); this.entityData.set(WALK_SPEED, this.walkSpeed); }
        if (nbt.contains("RunSpeed")) { this.runSpeed = nbt.getFloat("RunSpeed"); this.entityData.set(RUN_SPEED, this.runSpeed); }
        if (nbt.contains("WalkAnimSpeed")) { this.walkAnimSpeed = nbt.getFloat("WalkAnimSpeed"); this.entityData.set(WALK_ANIM_SPEED, this.walkAnimSpeed); }
        if (nbt.contains("RunAnimSpeed")) { this.runAnimSpeed = nbt.getFloat("RunAnimSpeed"); this.entityData.set(RUN_ANIM_SPEED, this.runAnimSpeed); }
        if (nbt.contains("IdleAnimSpeed")) { this.idleAnimSpeed = nbt.getFloat("IdleAnimSpeed"); this.entityData.set(IDLE_ANIM_SPEED, this.idleAnimSpeed); }
        if (nbt.contains("DieAnimSpeed")) { this.dieAnimSpeed = nbt.getFloat("DieAnimSpeed"); this.entityData.set(DIE_ANIM_SPEED, this.dieAnimSpeed); }
        if (nbt.contains("DoHurtTime")) this.doHurtTime = nbt.getInt("DoHurtTime");
        if (nbt.contains("DoHurtDistance")) this.doHurtDistance = nbt.getFloat("DoHurtDistance");
        applyMoveSpeed();
    }
}
