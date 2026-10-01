package org.bytechen.hall.overworld.registry.entities.population.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;

/**
 * 天穹裁决的垂直光柱 —— 纯渲染实体，伤害在 {@code DomeriteLongsword#castBeam} 里已经结算。
 *
 * <h3>几何契约</h3>
 * <pre>
 *   实体位置 = 光柱底面中心（玩家脚底）
 *   光柱沿 +Y 向上延伸 {@link #getLength()} 格，半径 {@link #getRadius()} 格
 * </pre>
 * <p>这个契约必须与 {@code castBeam} 里那个竖直 AABB 完全一致 ——
 * 判定范围就是「底面中心 + 半径 + 长度」，所以视觉与命中永远对得上。
 * 将来要改范围，两个地方一起改。</p>
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

    // ── 默认值 ──────────────────────────────────────────────────────
    /** 默认半径（格）。地表值，会被高度系数与裁决层数放大。 */
    public static final float DEFAULT_RADIUS = 2.6f;
    /** 默认长度（格）：抬头 90° 的满长度。 */
    public static final float DEFAULT_LENGTH = 44.0f;
    /**
     * 默认存活（tick）。
     * <p>刻意比"扫下来"所需的时间长得多 —— 光柱是一个<b>留在场上的裁决痕迹</b>，
     * 不是一次性闪一下就消失的特效。12 秒足够玩家看清它把哪条线判了，
     * 也让"抬头瞄准"这个动作有回执。</p>
     */
    public static final int DEFAULT_MAX_AGE = 240;

    /** 淡入时长（tick）：0 → 1 的强度爬升。 */
    public static final float FADE_IN_TICKS = 4f;
    /** 淡出时长（tick）：1 → 0 的收束。 */
    public static final float FADE_OUT_TICKS = 20f;

    private int age;

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
    }

    public float getRadius()  { return entityData.get(DATA_RADIUS); }
    public float getLength()  { return entityData.get(DATA_LENGTH); }
    public int   getMaxAge()  { return entityData.get(DATA_MAX_AGE); }
    public int   getAge()     { return age; }

    public void setRadius(float v) { entityData.set(DATA_RADIUS, Math.max(0.05f, v)); }
    public void setLength(float v) { entityData.set(DATA_LENGTH, Math.max(0.5f, v)); }
    public void setMaxAge(int v)   { entityData.set(DATA_MAX_AGE, Math.max(1, v)); }

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
        if (!level().isClientSide() && age >= getMaxAge()) discard();
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
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("Length")) setLength(tag.getFloat("Length"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Length", getLength());
        tag.putInt("MaxAge", getMaxAge());
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
     * @param baseCenter 光柱底面的中心（一般传玩家脚底位置）
     * @param radius     半径（格）
     * @param length     向上延伸的长度（格）
     */
    public static VerdictBeamEntity spawn(Level level, Vec3 baseCenter,
                                          float radius, float length, int maxAge) {
        VerdictBeamEntity e = new VerdictBeamEntity(EntityTypeRegistry.VERDICT_BEAM.get(), level);
        e.setPos(baseCenter);
        e.setRadius(radius);
        e.setLength(length);
        e.setMaxAge(maxAge);
        level.addFreshEntity(e);
        return e;
    }

    /** 默认参数：半径 {@value #DEFAULT_RADIUS} / 长度 {@value #DEFAULT_LENGTH}。 */
    public static VerdictBeamEntity spawn(Level level, Vec3 baseCenter, float radius, float length) {
        return spawn(level, baseCenter, radius, length, DEFAULT_MAX_AGE);
    }
}
