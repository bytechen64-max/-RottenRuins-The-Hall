package org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.items.CrimsonVow;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 绯红誓约 · <b>蓄力法阵</b>（玩家身下那一圈）。
 *
 * <h3>为什么它是一个实体，而不是客户端的一层渲染</h3>
 * <p>蓄力是一个<b>有服务端后果</b>的过程（蓄满要放技能），所以进度必须由服务端持有；
 * 而"身下渐渐扩散运动的法阵"又要每帧平滑地画出来。把两者缝在一起的最省事做法
 * 就是<b>一个跟着玩家走的技能实体</b>：</p>
 * <ul>
 *   <li>进度 {@code DATA_CHARGE} 走同步数据 —— 不需要任何新网络包；</li>
 *   <li>渲染侧拿到 {@code charge} / {@code radius} 两个同步量，
 *       再用 {@code partialTick} 在"上一 tick 的值 → 这一 tick 的值"之间插值，
 *       于是 20Hz 的同步在画面上是连续的（这正是"记得用部分 tick 插值"那条要求）；</li>
 *   <li>它挂在 {@code CrimsonVowBlockEntity} 之外 —— 也就是说
 *       <b>同一套蓄力可以被任何一把剑复用</b>，不必把逻辑写进物品类。</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@code CrimsonVow.onBlockStart} 在<b>蹲下</b>时生成本实体；</li>
 *   <li>每 tick 检查"主人是否还在用这把剑格挡" —— 松手 / 换手 / 死亡
 *       都会让法阵立刻散掉（{@link #discard()}），不留残影；</li>
 *   <li>蓄满（{@link #CHARGE_TICKS}）后在本实体里做射线选点、生成
 *       {@link CrimsonVowFieldEntity}、并把玩家的使用状态收掉。</li>
 * </ol>
 *
 * <h3>为什么"射线选点"写在这里</h3>
 * <p>它是"蓄满之后立刻要发生的那件事"，与蓄力进度是同一条时间线上的东西；
 * 拆到物品类里会让 {@code CrimsonVow} 同时承担格挡、蓄力、选点、放技能四件事。
 * 物品类只负责"开启蓄力"，其余归这里。</p>
 */
public class CrimsonVowChargeEntity extends Entity {

    // ── 同步数据 ──────────────────────────────────────────────────
    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(CrimsonVowChargeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(CrimsonVowChargeEntity.class, EntityDataSerializers.FLOAT);

    // ── 数值 ──────────────────────────────────────────────────────

    /** 蓄满需要的 tick 数（3 秒）。 */
    public static final int CHARGE_TICKS = 60;

    /** 起手时的法阵半径（格）。 */
    public static final float MIN_RADIUS = 0.85f;
    /** 蓄满时的法阵半径（格）。 */
    public static final float MAX_RADIUS = 4.6f;

    /** 射线最长找多远（格）。 */
    public static final double AIM_RANGE = 42.0;
    /** 落点找地面时向下最多扫多少格。 */
    private static final int GROUND_SCAN = 24;

    /** 生成后的存活上限（tick）—— 兜底，正常路径由 {@link #tick()} 提前结束。 */
    private static final int MAX_LIFE = CHARGE_TICKS + 200;

    private UUID ownerUUID;
    private int age;
    private int chargeTicks;

    // ── 客户端插值缓存 ────────────────────────────────────────────
    //
    //  同步数据只有 20Hz，直接拿去画的话法阵会以每秒 20 次的台阶向外跳。
    //  这里保存"上一 tick 收到的值"和"这一 tick 收到的值"，
    //  渲染时按 partialTick 在两者之间线性插值 —— 于是画面上是连续的。
    private float prevCharge, shownCharge;
    private float prevRadius, shownRadius;

    public CrimsonVowChargeEntity(EntityType<?> type, Level level) {
        super(type, level);
        // 必须：实体的碰撞箱只有 0.5，不代表它的视觉范围（法阵有几格宽），
        // 关掉剔除否则法阵会随视锥剔除"啪"地消失。
        this.noCulling = true;
    }

    // ══════════════════════════════════════════════════════════════
    //  同步数据
    // ══════════════════════════════════════════════════════════════

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_CHARGE, 0f);
        entityData.define(DATA_RADIUS, MIN_RADIUS);
    }

    public float getCharge() { return entityData.get(DATA_CHARGE); }
    public float getRadius() { return entityData.get(DATA_RADIUS); }
    public int getAge()      { return age; }

    /** 带部分 tick 插值的蓄力进度 0 → 1（渲染侧用）。 */
    public float charge(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0f, 1f), prevCharge, shownCharge);
    }

    /** 带部分 tick 插值的法阵半径（渲染侧用）。 */
    public float radius(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0f, 1f), prevRadius, shownRadius);
    }

    public void setOwner(@Nullable Entity owner) {
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    /** 起阵者的 UUID（客户端渲染侧靠它认出"这是我自己在蓄力"）。 */
    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nullable
    public Player getOwner() {
        if (ownerUUID == null) return null;
        return level().getPlayerByUUID(ownerUUID);
    }

    // ══════════════════════════════════════════════════════════════
    //  Tick
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        if (isPassenger() && getVehicle() != null && !getVehicle().isPassengerOfSameVehicle(this)) {
            stopRiding();
        }
        age++;

        if (level().isClientSide()) {
            // 客户端只做插值，不做任何逻辑（逻辑全在服务端，见类注释）。
            prevCharge = shownCharge;
            shownCharge = getCharge();
            prevRadius = shownRadius;
            shownRadius = getRadius();
            return;
        }

        Player owner = getOwner();

        // ── 有效性：主人必须还在"举着绯红誓约格挡" ──
        //
        //  松手、切物品、死亡、换维度都会在这里被判掉。
        //  用 IBlockingWeapon.isBlocking 而不是自己读 useItem —— 与减伤判定共用同一个谓词，
        //  不会出现"画面还在蓄力、格挡其实已经没了"这种不一致。
        if (owner == null || !owner.isAlive()
                || !IBlockingWeapon.isBlocking(owner)
                || !(owner.getUseItem().getItem() instanceof CrimsonVow)) {
            discard();
            return;
        }

        if (age > MAX_LIFE) { discard(); return; }

        chargeTicks++;

        float c = Mth.clamp(chargeTicks / (float) CHARGE_TICKS, 0f, 1f);
        entityData.set(DATA_CHARGE, c);
        entityData.set(DATA_RADIUS, Mth.lerp(c, MIN_RADIUS, MAX_RADIUS));

        // 法阵跟着玩家走。用脚底而不是眼睛：它是一圈贴地的阵。
        setPos(owner.getX(), owner.getY(), owner.getZ());

        chargeFeedback(owner, c);

        if (chargeTicks >= CHARGE_TICKS) {
            release(owner, c);
        }
    }

    /** 蓄力期间的声音与粒子（服务端）。 */
    private void chargeFeedback(Player owner, float c) {
        // 进度音：每 8 tick 一声，音调随进度升高 —— 不看屏幕也能听出蓄到几成。
        if (chargeTicks % 8 == 0) {
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                    0.35f + 0.25f * c, 0.7f + 0.75f * c);
        }
        // 满蓄前 10 tick 开始"过载"：连续的短促高音 + 火花。
        if (c > 0.83f && chargeTicks % 2 == 0) {
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.30f, 1.4f + 0.5f * c);
        }
        if (level() instanceof ServerLevel serverLevel) {
            double r = Math.max(0.6, getRadius());
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    owner.getX(), owner.getY() + 0.12, owner.getZ(),
                    1 + (int) (c * 7.0), r * 0.35, 0.05, r * 0.35, 0.01);
            if (chargeTicks % 4 == 0) {
                double a = chargeTicks * 0.42;
                serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                        owner.getX() + Math.cos(a) * r, owner.getY() + 1.0, owner.getZ() + Math.sin(a) * r,
                        2, 0.1, 0.4, 0.1, 0.02);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  蓄满：选点 + 落地
    // ══════════════════════════════════════════════════════════════

    /**
     * 蓄满的那一刻：沿<b>视线方向</b>选一个落点，在那里展开巨型法阵。
     *
     * <h3>选点规则</h3>
     * <ol>
     *   <li>从眼睛沿视线做一次方块射线（{@link #AIM_RANGE} 格）；</li>
     *   <li>打中了 → 以命中点为圆心。若命中面朝上（站在地面/台地上对着地看），
     *       直接取那块方块的顶面；否则从命中点往下扫一次地面，避免法阵挂在墙中间；</li>
     *   <li>没打中（对着天空放）→ 取射线末端，再从那里往下扫地面；
     *       扫不到（悬空/虚空）就用射线末端自己那一层。</li>
     * </ol>
     *
     * <p>这一步是"释放方向由玩家决定"的全部实现 —— 没有它，巨型法阵就只能放在脚下，
     * 而"视线指向的方向"是需求里明确写了的。</p>
     */
    private void release(Player owner, float charge) {
        Vec3 eye = owner.getEyePosition();
        Vec3 dir = owner.getLookAngle();
        Vec3 end = eye.add(dir.scale(AIM_RANGE));

        BlockHitResult hit = level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));

        Vec3 center;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = hit.getBlockPos();
            double y = groundYBelow(level(), bp.getX(), bp.getZ(), bp.getY() + 2);
            if (Double.isNaN(y)) y = bp.getY() + 1.0;
            center = new Vec3(hit.getLocation().x, y + 0.02, hit.getLocation().z);
        } else {
            double y = groundYBelow(level(), Mth.floor(end.x), Mth.floor(end.z), Mth.floor(end.y));
            if (Double.isNaN(y)) y = end.y;
            center = new Vec3(end.x, y + 0.02, end.z);
        }

        CrimsonVowFieldEntity field = CrimsonVowFieldEntity.spawn(
                level(), owner, center, CrimsonVowFieldEntity.DEFAULT_RADIUS,
                CrimsonVowFieldEntity.DEFAULT_MAX_AGE);

        // ── 落地表现 ──
        float radius = field != null ? field.getRadius() : CrimsonVowFieldEntity.DEFAULT_RADIUS;
        Vec3 fx = center.add(0, 0.25, 0);
        VerdictFeedback.impactRing(level(), fx, radius * 1.15f, radius * 0.95f, 1.0f);
        VerdictFeedback.playSound(level(), fx, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.85f);
        VerdictFeedback.playSound(level(), fx, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.6f);
        VerdictFeedback.burst(level(), fx, ParticleTypes.SOUL_FIRE_FLAME, 60, 0.55);
        VerdictFeedback.burst(level(), fx, ParticleTypes.CRIMSON_SPORE, 80, 0.85);
        // 落点在远处时，用带距离衰减的抖动 —— 近处才晃得狠。
        VerdictFeedback.shakeNear(owner, fx, 0.85f);
        VerdictFeedback.fovKick(owner, -2.6f);

        CrimsonVow.beginSkillCooldown(owner);

        // 先收使用状态再散掉自己：releaseUsing 会走 onBlockStop，
        // 而 onBlockStop 只是日志，不会重复触发这里（本实体已 discard）。
        owner.stopUsingItem();
        discard();
    }

    /**
     * 从 {@code (x,z)} 的 {@code fromY} 开始往下找第一个可站立表面，返回它的顶面 y。
     *
     * <p>用碰撞盒顶面而不是 {@code by+1}：台阶、栅栏、雪层的顶面与整格不同，
     * 只有碰撞盒高度才贴得准 —— 这与 {@code VerdictFieldRenderer} 里那套扫描同源。</p>
     *
     * @return 找不到时返回 {@link Double#NaN}
     */
    private static double groundYBelow(Level level, int x, int z, int fromY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int limit = fromY - GROUND_SCAN;
        for (int y = fromY; y >= limit; y--) {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) return Double.NaN;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            return y + shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return Double.NaN;
    }

    // ══════════════════════════════════════════════════════════════
    //  实体样板
    // ══════════════════════════════════════════════════════════════

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        chargeTicks = tag.getInt("Charge");
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Charge01")) entityData.set(DATA_CHARGE, tag.getFloat("Charge01"));
        if (tag.contains("Radius")) entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("Charge", chargeTicks);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putFloat("Charge01", getCharge());
        tag.putFloat("Radius", getRadius());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

    /** 在玩家脚下起阵。同一玩家已有法阵时先把旧的散掉（不会叠两个）。 */
    public static CrimsonVowChargeEntity spawn(Level level, Player owner) {
        for (CrimsonVowChargeEntity old : level.getEntitiesOfClass(
                CrimsonVowChargeEntity.class, owner.getBoundingBox().inflate(6.0))) {
            if (owner.getUUID().equals(old.ownerUUID)) old.discard();
        }

        CrimsonVowChargeEntity e = new CrimsonVowChargeEntity(
                EntityTypeRegistry.CRIMSON_VOW_CHARGE.get(), level);
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        e.setOwner(owner);
        e.entityData.set(DATA_CHARGE, 0f);
        e.entityData.set(DATA_RADIUS, MIN_RADIUS);
        level.addFreshEntity(e);
        return e;
    }
}
