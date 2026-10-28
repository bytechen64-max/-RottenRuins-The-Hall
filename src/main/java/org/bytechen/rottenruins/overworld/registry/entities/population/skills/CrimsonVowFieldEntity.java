package org.bytechen.rottenruins.overworld.registry.entities.population.skills;

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
 * 绯红誓约 · <b>巨型法阵</b>（蓄满之后放出去的那一个，持续 20 秒）。
 *
 * <h3>它由什么组成</h3>
 * <ol>
 *   <li><b>地面法阵</b> —— 贴地圆盘，径向/角向坐标烘在顶点属性里，图案在
 *       {@code rendertype_crimson_sigil.fsh} 的 field 分支里算；</li>
 *   <li><b>中心悬浮的液态铬</b> —— 一个世界空间的包围盒，片元里对"球 / 环 /
 *       圆角方块 / 八面体"熔融的距离场做光线步进，表面是镜面铬
 *       （{@code rendertype_crimson_cube.fsh}，文件名是历史遗留）。
 *       这就是需求里"中间为着色器渲染的立体内容"；</li>
 *   <li><b>上方 y 轴区域的方块扭曲</b> —— 法阵正上方一根看不见的圆柱，
 *       片元对场景拷贝做屏幕空间折射，强度随离地高度衰减
 *       （对应参考着色器里的 {@code dst_offset *= (1. - p_m.t)}）；</li>
 *   <li><b>周期扩散波</b> —— 每 {@link #WAVE_INTERVAL} tick 一次，
 *       既有一个真实的 {@link ShockwaveEntity} 冲击环，也有法阵自身的脉冲环；</li>
 *   <li><b>终末</b> —— 最后 {@link #FINALE_TICKS} tick：自天而降的 Strand+Muzzle
 *       光柱落地，然后<b>分帧</b>炸掉一整片地形。</li>
 * </ol>
 *
 * <h3>为什么地形破坏要分帧</h3>
 * <p>半径 11 格、深 10 格的碗形坑大约 4000+ 个方块。一 tick 全炸掉的话，
 * 光客户端要收的方块更新包就会让服务端卡住几百毫秒 —— 而"卡一下"会把这一招
 * 最有分量的那一下毁掉。所以这里把方块<b>先收集、按到中心的距离排序</b>，
 * 再每 tick 消化 {@link #BLOCKS_PER_TICK} 个：破坏顺序由内向外，
 * 画面上读起来就是"一圈冲击波在啃地面"，而且预算恒定、不随半径失控。</p>
 *
 * <h3>为什么实体寿命可以超过 {@link #DEFAULT_MAX_AGE}</h3>
 * <p>{@code maxAge} 是"法阵存在多久"（20 秒，视觉与判定都以它为准）；
 * 而破坏队列可能还差几 tick 没消化完。让它把队列吃完再散，
 * 而不是把剩下的方块凭空留下 —— 否则玩家会看到"坑挖到一半没了"。</p>
 */
public class CrimsonVowFieldEntity extends Entity {

    // ── 同步数据 ──────────────────────────────────────────────────
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

    // ── 阶段 ──────────────────────────────────────────────────────
    /** 展开中（法阵从中心铺开）。 */
    public static final int PHASE_DEPLOY = 0;
    /** 稳定运转（周期扩散波）。 */
    public static final int PHASE_SUSTAIN = 1;
    /** 终末：光柱从天而降。 */
    public static final int PHASE_FINALE = 2;
    /** 落点：地形被撕开。 */
    public static final int PHASE_IMPACT = 3;

    // ── 数值 ──────────────────────────────────────────────────────
    /** 默认半径（格）。"巨型"指的就是这个数 —— 直径 28 格。 */
    public static final float DEFAULT_RADIUS = 14.0f;
    /** 默认持续（tick）= 20 秒。需求写死的值，别随手改。 */
    public static final int DEFAULT_MAX_AGE = 400;

    /** 展开耗时（tick）。 */
    public static final int DEPLOY_TICKS = 10;
    /** 扩散波间隔（tick）= 1.2 秒。 */
    public static final int WAVE_INTERVAL = 24;
    /** 终末阶段总长（tick）= 3.5 秒。 */
    public static final int FINALE_TICKS = 70;
    /**
     * 终末的<b>预警拍</b>（tick）：法阵开始收、天顶裂口张开、光柱还没落下来的那一段。
     *
     * <p>这一拍是整招"张力"的来源，不是可选的装饰。旧版从 enterFinale 到触地只有
     * {@code BEAM_FALL_TICKS} = 7 tick（0.35 秒）—— 玩家看到的是"亮了一下、地上炸了"，
     * 大脑来不及建立"有东西正从天上砸下来"的预期，那一下自然就没有分量。
     * 先给 30 tick（1.5 秒）的预告：头顶开洞、地上三圈向内收、镜头一点点压紧、
     * 音调一路爬升，然后才让光柱落下来。</p>
     *
     * <p>取值理由：短于 20 tick 读成"闪了一下"，长于 45 tick 开始像卡住；
     * 30 tick 正好是"注意到 → 抬头 → 它来了"的三拍。</p>
     */
    public static final int WARNING_TICKS = 30;
    /**
     * 光柱自天而降的耗时（tick）。
     *
     * <p>{@code 14} tick ≈ 0.7 秒。这是第二版：第一版 7 tick（0.35 秒 / 200 格每秒）
     * 快到肉眼只有 2~3 帧，读不出"在往下走"，只剩一声响。70 格 / 14 tick = 5 格每 tick
     * 依然是高速轰击，但现在眼睛能跟住那个"头"，落点那一下才有分量。</p>
     *
     * <p>⚠️ 这个常数同时驱动<b>服务端</b>的相位切换（进入 {@link #PHASE_IMPACT}
     * 并开始炸地形）与客户端的 {@link #beamFall}。两边必须共用同一个数，否则会出现
     * "画面已经落地、伤害还没结算"或者反过来 —— 前者玩家会以为打空了，
     * 后者是无声掉血。所以它放在实体上，渲染侧只读不算。</p>
     */
    public static final int BEAM_FALL_TICKS = 14;

    /** 光柱起点离地高度（格）—— 渲染侧与冲击反馈都用它。 */
    public static final float BEAM_TOP = 70.0f;
    /**
     * 柱体自身长度（格）：触地那一帧柱顶还剩这么高。
     *
     * <p>没有它就分不开两个都在 {@link #BEAM_FALL_TICKS} 内走完的量：柱底从
     * {@code BEAM_TOP} 掉到 0（{@code beamFall} 表达的正是它），而柱顶只从
     * {@code BEAM_TOP} 掉到 {@code BEAM_TOP - BEAM_LENGTH}。少了这一项，柱顶会被
     * 算成也落到 0，整根柱子在下落最后几 tick 缩成地上的一个点 ——
     * <b>落地那一帧反而是空的</b>（离屏预览里复现过一次）。</p>
     *
     * <p>取 34 而不是原来的 45：它是"一根从天而降的矛"的长度，不是"一根立在那里的
     * 柱子"。45 格几乎顶满 {@link #BEAM_TOP} 时，整根柱子从第一帧就贯满画面，
     * 读不出"在往下走"；短一点、并且比 BEAM_TOP 明显短，运动方向才读得出来。</p>
     */
    public static final float BEAM_LENGTH = 34.0f;

    /** 每 tick 最多破坏多少个方块。 */
    public static final int BLOCKS_PER_TICK = 150;
    /** 坑半径 / 法阵半径。 */
    public static final float CRATER_RATIO = 0.80f;
    /** 坑最大深度（格）。 */
    public static final int CRATER_DEPTH = 10;
    /** 破坏范围向上额外抬起几格（把地面上的建筑一并抹平）。 */
    public static final int CRATER_LIFT = 3;

    /** 单次扩散波的伤害。 */
    public static final float WAVE_HURT = 5.0f;
    /** 终末那一下的伤害。 */
    public static final float IMPACT_HURT = 70.0f;

    /** 淡入 / 淡出（tick）。 */
    public static final float FADE_IN_TICKS = 8f;
    public static final float FADE_OUT_TICKS = 22f;

    /** 法阵上方的扭曲柱高度（格）。 */
    public static final float WARP_HEIGHT = 15.0f;
    /**
     * 中心晶核的<b>包围盒</b>半边长（格）。
     *
     * <p>它不是晶体/铬团的实际尺寸：着色器在 DF 空间里把 ±20 映射到这个半径的
     * 0.66 倍（{@code OBJ_FRACTION}），液态铬那支的实际大小约 4~5 格宽
     * （核心 + 四颗卫星球）。包围盒留出的那一圈是光线"从外逼近"的行程
     * （见 fsh 里 OBJ_FRACTION 的注释）。</p>
     *
     * <p>需求："悬浮的立方体小一半" —— 从 7.5 改到 3.75 之后，核心直径是
     * 法阵半径的一半多一点，读起来是"阵心浮着的那颗核心"而不是"一座占满
     * 阵面的建筑"。</p>
     */
    public static final float CORE_SIZE = 3.75f;

    private UUID ownerUUID;
    private int age;

    // ── 服务端破坏队列 ────────────────────────────────────────────
    private List<BlockPos> crater;
    private int craterCursor;

    public CrimsonVowFieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // ══════════════════════════════════════════════════════════════
    //  同步数据
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
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
     * 光柱开始下落的 tick = 预警结束。
     *
     * <p>这三个时刻必须由同一组常数推出来，不能各自写各的：
     * {@code finaleStartAge() → beamStartAge() → beamStartAge()+BEAM_FALL_TICKS}。
     * 渲染侧的"预警进度 / 下落进度 / 触地秒数"全从它们算，
     * 服务端的相位切换也用它们 —— 写死一个数字就会让画面和结算错拍。</p>
     */
    public int beamStartAge() { return finaleStartAge() + WARNING_TICKS; }

    /** 生命进度 0 → 1（含部分 tick 插值）。 */
    public float lifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        return Mth.clamp((age + Math.max(0f, partialTick)) / max, 0f, 1f);
    }

    /** 终末预警进度 0 → 1（0 = 还没开始，1 = 光柱已经落下）。 */
    public float warningProgress(float partialTick) {
        float at = age + Math.max(0f, partialTick) - finaleStartAge();
        return Mth.clamp(at / WARNING_TICKS, 0f, 1f);
    }

    /**
     * 整体强度 0 → 1（含淡入淡出）。
     *
     * <p>末段的收束刻意放在 {@code maxAge} 之内完成 —— 因为之后实体还要
     * 继续消化破坏队列，那时画面上不该还留着一张亮着的法阵。</p>
     */
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

    /** 终末进度 0 → 1（0 = 光柱开始下落）。 */
    public float finaleProgress(float partialTick) {
        float at = age + Math.max(0f, partialTick) - finaleStartAge();
        return Mth.clamp(at / FINALE_TICKS, 0f, 1f);
    }

    /** 光柱下落进度 0 → 1（1 = 触地）。 */
    public float beamFall(float partialTick) {
        float at = age + Math.max(0f, partialTick) - beamStartAge();
        return Mth.clamp(at / BEAM_FALL_TICKS, 0f, 1f);
    }

    /** 触地之后经过的 tick 数（含插值），用于"爆闪 → 余波"的收尾。 */
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

    // ══════════════════════════════════════════════════════════════
    //  Tick
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        if (isPassenger() && getVehicle() != null && !getVehicle().isPassengerOfSameVehicle(this)) {
            stopRiding();
        }
        // age 在两侧都推进：客户端要用它算生命周期插值（同步数据里不放它，
        // 省一个每 tick 都在变的字段 —— 生成时刻只差 1 tick，视觉上看不出来）。
        age++;

        if (level().isClientSide()) return;

        boolean spent = age >= getMaxAge();
        if (spent && crater == null) { discard(); return; }
        if (age > getMaxAge() + 200) { discard(); return; }   // 兜底：队列异常时也要收场

        Player owner = getOwner();
        int finaleStart = finaleStartAge();
        int beamStart = beamStartAge();

        // ── ③ 终末：光柱落地之后 ──
        if (age >= beamStart + BEAM_FALL_TICKS) {
            if (getPhase() != PHASE_IMPACT) enterImpact(owner, beamStart + BEAM_FALL_TICKS);
            tickCrater();
            return;
        }

        // ── ② 终末：预警 → 光柱下落 ──
        if (age >= finaleStart) {
            if (getPhase() != PHASE_FINALE) enterFinale(owner);
            telegraph(owner, finaleStart);
            return;
        }

        // ── ① 展开 / 稳定运转 ──
        if (age < DEPLOY_TICKS) {
            if (getPhase() != PHASE_DEPLOY) entityData.set(DATA_PHASE, PHASE_DEPLOY);
            return;
        }
        if (getPhase() != PHASE_SUSTAIN) entityData.set(DATA_PHASE, PHASE_SUSTAIN);

        if (age % WAVE_INTERVAL == 0) emitWave(owner);
    }

    /**
     * 发一圈扩散波。
     *
     * <p>判定与视觉在同一 tick 结算，玩家读得出"圈过来 = 掉血"这条因果。</p>
     *
     * <h3>为什么这里<b>不</b>再生成冲击波实体</h3>
     * <p>第一版每 24 tick 起一个 {@link ShockwaveEntity}，20 秒里就是 16 个
     * 全屏折射球壳 —— 它们叠在一起会互相盖掉，画面上反而是持续的糊；
     * 而且"一直在折射"会让真正该被记住的那两下（展开、终末）失去分量。</p>
     * <p>所以冲击波实体只留两次：<b>展开时一次</b>（在
     * {@code CrimsonVowChargeEntity#release}）与<b>终末触地时一次</b>
     * （在 {@link #enterImpact}）。周期波只留法阵自己那一圈脉冲环
     * （{@code uPulse} 驱动）—— 它够轻，叠多少层都不会糊。</p>
     */
    private void emitWave(@Nullable Player owner) {
        entityData.set(DATA_PULSE, getPulse() + 1);

        float r = getRadius();
        damageInField(owner, WAVE_HURT, false);

        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    getX(), getY() + 0.4, getZ(), 24, r * 0.25, 0.15, r * 0.25, 0.05);
        }
    }

    /** 扩散波造成的持续压制：让圈内的生物一直被法阵惦记着。 */
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

    // ══════════════════════════════════════════════════════════════
    //  终末
    // ══════════════════════════════════════════════════════════════

    /** 光柱开始下落：法阵锁定，颜色收紧，天空方向起一声长音。 */
    private void enterFinale(@Nullable Player owner) {
        entityData.set(DATA_PHASE, PHASE_FINALE);
        VerdictFeedback.playSound(level(), new Vec3(getX(), getY() + 2, getZ()),
                SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
        if (owner != null) {
            // 预警的第一下：轻微一顶，之后由 telegraph 一路加码。
            VerdictFeedback.shakeNear(owner, position(), 0.20f);
        }
    }

    /**
     * 预警拍：渐强的镜头抖动 + 一路上爬的音调 + 下落前的一次反向 FOV。
     *
     * <h3>为什么是"每 5 tick"而不是每 tick</h3>
     * <p>每 tick 一次会变成一片嗡嗡声，反而读不出"在逼近"；5 tick 一拍（每秒 4 次）
     * 配上逐渐升高的音高，才是"蓄力"的听感。镜头同理：抖动取 max 而不是累加，
     * 所以这里只需给出越来越大的值。</p>
     *
     * <h3>为什么最后要一次负的 fovKick</h3>
     * <p>张力来自对比。下落前把视野收 2.5°（画面轻微"压紧"），触地那一下再
     * +7° 撑开，同一段镜头里就有了"憋 → 放"的两拍；全程只有一次撑开的话，
     * 那一下会被前面 1.5 秒的静止抵消掉一半。</p>
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
            // 天顶裂口张开的那一下：远处、金属味的一声"开"
            VerdictFeedback.playSound(level(), at, SoundEvents.END_PORTAL_SPAWN, 0.7f, 0.55f);
        }
        if (w == WARNING_TICKS - 5) {
            VerdictFeedback.fovKick(owner, -2.5f);
        }
    }

    /**
     * 触地：一次巨量地形破坏的<b>开始</b>（真正拆解由 {@link #tickCrater()} 分帧完成）。
     */
    private void enterImpact(@Nullable Player owner, int impactAge) {
        entityData.set(DATA_PHASE, PHASE_IMPACT);

        Vec3 c = new Vec3(getX(), getY() + 0.5, getZ());
        float r = getRadius();

        // ── 视听：三层同时上，缺一层都会显得"轻" ──
        //  ① 冲击环（真实几何 + 折射）
        VerdictFeedback.impactRing(level(), c, r * 1.45f, r * 1.35f, 1.8f);
        //  ② 粒子：一次发包让客户端展开，绝不逐粒子发包
        VerdictFeedback.burst(level(), c, ParticleTypes.EXPLOSION_EMITTER, 6, 0.0);
        VerdictFeedback.burst(level(), c, ParticleTypes.SOUL_FIRE_FLAME, 180, 1.4);
        VerdictFeedback.burst(level(), c, ParticleTypes.CRIMSON_SPORE, 260, 1.8);
        VerdictFeedback.burst(level(), c, ParticleTypes.END_ROD, 90, 1.1);
        //  ③ 声音：低沉的那一下才是"重量"
        VerdictFeedback.playSound(level(), c, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.55f);
        VerdictFeedback.playSound(level(), c, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
        VerdictFeedback.playSound(level(), c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.3f, 0.5f);

        // ── 判定 ──
        damageInField(owner, IMPACT_HURT, true);

        // ── 相机：整场技能最重的一下 ──
        if (owner != null) {
            VerdictFeedback.shake(owner, 1.0f);
            VerdictFeedback.fovKick(owner, 7.0f);
        }
        // 旁观者也要被顶一下。旧版只在"施法者已经走了"时才遍历附近玩家 ——
        // 于是队友看着你砸下来，画面纹丝不动：半径 14 格的一击，站在阵边的
        // 人不该只是"看着"。强度 0.85 而不是 1.0：施法者才是被反作用力推的人。
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r + 12.0))) {
            if (owner != null && p.getUUID().equals(owner.getUUID())) continue;
            VerdictFeedback.shakeNear(p, c, 0.85f);
        }

        buildCrater();
    }

    /**
     * 收集要拆掉的方块，<b>按到阵心的水平距离升序</b>。
     *
     * <p>排序这一步是这份实现里最关键的一处：破坏顺序决定读感。
     * 由内向外扫，画面就是"一圈冲击波在啃地面"；按存储顺序（Y 优先）扫，
     * 画面上会看到一根根柱子从上往下塌，像在拆积木。</p>
     *
     * <p>复杂度：半径 11 格时约 400 条柱子 × 约 11 层 ≈ 4000+ 个候选位置，
     * 一次排序完全可以接受（只在触地那一 tick 做一次）。</p>
     */
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
                    // 硬度 < 0 = 不可破坏（基岩、屏障、命令方块……）——
                    // 少了这一句，这一招会把世界底层打穿。
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
            // dropItems=false：这一招是"抹平"，不是"挖矿"。
            // 掉落物只会让客户端多几百个实体，而这一下要的是一击清场。
            level().destroyBlock(pos, false);
            budget--;
        }

        if (craterCursor >= queue.size()) {
            crater = null;      // 消化完，下一 tick 走正常收场
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  实体样板
    // ══════════════════════════════════════════════════════════════

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Radius")) entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        if (tag.contains("MaxAge")) entityData.set(DATA_MAX_AGE, tag.getInt("MaxAge"));
        if (tag.contains("Pulse"))  entityData.set(DATA_PULSE, tag.getInt("Pulse"));
        if (tag.contains("Phase"))  entityData.set(DATA_PHASE, tag.getInt("Phase"));
        if (tag.contains("Seed"))   entityData.set(DATA_SEED, tag.getInt("Seed"));
        // 破坏队列刻意不存档：读档后从"没挖完的地方"续挖会很难解释，
        // 而剩下的方块留着不动也不影响任何判定。
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

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
