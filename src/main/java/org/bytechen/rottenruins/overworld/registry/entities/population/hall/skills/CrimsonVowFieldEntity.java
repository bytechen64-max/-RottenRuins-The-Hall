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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 绯红誓约 · 巨型法阵（蓄满之后放出去的那一个，持续 20 秒）：贴地法阵盘、中心悬浮的液态铬、正上方的方块扭曲柱、
 * 周期扩散波，最后由天而降的光柱落地并炸开一整片地形。
 *
 * <p>地形破坏必须分帧：半径 11 格、深 10 格的碗形坑约 4000+ 个方块，一 tick 全炸会让服务端卡几百毫秒，
 * 把这一招最有分量的那一下毁掉。所以先收集、按到阵心的水平距离排序，再每 tick 消化 {@link #BLOCKS_PER_TICK} 个 ——
 * 由内向外扫读感最好，预算也恒定。实体寿命因此可能超过 {@link #DEFAULT_MAX_AGE}：破坏队列吃完才散，
 * 否则玩家会看到"坑挖到一半没了"。</p>
 */
public class CrimsonVowFieldEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(CrimsonVowFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(CrimsonVowFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PULSE =
            SynchedEntityData.defineId(CrimsonVowFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(CrimsonVowFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(CrimsonVowFieldEntity.class, EntityDataSerializers.INT);

    public static final int PHASE_DEPLOY = 0;
    public static final int PHASE_SUSTAIN = 1;
    public static final int PHASE_FINALE = 2;
    public static final int PHASE_IMPACT = 3;

    /** 默认半径（格）：直径 28 格。 */
    public static final float DEFAULT_RADIUS = 14.0f;
    /** 默认持续（tick）= 20 秒。需求写死的值，别随手改。 */
    public static final int DEFAULT_MAX_AGE = 400;

    public static final int DEPLOY_TICKS = 10;
    /** 扩散波间隔（tick）= 1.2 秒。 */
    public static final int WAVE_INTERVAL = 24;
    /** 终末阶段总长（tick）= 3.5 秒。 */
    public static final int FINALE_TICKS = 70;
    /** 终末的预警拍（tick，1.5 秒）：短于 20 读成"闪了一下"，长于 45 像卡住；30 是"注意到 → 抬头 → 它来了"的三拍。 */
    public static final int WARNING_TICKS = 30;
    /** 光柱下落耗时（tick，14 = 0.7 秒）。⚠ 必须与客户端 {@code beamFall} 共用同一个值，否则会出现"画面已落地、伤害没结算"或无声掉血。 */
    public static final int BEAM_FALL_TICKS = 14;

    /** 光柱起点离地高度（格）—— 渲染侧与冲击反馈都用它。 */
    public static final float BEAM_TOP = 70.0f;
    /** 柱体自身长度（格）：少了它柱顶会被算成也落到 0，整根柱子在最后几 tick 缩成一个点，落地那帧反而是空的。 */
    public static final float BEAM_LENGTH = 34.0f;

    /** 每 tick 最多破坏多少个方块。 */
    public static final int BLOCKS_PER_TICK = 150;
    public static final float CRATER_RATIO = 0.80f;
    public static final int CRATER_DEPTH = 10;
    /** 破坏范围向上额外抬起几格（把地面上的建筑一并抹平）。 */
    public static final int CRATER_LIFT = 3;

    /** 单次扩散波与终末那一下的伤害。 */
    public static final float WAVE_HURT = 5.0f;
    public static final float IMPACT_HURT = 70.0f;

    /** 淡入 / 淡出（tick）。 */
    public static final float FADE_IN_TICKS = 8f;
    public static final float FADE_OUT_TICKS = 22f;

    /** 法阵上方的扭曲柱高度（格）。 */
    public static final float WARP_HEIGHT = 15.0f;
    /** 中心晶核包围盒的半边长（格）：着色器在 DF 空间把 ±20 映射到它的 0.66 倍，实际液态铬只有 4~5 格宽，包围盒多出的一圈是光线逼近的行程。 */
    public static final float CORE_SIZE = 3.75f;

    private UUID ownerUUID;
    private int age;

    private List<BlockPos> crater;
    private int craterCursor;

    public CrimsonVowFieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_RADIUS, DEFAULT_RADIUS);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_PULSE, 0);
        entityData.define(DATA_PHASE, PHASE_DEPLOY);
        entityData.define(DATA_SEED, 0);
    }

    public float getRadius() { return entityData.get(DATA_RADIUS); }
    public int getMaxAge()   { return entityData.get(DATA_MAX_AGE); }
    public int getPulse()    { return entityData.get(DATA_PULSE); }
    public int getPhase()    { return entityData.get(DATA_PHASE); }
    /** 每张法阵一个种子：同时放两张时图案不会完全同步。 */
    public int getSeed()     { return entityData.get(DATA_SEED); }
    public int getAge()      { return age; }

    public void setRadius(float v) { entityData.set(DATA_RADIUS, Math.max(1f, v)); }
    public void setMaxAge(int v)   { entityData.set(DATA_MAX_AGE, Math.max(FINALE_TICKS + 20, v)); }

    /** 终末阶段开始的那一 tick（= 预警开始）。 */
    public int finaleStartAge() { return Math.max(DEPLOY_TICKS + 1, getMaxAge() - FINALE_TICKS); }

    /**
     * 光柱开始下落的 tick = 预警结束。三个时刻必须由同一组常数推出（{@code finaleStartAge → beamStartAge → +BEAM_FALL_TICKS}），
     * 渲染侧的进度与服务端的相位切换都读它们 —— 写死一个数字就会让画面和结算错拍。
     */
    public int beamStartAge() { return finaleStartAge() + WARNING_TICKS; }

    public float lifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        return Mth.clamp((age + Math.max(0f, partialTick)) / max, 0f, 1f);
    }

    public float warningProgress(float partialTick) {
        float at = age + Math.max(0f, partialTick) - finaleStartAge();
        return Mth.clamp(at / WARNING_TICKS, 0f, 1f);
    }

    /** 整体强度 0 → 1（含淡入淡出）；末段收束刻意在 {@code maxAge} 内完成 —— 之后实体还要消化破坏队列，不该还留着一张亮着的法阵。 */
    public float intensity(float partialTick) {
        float at = age + Math.max(0f, partialTick);
        float in = Math.min(at / FADE_IN_TICKS, 1f);
        float out = Mth.clamp((getMaxAge() - at) / FADE_OUT_TICKS, 0f, 1f);
        return Mth.clamp(in, 0f, 1f) * out;
    }

    /** 当前扩散波的相位 0 → 1（0 = 刚发出一圈）。 */
    public float wavePhase(float partialTick) {
        float at = age + Math.max(0f, partialTick);
        return (at % WAVE_INTERVAL) / WAVE_INTERVAL;
    }

    public float finaleProgress(float partialTick) {
        float at = age + Math.max(0f, partialTick) - finaleStartAge();
        return Mth.clamp(at / FINALE_TICKS, 0f, 1f);
    }

    public float beamFall(float partialTick) {
        float at = age + Math.max(0f, partialTick) - beamStartAge();
        return Mth.clamp(at / BEAM_FALL_TICKS, 0f, 1f);
    }

    public float impactElapsed(float partialTick) {
        float at = age + Math.max(0f, partialTick) - (beamStartAge() + BEAM_FALL_TICKS);
        return Math.max(0f, at);
    }

    public void setOwner(@Nullable Entity owner) {
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Nullable
    public Player getOwner() {
        if (ownerUUID == null) return null;
        return level().getPlayerByUUID(ownerUUID);
    }

    @Override
    public void tick() {
        if (isPassenger() && getVehicle() != null && !getVehicle().isPassengerOfSameVehicle(this)) {
            stopRiding();
        }
        // age 两侧都推进：客户端用它算生命周期插值；不进同步数据，省一个每 tick 都变的字段（生成时刻只差 1 tick，看不出来）。
        age++;

        if (level().isClientSide()) return;

        boolean spent = age >= getMaxAge();
        if (spent && crater == null) { discard(); return; }
        if (age > getMaxAge() + 200) { discard(); return; }

        Player owner = getOwner();
        int finaleStart = finaleStartAge();
        int beamStart = beamStartAge();

        if (age >= beamStart + BEAM_FALL_TICKS) {
            if (getPhase() != PHASE_IMPACT) enterImpact(owner, beamStart + BEAM_FALL_TICKS);
            tickCrater();
            return;
        }

        if (age >= finaleStart) {
            if (getPhase() != PHASE_FINALE) enterFinale(owner);
            telegraph(owner, finaleStart);
            return;
        }

        if (age < DEPLOY_TICKS) {
            if (getPhase() != PHASE_DEPLOY) entityData.set(DATA_PHASE, PHASE_DEPLOY);
            return;
        }
        if (getPhase() != PHASE_SUSTAIN) entityData.set(DATA_PHASE, PHASE_SUSTAIN);

        if (age % WAVE_INTERVAL == 0) emitWave(owner);
    }

    /** 发一圈扩散波，判定与视觉同 tick 结算（玩家要读得出"圈过来 = 掉血"）。冲击波实体只留展开与触地两次：周期波也起实体的话，20 秒 16 个全屏折射球壳会叠成一片糊。 */
    private void emitWave(@Nullable Player owner) {
        entityData.set(DATA_PULSE, getPulse() + 1);

        float r = getRadius();
        damageInField(owner, WAVE_HURT, false);

        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    getX(), getY() + 0.4, getZ(), 24, r * 0.25, 0.15, r * 0.25, 0.05);
        }
    }

    /** 扩散波的持续压制：圈内生物一直被法阵惦记着。 */
    private void damageInField(@Nullable Player owner, float amount, boolean launch) {
        double r = getRadius();
        AABB box = new AABB(getX() - r, getY() - 4.0, getZ() - r,
                            getX() + r, getY() + 16.0, getZ() + r);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator()
                        && (owner == null || !e.getUUID().equals(owner.getUUID())));
        for (LivingEntity target : targets) {
            if (target.distanceToSqr(getX(), target.getY(), getZ()) > r * r * 1.1) continue;
            target.hurt(level().damageSources().indirectMagic(this, owner), amount);
            VerdictFeedback.landed(target, launch ? 1.0f : 0.5f);
            if (launch) {
                target.setDeltaMovement(target.getDeltaMovement().add(0, 1.1, 0));
                target.hurtMarked = true;
            }
        }
    }

    /** 光柱开始下落：法阵锁定、颜色收紧，天空方向起一声长音。 */
    private void enterFinale(@Nullable Player owner) {
        entityData.set(DATA_PHASE, PHASE_FINALE);
        VerdictFeedback.playSound(level(), new Vec3(getX(), getY() + 2, getZ()),
                SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
        if (owner != null) {
            VerdictFeedback.shakeNear(owner, position(), 0.20f);
        }
    }

    /**
     * 预警拍：渐强的镜头抖动 + 一路上爬的音调 + 下落前的一次反向 FOV。
     * 每 5 tick 一拍（每秒 4 次）才有"蓄力"的听感，每 tick 会变成一片嗡嗡声；
     * 下落前收 2.5°、触地再撑开 +7°，才有"憋 → 放"的两拍。
     */
    private void telegraph(@Nullable Player owner, int start) {
        int w = age - start;
        if (w < 0 || w > WARNING_TICKS) return;
        if (w % 5 != 0) return;

        float k = w / (float) WARNING_TICKS;
        Vec3 at = new Vec3(getX(), getY() + 2.0, getZ());

        VerdictFeedback.shakeNear(owner, at, 0.08f + 0.30f * k);
        VerdictFeedback.playSound(level(), at, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                0.28f + 0.40f * k, 0.55f + 0.80f * k);

        if (w == 0) {
            VerdictFeedback.playSound(level(), at, SoundEvents.END_PORTAL_SPAWN, 0.7f, 0.55f);
        }
        if (w == WARNING_TICKS - 5) {
            VerdictFeedback.fovKick(owner, -2.5f);
        }
    }

    /** 触地：一次巨量地形破坏的开始（真正拆解由 {@link #tickCrater()} 分帧完成）。 */
    private void enterImpact(@Nullable Player owner, int impactAge) {
        entityData.set(DATA_PHASE, PHASE_IMPACT);

        Vec3 c = new Vec3(getX(), getY() + 0.5, getZ());
        float r = getRadius();

        // 视听三层同开，缺一层都会显得"轻"：冲击环（真实几何 + 折射）、一次性爆发的粒子、低沉的音效。
        VerdictFeedback.impactRing(level(), c, r * 1.45f, r * 1.35f, 1.8f);
        VerdictFeedback.burst(level(), c, ParticleTypes.EXPLOSION_EMITTER, 6, 0.0);
        VerdictFeedback.burst(level(), c, ParticleTypes.SOUL_FIRE_FLAME, 180, 1.4);
        VerdictFeedback.burst(level(), c, ParticleTypes.CRIMSON_SPORE, 260, 1.8);
        VerdictFeedback.burst(level(), c, ParticleTypes.END_ROD, 90, 1.1);
        VerdictFeedback.playSound(level(), c, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.55f);
        VerdictFeedback.playSound(level(), c, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
        VerdictFeedback.playSound(level(), c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.3f, 0.5f);

        damageInField(owner, IMPACT_HURT, true);

        if (owner != null) {
            VerdictFeedback.shake(owner, 1.0f);
            VerdictFeedback.fovKick(owner, 7.0f);
        }
        // 旁观者也要被顶一下（强度 0.85：施法者才是被反作用力推的人）——
        // 否则队友看着你砸下来，画面纹丝不动。粒子同理：一次发包让客户端展开，绝不逐粒子发包。
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r + 12.0))) {
            if (owner != null && p.getUUID().equals(owner.getUUID())) continue;
            VerdictFeedback.shakeNear(p, c, 0.85f);
        }

        buildCrater();
    }

    /** 收集要拆掉的方块，按到阵心的水平距离升序：由内向外扫读起来是"一圈冲击波在啃地面"，按存储顺序（Y 优先）扫像在拆积木。 */
    private void buildCrater() {
        if (!(level() instanceof ServerLevel sl)) return;

        float r = getRadius() * CRATER_RATIO;
        int ri = Mth.ceil(r);
        double cx = getX();
        double cz = getZ();
        double r2 = (double) r * (double) r;
        int baseX = Mth.floor(cx);
        int baseZ = Mth.floor(cz);
        int top = Mth.floor(getY()) + CRATER_LIFT;

        List<BlockPos> list = new ArrayList<>(4096);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d2 = (double) dx * dx + (double) dz * dz;
                if (d2 > r2) continue;

                // 碗形：中心最深、边缘与地表相切，读起来是"被砸出来的"而不是"被切掉的"。
                int depth = (int) Math.round(CRATER_DEPTH * Math.sqrt(Math.max(0.0, 1.0 - d2 / r2)));
                int bx = baseX + dx;
                int bz = baseZ + dz;

                for (int y = top; y >= top - depth; y--) {
                    mp.set(bx, y, bz);
                    if (!sl.hasChunkAt(mp)) continue;
                    BlockState state = sl.getBlockState(mp);
                    if (state.isAir()) continue;
                // 硬度 < 0 = 不可破坏（基岩、屏障、命令方块……）：少了这一句，这一招会把世界底层打穿。
                    if (state.getDestroySpeed(sl, mp) < 0) continue;
                    list.add(mp.immutable());
                }
            }
        }

        final double fcx = cx, fcz = cz;
        list.sort(Comparator.comparingDouble(p ->
                (p.getX() + 0.5 - fcx) * (p.getX() + 0.5 - fcx)
                        + (p.getZ() + 0.5 - fcz) * (p.getZ() + 0.5 - fcz)));

        crater = list;
        craterCursor = 0;
    }

    /** 每 tick 消化一小段破坏队列。 */
    private void tickCrater() {
        List<BlockPos> queue = crater;
        if (queue == null) return;

        int budget = BLOCKS_PER_TICK;
        while (craterCursor < queue.size() && budget > 0) {
            BlockPos pos = queue.get(craterCursor++);
            BlockState state = level().getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level(), pos) < 0) continue;
            // dropItems=false：这一招是"抹平"不是"挖矿"，掉落物只会让客户端多几百个实体。
            level().destroyBlock(pos, false);
            budget--;
        }

        if (craterCursor >= queue.size()) {
            crater = null;      // 消化完，下一 tick 走正常收场
        }
    }

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Radius")) entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        if (tag.contains("MaxAge")) entityData.set(DATA_MAX_AGE, tag.getInt("MaxAge"));
        if (tag.contains("Pulse"))  entityData.set(DATA_PULSE, tag.getInt("Pulse"));
        if (tag.contains("Phase"))  entityData.set(DATA_PHASE, tag.getInt("Phase"));
        if (tag.contains("Seed"))   entityData.set(DATA_SEED, tag.getInt("Seed"));
        // 破坏队列刻意不存档：读档后从"没挖完的地方"续挖很难解释，剩下的方块留着也不影响任何判定。
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putFloat("Radius", getRadius());
        tag.putInt("MaxAge", getMaxAge());
        tag.putInt("Pulse", getPulse());
        tag.putInt("Phase", getPhase());
        tag.putInt("Seed", getSeed());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** 在指定落点展开法阵。{@code center} 的 y 已经是地表高度。 */
    public static CrimsonVowFieldEntity spawn(Level level, @Nullable Player owner, Vec3 center,
                                             float radius, int maxAge) {
        CrimsonVowFieldEntity e = new CrimsonVowFieldEntity(
                EntityTypeRegistry.CRIMSON_VOW_FIELD.get(), level);
        e.setPos(center.x, center.y, center.z);
        e.setOwner(owner);
        e.setRadius(radius);
        e.setMaxAge(maxAge);
        e.entityData.set(DATA_SEED, level.random.nextInt(1 << 20));
        level.addFreshEntity(e);
        return e;
    }
}
