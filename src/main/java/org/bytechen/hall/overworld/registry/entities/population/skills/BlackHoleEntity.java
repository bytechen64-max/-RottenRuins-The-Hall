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
import org.bytechen.hall.event.impl.BlackHolePhysicsHandler;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;

/**
 * 黑洞实体 —— 只承载状态，不含任何效果逻辑。
 *
 * <p>本身不移动、不受重力、不可拾取、不可攻击。它只做两件事：
 * <ol>
 *   <li>作为 {@code BlackHoleRenderer} 程序化绘制的渲染锚点；</li>
 *   <li>通过同步数据向外部暴露参数：史瓦西半径、弯曲强度、质量代理量
 *       （碰撞箱体积 x 系数）、引力作用半径、事件视界半径、存活时长。</li>
 * </ol>
 *
 * <p><b>引力与伤害逻辑不在这里。</b>它们由
 * {@code org.bytechen.hall.event.impl.BlackHolePhysicsHandler} 在服务端
 * LevelTickEvent 中读取上述参数后计算并施加 —— 这样效果与实体解耦，
 * 黑洞可以纯粹当装饰摆放，也可以被别的系统赋予不同力度。
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
     * 每 tick 的<b>改血</b>伤害（点）。
     * <p>{@code 0}（默认）= 走 {@code BlackHolePhysicsHandler} 里原有的"物理伤害"路径
     * （原版 hurt，20 点/tick，受无敌帧/护甲影响）；&gt;0 则表示这个黑洞是技能召唤物，
     * 由处理器按这个数值走 VitalProbe 改血。判断与施加都在处理器里，本实体只存数据。
     */
    private static final EntityDataAccessor<Float> DATA_BC_DAMAGE_PER_TICK =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 改血伤害的剩余 tick 数（由处理器每 tick 递减）。
     * <p>归零后黑洞仍然存在、仍然有引力与透镜，只是不再削血 —— 这样"伤害窗口"
     * 可以比"黑洞存活时间"短。
     */
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

    public BlackHoleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
        // 登记进物理处理器：处理器靠这张表找黑洞，不再扫世界（详见 BlackHolePhysicsHandler）
        if (!level.isClientSide()) {
            BlackHolePhysicsHandler.register(this);
        }
    }

    /**
     * 移除时注销登记。
     * <p>{@code discard()} / {@code kill()} 最终都会走到这里，所以覆盖这一个点就够了；
     * 区块卸载 / 存档读回时实体对象会被重建，构造函数会重新登记。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) {
            BlackHolePhysicsHandler.unregister(this);
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, DEF_RADIUS);
        this.entityData.define(DATA_BEND, DEF_BEND);
        this.entityData.define(DATA_LIFETIME, DEF_LIFETIME);
        this.entityData.define(DATA_COLLIDER_VOLUME, DEF_COLLIDER_VOLUME);
        this.entityData.define(DATA_GRAVITY_RADIUS, DEF_GRAVITY_RADIUS);
        this.entityData.define(DATA_KILL_RADIUS, DEF_KILL_RADIUS);
        this.entityData.define(DATA_BC_DAMAGE_PER_TICK, DEF_BC_DAMAGE_PER_TICK);
        this.entityData.define(DATA_BC_DAMAGE_TICKS, DEF_BC_DAMAGE_TICKS);
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

    /**
     * 本体的“质量”代理量：碰撞箱体积 x 密度系数。
     * <p>牛顿引力 F = G*M*m/r^2 中，本值即这里的 M；具体计算与施加由
     * {@code BlackHolePhysicsHandler} 负责 —— 本实体自身不含逻辑。
     */
    public float getColliderVolume() { return this.entityData.get(DATA_COLLIDER_VOLUME); }
    public void setColliderVolume(float v) { this.entityData.set(DATA_COLLIDER_VOLUME, Math.max(1.0e-4f, v)); }

    /** 引力作用半径（格）：该范围内的生物被吸引。 */
    public float getGravityRadius() { return this.entityData.get(DATA_GRAVITY_RADIUS); }
    public void setGravityRadius(float v) { this.entityData.set(DATA_GRAVITY_RADIUS, Math.max(0.5f, v)); }

    /**
     * 事件视界半径（格）：生物进入该范围后每 tick 受到伤害。
     * <p>只作为数据供外部处理器读取 —— 伤害判定写在
     * {@code BlackHolePhysicsHandler} 里，本实体不做任何伤害逻辑。
     */
    public float getKillRadius() { return this.entityData.get(DATA_KILL_RADIUS); }
    public void setKillRadius(float v) { this.entityData.set(DATA_KILL_RADIUS, Math.max(0.5f, v)); }

    /**
     * 每 tick 改血伤害（点）。{@code 0} = 走原版物理伤害路径。
     * <p>只作为数据供 {@code BlackHolePhysicsHandler} 读取 —— 本实体不做任何伤害逻辑。
     */
    public float getBcDamagePerTick() { return this.entityData.get(DATA_BC_DAMAGE_PER_TICK); }
    public void setBcDamagePerTick(float v) { this.entityData.set(DATA_BC_DAMAGE_PER_TICK, Math.max(0.0f, v)); }

    /** 改血伤害的剩余 tick 数（由处理器递减）。 */
    public int getBcDamageTicks() { return this.entityData.get(DATA_BC_DAMAGE_TICKS); }
    public void setBcDamageTicks(int v) { this.entityData.set(DATA_BC_DAMAGE_TICKS, Math.max(0, v)); }

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

    // ── 不可交互 ──

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

    // ── 持久化 ──

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
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
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
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

    // ── 静态工厂 ──

    /** 本体的质量代理量 = 碰撞箱体积 x 密度系数。 */
    public static float defaultMass() {
        return DENSITY * (COLLIDER_W * COLLIDER_W * COLLIDER_H);
    }

    public static BlackHoleEntity spawn(Level level, Vec3 pos,
                                         float radius, float bend, int lifetime) {
        return spawn(level, pos, radius, bend, lifetime,
                DEF_GRAVITY_RADIUS, DEF_KILL_RADIUS, defaultMass());
    }

    /**
     * 生成一个“小型”黑洞：透镜强度与默认黑洞等价（按 {@code bend ∝ 1/Rs²} 配比），
     * 引力半径 {@link #DEF_GRAVITY_RADIUS}（40 格），质量按碰撞箱体积计算。
     *
     * @param radius     史瓦西半径（方块）。渲染器的透镜球体半径是 6·Rs
     * @param killRadius 事件视界半径（格），进入后每 tick 受伤
     */
    public static BlackHoleEntity spawnSmall(Level level, Vec3 pos, int lifetime,
                                              float radius, float killRadius) {
        return spawn(level, pos, radius, bendForRadius(radius), lifetime,
                DEF_GRAVITY_RADIUS, killRadius, defaultMass());
    }

    /**
     * 由史瓦西半径反推弯曲强度，使**透镜观感的归一化强度**保持不变。
     * <p>着色器里实际强度是 {@code uGrav = bend · Rs²}，所以要保持同样的
     * 归一化强度就必须 {@code bend ∝ 1/Rs²}；以默认黑洞（Rs=1.5, bend=3.0）
     * 为基准。这样缩小黑洞只是整体变小，折射的观感不变。</p>
     */
    public static float bendForRadius(float radius) {
        float r = Math.max(radius, 0.1f);
        return Math.max(0.0f, Math.min(50.0f, DEF_BEND * DEF_RADIUS * DEF_RADIUS / (r * r)));
    }

    /**
     * 完整生成：同时指定渲染参数、引力作用半径、事件视界半径与质量代理量。
     *
     * @param radius         史瓦西半径（方块），决定透镜盘面大小
     * @param bend           引力弯曲强度（越大透镜越强）
     * @param lifetime       存活 tick 数
     * @param gravityRadius  引力作用半径（格），此范围内生物被吸引
     * @param killRadius     事件视界半径（格），进入后每 tick 受伤
     * @param colliderVolume 质量代理量（碰撞箱体积 x 系数）
     */
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
     * <b>技能召唤</b>用工厂：除渲染/引力/视界参数外，额外指定"每 tick 改血伤害"与持续 tick。
     *
     * <p>与 {@link #spawnSmall} 的区别在于伤害语义：
     * <ul>
     *   <li>{@code spawnSmall} 的黑洞走处理器里原有的物理伤害（原版 hurt，20 点/tick）；</li>
     *   <li>本工厂的黑洞走 {@code BCCoreCompat.damage} 改血（可穿透抗改血实现），
     *       且伤害只在 {@code damageTicks} 内生效，之后黑洞继续存在但不再削血。</li>
     * </ul>
     *
     * @param lifetime       黑洞存活 tick 数
     * @param radius         史瓦西半径（方块），决定透镜盘面大小
     * @param gravityRadius  引力作用半径（格）
     * @param killRadius     事件视界半径（格），进入后每 tick 被改血
     * @param damagePerTick  每 tick 改血量（点）
     * @param damageTicks    改血伤害持续 tick 数
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
