package org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills;

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
import org.bytechen.rottenruins.event.impl.BlackHolePhysicsHandler;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import java.util.UUID;

/**
 * 黑洞实体 —— 只承载状态，不含任何效果逻辑：作为 {@code BlackHoleRenderer} 的渲染锚点，
 * 并通过同步数据暴露史瓦西半径、弯曲强度、质量代理量、引力/事件视界半径与存活时长。
 * 引力与伤害由 {@code BlackHolePhysicsHandler} 在服务端 LevelTickEvent 里读取后施加。
 */
public class BlackHoleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEND =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    /** 碰撞箱体积（质量代理量）。牛顿引力 F = G*M*m/r^2 里的 M。 */
    private static final EntityDataAccessor<Float> DATA_COLLIDER_VOLUME =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    /** 引力作用半径（格）。超出该距离的生物不再被吸引。 */
    private static final EntityDataAccessor<Float> DATA_GRAVITY_RADIUS =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    /** 事件视界半径（格）。进入此范围每 tick 受到伤害。 */
    private static final EntityDataAccessor<Float> DATA_KILL_RADIUS =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 每 tick 的改血伤害（点）：{@code 0}（默认）= 走处理器里原有的物理伤害路径，
     * &gt;0 则表示技能召唤物、按此值走 VitalProbe 改血。判断与施加都在处理器里。
     */
    private static final EntityDataAccessor<Float> DATA_BC_DAMAGE_PER_TICK =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    /** 改血伤害的剩余 tick 数：归零后黑洞仍在、仍有引力与透镜，只是不再削血。 */
    private static final EntityDataAccessor<Integer> DATA_BC_DAMAGE_TICKS =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);

    private static final float DEF_RADIUS = 1.5f;
    private static final float DEF_BEND = 3.0f;
    private static final int DEF_LIFETIME = 400;
    private static final float DEF_COLLIDER_VOLUME = 1.0f;
    private static final float DEF_GRAVITY_RADIUS = 40.0f;
    private static final float DEF_KILL_RADIUS = 3.0f;
    private static final float DEF_BC_DAMAGE_PER_TICK = 0.0f;
    private static final int DEF_BC_DAMAGE_TICKS = 0;

    /** 本体碰撞箱尺寸（与 {@code EntityTypeRegistry.registerNoopEntity} 保持一致）。 */
    private static final float COLLIDER_W = 0.5f, COLLIDER_H = 0.5f;
    /** 碰撞箱体积 -> 质量的密度系数，与 BlackHolePhysicsHandler 的取值一致。 */
    private static final float DENSITY = 24.0f;

    private int age;
    private UUID anomalyAttacker;

    /** Only an identifier is retained, so multiple holes from one caster share anomaly throttling. */
    public UUID getAnomalyAttacker() { return anomalyAttacker == null ? getUUID() : anomalyAttacker; }
    public void setAnomalyAttacker(UUID attacker) { anomalyAttacker = attacker; }

    public BlackHoleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
        // 登记进物理处理器：处理器靠这张表找黑洞，不再扫世界（详见 BlackHolePhysicsHandler）
        if (!level.isClientSide()) {
            BlackHolePhysicsHandler.register(this);
        }
    }

    /** 移除时注销；{@code discard()} / {@code kill()} 都会走到这里，区块卸载后由构造函数重新登记。 */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) {
            BlackHolePhysicsHandler.unregister(this);
        }
        super.remove(reason);
    }

    @Override
    public void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, DEF_RADIUS);
        this.entityData.define(DATA_BEND, DEF_BEND);
        this.entityData.define(DATA_LIFETIME, DEF_LIFETIME);
        this.entityData.define(DATA_COLLIDER_VOLUME, DEF_COLLIDER_VOLUME);
        this.entityData.define(DATA_GRAVITY_RADIUS, DEF_GRAVITY_RADIUS);
        this.entityData.define(DATA_KILL_RADIUS, DEF_KILL_RADIUS);
        this.entityData.define(DATA_BC_DAMAGE_PER_TICK, DEF_BC_DAMAGE_PER_TICK);
        this.entityData.define(DATA_BC_DAMAGE_TICKS, DEF_BC_DAMAGE_TICKS);
    }

    /** 史瓦西半径（方块）。决定黑洞盘面大小与引力弯曲范围。 */
    public float getRadius() { return this.entityData.get(DATA_RADIUS); }
    public void setRadius(float v) { this.entityData.set(DATA_RADIUS, Math.max(0.1f, v)); }

    /** 引力弯曲强度系数（无量纲，越大透镜越强）。 */
    public float getBend() { return this.entityData.get(DATA_BEND); }
    public void setBend(float v) { this.entityData.set(DATA_BEND, Math.max(0.0f, Math.min(50.0f, v))); }

    public int getLifetime() { return this.entityData.get(DATA_LIFETIME); }
    public void setLifetime(int v) { this.entityData.set(DATA_LIFETIME, Math.max(1, v)); }

    /** 质量代理量 = 碰撞箱体积 × 密度系数，即 F = G*M*m/r^2 里的 M；计算与施加在处理器里。 */
    public float getColliderVolume() { return this.entityData.get(DATA_COLLIDER_VOLUME); }
    public void setColliderVolume(float v) { this.entityData.set(DATA_COLLIDER_VOLUME, Math.max(1.0e-4f, v)); }

    /** 引力作用半径（格）：该范围内的生物被吸引。 */
    public float getGravityRadius() { return this.entityData.get(DATA_GRAVITY_RADIUS); }
    public void setGravityRadius(float v) { this.entityData.set(DATA_GRAVITY_RADIUS, Math.max(0.5f, v)); }

    /** 事件视界半径（格）：进入后每 tick 受伤；伤害判定在处理器里，本实体只存数据。 */
    public float getKillRadius() { return this.entityData.get(DATA_KILL_RADIUS); }
    public void setKillRadius(float v) { this.entityData.set(DATA_KILL_RADIUS, Math.max(0.5f, v)); }

    /** 每 tick 改血伤害（点）；{@code 0} = 走原版物理伤害路径。 */
    public float getBcDamagePerTick() { return this.entityData.get(DATA_BC_DAMAGE_PER_TICK); }
    public void setBcDamagePerTick(float v) { this.entityData.set(DATA_BC_DAMAGE_PER_TICK, Math.max(0.0f, v)); }

    /** 改血伤害的剩余 tick 数（由处理器递减）。 */
    public int getBcDamageTicks() { return this.entityData.get(DATA_BC_DAMAGE_TICKS); }
    public void setBcDamageTicks(int v) { this.entityData.set(DATA_BC_DAMAGE_TICKS, Math.max(0, v)); }

    public int getAge() { return age; }

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

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isAttackable() { return false; }

    /** 渲染时的大范围剔除盒：覆盖整块透镜球体（渲染器用 10·Rs 的球）。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        float r = getRadius();
        return this.getBoundingBox().inflate(r * 20.0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        anomalyAttacker = tag.hasUUID("AnomalyAttacker") ? tag.getUUID("AnomalyAttacker") : null;
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("Bend")) setBend(tag.getFloat("Bend"));
        if (tag.contains("Lifetime")) setLifetime(tag.getInt("Lifetime"));
        if (tag.contains("ColliderVolume")) setColliderVolume(tag.getFloat("ColliderVolume"));
        if (tag.contains("GravityRadius")) setGravityRadius(tag.getFloat("GravityRadius"));
        if (tag.contains("KillRadius")) setKillRadius(tag.getFloat("KillRadius"));
        if (tag.contains("BcDamagePerTick")) setBcDamagePerTick(tag.getFloat("BcDamagePerTick"));
        if (tag.contains("BcDamageTicks")) setBcDamageTicks(tag.getInt("BcDamageTicks"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        if (anomalyAttacker != null) tag.putUUID("AnomalyAttacker", anomalyAttacker);
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Bend", getBend());
        tag.putInt("Lifetime", getLifetime());
        tag.putFloat("ColliderVolume", getColliderVolume());
        tag.putFloat("GravityRadius", getGravityRadius());
        tag.putFloat("KillRadius", getKillRadius());
        tag.putFloat("BcDamagePerTick", getBcDamagePerTick());
        tag.putInt("BcDamageTicks", getBcDamageTicks());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** 本体的质量代理量 = 碰撞箱体积 x 密度系数。 */
    public static float defaultMass() {
        return DENSITY * (COLLIDER_W * COLLIDER_W * COLLIDER_H);
    }

    public static BlackHoleEntity spawn(Level level, Vec3 pos,
                                         float radius, float bend, int lifetime) {
        return spawn(level, pos, radius, bend, lifetime,
                DEF_GRAVITY_RADIUS, DEF_KILL_RADIUS, defaultMass());
    }

    /** 小型黑洞：透镜强度按 {@code bend ∝ 1/Rs²} 配比（观感与默认黑洞一致），引力半径 40 格。 */
    public static BlackHoleEntity spawnSmall(Level level, Vec3 pos, int lifetime,
                                              float radius, float killRadius) {
        return spawn(level, pos, radius, bendForRadius(radius), lifetime,
                DEF_GRAVITY_RADIUS, killRadius, defaultMass());
    }

    /** 由半径反推弯曲强度：着色器强度为 {@code uGrav = bend · Rs²}，故 {@code bend ∝ 1/Rs²} 以保持观感不变。 */
    public static float bendForRadius(float radius) {
        float r = Math.max(radius, 0.1f);
        return Math.max(0.0f, Math.min(50.0f, DEF_BEND * DEF_RADIUS * DEF_RADIUS / (r * r)));
    }

    /** 完整生成：渲染参数 + 引力作用半径 + 事件视界半径 + 质量代理量。 */
    public static BlackHoleEntity spawn(Level level, Vec3 pos,
                                         float radius, float bend, int lifetime,
                                         float gravityRadius, float killRadius,
                                         float colliderVolume) {
        BlackHoleEntity e = new BlackHoleEntity(EntityTypeRegistry.BLACK_HOLE.get(), level);
        e.setPos(pos);
        e.setRadius(radius);
        e.setBend(bend);
        e.setLifetime(lifetime);
        e.setGravityRadius(gravityRadius);
        e.setKillRadius(killRadius);
        e.setColliderVolume(colliderVolume);
        level.addFreshEntity(e);
        return e;
    }

    public static BlackHoleEntity spawn(Level level, Vec3 pos) {
        return spawn(level, pos, DEF_RADIUS, DEF_BEND, DEF_LIFETIME);
    }

    /**
     * 技能召唤用工厂：额外指定每 tick 改血量与持续 tick。与 {@link #spawnSmall} 的区别在伤害语义 ——
     * 这里走 {@code BCCoreCompat.damage} 改血，且只在 {@code damageTicks} 内生效。
     */
    public static BlackHoleEntity spawnSkill(Level level, Vec3 pos, int lifetime,
                                             float radius, float gravityRadius, float killRadius,
                                             float damagePerTick, int damageTicks) {
        BlackHoleEntity e = spawn(level, pos, radius, bendForRadius(radius), lifetime,
                gravityRadius, killRadius, defaultMass());
        e.setBcDamagePerTick(damagePerTick);
        e.setBcDamageTicks(damageTicks);
        return e;
    }
}
