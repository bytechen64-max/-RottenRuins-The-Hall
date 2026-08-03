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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;

/**
 * 黑洞实体 —— 纯视觉特效，无任何内部逻辑。
 *
 * <p>不移动、不受重力、不可拾取、不可攻击、不产生伤害，只提供一个
 * 被 {@code BlackHoleRenderer} 程序化绘制的渲染锚点。渲染参数通过
 * 同步数据下发，客户端在 {@code BlackHoleRenderHandler} 中绘制。
 *
 * <p>生命周期：存活 {@code lifetime} tick 后自动丢弃。
 */
public class BlackHoleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEND =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);

    private static final float DEF_RADIUS = 1.5f;
    private static final float DEF_BEND = 3.0f;
    private static final int DEF_LIFETIME = 400;

    private int age;

    public BlackHoleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, DEF_RADIUS);
        this.entityData.define(DATA_BEND, DEF_BEND);
        this.entityData.define(DATA_LIFETIME, DEF_LIFETIME);
    }

    // ── 渲染参数（同步数据） ──

    /** 史瓦西半径（方块）。决定黑洞盘面大小与引力弯曲范围。 */
    public float getRadius() { return this.entityData.get(DATA_RADIUS); }
    public void setRadius(float v) { this.entityData.set(DATA_RADIUS, Math.max(0.1f, v)); }

    /** 引力弯曲强度系数（无量纲，越大透镜越强）。 */
    public float getBend() { return this.entityData.get(DATA_BEND); }
    public void setBend(float v) { this.entityData.set(DATA_BEND, Math.max(0.0f, Math.min(50.0f, v))); }

    public int getLifetime() { return this.entityData.get(DATA_LIFETIME); }
    public void setLifetime(int v) { this.entityData.set(DATA_LIFETIME, Math.max(1, v)); }

    public int getAge() { return age; }

    // ── Tick：仅计时，到期丢弃（无其他逻辑） ──

    @Override
    public void tick() {
        if (this.isPassenger() && this.getVehicle() != null
                && !this.getVehicle().isPassengerOfSameVehicle(this)) {
            this.stopRiding();
        }
        this.age++;
        if (!this.level().isClientSide() && this.age >= getLifetime()) {
            this.discard();
        }
    }

    // ── 纯视觉：不可交互 ──

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isAttackable() { return false; }

    /** 渲染时的大范围剔除盒：覆盖整块透镜 billboard。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        float r = getRadius();
        return this.getBoundingBox().inflate(r * 12.0);
    }

    // ── 持久化 ──

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("Bend")) setBend(tag.getFloat("Bend"));
        if (tag.contains("Lifetime")) setLifetime(tag.getInt("Lifetime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Bend", getBend());
        tag.putInt("Lifetime", getLifetime());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ── 静态工厂 ──

    public static BlackHoleEntity spawn(Level level, Vec3 pos,
                                         float radius, float bend, int lifetime) {
        BlackHoleEntity e = new BlackHoleEntity(EntityTypeRegistry.BLACK_HOLE.get(), level);
        e.setPos(pos);
        e.setRadius(radius);
        e.setBend(bend);
        e.setLifetime(lifetime);
        level.addFreshEntity(e);
        return e;
    }

    public static BlackHoleEntity spawn(Level level, Vec3 pos) {
        return spawn(level, pos, DEF_RADIUS, DEF_BEND, DEF_LIFETIME);
    }
}
