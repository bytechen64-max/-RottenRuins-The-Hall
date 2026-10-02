package org.bytechen.hall.overworld.registry.entities.population.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;

/**
 * 剑气技能实体 —— 两个圆锥底面贴合组成的白色发光剑气。
 * <p>
 * 渲染行为：初始正常大小，随时间逐渐缩小，透明度逐渐降低。
 * 旋转角度随机，缩小速度可配置。
 */
public class SwordAuraEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ALPHA =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    /** 缩小速率乘数，1.0=正常速度，>1 更快，<1 更慢 */
    private static final EntityDataAccessor<Float> DATA_SHRINK_SPEED =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 渲染样式。
     * <ul>
     *   <li>{@link #STYLE_DEFAULT}：原有样式 —— 单个白色加法混合圆锥对，发光。</li>
     *   <li>{@link #STYLE_APOSTLE}：使徒斩击 —— 额外一层更大的<b>白色外层</b>，
     *       内部黑色剑气<b>始终画在白色之上</b>（形成 outline 观感），
     *       白色外层外侧再叠一层空间扭曲。见 {@code SwordAuraRenderer}。</li>
     * </ul>
     */
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.INT);
    /** 外层白色剑气相对内芯的缩放倍数（仅使徒样式使用）。 */
    private static final EntityDataAccessor<Float> DATA_OUTER_SCALE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);

    /** 默认样式：白色发光剑气。 */
    public static final int STYLE_DEFAULT = 0;
    /** 使徒样式：白色外层 + 黑色内芯 outline + 外层空间扭曲。 */
    public static final int STYLE_APOSTLE = 1;
    /**
     * 天穹裁决用的"纯白发光 + 指定朝向"样式。
     *
     * <p>与 {@link #STYLE_DEFAULT} 的唯一区别是<b>几何朝向可控</b>：
     * DEFAULT 按 entity id 派生一个随机俯仰（{@code getPitch()}），锥体是歪的 ——
     * 那对"一片混战里的斩光"是对的，但对裁决不行：
     * 裁决的剑气必须读作"一道竖着劈下来的斩击"（领域落剑）
     * 或"一道横着扫过去的斩击"（剑气飞出）。歪着放只会变成一团斜光斑。</p>
     *
     * <p>朝向由 {@link #setVerdictOrientation(float)} 指定，复用了
     * {@code DATA_OUTER_SCALE} 那个字段的存储位（本样式不用外层白边，
     * 于是它空着 —— 这样不必为了一个 float 再动一次同步数据表）。</p>
     */
    public static final int STYLE_VERDICT = 2;

    /** 竖直（俯仰 0）：剑尖朝下劈。 */
    public static final float ORIENT_VERTICAL = 0.0f;
    /** 平躺（俯仰 90°）：剑身横过来扫。 */
    public static final float ORIENT_HORIZONTAL = 1.0f;

    /** 使徒样式外层白色剑气的默认缩放倍数。 */
    public static final float DEFAULT_OUTER_SCALE = 1.45f;
    /** 使徒样式剑气默认总长度（细长）。 */
    public static final float APOSTLE_HEIGHT = 8.0f;
    /** 使徒样式剑气默认底面半径。 */
    public static final float APOSTLE_RADIUS = 1.0f;
    /** Y 轴旋转角度（度），由 entity ID 派生，无需同步 */

    /** 默认总长度（两个圆锥高度之和），3.0 让剑气看起来修长 */
    private static final float DEFAULT_HEIGHT = 3.0f;
    /** 默认底面半径，0.28 配合 height=3.0 比例协调 */
    private static final float DEFAULT_RADIUS = 0.28f;
    /** 默认存活时间（tick） */
    private static final int DEFAULT_MAX_AGE = 40;
    /** 默认初始透明度 */
    private static final float DEFAULT_ALPHA = 1.0f;
    /** 默认初始缩放 */
    private static final float DEFAULT_SCALE = 1.0f;
    /** 默认缩小速率 */
    private static final float DEFAULT_SHRINK_SPEED = 1.0f;

    private int age;

    public SwordAuraEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 同步数据
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_SCALE, DEFAULT_SCALE);
        entityData.define(DATA_ALPHA, DEFAULT_ALPHA);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_HEIGHT, DEFAULT_HEIGHT);
        entityData.define(DATA_RADIUS, DEFAULT_RADIUS);
        entityData.define(DATA_SHRINK_SPEED, DEFAULT_SHRINK_SPEED);
        entityData.define(DATA_STYLE, STYLE_DEFAULT);
        entityData.define(DATA_OUTER_SCALE, DEFAULT_OUTER_SCALE);
    }

    // ═══════════════════════════════════════════════════════════════
    // Getter / Setter
    // ═══════════════════════════════════════════════════════════════

    public float getScale()        { return entityData.get(DATA_SCALE); }
    public float getAlpha()        { return entityData.get(DATA_ALPHA); }
    public int   getMaxAge()       { return entityData.get(DATA_MAX_AGE); }
    public float getAuraHeight()   { return entityData.get(DATA_HEIGHT); }
    public float getAuraRadius()   { return entityData.get(DATA_RADIUS); }
    public float getShrinkSpeed()  { return entityData.get(DATA_SHRINK_SPEED); }
    public int   getAge()          { return age; }

    /** 渲染样式，见 {@link #STYLE_DEFAULT} / {@link #STYLE_APOSTLE}。 */
    public int   getStyle()        { return entityData.get(DATA_STYLE); }
    /** 外层白色剑气的缩放倍数（仅使徒样式有意义）。 */
    public float getOuterScale()   { return entityData.get(DATA_OUTER_SCALE); }

    /**
     * 由 entity ID 派生的 32 位哈希，客户端/服务端一致，无需同步。
     */
    private long idHash() {
        return (this.getId() * 2654435761L) & 0xFFFFFFFFL;
    }

    /** 绕 Z 轴旋转角度（度），前后倾倒，由 entity ID 高 16 位派生 */
    public float getPitch() {
        return ((idHash() >>> 16) & 0xFFFF) * (360f / 0x10000);
    }

    /** 绕 Y 轴旋转角度（度），水平转向，由 entity ID 低 16 位派生 */
    public float getYaw() {
        return (idHash() & 0xFFFF) * (360f / 0x10000);
    }

    /** 0→1 的生命进度，用于渲染插值 */
    public float getLifeProgress() {
        return getLifeProgress(0f);
    }

    /**
     * 带 partialTick 的生命进度。
     * <p>剑气只活 {@code maxAge} tick，而 20 连斩每 5 tick 一刀、每刀在世十几 tick ——
     * 不做部分刻插值的话，缩小与淡出都会是"跳"的（每秒只更新 20 次），
     * 在长刀身上放大成明显的分段感。渲染侧统一用这个重载。</p>
     *
     * @param partialTick 当前帧在当前 tick 内的插值比例（0~1）
     */
    public float getLifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        float at = Math.min(age + Math.max(0f, partialTick), max);
        return Math.min(at / max, 1f);
    }

    /**
     * 当前渲染缩放 = 初始缩放 × max(0, 1 - shrinkSpeed × lifeProgress)
     * shrinkSpeed 越大缩小越快。
     */
    public float getCurrentScale() {
        return getCurrentScale(0f);
    }

    /** 带 partialTick 的当前缩放。 */
    public float getCurrentScale(float partialTick) {
        return getScale() * Math.max(0f, 1f - getShrinkSpeed() * getLifeProgress(partialTick));
    }

    /** 当前渲染透明度 = 初始透明度 × (1 - lifeProgress)² */
    public float getCurrentAlpha() {
        return getCurrentAlpha(0f);
    }

    /** 带 partialTick 的当前透明度。 */
    public float getCurrentAlpha(float partialTick) {
        float t = getLifeProgress(partialTick);
        return getAlpha() * (1f - t) * (1f - t);
    }

    public void setScale(float v)       { entityData.set(DATA_SCALE, v); }
    public void setAlpha(float v)       { entityData.set(DATA_ALPHA, v); }
    public void setMaxAge(int v)        { entityData.set(DATA_MAX_AGE, v); }
    public void setAuraHeight(float v)  { entityData.set(DATA_HEIGHT, v); }
    public void setAuraRadius(float v)  { entityData.set(DATA_RADIUS, v); }
    public void setShrinkSpeed(float v) { entityData.set(DATA_SHRINK_SPEED, v); }
    public void setStyle(int v)         { entityData.set(DATA_STYLE, v == STYLE_APOSTLE ? STYLE_APOSTLE : v == STYLE_VERDICT ? STYLE_VERDICT : STYLE_DEFAULT); }
    public void setOuterScale(float v)  { entityData.set(DATA_OUTER_SCALE, Math.max(0f, v)); }

    /**
     * 设置裁决样式（{@link #STYLE_VERDICT}）的几何朝向。
     *
     * @param orientation {@link #ORIENT_VERTICAL} 或 {@link #ORIENT_HORIZONTAL}
     */
    public void setVerdictOrientation(float orientation) {
        entityData.set(DATA_OUTER_SCALE, Mth.clamp(orientation, 0f, 1f));
    }

    /** 裁决样式的朝向（0 = 竖直，1 = 平躺）。其它样式下无意义。 */
    public float getVerdictOrientation() {
        return Mth.clamp(entityData.get(DATA_OUTER_SCALE), 0f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    // Tick
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        if (isPassenger() && getVehicle() != null && !getVehicle().isPassengerOfSameVehicle(this)) {
            stopRiding();
        }
        age++;
        if (!level().isClientSide() && age >= getMaxAge()) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override
    public boolean isPickable()   { return false; }
    @Override
    public boolean isAttackable() { return false; }

    // ═══════════════════════════════════════════════════════════════
    // NBT 存档
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.contains("Scale"))       setScale(tag.getFloat("Scale"));
        if (tag.contains("Alpha"))       setAlpha(tag.getFloat("Alpha"));
        if (tag.contains("MaxAge"))      setMaxAge(tag.getInt("MaxAge"));
        if (tag.contains("Height"))      setAuraHeight(tag.getFloat("Height"));
        if (tag.contains("Radius"))      setAuraRadius(tag.getFloat("Radius"));
        if (tag.contains("ShrinkSpeed")) setShrinkSpeed(tag.getFloat("ShrinkSpeed"));
        if (tag.contains("Style"))       setStyle(tag.getInt("Style"));
        if (tag.contains("OuterScale"))  setOuterScale(tag.getFloat("OuterScale"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putFloat("Scale", getScale());
        tag.putFloat("Alpha", getAlpha());
        tag.putInt("MaxAge", getMaxAge());
        tag.putFloat("Height", getAuraHeight());
        tag.putFloat("Radius", getAuraRadius());
        tag.putFloat("ShrinkSpeed", getShrinkSpeed());
        tag.putInt("Style", getStyle());
        tag.putFloat("OuterScale", getOuterScale());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ═══════════════════════════════════════════════════════════════
    // 静态工厂
    // ═══════════════════════════════════════════════════════════════

    /**
     * 在指定位置生成剑气实体。
     *
     * @param level       世界
     * @param pos         生成位置
     * @param scale       初始缩放
     * @param alpha       初始透明度
     * @param height      剑气总长度
     * @param radius      底面半径
     * @param maxAge      存活 tick 数
     * @param shrinkSpeed 缩小速率（1.0=正常，>1 更快，<1 更慢）
     */
    public static SwordAuraEntity spawn(Level level, Vec3 pos,
                                         float scale, float alpha,
                                         float height, float radius,
                                         int maxAge, float shrinkSpeed) {
        SwordAuraEntity e = new SwordAuraEntity(EntityTypeRegistry.SWORD_AURA.get(), level);
        e.setPos(pos);
        e.setScale(scale);
        e.setAlpha(alpha);
        e.setAuraHeight(height);
        e.setAuraRadius(radius);
        e.setMaxAge(maxAge);
        e.setShrinkSpeed(shrinkSpeed);
        level.addFreshEntity(e);
        return e;
    }

    /** 使用默认参数的快捷工厂（随机角度自动设置） */
    public static SwordAuraEntity spawn(Level level, Vec3 pos) {
        return spawn(level, pos, DEFAULT_SCALE, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, DEFAULT_MAX_AGE, DEFAULT_SHRINK_SPEED);
    }

    /** 指定位置和存活时间的快捷工厂 */
    public static SwordAuraEntity spawn(Level level, Vec3 pos, int maxAge) {
        return spawn(level, pos, DEFAULT_SCALE, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, maxAge, DEFAULT_SHRINK_SPEED);
    }

    /** 指定位置，存活时间和缩小速度的快捷工厂 */
    public static SwordAuraEntity spawn(Level level, Vec3 pos, int maxAge, float shrinkSpeed) {
        return spawn(level, pos, DEFAULT_SCALE, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, maxAge, shrinkSpeed);
    }

    /** 指定位置，缩放，存活时间和缩小速度的快捷工厂 */
    public static SwordAuraEntity spawn(Level level, Vec3 pos, float scale, int maxAge, float shrinkSpeed) {
        return spawn(level, pos, scale, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, maxAge, shrinkSpeed);
    }

    /**
     * <b>使徒斩击</b>专用工厂：细长剑气 + outline 样式。
     *
     * <p>与默认剑气的区别（全部由 {@link #STYLE_APOSTLE} 在渲染器里决定）：
     * <ul>
     *   <li>默认尺寸改为高 {@value #APOSTLE_HEIGHT} 格、底面半径 {@value #APOSTLE_RADIUS} 格（细长）；</li>
     *   <li>额外渲染一层更大的白色剑气，内部黑色剑气始终压在白色之上 → outline 观感；</li>
     *   <li>白色外层外侧叠一层空间扭曲（带光影兼容的延迟回放路径）。</li>
     * </ul>
     *
     * @param scale       初始缩放
     * @param height      剑气总长度（格），建议 {@link #APOSTLE_HEIGHT}
     * @param radius      底面半径（格），建议 {@link #APOSTLE_RADIUS}
     * @param maxAge      存活 tick
     * @param shrinkSpeed 收缩速度
     */
    public static SwordAuraEntity spawnApostleSlash(Level level, Vec3 pos,
                                                    float scale, float height, float radius,
                                                    int maxAge, float shrinkSpeed) {
        SwordAuraEntity e = new SwordAuraEntity(EntityTypeRegistry.SWORD_AURA.get(), level);
        e.setPos(pos);
        e.setStyle(STYLE_APOSTLE);
        e.setOuterScale(DEFAULT_OUTER_SCALE);
        e.setScale(scale);
        e.setAlpha(DEFAULT_ALPHA);
        e.setAuraHeight(height);
        e.setAuraRadius(radius);
        e.setMaxAge(maxAge);
        e.setShrinkSpeed(shrinkSpeed);
        level.addFreshEntity(e);
        return e;
    }

    /** 使徒斩击的默认参数快捷工厂。 */
    public static SwordAuraEntity spawnApostleSlash(Level level, Vec3 pos) {
        return spawnApostleSlash(level, pos,
                DEFAULT_SCALE, APOSTLE_HEIGHT, APOSTLE_RADIUS, 14, 1.6f);
    }

    /**
     * <b>天穹裁决</b>专用：白发光剑气 + 可控朝向。
     *
     * <p>用同一个实体做三种不同读法的剑光，避免为"竖着的剑"再开一类实体：</p>
     * <ul>
     *   <li>{@link #ORIENT_VERTICAL} —— 领域落剑：剑尖朝下钉进地里；</li>
     *   <li>{@link #ORIENT_HORIZONTAL} —— 剑气飞出 / 突进拖尾的变体：剑身横过来；</li>
     *   <li>配合不同的 {@code height}/{@code radius} 比例，同一套几何还能读出
     *       "细长剑"与"短促斩光"的区别。</li>
     * </ul>
     *
     * @param orientation {@link #ORIENT_VERTICAL} 或 {@link #ORIENT_HORIZONTAL}
     */
    public static SwordAuraEntity spawnVerdictBlade(Level level, Vec3 pos, float scale,
                                                   float alpha, float height, float radius,
                                                   int maxAge, float shrinkSpeed,
                                                   float orientation) {
        SwordAuraEntity e = new SwordAuraEntity(EntityTypeRegistry.SWORD_AURA.get(), level);
        e.setPos(pos);
        e.setStyle(STYLE_VERDICT);
        e.setVerdictOrientation(orientation);
        e.setScale(scale);
        e.setAlpha(alpha);
        e.setAuraHeight(height);
        e.setAuraRadius(radius);
        e.setMaxAge(maxAge);
        e.setShrinkSpeed(shrinkSpeed);
        level.addFreshEntity(e);
        return e;
    }
}
