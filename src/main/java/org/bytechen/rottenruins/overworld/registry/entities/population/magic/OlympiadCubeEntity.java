package org.bytechen.rottenruins.overworld.registry.entities.population.magic;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.ModUtils;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 奥数立方的投射物：一颗会自己找目标的小立方体，命中后按形态结算 ——
 * TETRA 造成 {@value #TETRA_DAMAGE} 点伤害并挂 {@code rotten_ruins:arcane_mark}（{@value #MARK_DURATION} tick）；
 * CUBE 造成「目标最大生命 × {@value #CUBE_DAMAGE_PERCENT}%」的伤害；ICO 展开成半径 {@value #EXPAND_RADIUS} 格的伤害场
 * （每 tick 随机伤害类型、无视无敌帧）。飞行阶段按有限转向速率追踪 {@value #CHASE_RANGE} 格内最近目标。
 * 用 {@code Projectile} 而非 {@code AbstractHurtingProjectile}：后者那套加速度/阻尼是给抛物线火球准备的。
 */
public class OlympiadCubeEntity extends Projectile {

    /** 三棱锥：命中挂自定义效果。 */
    public static final int FORM_TETRA = 0;

    /** 立方体：命中造成最大生命 10% 的伤害。 */
    public static final int FORM_CUBE = 1;

    /** 正二十面体（20 个正三角面）：命中展开成伤害场。 */
    public static final int FORM_ICO = 2;

    public static final int FORM_COUNT = 3;

    /** 飞行速度（格/tick）：{@value #FLY_SPEED} ⇒ 22 格/秒（原 0.55 翻倍，手感从"飘过去"变成"射过去"）。 */
    public static final double FLY_SPEED = 1.10d;

    /** 每 tick 最多转向多少（0.3 ≈ 每秒约 340°）。必须跟着速度一起调：转弯半径 ≈ 速度 / 角速度，
     *  速度翻倍而不调它就会变成 10 格以上的大圈，追 6 格内目标永远打不中；0.3 把半径拉回 3~4 格。 */
    private static final double TURN_RATE = 0.3d;

    /** 自动追击范围（格）：只找这么近的目标（原为 3 格）。 */
    public static final double CHASE_RANGE = 6.0d;

    /** 没命中的最长存活时间（tick）：4 秒。 */
    public static final int MAX_AGE = 80;

    /** 展开场的半径（格）。 */
    public static final double EXPAND_RADIUS = 8.0d;

    /** 展开场的持续时间（tick）：3 秒。 */
    public static final int EXPAND_DURATION = 60;

    /** 展开场每 tick 每目标的基础伤害。 */
    public static final float EXPAND_DAMAGE = 4.0f;

    /**
     * 立方体形态每次命中造成目标<b>最大生命的 10%</b> 作为伤害。注意是"造成这么多伤害"（当前血量减去它），
     * 不是把血量设成 10% —— 后者对残血目标是直接处决、对满血目标几乎无感，两个方向都不像"一次伤害"。
     */
    public static final float CUBE_DAMAGE_PERCENT = 10.0f;

    /** 三棱锥形态命中时额外造成的固定伤害（点），与挂上的效果叠加。 */
    public static final float TETRA_DAMAGE = 10.0f;

    /** 三棱锥形态挂的效果时长（tick）：10 秒。 */
    public static final int MARK_DURATION = 200;

    /** 正二十面体每 tick 随机抽的伤害类型池。 */
    private static final ResourceKey<DamageType>[] EXPAND_TYPES = new ResourceKey[]{
            DamageTypes.MAGIC,
            DamageTypes.FREEZE,
            DamageTypes.INDIRECT_MAGIC,
            DamageTypes.WITHER,
            DamageTypes.DRAGON_BREATH,
            DamageTypes.FALL
    };

    private static final EntityDataAccessor<Integer> DATA_FORM =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.INT);

    /** 0 = 飞行中，1 = 已展开。客户端渲染靠它切换形状大小。 */
    private static final EntityDataAccessor<Boolean> DATA_EXPANDED =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.BOOLEAN);

    /** 剩余展开时间（tick）；0 表示不在展开态。客户端用不到，可读性保留。 */
    private static final EntityDataAccessor<Integer> DATA_EXPAND_LIFE =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.INT);

    /** 命中爆开剩余 tick（>0 = 正在"快速放大后消失"）。 */
    private static final EntityDataAccessor<Integer> DATA_BURST_LIFE =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.INT);

    /** 命中爆开的总时长（tick）：8 tick = 0.4 秒，够"猛一下涨开再散掉"。 */
    public static final int BURST_TICKS = 8;

    private int age;

    /** 拖尾保留的采样点数（客户端）：{@value #TRAIL_POINTS} tick 的历史轨迹。 */
    public static final int TRAIL_POINTS = 12;

    /**
     * <b>客户端专用</b>的轨迹采样（从旧到新），供 {@code OlympiadCubeRenderer} 画拖尾。
     * 存在实体上而不是渲染器里：渲染无状态，没地方安全地攒历史；实体自己有 tick，顺手就记下了。
     * 不参与同步、不进存档，服务端那份永远是空的。
     */
    private final Deque<Vec3> clientTrail = new ArrayDeque<>();

    public OlympiadCubeEntity(EntityType<? extends OlympiadCubeEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_FORM, FORM_TETRA);
        entityData.define(DATA_EXPANDED, false);
        entityData.define(DATA_EXPAND_LIFE, 0);
        entityData.define(DATA_BURST_LIFE, 0);
    }

    public int getForm() {
        return entityData.get(DATA_FORM);
    }

    public void setForm(int form) {
        entityData.set(DATA_FORM, Math.floorMod(form, FORM_COUNT));
    }

    public boolean isExpanded() {
        return entityData.get(DATA_EXPANDED);
    }

    private void setExpanded(boolean expanded) {
        entityData.set(DATA_EXPANDED, expanded);
    }

    public int getExpandLife() {
        return entityData.get(DATA_EXPAND_LIFE);
    }

    /** 命中爆开剩余 tick；>0 表示正在播放"快速放大后消失"。 */
    public int getBurstLife() {
        return entityData.get(DATA_BURST_LIFE);
    }

    public boolean isBursting() {
        return getBurstLife() > 0;
    }

    private void setBurstLife(int ticks) {
        entityData.set(DATA_BURST_LIFE, Math.max(0, ticks));
    }

    /** 命中后进入爆开态：停止运动，播放完自行消失。 */
    private void beginBurst() {
        setDeltaMovement(Vec3.ZERO);
        setBurstLife(BURST_TICKS);
    }

    private void setExpandLife(int ticks) {
        entityData.set(DATA_EXPAND_LIFE, ticks);
    }

    /**
     * 从施法者视线方向发射一颗。yawGap 是相对视线的偏转角（度），副弹用它错开、别和主弹叠在一起。
     */
    public static OlympiadCubeEntity spawn(@NotNull Player caster, @NotNull Level level,
                                           int form, float yawGap) {
        Vec3 look = caster.getViewVector(1.0f);
        if (yawGap != 0.0f) {
            look = look.yRot((float) Math.toRadians(yawGap)).normalize();
        }

        OlympiadCubeEntity cube = new OlympiadCubeEntity(
                org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry.OLYMPIAD_CUBE.get(),
                level);
        Vec3 pos = caster.getEyePosition().add(look.scale(0.6d));
        cube.setPos(pos.x, pos.y, pos.z);
        cube.setOwner(caster);
        cube.setForm(form);
        cube.setDeltaMovement(look.scale(FLY_SPEED));
        cube.setYRot((float) (Math.atan2(look.x, look.z) * 180.0d / Math.PI));
        cube.setXRot((float) (-Math.asin(look.y) * 180.0d / Math.PI));
        level.addFreshEntity(cube);
        return cube;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickClientTrail();
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }

        // 命中爆开：只倒计时，别的逻辑全部停掉，播完就消失
        if (isBursting()) {
            int left = getBurstLife() - 1;
            setBurstLife(left);
            if (left <= 0) {
                discard();
            }
            return;
        }

        if (isExpanded()) {
            tickExpanded(level);
            return;
        }

        age++;
        if (age > MAX_AGE) {
            discard();
            return;
        }

        chase(level);
        moveAndCollide(level);
    }

    /** 飞行阶段：找 CHASE_RANGE 内最近的目标，把速度方向慢慢转过去。 */
    private void chase(@NotNull ServerLevel level) {
        LivingEntity target = nearestTarget(level);
        if (target == null) {
            return;
        }
        Vec3 toTarget = target.getBoundingBox().getCenter()
                .subtract(position()).normalize().scale(FLY_SPEED);
        Vec3 motion = getDeltaMovement();
        // 限速转向：每 tick 只往目标方向靠 TURN_RATE，读起来像"被吸过去"而不是"瞬间锁定"
        Vec3 blended = motion.scale(1.0d - TURN_RATE).add(toTarget.scale(TURN_RATE)).normalize()
                .scale(FLY_SPEED);
        setDeltaMovement(blended);
    }

    /** CHASE_RANGE 内最近的可打击目标；排除主人/旁观者/已死，见 {@link #canHit}。 */
    @Nullable
    private LivingEntity nearestTarget(@NotNull ServerLevel level) {
        Entity owner = getOwner();
        LivingEntity best = null;
        double bestDistSqr = CHASE_RANGE * CHASE_RANGE;

        for (LivingEntity candidate : EntityChooseUtil.sphereAt(level, owner, position(), CHASE_RANGE)) {
            if (candidate == owner || !candidate.isAlive() || !canHit(candidate)) {
                continue;
            }
            double distSqr = candidate.getBoundingBox().getCenter().distanceToSqr(position());
            if (distSqr <= bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }
        return best;
    }

    /** 移动并做撞击检测（方块 → 消失；生物 → {@link #onHitEntity}）。 */
    private void moveAndCollide(@NotNull ServerLevel level) {
        Vec3 from = position();
        Vec3 motion = getDeltaMovement();
        Vec3 to = from.add(motion);
        setPos(to.x, to.y, to.z);

        // 原版的写法：先算方块射线，再算生物射线，取更近的那个
        HitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, from, to,
                getBoundingBox().expandTowards(motion).inflate(1.0d),
                e -> e instanceof LivingEntity living && canHit(living));

        if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS
                || from.distanceToSqr(entityHit.getLocation()) <= from.distanceToSqr(blockHit.getLocation()))) {
            onHitEntity(entityHit);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            // 撞墙就地消失，避免一路穿墙找目标
            level.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.6f);
            discard();
        }
    }

    /** 给渲染器读的轨迹采样（从旧到新，世界坐标）：只在客户端有内容，只读不要改它。 */
    @NotNull
    public Deque<Vec3> getClientTrail() {
        return clientTrail;
    }

    /** 客户端每 tick 采样一次位置。以前撒的是潜影贝的 END_ROD 拖尾粒子，换成自记轨迹后形状/粗细/颜色可控
     *  且能跟着追踪曲线拐弯；展开成领域后没有"飞行"这回事，直接把轨迹清掉。 */
    private void tickClientTrail() {
        if (isExpanded()) {
            clientTrail.clear();
            return;
        }
        clientTrail.addLast(position());
        while (clientTrail.size() > TRAIL_POINTS) {
            clientTrail.removeFirst();
        }
    }

    /** 展开阶段：每 tick 对范围内目标造成一次随机类型的伤害（清无敌帧）。 */
    private void tickExpanded(@NotNull ServerLevel level) {
        int left = getExpandLife() - 1;
        setExpandLife(left);

        // 每 tick 抽一个伤害类型，这一 tick 里所有目标共用（读起来是"这一下是火/冰/虚空"）
        Holder<DamageType> type = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(EXPAND_TYPES[random.nextInt(EXPAND_TYPES.length)]);
        Entity owner = getOwner();
        DamageSource source = new DamageSource(type, owner, owner);

        AABB box = new AABB(position(), position()).inflate(EXPAND_RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator()
                        && e.getBoundingBox().distanceToSqr(position()) <= EXPAND_RADIUS * EXPAND_RADIUS);
        for (LivingEntity victim : victims) {
            // 每 tick 一下必然落在原版 20 tick 无敌帧里，不清就只剩第一下
            SpellDamageUtil.prepareRepeatHit(victim);
            // 带上施法者：击杀归因 / 掉落表的 killer 条件 / 进度都靠它
            SpellDamageUtil.hurt(victim, source, ItemStack.EMPTY, casterOrNull(), EXPAND_DAMAGE);
        }

        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    getX(), getY(), getZ(), 12, 1.2d, 1.2d, 1.2d, 0.05d);
        }
        if (left <= 0) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level)
                || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }

        switch (getForm()) {
            case FORM_TETRA -> {
                // 先打固定伤害，再挂印记；带印记的目标每次受伤会被随机塞一个负面效果，那一半在 ArcaneMarkHandler 里
                SpellDamageUtil.prepareRepeatHit(target);
                SpellDamageUtil.hurt(target, attackSourceOf(target), ItemStack.EMPTY,
                        casterOrNull(), TETRA_DAMAGE);

                if (target.isAlive()) {
                    boolean applied = CustomEffects.addEffect(target,
                            RegisterCustomEffect.ARCANE_MARK, MARK_DURATION, 0);
                    if (!applied) {
                        // 印记没挂上就等于"这次命中的后续联动全废"，必须能看见原因
                        ModUtils.LOGGER.warn("[OlympiadCube] 印记未能施加到 {}：容器={} 存活={} 可按={}",
                                target.getType().toShortString(),
                                CustomEffects.hasHolder(target), target.isAlive(),
                                RegisterCustomEffect.ARCANE_MARK.canApply(target));
                    }
                } else {
                    ModUtils.LOGGER.info("[OlympiadCube] {} 被这一下打死，不再挂印记",
                            target.getType().toShortString());
                }
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
                level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                        target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        18, 0.5d, 0.6d, 0.5d, 0.15d);
                beginBurst();   // 命中特效：原地快速放大后消失
            }
            case FORM_CUBE -> {
                // 造成「最大生命 × 10%」的伤害。走 SpellDamageUtil：内部直接改血（不钳制）并补击杀归因，
                // 掉落 / 经验 / killer 条件因此与普通击杀一致。
                float damage = target.getMaxHealth() * CUBE_DAMAGE_PERCENT / 100.0f;
                SpellDamageUtil.hurt(target, attackSourceOf(target), ItemStack.EMPTY,
                        casterOrNull(), damage);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 0.9f);
                level.sendParticles(ParticleTypes.CRIT, target.getX(),
                        target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        14, 0.4d, 0.5d, 0.4d, 0.2d);
                beginBurst();   // 命中特效：原地快速放大后消失
            }
            case FORM_ICO -> {
                // 不消失，展开成伤害场
                setExpanded(true);
                setExpandLife(EXPAND_DURATION);
                setDeltaMovement(Vec3.ZERO);
                level.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.7f);
            }
            default -> discard();
        }
    }

    /** 攻击来源：主人是生物就用 {@code indirectMagic}（带攻击者，击杀归因与掉落 killer 条件才认得出），
     *  否则退化成环境 magic。 */
    @NotNull
    private DamageSource attackSourceOf(@NotNull LivingEntity target) {
        Entity owner = getOwner();
        if (owner instanceof LivingEntity) {
            return owner.damageSources().indirectMagic(owner, owner);
        }
        return target.damageSources().magic();
    }

    /** 主人（必须是 LivingEntity 才有意义），可能为 {@code null}。 */
    @Nullable
    private LivingEntity ownerOrNull() {
        return getOwner() instanceof LivingEntity living ? living : null;
    }

    /** 主人是玩家时返回它 —— 给 SpellDamageUtil 补击杀归因（掉落 killer 条件依赖它）。 */
    @Nullable
    private net.minecraft.server.level.ServerPlayer casterOrNull() {
        return getOwner() instanceof net.minecraft.server.level.ServerPlayer player ? player : null;
    }

    @Override
    protected boolean canHitEntity(@NotNull Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        return target instanceof LivingEntity living && canHit(living);
    }

    /** 能不能打这个生物：不能打主人、不能打旁观者、不能打已死的。 */
    private boolean canHit(@NotNull LivingEntity target) {
        return target != getOwner()
                && target.isAlive()
                && !target.isSpectator();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Form", getForm());
        tag.putBoolean("Expanded", isExpanded());
        tag.putInt("ExpandLife", getExpandLife());
        tag.putInt("Age", age);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setForm(tag.getInt("Form"));
        setExpanded(tag.getBoolean("Expanded"));
        setExpandLife(tag.getInt("ExpandLife"));
        age = tag.getInt("Age");
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 4096.0d;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
