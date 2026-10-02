package org.bytechen.hall.overworld.registry.entities.population.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.items.verdict.HeightFactor;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 天穹裁决的垂直光柱 —— 视觉 + <b>两波伤害</b>。
 *
 * <h3>几何契约</h3>
 * <pre>
 *   实体位置 = 光柱底面中心（玩家脚底）
 *   光柱沿 +Y 向上延伸 {@link #getLength()} 格，半径 {@link #getRadius()} 格
 * </pre>
 *
 * <h3>伤害为什么搬到了实体里</h3>
 * <p>原本伤害在 {@code DomeriteLongsword#castBeam} 里一次性结算，实体纯渲染。
 * 改成两波之后那样做就需要一个"延迟再判一次"的调度器 —— 而实体本身就是
 * 每 tick 被处理的、有生命周期的东西。<b>让效果自己持有它的伤害</b>之后：</p>
 * <ul>
 *   <li>不需要新的调度器、新的持久数据、新的网络包；</li>
 *   <li>第二波天然与 {@code VerdictBeamRenderer} 里那个下扫球同步 ——
 *       视觉上"扫到哪"和"打到哪"变成同一件事；</li>
 *   <li>光影/区块卸载把实体收掉时，未结算的那一波也一起消失，不会留下"幽灵伤害"。</li>
 * </ul>
 *
 * <h3>为什么不需要 {@code Entity.renderer} 之外的任何逻辑</h3>
 * <p>光柱不移动、不碰撞、不检测、不施加效果，只随时间做三件事：
 * 淡入 → 保持 → 收束消失。全部由同步数据驱动，渲染器只读数据做 partialTick 插值。</p>
 */
public class VerdictBeamEntity extends Entity {

    // ── 同步数据 ────────────────────────────────────────────────────
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.INT);
    /** 扩散终点半径（格）。起点是 {@link #getRadius()}，终点是它。 */
    private static final EntityDataAccessor<Float> DATA_TARGET_RADIUS =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.FLOAT);
    /** 扩散完成所需的 tick。 */
    private static final EntityDataAccessor<Integer> DATA_GROW_TICKS =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.INT);

    // ── 默认值 ──────────────────────────────────────────────────────
    /** 默认半径（格）。地表值，会被高度系数与裁决层数放大。 */
    public static final float DEFAULT_RADIUS = 2.6f;
    /** 默认长度（格）：抬头 90° 的满长度。 */
    public static final float DEFAULT_LENGTH = 44.0f;
    /**
     * 扩散终点半径（格）。落柱之后一路涨到它，然后停在那里。
     *
     * <h3>为什么要扩散</h3>
     * <p>两个理由，一个玩法一个技术：</p>
     * <ul>
     *   <li><b>玩法</b>：一棵一直在变宽的裁决柱比一根等宽的柱子更像"天罚"，
     *       而且它把"站在里面"这件事说清楚了 —— 起始就把施法者包在柱心；</li>
     *   <li><b>技术</b>：背面剔除只在"相机位于柱外"时才生效，而柱外正好是那套
     *       剔除显出问题的位置。让柱子扩散到把玩家整个罩住，玩家就始终落在
     *       "柱内"分支（{@code uBackCull = 0}），那条画面路径自然不会被看到。</li>
     * </ul>
     * <p>20 格是个明确的量级：脚下方圆 20 格全归裁决管，远大于领域（6~11 格），
     * 与"它是三招里覆盖最强的一招"这个定位一致。</p>
     */
    public static final float DEFAULT_TARGET_RADIUS = 20.0f;
    /** 扩散完成所需 tick = 1.5 秒。太快读不出"扩散"，太慢就成了慢慢胀。 */
    public static final int DEFAULT_GROW_TICKS = 30;

    /**
     * 默认存活（tick）= 12 秒。
     *
     * <p>从 8 秒加回来：扩散 + 通天这两件事都需要够长的窗口才看得见，
     * 8 秒时柱子刚铺开就该消失了。12 秒仍然不比最初那版长 ——
     * 因为末段有自下而上的塌陷收束，不会像早期那样"挂着半透明的一根不退"。</p>
     */
    public static final int DEFAULT_MAX_AGE = 240;

    /** 淡入时长（tick）：0 → 1 的强度爬升。 */
    public static final float FADE_IN_TICKS = 3f;
    /** 淡出时长（tick）：1 → 0 的收束。 */
    public static final float FADE_OUT_TICKS = 26f;

    // ── 伤害三波 ────────────────────────────────────────────────────
    /**
     * 三波打击的触发 tick。
     *
     * <p>打在扩散的三个阶段上（起始 / 中段 / 扩散完成），于是"柱子越宽、打到的人
     * 越多"在时间上是连续发生的，而不是一发放就把整片场地判完。</p>
     */
    public static final int WAVE1_TICK = 0;
    public static final int WAVE2_TICK = 10;
    public static final int WAVE3_TICK = DEFAULT_GROW_TICKS;

    /**
     * 后两波的横向收紧系数（相对<b>当时</b>的扩散半径）。
     *
     * <p>扩散后期柱子已经很宽，如果每波都按完整半径整片结算，
     * "边缘蹭到"和"正中柱心"就没有区别了。收紧之后读到的是
     * "柱心那一圈才算真正的裁决"。</p>
     */
    private static final double WAVE2_RADIUS_SCALE = 0.78;
    private static final double WAVE3_RADIUS_SCALE = 0.62;

    /**
     * 单波最多处理多少个目标（性能闸门）。
     *
     * <p>扩散到 20 格之后，一次结算可能捞到几十个实体，而三波都要各来一次。
     * 这里给一个硬上限，超出部分直接不打 —— 与其让一次技能造成几百点伤害，
     * 不如让它在"人特别多"的场景里自然饱和。20 已经远超实际战斗的密度。</p>
     */
    private static final int MAX_TARGETS_PER_WAVE = 20;

    private int age;
    private boolean wave1Done;
    private boolean wave2Done;
    private boolean wave3Done;

    public VerdictBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;                      // 光柱很长，包围盒剔除会误杀
    }

    // ══════════════════════════════════════════════════════════════
    //  同步数据
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, DEFAULT_RADIUS);
        entityData.define(DATA_LENGTH, DEFAULT_LENGTH);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_CASTER, -1);
        entityData.define(DATA_TARGET_RADIUS, DEFAULT_TARGET_RADIUS);
        entityData.define(DATA_GROW_TICKS, DEFAULT_GROW_TICKS);
    }

    /** <b>起始</b>半径（格）。当前半径要用 {@link #currentRadius}。 */
    public float getRadius()  { return entityData.get(DATA_RADIUS); }
    public float getLength()  { return entityData.get(DATA_LENGTH); }
    public int   getMaxAge()  { return entityData.get(DATA_MAX_AGE); }
    public int   getAge()     { return age; }
    /** 扩散终点半径（格）。 */
    public float getTargetRadius() { return entityData.get(DATA_TARGET_RADIUS); }
    /** 扩散完成所需 tick。 */
    public int   getGrowTicks()    { return entityData.get(DATA_GROW_TICKS); }

    public void setRadius(float v) { entityData.set(DATA_RADIUS, Math.max(0.05f, v)); }
    public void setLength(float v) { entityData.set(DATA_LENGTH, Math.max(0.5f, v)); }
    public void setMaxAge(int v)   { entityData.set(DATA_MAX_AGE, Math.max(1, v)); }
    public void setTargetRadius(float v) { entityData.set(DATA_TARGET_RADIUS, Math.max(0.5f, v)); }
    public void setGrowTicks(int v) { entityData.set(DATA_GROW_TICKS, Math.max(1, v)); }

    /**
     * 扩散进度 0 → 1，<b>开方曲线</b>（前快后慢）。
     *
     * <p>用 {@code sqrt} 而不是线性：扩散的读法应该是"轰地一下铺开、然后慢慢
     * 推到最后那一圈"。线性会让它读成"匀速膨胀的气球"。</p>
     *
     * @param partialTick 插值；传 0 表示"这一 tick 的起点"
     */
    public float growProgress(float partialTick) {
        int gt = getGrowTicks();
        if (gt <= 0) return 1f;
        float t = Mth.clamp((age + Math.max(0f, partialTick)) / gt, 0f, 1f);
        return Mth.sqrt(t);
    }

    /** 当前半径 = 起始 +（终点 − 起始）× 扩散进度。 */
    public float currentRadius(float partialTick) {
        float g = growProgress(partialTick);
        return getRadius() + (getTargetRadius() - getRadius()) * g;
    }

    /**
     * 当前<b>视觉</b>高度。
     *
     * <p>柱子在扩散时也一起长高：一颗只变宽、不变高的柱子会读成"被压扁了"。
     * 但它<b>不</b>跟着横向系数一路乘上去 —— 起始高度已经是 440 格量级，
     * 再乘 7.7 就是 3400 格，早就越过远裁剪面了，画面上会读成"柱子断在半空"。
     * 所以纵向单独封顶在 {@link #MAX_HEIGHT_SCALE}。</p>
     */
    public float currentLength(float partialTick) {
        return getLength() * currentHeightScale(partialTick);
    }

    /**
     * 扩散过程中的<b>纵向</b>放大系数。
     *
     * <p>恒为 {@value #MAX_HEIGHT_SCALE}（= 1.0，也就是<b>不放大</b>）。</p>
     *
     * <h3>为什么故意让它不动</h3>
     * <p>起始高度已经是 {@code HeightFactor.BEAM_MAX_LENGTH} = 440 格
     * （相比之下原来只有 44 格，已经是你要的 10 倍）。再让它随扩散长高，
     * 就变成 440 × 3.5 = 1540 格 —— <b>远超远裁剪面</b>，
     * 画面上只会读成"柱子在上面被切断了"，而不是"更高了"。</p>
     *
     * <p>扩散只需要在<b>横向</b>上读出来："一根越来越粗、并且把你整个罩住的光柱"
     * 已经足够，纵向再涨只会把画面推出可见范围。这也是把两个轴分开的原因。</p>
     */
    public float currentHeightScale(float partialTick) {
        return MAX_HEIGHT_SCALE;
    }

    /** 纵向放大系数。见 {@link #currentHeightScale}：刻意保持 1.0。 */
    public static final float MAX_HEIGHT_SCALE = 1.0f;

    /** 施法者。存 entity id 而不是 UUID：伤害结算只需要在当前世界里拿到那个人。 */
    public void setCaster(@Nullable Player player) {
        entityData.set(DATA_CASTER, player == null ? -1 : player.getId());
    }

    @Nullable
    public Player getCaster() {
        int id = entityData.get(DATA_CASTER);
        if (id < 0) return null;
        return level().getEntity(id) instanceof Player p ? p : null;
    }

    // ══════════════════════════════════════════════════════════════
    //  生命周期
    // ══════════════════════════════════════════════════════════════

    /** 0 → 1 的存活进度，带 partialTick 插值。 */
    public float lifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        return Math.min((age + Math.max(0f, partialTick)) / max, 1f);
    }

    /**
     * 当前强度（0 = 完全不画）。
     *
     * <p>曲线刻意不对称：淡入极快（{@value #FADE_IN_TICKS} tick，几乎是"砸下来"），
     * 淡出很慢（{@value #FADE_OUT_TICKS} tick）。裁决应当是"瞬间降下、缓慢退去"，
     * 反过来会显得软。</p>
     */
    public float intensity(float partialTick) {
        float at = age + Math.max(0f, partialTick);
        float in = Math.min(at / FADE_IN_TICKS, 1f);
        int max = getMaxAge();
        float out = Math.min(Math.max(0f, (max - at) / FADE_OUT_TICKS), 1f);
        return in * out;
    }

    @Override
    public void tick() {
        age++;
        if (level().isClientSide()) return;

        // ── 两波伤害：都在服务端结算 ──
        if (!wave1Done && age >= WAVE1_TICK) {
            wave1Done = true;
            strikeWave(1.0);
        }
        if (!wave2Done && age >= WAVE2_TICK) {
            wave2Done = true;
            strikeWave(WAVE2_RADIUS_SCALE);
        }
        // 第三波落在"扩散完成"那一刻：那时柱子最宽，该结算的范围也最大
        if (!wave3Done && age >= WAVE3_TICK) {
            wave3Done = true;
            strikeWave(WAVE3_RADIUS_SCALE);
        }

        if (age >= getMaxAge()) discard();
    }

    /**
     * 结算一波裁决。
     *
     * <p><b>半径是"当时"的扩散半径</b>，不是生成时那个：三波分别落在扩散的
     * 起始 / 中段 / 完成，所以柱子越铺越开、每一波能捞到的目标也越多。</p>
     *
     * <p><b>判定高度有独立上限</b>（{@link HeightFactor#BEAM_HIT_MAX_HEIGHT}）：
     * 视觉柱子现在有几百格高，但判定不会跟着长到看不见的地方去。
     * 见 {@code HeightFactor.BEAM_MAX_LENGTH} 的说明。</p>
     *
     * @param radiusScale 横向收紧系数（1.0 = 当时的完整半径）
     */
    private void strikeWave(double radiusScale) {
        Player caster = getCaster();
        float radius = currentRadius(0f) * (float) radiusScale;
        float hitHeight = HeightFactor.beamHitHeight(getLength());
        AABB box = new AABB(
                getX() - radius, getY(), getZ() - radius,
                getX() + radius, getY() + hitHeight, getZ() + radius);

        List<LivingEntity> hits = new ArrayList<>(
                level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e.isAlive() && !e.isSpectator()
                                && (caster == null || !e.getUUID().equals(caster.getUUID()))));

        // 性能闸门：20 格半径 + 三波在极端场景下能捞到几十个实体，
        // 与其让一次技能造成几百点伤害，不如让它在这里饱和。
        if (hits.size() > MAX_TARGETS_PER_WAVE) {
            hits = hits.subList(0, MAX_TARGETS_PER_WAVE);
        }

        // 伤害按波次取：第一波最重，后两波是"扩散到更大范围之后的追加判定"。
        boolean first = radiusScale >= 1.0;
        float hurt = first ? VerdictDamage.BEAM_HURT_WAVE1 : VerdictDamage.BEAM_HURT_WAVE2;
        float bypass = first ? VerdictDamage.BEAM_BYPASS_WAVE1 : VerdictDamage.BEAM_BYPASS_WAVE2;

        int landed = 0;
        var source = caster != null ? caster.damageSources().playerAttack(caster)
                                    : level().damageSources().magic();
        for (LivingEntity target : hits) {
            if (!target.isAlive()) continue;
            if (VerdictDamage.strike(target, source, hurt, bypass, caster)) {
                VerdictFeedback.landed(target, VerdictFeedback.weightOf(hits.size()));
                landed++;
            }
        }

        if (landed > 0) {
            // 命中越多音越高：玩家不用看屏幕就知道这一柱扫到了几个人
            VerdictFeedback.playSound(level(), position(),
                    SoundEvents.PLAYER_ATTACK_CRIT, 0.55f, VerdictFeedback.hitPitch(landed));
        }

        // ── 落地冲击：只有第一波给"落柱"的那一下，后两波是扩散的余韵 ──
        if (first) {
            Vec3 base = position();
            VerdictFeedback.impactRing(level(), base.add(0, 0.05, 0),
                    Math.max(3.0f, getRadius() * 1.35f), 4.2f, 0.9f);
            VerdictFeedback.burst(level(), base.add(0, 0.4, 0),
                    ParticleTypes.END_ROD, 26, 0.22);
            VerdictFeedback.burst(level(), base.add(0, 0.15, 0),
                    ParticleTypes.CLOUD, 14, 0.05);

            // 相机：落柱那一刻的一次短促后坐（离得越远越轻）
            if (caster != null) {
                float w = VerdictFeedback.weightOf(Math.max(1, landed));
                VerdictFeedback.shakeNear(caster, base, 0.55f + 0.35f * w);
                VerdictFeedback.fovKick(caster, -2.2f);      // 负号 = 视野收紧 = 被压住
            }
        } else if (caster != null && landed > 0) {
            // 后两波：一次轻微抖动，作为"柱子又推宽了一圈"的触觉回执。
            // 冲击环的半径跟着当时的扩散半径走，于是那个环就是"范围边界"的可视化。
            VerdictFeedback.shakeNear(caster, position(), 0.28f);
            VerdictFeedback.impactRing(level(), position().add(0, 0.05, 0),
                    Math.max(2.0f, radius * 1.15f), 6.5f, 0.5f);
        }
    }

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }

    // ══════════════════════════════════════════════════════════════
    //  存档
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        wave1Done = tag.getBoolean("Wave1");
        wave2Done = tag.getBoolean("Wave2");
        wave3Done = tag.getBoolean("Wave3");
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("Length")) setLength(tag.getFloat("Length"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.contains("TargetRadius")) setTargetRadius(tag.getFloat("TargetRadius"));
        if (tag.contains("GrowTicks")) setGrowTicks(tag.getInt("GrowTicks"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Wave1", wave1Done);
        tag.putBoolean("Wave2", wave2Done);
        tag.putBoolean("Wave3", wave3Done);
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Length", getLength());
        tag.putInt("MaxAge", getMaxAge());
        tag.putFloat("TargetRadius", getTargetRadius());
        tag.putInt("GrowTicks", getGrowTicks());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

    /**
     * 在指定底面中心生成一根光柱。
     *
     * <p>生成即开始计时，第一波伤害在 {@link #WAVE1_TICK} tick 结算 ——
     * 也就是"出现"与"打到"是同一帧，视觉上不会有任何前摇延迟感。
     * 之后柱子按 {@code sqrt} 曲线扩散到 {@link #DEFAULT_TARGET_RADIUS}，
     * 第二、三波分别落在扩散的中段与完成。</p>
     *
     * @param baseCenter   光柱底面的中心（一般传玩家脚底位置）
     * @param radius       <b>起始</b>半径（格）
     * @param targetRadius 扩散终点半径（格）
     * @param length       向上延伸的<b>视觉</b>长度（格）
     *                     —— 判定高度另有上限，见 {@link HeightFactor#beamHitHeight}
     * @param caster       施法者（伤害归因 + 反馈对象）
     */
    public static VerdictBeamEntity spawn(Level level, Vec3 baseCenter,
                                          float radius, float targetRadius, float length,
                                          int growTicks, int maxAge, @Nullable Player caster) {
        VerdictBeamEntity e = new VerdictBeamEntity(EntityTypeRegistry.VERDICT_BEAM.get(), level);
        e.setPos(baseCenter);
        e.setRadius(radius);
        e.setTargetRadius(Math.max(radius, targetRadius));   // 终点不得小于起点
        e.setLength(length);
        e.setGrowTicks(growTicks);
        e.setMaxAge(maxAge);
        e.setCaster(caster);
        level.addFreshEntity(e);
        return e;
    }

    /** 默认参数的快捷工厂。 */
    public static VerdictBeamEntity spawn(Level level, Vec3 baseCenter,
                                          float radius, float length, @Nullable Player caster) {
        return spawn(level, baseCenter, radius, DEFAULT_TARGET_RADIUS, length,
                DEFAULT_GROW_TICKS, DEFAULT_MAX_AGE, caster);
    }
}
