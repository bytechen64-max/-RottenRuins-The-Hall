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
import org.bytechen.rottenruins.utils.entity.SetHealthDieUnti;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * <b>奥数立方</b>的投射物 —— 一颗会自己找目标的小立方体，有三种形态。
 *
 * <h3>三种形态</h3>
 * <table border="1">
 *   <tr><th>形态</th><th>命中后</th></tr>
 *   <tr><td>{@link #FORM_TETRA 三棱锥}</td>
 *       <td>造成 {@value #TETRA_DAMAGE} 点伤害，并给目标挂 {@code rotten_ruins:arcane_mark}
 *       自定义效果 {@value #MARK_DURATION} tick（10 秒）；</td></tr>
 *   <tr><td>{@link #FORM_CUBE 立方体}</td>
 *       <td>造成「目标最大生命 × {@value #CUBE_DAMAGE_PERCENT}%」的伤害
 *       （{@link SetHealthDieUnti#setData}，不钳制）；</td></tr>
 *   <tr><td>{@link #FORM_ICO 正二十面体}</td>
 *       <td>立刻展开成半径 {@value #EXPAND_RADIUS} 格的场，存活 {@value #EXPAND_DURATION} tick（3 秒），
 *           每 tick 对范围内目标造成<b>随机类型</b>的伤害、<b>无视无敌帧</b>
 *           —— 20 个面都是正三角形的正二十面体。</td></tr>
 * </table>
 *
 * <h3>自动追击</h3>
 * <p>飞行阶段每 tick 找 {@value #CHASE_RANGE} 格内最近的可打击目标，把速度方向<b>转向</b>它
 * （不是瞬移，也不是瞬间掉头：用一个有限的转向速率，读起来像"被吸引"而不是"锁定"）。
 * 找不到目标就沿原方向继续飞。</p>
 *
 * <h3>为什么用 {@code Projectile} 而不是 {@code AbstractHurtingProjectile}</h3>
 * <p>后者的加速度/阻尼曲线是给火球那种"抛物线感"准备的，而这里的立方体是<b>匀速追踪</b>，
 * 自己管速度更直观；同时不需要它那套 {@code xPower/yPower/zPower}。</p>
 */
public class OlympiadCubeEntity extends Projectile {

    // ══════════════════════════════════════════════════════════════
    // 形态
    // ══════════════════════════════════════════════════════════════

    /** 三棱锥：命中挂自定义效果。 */
    public static final int FORM_TETRA = 0;

    /** 立方体：命中把血量改成最大生命的 10%。 */
    public static final int FORM_CUBE = 1;

    /** 正二十面体（20 个正三角面）：命中展开成伤害场。 */
    public static final int FORM_ICO = 2;

    /** 形态总数。 */
    public static final int FORM_COUNT = 3;

    // ── 可调数值 ──

    /**
     * 飞行速度（格/tick）：{@value #FLY_SPEED} ⇒ 22 格/秒。
     *
     * <p>原为 0.55（11 格/秒），翻倍后手感从"飘过去"变成"射过去"，
     * 配合翻倍的索敌范围（{@link #CHASE_RANGE}）也能在更远处就开始拐。</p>
     */
    public static final double FLY_SPEED = 1.10d;

    /**
     * 每 tick 最多转向多少（0.3 ≈ 每秒能拐约 340°）。
     *
     * <p>这个值<b>必须</b>跟着速度一起调：转向速率不变而速度翻倍，转弯半径会跟着翻倍
     * （半径 ≈ 速度 / 角速度），在 22 格/秒下就变成 10 格以上的大圈 ——
     * 追 6 格内的目标会绕着它转圈永远打不中。0.3 把转弯半径拉回 3~4 格，
     * 与 {@link #CHASE_RANGE} 同量级。</p>
     */
    private static final double TURN_RATE = 0.3d;

    /**
     * 自动追击范围（格）：只找这么近的目标。原为 3 格，翻倍到 6 格。
     */
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
     * 立方体形态每次命中造成的伤害：目标<b>最大生命的 10%</b>。
     *
     * <p>注意是"造成这么多伤害"（当前血量减去它），<b>不是</b>把血量<b>设成</b> 10%
     * —— 后者对残血目标是直接处决、对满血目标却几乎无感，两个方向都不像"一次伤害"。</p>
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

    // ══════════════════════════════════════════════════════════════
    // 同步数据
    // ══════════════════════════════════════════════════════════════

    private static final EntityDataAccessor<Integer> DATA_FORM =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.INT);

    /** 0 = 飞行中，1 = 已展开。客户端渲染靠它切换形状大小。 */
    private static final EntityDataAccessor<Boolean> DATA_EXPANDED =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.BOOLEAN);

    /** 剩余展开时间（tick）；0 表示不在展开态。客户端用不到，可读性保留。 */
    private static final EntityDataAccessor<Integer> DATA_EXPAND_LIFE =
            SynchedEntityData.defineId(OlympiadCubeEntity.class, EntityDataSerializers.INT);

    private int age;

    /** 拖尾保留的采样点数（客户端）：{@value #TRAIL_POINTS} tick 的历史轨迹。 */
    public static final int TRAIL_POINTS = 12;

    /**
     * <b>客户端专用</b>的轨迹采样（从旧到新），供 {@code OlympiadCubeRenderer} 画拖尾。
     *
     * <p>为什么存在实体上而不是渲染器里：渲染是无状态的（每帧都可能换视锥），
     * 没有地方安全地攒历史；而实体自己有 tick，客户端也照样 tick —— 顺手就把
     * "最近 12 tick 飞到过哪里"记下来了。它<b>不参与同步、不进存档</b>，
     * 服务端那份永远是空的。</p>
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
    }

    // ══════════════════════════════════════════════════════════════
    // 访问器
    // ══════════════════════════════════════════════════════════════

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

    private void setExpandLife(int ticks) {
        entityData.set(DATA_EXPAND_LIFE, ticks);
    }

    // ══════════════════════════════════════════════════════════════
    // 生成
    // ══════════════════════════════════════════════════════════════

    /**
     * 发射一颗。
     *
     * @param form   形态（{@link #FORM_TETRA} / {@link #FORM_CUBE} / {@link #FORM_ICO}）
     * @param yawGap 相对施法者视线偏转的角度（度）；副弹用它错开一点，别和主弹叠在一起
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

    // ══════════════════════════════════════════════════════════════
    // Tick
    // ══════════════════════════════════════════════════════════════

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

    /** 飞行阶段：找 3 格内最近的目标，把速度方向慢慢转过去。 */
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

    /**
     * 3 格内最近的可打击目标。排除：主人、旁观者、已死、以及"目标是自己人"的情况交给
     * {@link #canHit}。用 {@link EntityChooseUtil#sphereAt} 拿初筛列表再挑最近。
     */
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

    // ══════════════════════════════════════════════════════════════
    // 拖尾（客户端）
    // ══════════════════════════════════════════════════════════════

    /**
     * 给渲染器读的轨迹采样（从旧到新，世界坐标）。<b>只在客户端有内容</b>，
     * 且只应由渲染器读、不要改它。
     */
    @NotNull
    public Deque<Vec3> getClientTrail() {
        return clientTrail;
    }

    /**
     * 客户端每 tick 采样一次位置。
     *
     * <p>以前这一段撒的是 {@code ParticleTypes.END_ROD} —— 那是<b>潜影贝子弹</b>的拖尾粒子，
     * 借来的观感。现在改成自己记轨迹、由 {@code OlympiadCubeRenderer} 画一条细长拖尾：
     * 形状、粗细、颜色全都可控，而且能跟着真实的追踪曲线拐弯（粒子做不到，它们是散的）。</p>
     *
     * <p>展开成领域后没有"飞行"这回事，直接把轨迹清掉。</p>
     */
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
            // 无伤无敌帧：每 tick 一下必然落在原版 20 tick 无敌帧里，不清就只剩第一下
            SpellDamageUtil.prepareRepeatHit(victim);
            SpellDamageUtil.hurt(victim, source, ItemStack.EMPTY, null, EXPAND_DAMAGE);
        }

        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    getX(), getY(), getZ(), 12, 1.2d, 1.2d, 1.2d, 0.05d);
        }
        if (left <= 0) {
            discard();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 命中
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level)
                || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }

        switch (getForm()) {
            case FORM_TETRA -> {
                // 三棱锥：先打 10 点伤害，再挂 10 秒印记。
                // 带印记的目标每次受伤都会再被随机塞一个负面效果，
                // 那一半逻辑在 ArcaneMarkHandler（LivingHurtEvent）里。
                SpellDamageUtil.prepareRepeatHit(target);
                SpellDamageUtil.hurt(target, attackSourceOf(target), ItemStack.EMPTY, null, TETRA_DAMAGE);

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
                discard();
            }
            case FORM_CUBE -> {
                // 立方体：造成「最大生命 × 10%」的伤害（当前血量 - 这么多），
                // 走 SetHealthDieUnti.setData：不钳制，所以残血目标会被这一下写死；
                // 该方法内部只在血量真的 ≤0 时才补 die()，掉落与击杀归因照常。
                float damage = target.getMaxHealth() * CUBE_DAMAGE_PERCENT / 100.0f;
                float remaining = target.getHealth() - damage;
                SetHealthDieUnti.setData(target, ownerOrNull(), remaining);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 0.9f);
                level.sendParticles(ParticleTypes.CRIT, target.getX(),
                        target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        14, 0.4d, 0.5d, 0.4d, 0.2d);
                discard();
            }
            case FORM_ICO -> {
                // 正二十面体：不消失，展开成半径 8 格的场
                setExpanded(true);
                setExpandLife(EXPAND_DURATION);
                setDeltaMovement(Vec3.ZERO);
                level.playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.7f);
            }
            default -> discard();
        }
    }

    /**
     * 这颗立方体的"攻击来源"：主人是生物就用 {@code indirectMagic}（带攻击者，
     * 所以击杀归因、掉落 killer 条件都认得出），否则退化成环境 magic。
     */
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

    // ── 命中判定 ──

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

    // ── 客户端表现 ──
    //  拖尾已经改成"记轨迹 + 渲染器自己画"（见 tickClientTrail / OlympiadCubeRenderer），
    //  原先那颗借来的潜影贝 END_ROD 粒子已经删掉了。

    // ══════════════════════════════════════════════════════════════
    // 杂项
    // ══════════════════════════════════════════════════════════════

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
