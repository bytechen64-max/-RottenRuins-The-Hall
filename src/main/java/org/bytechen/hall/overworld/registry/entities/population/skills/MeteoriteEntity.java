package org.bytechen.hall.overworld.registry.entities.population.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.MeteorShowerConfig;
import org.bytechen.hall.overworld.manager.BlockSpreadManager;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.utils.entity.EntityParticleUtils;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.infcore.core.evolution.EvolutionManager;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 陨石技能实体 —— 无模型动画，纯白色水滴状多面体。
 */
public class MeteoriteEntity extends Entity implements IAutoRenderableEntity {

    // ────────── 物理参数 ──────────
    private static final float GRAVITY = 0.08f;
    private static final float AIR_FRICTION = 0.99f;

    // ────────── 拖尾参数 ──────────
    private static final int MAX_TRAIL_POINTS = 40;

    // ────────── 尾部圆环参数 ──────────
    private static final int RING_SPAWN_INTERVAL = 2;
    public static final int RING_MAX_AGE = 30;
    private final List<TailRing> tailRings = new ArrayList<>();

    // ────────── AoE 扩散圆环参数 ──────────
    private static final int AOE_RING_INTERVAL = 4;
    public static final int AOE_RING_MAX_AGE = 80;
    public static final float AOE_RING_START_R = 1.0f;
    public static final float AOE_RING_SPEED = 0.35f;
    private final List<AoeRing> aoeRings = new ArrayList<>();

    // ────────── 二十面体基础参数 ──────────
    private static final float ICOSA_RADIUS = 1.0f;
    private static final double PARTICLE_SPEED = 0.15;

    private static final float GROUND_EFFECT_RADIUS = 8.0f;

    private static final ResourceLocation DUMMY = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/misc/white.png");

    // ────────── 配置缓存（生成时从 ConfigHelper 读取一次，保证服务端正确） ──────────
    private float cfgAoeDamage = 5.0f;
    private float cfgIcosaMaxScale = 10.0f;
    private int cfgFadeTicks = 170;
    private int cfgLingerDuration = 60;
    private double cfgParticleDensity = 15.0;
    private int cfgSpreadRadiusMin = 3;
    private int cfgSpreadRadiusMax = 5;
    private int cfgSpreadCountMin = 8;
    private int cfgSpreadCountMax = 16;
    private float cfgOreChance = 0.5f;
    private float cfgShockwaveAngle = 45.0f;
    private float cfgShockwaveSpeed = 32.0f;
    private int cfgShockwaveDuration = 30;
    private float cfgShockwaveInitialScale = 1.0f;
    private float cfgShockwaveShrink = 0.06f;

    // ────────── 同步数据：fadeTimer 需要客户端可见用于渲染 ──────────
    private static final EntityDataAccessor<Integer> FADE_TIMER =
            SynchedEntityData.defineId(MeteoriteEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final LinkedList<Vec3> trailPositions = new LinkedList<>();
    private long lastTrailRecordTick = 0;

    /** 服务端：是否已触发撞击特效（同步到 NBT 防崩服重连） */
    private boolean hasImpacted = false;

    public MeteoriteEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        loadConfig();
    }

    /** 从配置文件读取参数并缓存到实例字段 */
    private void loadConfig() {
        MeteorShowerConfig cfg = ConfigHelper.meteorConfigHolder != null
                ? ConfigHelper.meteorConfigHolder.get()
                : null;
        if (cfg == null) return;
        this.cfgAoeDamage = cfg.aoeDamage;
        this.cfgIcosaMaxScale = cfg.icosaMaxScale;
        this.cfgFadeTicks = cfg.fadeTicks;
        this.cfgLingerDuration = cfg.lingerDuration;
        this.cfgParticleDensity = cfg.particleDensity;
        this.cfgSpreadRadiusMin = cfg.spreadRadiusMin;
        this.cfgSpreadRadiusMax = cfg.spreadRadiusMax;
        this.cfgSpreadCountMin = cfg.spreadCountMin;
        this.cfgSpreadCountMax = cfg.spreadCountMax;
        this.cfgOreChance = cfg.oreChancePercent / 100.0f;
        this.cfgShockwaveAngle = cfg.shockwaveAngle;
        this.cfgShockwaveSpeed = cfg.shockwaveSpeed;
        this.cfgShockwaveDuration = cfg.shockwaveDuration;
        this.cfgShockwaveInitialScale = cfg.shockwaveInitialScale;
        this.cfgShockwaveShrink = cfg.shockwaveShrink;
    }

    // ═══════════════════════════════════════════════════════════════
    // Tick
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        // 不调用 super.tick() — 避免 vanilla baseTick 干扰碰撞状态
        if (this.isPassenger() && this.getVehicle() != null && !this.getVehicle().isPassengerOfSameVehicle(this)) {
            this.stopRiding();
        }

        // 客户端：更新尾部圆环和 AoE 扩散圆环
        if (level().isClientSide()) {
            updateTailRings();
            updateAoeRings();
        }

        // ── 二十面体 AoE 伤害 + 粒子 ──
        doIcosahedronAoe();

        // ── 滞留消退阶段（synced: server 递减并同步到 client）──
        int timer = entityData.get(FADE_TIMER);
        if (timer > 0) {
            if (!level().isClientSide()) {
                timer--;
                entityData.set(FADE_TIMER, timer);
            }
            // 仅 AoE 活跃期（timer > LINGER）进行伤害和圆环生成
            // 停留期 timer ≤ LINGER 时只渲染现有圆环淡出
            if (timer <= 0) {
                this.discard();
                return;
            }
            // 双方均跳过移动逻辑
            return;
        }

        // ── 已触发过，直接丢弃（兜底） ──
        if (hasImpacted) {
            this.discard();
            return;
        }

        // ── 飞行阶段 ──
        Vec3 motion = getDeltaMovement();
        if (!isNoGravity()) {
            motion = motion.add(0, -GRAVITY, 0).scale(AIR_FRICTION);
            setDeltaMovement(motion);
        }

        // ▸ 移到前：检查预计落点是否有方块
        if (!level().isClientSide() && willCollideThisTick(motion)) {
            doImpact();
            return;
        }

        this.move(MoverType.SELF, getDeltaMovement());

        // 移到后：兜底检测（move 后仍可能触地）
        if (!hasImpacted && !level().isClientSide()
                && (this.onGround() || this.verticalCollision || this.horizontalCollision
                    || position().y <= level().getMinBuildHeight())) {
            doImpact();
            return;
        }

        // 客户端：记录拖尾点
        if (level().isClientSide()) {
            recordTrailPoint();
        }
    }

    /**
     * 预测移动后是否会撞到方块 —— 在服务端 move 之前调用。
     * 用当前位置 + motion 做一个 AABB 扫描，彻底避免依赖 onGround 标志。
     */
    private boolean willCollideThisTick(Vec3 motion) {
        AABB box = this.getBoundingBox().move(motion);
        // 扩大一点检测范围，避免高速穿透
        return !level().noCollision(this, box.inflate(0.02));
    }

    private void doImpact() {
        hasImpacted = true;
        onImpactGround();
        entityData.set(FADE_TIMER, cfgFadeTicks);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
    }

    // ═══════════════════════════════════════════════════════════════
    // 拖尾
    // ═══════════════════════════════════════════════════════════════

    private void recordTrailPoint() {
        long currentTick = level().getGameTime();
        if (currentTick != lastTrailRecordTick) {
            lastTrailRecordTick = currentTick;
            trailPositions.addLast(position());
            while (trailPositions.size() > MAX_TRAIL_POINTS) {
                trailPositions.removeFirst();
            }
        }
    }

    public List<Vec3> getTrailPositions() { return trailPositions; }
    public int getFadeTimer() { return entityData.get(FADE_TIMER); }
    public boolean isFading() { return entityData.get(FADE_TIMER) > 0; }
    public List<TailRing> getTailRings() { return tailRings; }
    public List<AoeRing> getAoeRings() { return aoeRings; }

    // ═══════════════════════════════════════════════════════════════
    // 二十面体 AoE 伤害 + 粒子
    // ═══════════════════════════════════════════════════════════════

    private void doIcosahedronAoe() {
        int timer = entityData.get(FADE_TIMER);
        // 停留期不造成伤害也不生成粒子
        if (timer <= 0) return;

        float radius = getCurrentIcosaRadius();
        AABB aoe = this.getBoundingBox().inflate(radius);

        if (level().isClientSide()) {
            // 客户端：粒子（停留期也不生成，timer <= LINGER 时无粒子）
            if (timer <= cfgLingerDuration) return;
            for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, aoe,
                    e -> e.isAlive() && !isExcluded(e))) {
                EntityParticleUtils.spawnParticles(target, ParticleTypes.END_ROD,
                        cfgParticleDensity, PARTICLE_SPEED, 1.0f);
            }
        } else {
            // 服务端：对非 hall 感染生物造成伤害，治疗 hall 感染生物
            if (timer > cfgLingerDuration) {
                for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, aoe,
                        e -> e.isAlive() && !isExcluded(e))) {
                    if (target instanceof IInfectedEntity infectedEntity
                            && infectedEntity.getInfectionType().getPath().equals("hall")) {
                        // 治疗 hall 感染生物
                        target.heal(cfgAoeDamage);
                    } else {
                        // 清零无敌帧，确保每 tick 都能命中非 hall 生物
                        target.invulnerableTime = 0;
                        CompoundTag persistentData = target.getPersistentData();
                        if (!persistentData.getBoolean("evolved")) {
                            EvolutionManager.applyEvolution((ServerLevel)target.level(), target,  new ResourceLocation(HallMod.MODID, "inf"));
                            persistentData.putBoolean("evolved", true);
                        }
                        target.hurt(level().damageSources().magic(), cfgAoeDamage);
                    }
                }
            }
        }
    }

    /** 排除创造/旁观玩家 */
    private static boolean isExcluded(LivingEntity e) {
        if (e instanceof Player player) {
            return player.isCreative() || player.isSpectator();
        }
        return false;
    }

    /** 返回当前二十面体半径（飞行=基础半径，消退=基础半径×动画缩放） */
    private float getCurrentIcosaRadius() {
        int timer = entityData.get(FADE_TIMER);
        if (timer <= 0) return ICOSA_RADIUS;

        // 根据配置动态计算时间轴阶段阈值
        int totalActive = cfgFadeTicks - cfgLingerDuration; // 非停留阶段总 tick
        int expandDuration = totalActive * 3 / 11;           // 展开占 3/11
        int holdDuration   = totalActive * 6 / 11;           // 保持占 6/11
        int shrinkDuration = totalActive - expandDuration - holdDuration; // 收缩占 2/11

        int expandEnd  = cfgFadeTicks - expandDuration;
        int holdEnd    = expandEnd - holdDuration;
        int shrinkEnd  = holdEnd - shrinkDuration; // == cfgLingerDuration

        float scale;
        if (timer > expandEnd) {
            // 展开阶段
            float elapsed = cfgFadeTicks - timer;
            float t = Math.min(1.0f, elapsed / (float) expandDuration);
            scale = 1.0f + (cfgIcosaMaxScale - 1.0f) * easeOutCubic(t);
        } else if (timer > holdEnd) {
            // 保持阶段
            scale = cfgIcosaMaxScale;
        } else if (timer > shrinkEnd) {
            // 收缩阶段
            float elapsed = holdEnd - timer;
            float t = Math.min(1.0f, elapsed / (float) shrinkDuration);
            scale = 1.0f + (cfgIcosaMaxScale - 1.0f) * easeOutCubic(1.0f - t);
        } else {
            // 停留阶段：保持 1x
            scale = 1.0f;
        }
        return ICOSA_RADIUS * scale;
    }

    private static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    private void updateTailRings() {
        Vec3 meteorVel = getDeltaMovement();
        // 仅飞行阶段生成新圆环（非 fading 且未撞击）
        if (!isFading() && !hasImpacted
                && level().getGameTime() % RING_SPAWN_INTERVAL == 0
                && meteorVel.lengthSqr() > 0.0001) {
            tailRings.add(new TailRing(position(), meteorVel.scale(-1.5), meteorVel.normalize()));
        }
        // 更新所有圆环位置与寿命（无论是否飞行阶段）
        for (int i = tailRings.size() - 1; i >= 0; i--) {
            TailRing ring = tailRings.get(i);
            ring.position = ring.position.add(ring.velocity);
            ring.age++;
            if (ring.age >= RING_MAX_AGE) {
                tailRings.remove(i);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AoE 扩散圆环（保持阶段持续生成）
    // ═══════════════════════════════════════════════════════════════

    private void updateAoeRings() {
        int timer = entityData.get(FADE_TIMER);
        long gameTime = level().getGameTime();

        // 活跃期（展开+保持+收缩）生成新圆环，停留期只让现有圆环自然老化
        if (timer > cfgLingerDuration && gameTime % AOE_RING_INTERVAL == 0) {
            aoeRings.add(new AoeRing(position()));
        }

        for (int i = aoeRings.size() - 1; i >= 0; i--) {
            AoeRing ring = aoeRings.get(i);
            ring.age++;
            if (ring.age >= AOE_RING_MAX_AGE) {
                aoeRings.remove(i);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 地面撞击 → 网络包
    // ═══════════════════════════════════════════════════════════════

    private void onImpactGround() {
        // ── 粒子爆发（全部 END_ROD 白色发光） ──
        if (level() instanceof ServerLevel serverLevel) {
            double x = getX(), y = getY(), z = getZ();

            // 密集中心爆发
            for (int i = 0; i < 80; i++) {
                double ox = (level().random.nextDouble() - 0.5) * 1.2;
                double oy = level().random.nextDouble() * 0.3;
                double oz = (level().random.nextDouble() - 0.5) * 1.2;
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        x + ox, y + oy, z + oz, 1,
                        ox * 0.3, oy * 0.3 + 0.1, oz * 0.3, 0.03);
            }
            // 外圈扩散粒子
            for (int j = 0; j < 50; j++) {
                double angle = level().random.nextDouble() * 2.0 * Math.PI;
                double dist = Math.sqrt(level().random.nextDouble()) * GROUND_EFFECT_RADIUS;
                double px = x + Math.cos(angle) * dist;
                double pz = z + Math.sin(angle) * dist;
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        px, y + 0.05, pz, 1,
                        (level().random.nextDouble() - 0.5) * 0.2,
                        level().random.nextDouble() * 0.15,
                        (level().random.nextDouble() - 0.5) * 0.2, 0.02);
            }

            // ── 撞击点下方生成矿石 ──
            if (this.random.nextFloat() < cfgOreChance) {
                tryPlaceOreAt(serverLevel, x, y, z, RegisterBlock.DOMITE_MINERAL.get());
            } else {
                tryPlaceOreAt(serverLevel, x, y, z, RegisterBlock.DOMERITE_MINERAL.get());
            }

            // ── 撞击点周围扩散 Hall 感染方块 ──
            int spreadRadius = cfgSpreadRadiusMin + this.random.nextInt(cfgSpreadRadiusMax - cfgSpreadRadiusMin + 1);
            int count = cfgSpreadCountMin + this.random.nextInt(cfgSpreadCountMax - cfgSpreadCountMin + 1);
            BlockPos impactCenter = BlockPos.containing(x, y, z);
            for (int i = 0; i < count; i++) {
                int dx = this.random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
                int dy = this.random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
                int dz = this.random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
                BlockPos target = impactCenter.offset(dx, dy, dz);
                BlockState state = level().getBlockState(target);
                if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
                    continue;
                }
                BlockSpreadManager.applySpread(serverLevel, target);
            }

            // ── 冲击波 ──
            ShockwaveEntity.spawn(level(), position().add(0, 0.05, 0),
                    cfgShockwaveAngle, cfgShockwaveSpeed, cfgShockwaveDuration,
                    cfgShockwaveInitialScale, cfgShockwaveShrink, 0.94f);
        }
    }

    /** 在撞击点下方放置矿石方块 */
    private static void tryPlaceOreAt(ServerLevel level, double x, double y, double z, Block ore) {
        BlockPos spawnPos = BlockPos.containing(x, y, z).below();
        BlockState state = level.getBlockState(spawnPos);
        if (!state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER) && !state.isAir()) {
            level.setBlock(spawnPos, ore.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 数据同步 / 存档
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FADE_TIMER, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        hasImpacted = tag.getBoolean("impacted");
        entityData.set(FADE_TIMER, tag.getInt("fadeTimer"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("impacted", hasImpacted);
        tag.putInt("fadeTimer", entityData.get(FADE_TIMER));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ═══════════════════════════════════════════════════════════════
    // IGeoResources
    // ═══════════════════════════════════════════════════════════════

    @Override public ResourceLocation model()     { return DUMMY; }
    @Override public ResourceLocation texture()   { return DUMMY; }
    @Override public ResourceLocation animation() { return DUMMY; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ═══════════════════════════════════════════════════════════════
    // 尾部圆环数据 & 静态工厂
    // ═══════════════════════════════════════════════════════════════

    public static class TailRing {
        public Vec3 position;
        public Vec3 velocity;
        /** 圆环平面法线 = 生成时陨石速度方向 */
        public Vec3 normal;
        public int age;

        TailRing(Vec3 position, Vec3 velocity, Vec3 normal) {
            this.position = position;
            this.velocity = velocity;
            this.normal = normal;
            this.age = 0;
        }
    }

    /**
     * AoE 扩散圆环数据 — 在二十面体保持阶段从中心向外扩散的水平圆环。
     */
    public static class AoeRing {
        /** 生成时的世界坐标（不动点） */
        public Vec3 position;
        public int age;

        AoeRing(Vec3 position) {
            this.position = position;
            this.age = 0;
        }
    }

    public static MeteoriteEntity spawn(Level level, Vec3 pos, Vec3 velocity) {
        MeteoriteEntity meteor = new MeteoriteEntity(
                org.bytechen.hall.overworld.registry.EntityTypeRegistry.METEORITE.get(), level);
        meteor.setPos(pos);
        meteor.setDeltaMovement(velocity);
        level.addFreshEntity(meteor);
        return meteor;
    }
}
