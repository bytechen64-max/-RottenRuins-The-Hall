package org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills;

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
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;

/**
 * 剑气技能实体：两个圆锥底面贴合组成的发光剑气，随时间缩小并淡出，旋转角度随机。
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
    private static final EntityDataAccessor<Float> DATA_SHRINK_SPEED =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 渲染样式：DEFAULT 单层白色发光圆锥对；APOSTLE 额外一层更大的白色外层、黑内芯压在上面
     * （outline 观感）+ 外层空间扭曲，见 {@code SwordAuraRenderer}；VERDICT 发光 + 受控朝向；
     * FROST 几何同使徒、配色换成冰白 / 深蓝。
     */
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_OUTER_SCALE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);

    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_APOSTLE = 1;
    /**
     * 天穹裁决样式：与 {@link #STYLE_DEFAULT} 只差几何朝向可控 —— DEFAULT 按 entity id 派生随机俯仰（锥体是歪的），
     * 而裁决必须读作"竖着劈下来"或"横着扫过去"。朝向复用 {@code DATA_OUTER_SCALE} 的存储位，省一次同步表改动。
     */
    public static final int STYLE_VERDICT = 2;

    /** 寒霜斩击样式：几何与 {@link #STYLE_APOSTLE} 完全一致，只把配色从白 / 黑换成冰白 / 深蓝
     *  （单开一档而不加颜色参数：加三通道要多三个 {@code EntityDataAccessor} 并多写一遍 NBT）。 */
    public static final int STYLE_FROST = 3;

    /** 焰斩样式：朝向规则同 {@link #STYLE_VERDICT}，配色由渲染器换成红橙。 */
    public static final int STYLE_FLAME = 4;

    /** 竖直（俯仰 0）：剑尖朝下劈。 */
    public static final float ORIENT_VERTICAL = 0.0f;
    /** 平躺（俯仰 90°）：剑身横过来扫。 */
    public static final float ORIENT_HORIZONTAL = 1.0f;

    public static final float DEFAULT_OUTER_SCALE = 1.45f;
    public static final float APOSTLE_HEIGHT = 8.0f;
    public static final float APOSTLE_RADIUS = 1.0f;

    private static final float DEFAULT_HEIGHT = 3.0f;
    private static final float DEFAULT_RADIUS = 0.28f;
    private static final int DEFAULT_MAX_AGE = 40;
    private static final float DEFAULT_ALPHA = 1.0f;
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float DEFAULT_SHRINK_SPEED = 1.0f;

    private int age;

    public SwordAuraEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_SCALE, DEFAULT_SCALE);
        entityData.define(DATA_ALPHA, DEFAULT_ALPHA);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_HEIGHT, DEFAULT_HEIGHT);
        entityData.define(DATA_RADIUS, DEFAULT_RADIUS);
        entityData.define(DATA_SHRINK_SPEED, DEFAULT_SHRINK_SPEED);
        entityData.define(DATA_STYLE, STYLE_DEFAULT);
        entityData.define(DATA_OUTER_SCALE, DEFAULT_OUTER_SCALE);
    }

    public float getScale()        { return entityData.get(DATA_SCALE); }
    public float getAlpha()        { return entityData.get(DATA_ALPHA); }
    public int   getMaxAge()       { return entityData.get(DATA_MAX_AGE); }
    public float getAuraHeight()   { return entityData.get(DATA_HEIGHT); }
    public float getAuraRadius()   { return entityData.get(DATA_RADIUS); }
    public float getShrinkSpeed()  { return entityData.get(DATA_SHRINK_SPEED); }
    public int   getAge()          { return age; }

    /** 渲染样式，见 {@link #DATA_STYLE} 的说明。 */
    public int   getStyle()        { return entityData.get(DATA_STYLE); }
    public float getOuterScale()   { return entityData.get(DATA_OUTER_SCALE); }

    /** 由 entity ID 派生的 32 位哈希：客户端/服务端一致，无需同步。 */
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
     * 带 partialTick 的生命进度：20 连斩每 5 tick 一刀、每刀在世十几 tick，
     * 不做部分刻插值的话缩小与淡出会按 20Hz 跳，在长刀身上放大成分段感。渲染侧统一用这个重载。
     */
    public float getLifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        float at = Math.min(age + Math.max(0f, partialTick), max);
        return Math.min(at / max, 1f);
    }

    /** 当前渲染缩放 = 初始缩放 × max(0, 1 - shrinkSpeed × lifeProgress)。 */
    public float getCurrentScale() {
        return getCurrentScale(0f);
    }

    public float getCurrentScale(float partialTick) {
        return getScale() * Math.max(0f, 1f - getShrinkSpeed() * getLifeProgress(partialTick));
    }

    /** 当前渲染透明度 = 初始透明度 × (1 - lifeProgress)² */
    public float getCurrentAlpha() {
        return getCurrentAlpha(0f);
    }

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
    public void setStyle(int v)         { entityData.set(DATA_STYLE, normalizeStyle(v)); }

    /** 把外部传来的样式号收敛到已知的四个样式之一（未知值退化成默认样式）。 */
    private static int normalizeStyle(int v) {
        if (v == STYLE_APOSTLE) return STYLE_APOSTLE;
        if (v == STYLE_VERDICT) return STYLE_VERDICT;
        if (v == STYLE_FROST)   return STYLE_FROST;
        return STYLE_DEFAULT;
    }
    public void setOuterScale(float v)  { entityData.set(DATA_OUTER_SCALE, Math.max(0f, v)); }

    /** 设置裁决样式（{@link #STYLE_VERDICT}）的几何朝向：{@link #ORIENT_VERTICAL} 或 {@link #ORIENT_HORIZONTAL}。 */
    public void setVerdictOrientation(float orientation) {
        entityData.set(DATA_OUTER_SCALE, Mth.clamp(orientation, 0f, 1f));
    }

    /** 裁决样式的朝向（0 = 竖直，1 = 平躺）。其它样式下无意义。 */
    public float getVerdictOrientation() {
        return Mth.clamp(entityData.get(DATA_OUTER_SCALE), 0f, 1f);
    }

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

    // NBT 存档

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
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
    public void addAdditionalSaveData(CompoundTag tag) {
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

    /**
     * 在指定位置生成剑气实体。maxAge 是存活 tick 数，shrinkSpeed 是缩小速率（1.0 正常，>1 更快）。
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

    public static SwordAuraEntity spawn(Level level, Vec3 pos) {
        return spawn(level, pos, DEFAULT_SCALE, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, DEFAULT_MAX_AGE, DEFAULT_SHRINK_SPEED);
    }

    public static SwordAuraEntity spawn(Level level, Vec3 pos, int maxAge) {
        return spawn(level, pos, DEFAULT_SCALE, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, maxAge, DEFAULT_SHRINK_SPEED);
    }

    public static SwordAuraEntity spawn(Level level, Vec3 pos, int maxAge, float shrinkSpeed) {
        return spawn(level, pos, DEFAULT_SCALE, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, maxAge, shrinkSpeed);
    }

    public static SwordAuraEntity spawn(Level level, Vec3 pos, float scale, int maxAge, float shrinkSpeed) {
        return spawn(level, pos, scale, DEFAULT_ALPHA,
                DEFAULT_HEIGHT, DEFAULT_RADIUS, maxAge, shrinkSpeed);
    }

    /**
     * 使徒斩击专用工厂：细长剑气（建议高 {@link #APOSTLE_HEIGHT}、半径 {@link #APOSTLE_RADIUS}）+ outline 样式，
     * 具体画法（更大白外层 + 黑内芯 + 外层空间扭曲）由 {@link #STYLE_APOSTLE} 在渲染器里决定。
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

    public static SwordAuraEntity spawnApostleSlash(Level level, Vec3 pos) {
        return spawnApostleSlash(level, pos,
                DEFAULT_SCALE, APOSTLE_HEIGHT, APOSTLE_RADIUS, 14, 1.6f);
    }

    /** 焰斩专用工厂：横躺的红色剑气，朝向用 {@link #setVerdictOrientation} 控制。 */
    public static SwordAuraEntity spawnFlameSlash(Level level, Vec3 pos, float scale, float orientation) {
        SwordAuraEntity e = new SwordAuraEntity(EntityTypeRegistry.SWORD_AURA.get(), level);
        e.setPos(pos);
        e.setStyle(STYLE_FLAME);
        e.setVerdictOrientation(orientation);
        e.setScale(scale);
        e.setAlpha(DEFAULT_ALPHA);
        e.setAuraHeight(5.0f);
        e.setAuraRadius(0.32f);
        e.setMaxAge(18);
        e.setShrinkSpeed(1.4f);
        level.addFreshEntity(e);
        return e;
    }

    /**
     * 寒霜斩击专用工厂：构件与 {@link #spawnApostleSlash} 一致，只把样式换成 {@link #STYLE_FROST}（蓝白 outline）。
     * 尺寸更短更细（寂寒白日三连斩高 6、半径 0.9）—— 它贴着目标放，不像使徒那样贯穿半个房间。
     */
    public static SwordAuraEntity spawnFrostSlash(Level level, Vec3 pos,
                                                 float scale, float height, float radius,
                                                 int maxAge, float shrinkSpeed) {
        SwordAuraEntity e = new SwordAuraEntity(EntityTypeRegistry.SWORD_AURA.get(), level);
        e.setPos(pos);
        e.setStyle(STYLE_FROST);
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

    /**
     * 天穹裁决专用：发光剑气 + 可控朝向（{@link #ORIENT_VERTICAL} 领域落剑 / {@link #ORIENT_HORIZONTAL} 剑气飞出），
     * 用同一个实体做三种读法，避免为"竖着的剑"再开一类实体。
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
