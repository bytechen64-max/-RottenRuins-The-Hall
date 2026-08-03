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
        int max = getMaxAge();
        return max <= 0 ? 1f : Math.min((float) age / max, 1f);
    }

    /**
     * 当前渲染缩放 = 初始缩放 × max(0, 1 - shrinkSpeed × lifeProgress)
     * shrinkSpeed 越大缩小越快。
     */
    public float getCurrentScale() {
        return getScale() * Math.max(0f, 1f - getShrinkSpeed() * getLifeProgress());
    }

    /** 当前渲染透明度 = 初始透明度 × (1 - lifeProgress)² */
    public float getCurrentAlpha() {
        float t = getLifeProgress();
        return getAlpha() * (1f - t) * (1f - t);
    }

    public void setScale(float v)       { entityData.set(DATA_SCALE, v); }
    public void setAlpha(float v)       { entityData.set(DATA_ALPHA, v); }
    public void setMaxAge(int v)        { entityData.set(DATA_MAX_AGE, v); }
    public void setAuraHeight(float v)  { entityData.set(DATA_HEIGHT, v); }
    public void setAuraRadius(float v)  { entityData.set(DATA_RADIUS, v); }
    public void setShrinkSpeed(float v) { entityData.set(DATA_SHRINK_SPEED, v); }

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
}
