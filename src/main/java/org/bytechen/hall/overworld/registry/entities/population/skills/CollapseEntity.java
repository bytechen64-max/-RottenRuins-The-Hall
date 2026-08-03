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

import java.util.ArrayList;
import java.util.List;

/**
 * 坍缩实体 —— 支持多种坍缩渲染类型，通过同步数据控制渲染参数。
 *
 * <pre>
 * 渲染类型:
 *   0 - ENDER_DRAGON       : 末影龙死亡式光芒四射，光束旋转
 *   1 - HYPERCUBE          : 四维超立方体在三维空间的投影
 *   2 - ICOSAHEDRON        : 二十面体
 *   3 - TRANSFORMING_PRISM : 多棱柱变换（3棱→20棱）
 *
 * 生命周期:
 *   生成动画(spawnDuration) → 维持(maxLiveTime - spawn - end) → 结束动画(endDuration) → 丢弃
 * </pre>
 */
public class CollapseEntity extends Entity {

    // ────────── 坍缩渲染类型 ──────────
    public enum CollapseType {
        ENDER_DRAGON,
        HYPERCUBE,
        ICOSAHEDRON,
        TRANSFORMING_PRISM;

        public static CollapseType fromId(int id) {
            CollapseType[] values = values();
            if (id < 0 || id >= values.length) return ENDER_DRAGON;
            return values[id];
        }
    }

    // ────────── 坍缩生命周期阶段 ──────────
    public enum CollapsePhase {
        SPAWN,      // 生成动画阶段
        MAINTAIN,   // 维持阶段
        END,        // 结束动画阶段
        DEAD        // 已结束
    }

    // ────────── 同步数据 key ──────────
    private static final EntityDataAccessor<Integer> DATA_LIVE_TIME =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_LIVE_TIME =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE_TYPE =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPAWN_DURATION =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_END_DURATION =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR1 =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR2 =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CALLBACK_RADIUS =
            SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.FLOAT);

    // ────────── 默认值 ──────────
    private static final int DEF_SPAWN_DURATION = 20;
    private static final int DEF_END_DURATION = 20;
    private static final int DEF_MAINTAIN_DURATION = 60;
    private static final int DEF_MAX_LIVE_TIME = DEF_SPAWN_DURATION + DEF_MAINTAIN_DURATION + DEF_END_DURATION;
    private static final int DEF_COLOR1 = 0xFFFFFF;
    private static final int DEF_COLOR2 = 0x8888FF;
    private static final float DEF_CALLBACK_RADIUS = 16.0f;

    // ────────── 实例字段 ──────────
    private Entity owner;
    private final List<ICollapseCallback> callbacks = new ArrayList<>();
    /** 前一帧的阶段，用于检测阶段切换 */
    private CollapsePhase prevPhase = CollapsePhase.SPAWN;

    public CollapseEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        // 设置默认值
        this.entityData.set(DATA_LIVE_TIME, DEF_MAX_LIVE_TIME);
        this.entityData.set(DATA_MAX_LIVE_TIME, DEF_MAX_LIVE_TIME);
        this.entityData.set(DATA_COLLAPSE_TYPE, 0);
        this.entityData.set(DATA_SPAWN_DURATION, DEF_SPAWN_DURATION);
        this.entityData.set(DATA_END_DURATION, DEF_END_DURATION);
        this.entityData.set(DATA_COLOR1, DEF_COLOR1);
        this.entityData.set(DATA_COLOR2, DEF_COLOR2);
        this.entityData.set(DATA_CALLBACK_RADIUS, DEF_CALLBACK_RADIUS);
    }

    // ═══════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════

    public Entity getOwner() { return owner; }
    public void setOwner(Entity owner) { this.owner = owner; }

    public int getLiveTime() { return this.entityData.get(DATA_LIVE_TIME); }
    public void setLiveTime(int v) { this.entityData.set(DATA_LIVE_TIME, v); }

    public int getMaxLiveTime() { return this.entityData.get(DATA_MAX_LIVE_TIME); }
    public void setMaxLiveTime(int v) { this.entityData.set(DATA_MAX_LIVE_TIME, v); }

    public CollapseType getCollapseType() { return CollapseType.fromId(this.entityData.get(DATA_COLLAPSE_TYPE)); }
    public void setCollapseType(CollapseType type) { this.entityData.set(DATA_COLLAPSE_TYPE, type.ordinal()); }
    public int getCollapseTypeId() { return this.entityData.get(DATA_COLLAPSE_TYPE); }

    public int getSpawnDuration() { return this.entityData.get(DATA_SPAWN_DURATION); }
    public void setSpawnDuration(int v) { this.entityData.set(DATA_SPAWN_DURATION, v); }

    public int getEndDuration() { return this.entityData.get(DATA_END_DURATION); }
    public void setEndDuration(int v) { this.entityData.set(DATA_END_DURATION, v); }

    /** 维持阶段时长 = 总时长 - 生成 - 结束 */
    public int getMaintainDuration() {
        return Math.max(0, getMaxLiveTime() - getSpawnDuration() - getEndDuration());
    }

    public int getColor1() { return this.entityData.get(DATA_COLOR1); }
    public void setColor1(int color) { this.entityData.set(DATA_COLOR1, color); }

    public int getColor2() { return this.entityData.get(DATA_COLOR2); }
    public void setColor2(int color) { this.entityData.set(DATA_COLOR2, color); }

    public float getCallbackRadius() { return this.entityData.get(DATA_CALLBACK_RADIUS); }
    public void setCallbackRadius(float v) { this.entityData.set(DATA_CALLBACK_RADIUS, v); }

    // ────────── 预解包颜色分量（便于渲染器使用） ──────────
    public float color1R() { return ((getColor1() >> 16) & 0xFF) / 255f; }
    public float color1G() { return ((getColor1() >> 8) & 0xFF) / 255f; }
    public float color1B() { return (getColor1() & 0xFF) / 255f; }
    public float color2R() { return ((getColor2() >> 16) & 0xFF) / 255f; }
    public float color2G() { return ((getColor2() >> 8) & 0xFF) / 255f; }
    public float color2B() { return (getColor2() & 0xFF) / 255f; }

    // ═══════════════════════════════════════════════════════════════
    // 阶段判定
    // ═══════════════════════════════════════════════════════════════

    /** 获取当前生命周期阶段 */
    public CollapsePhase getPhase() {
        int live = getLiveTime();
        if (live <= 0) return CollapsePhase.DEAD;
        int max = getMaxLiveTime();
        int endDur = getEndDuration();
        int spawnDur = getSpawnDuration();
        int elapsed = max - live;
        if (elapsed < spawnDur) return CollapsePhase.SPAWN;
        if (elapsed < max - endDur) return CollapsePhase.MAINTAIN;
        return CollapsePhase.END;
    }

    /**
     * 获取阶段进度（0~1，用于动画插值）
     * SPAWN: 0→1, MAINTAIN: 随机(可用于持续旋转), END: 0→1
     */
    public float getPhaseProgress() {
        CollapsePhase phase = getPhase();
        int live = getLiveTime();
        int max = getMaxLiveTime();
        int spawnDur = getSpawnDuration();
        int endDur = getEndDuration();
        int elapsed = max - live;
        return switch (phase) {
            case SPAWN -> spawnDur > 0 ? (float) elapsed / spawnDur : 1f;
            case MAINTAIN -> {
                int maintainDur = getMaintainDuration();
                yield maintainDur > 0 ? (float) (elapsed - spawnDur) / maintainDur : 1f;
            }
            case END -> endDur > 0 ? (float) (live) / endDur : 0f; // END阶段: live从endDur递减到0，progress=0→1
            default -> 0f;
        };
    }

    /** 获取结束阶段的剩余时间比例（1→0，用于淡出） */
    public float getEndFadeProgress() {
        CollapsePhase phase = getPhase();
        if (phase == CollapsePhase.END) {
            int endDur = getEndDuration();
            return endDur > 0 ? (float) getLiveTime() / endDur : 0f;
        }
        if (phase == CollapsePhase.DEAD) return 0f;
        return 1f;
    }

    // ═══════════════════════════════════════════════════════════════
    // 回调系统
    // ═══════════════════════════════════════════════════════════════

    public void addCallback(ICollapseCallback callback) {
        if (!callbacks.contains(callback)) callbacks.add(callback);
    }

    public void removeCallback(ICollapseCallback callback) {
        callbacks.remove(callback);
    }

    public List<ICollapseCallback> getCallbacks() {
        return callbacks;
    }

    // ═══════════════════════════════════════════════════════════════
    // Tick
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        if (this.isPassenger() && this.getVehicle() != null
                && !this.getVehicle().isPassengerOfSameVehicle(this)) {
            this.stopRiding();
        }

        CollapsePhase currentPhase = getPhase();

        // 服务端：递减生命 & 回调
        if (!level().isClientSide()) {
            if (getLiveTime() > 0) {
                setLiveTime(getLiveTime() - 1);
            }

            // 阶段切换时，触发新阶段的回调
            if (currentPhase != prevPhase) {
                prevPhase = currentPhase;
            }

            // 每 tick 对范围内实体调用回调
            invokeCallbacks(currentPhase);

            // 生命周期结束
            if (currentPhase == CollapsePhase.DEAD) {
                this.discard();
                return;
            }
        }

        // 基类 tick（处理碰撞等）
        super.tick();
    }

    private void invokeCallbacks(CollapsePhase phase) {
        if (callbacks.isEmpty()) return;
        float radius = getCallbackRadius();
        AABB aabb = this.getBoundingBox().inflate(radius);
        List<Entity> entitiesInRange = level().getEntitiesOfClass(Entity.class, aabb,
                e -> e.isAlive() && e != this);

        for (Entity target : entitiesInRange) {
            for (ICollapseCallback callback : callbacks) {
                switch (phase) {
                    case SPAWN -> callback.collapseStarting(target);
                    case MAINTAIN -> callback.collapseLiving(target);
                    case END -> callback.collapseEnding(target);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 同步数据定义
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_LIVE_TIME, DEF_MAX_LIVE_TIME);
        this.entityData.define(DATA_MAX_LIVE_TIME, DEF_MAX_LIVE_TIME);
        this.entityData.define(DATA_COLLAPSE_TYPE, 0);
        this.entityData.define(DATA_SPAWN_DURATION, DEF_SPAWN_DURATION);
        this.entityData.define(DATA_END_DURATION, DEF_END_DURATION);
        this.entityData.define(DATA_COLOR1, DEF_COLOR1);
        this.entityData.define(DATA_COLOR2, DEF_COLOR2);
        this.entityData.define(DATA_CALLBACK_RADIUS, DEF_CALLBACK_RADIUS);
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT 持久化
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("LiveTime")) setLiveTime(tag.getInt("LiveTime"));
        if (tag.contains("MaxLiveTime")) setMaxLiveTime(tag.getInt("MaxLiveTime"));
        if (tag.contains("CollapseType")) setCollapseType(CollapseType.fromId(tag.getInt("CollapseType")));
        if (tag.contains("SpawnDuration")) setSpawnDuration(tag.getInt("SpawnDuration"));
        if (tag.contains("EndDuration")) setEndDuration(tag.getInt("EndDuration"));
        if (tag.contains("Color1")) setColor1(tag.getInt("Color1"));
        if (tag.contains("Color2")) setColor2(tag.getInt("Color2"));
        if (tag.contains("CallbackRadius")) setCallbackRadius(tag.getFloat("CallbackRadius"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LiveTime", getLiveTime());
        tag.putInt("MaxLiveTime", getMaxLiveTime());
        tag.putInt("CollapseType", getCollapseTypeId());
        tag.putInt("SpawnDuration", getSpawnDuration());
        tag.putInt("EndDuration", getEndDuration());
        tag.putInt("Color1", getColor1());
        tag.putInt("Color2", getColor2());
        tag.putFloat("CallbackRadius", getCallbackRadius());
    }

    // ═══════════════════════════════════════════════════════════════
    // 网络
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    // ═══════════════════════════════════════════════════════════════
    // 静态工厂方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 生成坍缩实体（完整参数）。
     *
     * @param level          世界
     * @param pos            生成位置
     * @param type           坍缩渲染类型
     * @param spawnDuration  生成动画时长 (tick)
     * @param maintainDuration 维持阶段时长 (tick)
     * @param endDuration    结束动画时长 (tick)
     * @param color1         主颜色 (RGB packed)
     * @param color2         辅颜色 (RGB packed)
     * @param callbackRadius 回调范围半径
     * @param owner          所有者（可为 null）
     * @param callbacks      回调列表（可为 null）
     * @return 生成的坍缩实体
     */
    public static CollapseEntity spawn(Level level, Vec3 pos,
                                        CollapseType type,
                                        int spawnDuration, int maintainDuration, int endDuration,
                                        int color1, int color2,
                                        float callbackRadius,
                                        Entity owner,
                                        List<ICollapseCallback> callbacks) {
        CollapseEntity e = new CollapseEntity(
                org.bytechen.hall.overworld.registry.EntityTypeRegistry.COLLAPSE.get(), level);
        e.setPos(pos);
        e.setCollapseType(type);
        e.setSpawnDuration(Math.max(1, spawnDuration));
        e.setEndDuration(Math.max(1, endDuration));
        int maxLife = Math.max(1, spawnDuration) + Math.max(0, maintainDuration) + Math.max(1, endDuration);
        e.setMaxLiveTime(maxLife);
        e.setLiveTime(maxLife);
        e.setColor1(color1);
        e.setColor2(color2);
        e.setCallbackRadius(callbackRadius);
        e.setOwner(owner);
        if (callbacks != null) {
            for (ICollapseCallback cb : callbacks) e.addCallback(cb);
        }
        level.addFreshEntity(e);
        return e;
    }

    /**
     * 生成坍缩实体（简化参数，使用默认颜色和回调）。
     */
    public static CollapseEntity spawn(Level level, Vec3 pos, CollapseType type,
                                        int spawnDuration, int maintainDuration, int endDuration) {
        return spawn(level, pos, type, spawnDuration, maintainDuration, endDuration,
                DEF_COLOR1, DEF_COLOR2, DEF_CALLBACK_RADIUS, null, null);
    }

    /**
     * 生成坍缩实体（带颜色）。
     */
    public static CollapseEntity spawn(Level level, Vec3 pos, CollapseType type,
                                        int spawnDuration, int maintainDuration, int endDuration,
                                        int color1, int color2) {
        return spawn(level, pos, type, spawnDuration, maintainDuration, endDuration,
                color1, color2, DEF_CALLBACK_RADIUS, null, null);
    }

    // ═══════════════════════════════════════════════════════════════
    // 回调接口
    // ═══════════════════════════════════════════════════════════════

    public interface ICollapseCallback {
        /** 生成动画阶段，每 tick 对渲染范围内的每个生物调用 */
        void collapseStarting(Entity entity);

        /** 维持阶段，每 tick 对渲染范围内的每个生物调用 */
        void collapseLiving(Entity entity);

        /** 结束动画阶段，每 tick 对渲染范围内的每个生物调用 */
        void collapseEnding(Entity entity);
    }
}
