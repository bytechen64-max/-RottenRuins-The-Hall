package org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.effect.VerdictEffect;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictTuning;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
/**
 * 裁决领域 —— 地面上的空中剑阵：持续时间内的每 {@link #swordInterval()} tick 脉冲出剑。
 * 半径只影响覆盖范围、不影响伤害（{@code FIELD_HURT} 固定）；主人离场则停止出剑。
 */
public class VerdictFieldEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PULSE =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OWNER_PRESENT =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.BOOLEAN);

    /** 默认半径（格）。地表值，会被高度系数放大。 */
    public static final float DEFAULT_RADIUS = 6.0f;
    /** 默认持续（tick）= 10 秒。 */
    public static final int DEFAULT_MAX_AGE = 200;
    /** 出剑间隔（tick）= 1.2 秒的基准值；实际间隔见 {@link #swordInterval()}。 */
    public static final int SWORD_INTERVAL = 24;
    /** 出剑间隔下限（tick）：配置把次数拉高时防止退化成"每 tick 一道"。 */
    public static final int MIN_SWORD_INTERVAL = 4;
    /** 水平位置偏移量。与 {@code DomeriteLongsword#castField} 的生成坐标一致。 */
    public static final float CENTER_OFFSET = 0.5f;

    public static final float FADE_IN_TICKS = 6f;
    public static final float FADE_OUT_TICKS = 24f;

    private UUID ownerUUID;
    private int age;

    public VerdictFieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_RADIUS, DEFAULT_RADIUS);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_PULSE, 0);
        entityData.define(DATA_OWNER_PRESENT, true);
    }

    public float getRadius()      { return entityData.get(DATA_RADIUS); }
    public int   getMaxAge()      { return entityData.get(DATA_MAX_AGE); }
    public int   getAge()         { return age; }
    /** 已经出过几道剑气（客户端用它做脉冲动画的相位）。 */
    public int   getPulse()       { return entityData.get(DATA_PULSE); }
    /** 施法者是否还在附近。不在时领域变暗，给玩家一个"你走太远了"的反馈。 */
    public boolean isOwnerPresent() { return entityData.get(DATA_OWNER_PRESENT); }

    public void setRadius(float v) { entityData.set(DATA_RADIUS, Math.max(0.5f, v)); }
    public void setMaxAge(int v)   { entityData.set(DATA_MAX_AGE, Math.max(1, v)); }

    /** 生命进度 0 → 1，带 partialTick 插值。 */
    public float lifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        return Math.min((age + Math.max(0f, partialTick)) / max, 1f);
    }

    /** 强度（含淡入淡出）。 */
    public float intensity(float partialTick) {
        float at = age + Math.max(0f, partialTick);
        float in = Math.min(at / FADE_IN_TICKS, 1f);
        float out = Math.min(Math.max(0f, (getMaxAge() - at) / FADE_OUT_TICKS), 1f);
        return in * out;
    }

    public void setOwner(@Nullable Entity owner) {
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Nullable
    public Player getOwner() {
        if (ownerUUID == null) return null;
        Player p = level().getPlayerByUUID(ownerUUID);
        return p;
    }

    @Override
    public void tick() {
        if (isPassenger() && getVehicle() != null && !getVehicle().isPassengerOfSameVehicle(this)) {
            stopRiding();
        }
        age++;

        if (level().isClientSide()) return;

        if (age >= getMaxAge()) {
            discard();
            return;
        }

        // 施法者在不在场先判定 —— 它决定这一 tick 到底出不出剑
        Player owner = getOwner();
        boolean present = owner != null
                && owner.isAlive()
                && owner.distanceToSqr(this) < (getRadius() + 6.0) * (getRadius() + 6.0);
        if (present != isOwnerPresent()) {
            entityData.set(DATA_OWNER_PRESENT, present);
        }

        // 主人离场就停止出剑（不只是把光纹调暗）：让"你走太远了"由行为说清，而不是让玩家觉得技能没伤害；
        // 领域本身仍走完 10 秒。
        if (!present) return;

        if (age % swordInterval() == 0 && age > 0) {
            emitSword();
        }
    }

    /** 实际出剑间隔（tick）= 基准值 ÷（配置倍率 × 裁决层数倍率），下限 {@link #MIN_SWORD_INTERVAL}；两端算法须一致才能对齐脉冲环。 */
    public int swordInterval() {
        float rate = VerdictTuning.fieldRate();
        Player owner = getOwner();
        if (owner != null) rate *= VerdictEffect.fieldRateScale(owner);
        if (!(rate > 0.01f)) return SWORD_INTERVAL;      // 倍率被设成 0：退回基准，别除零
        return Math.max(MIN_SWORD_INTERVAL, Math.round(SWORD_INTERVAL / rate));
    }

    /** 发一轮剑气：锁定范围内最近的若干目标，各生成一道从天而降的落剑（伤害在落地帧结算，比原方案晚约 0.45 秒）。 */
    private void emitSword() {
        Player owner = getOwner();
        List<LivingEntity> targets = findTargets(owner);
        if (targets.isEmpty()) return;

        entityData.set(DATA_PULSE, getPulse() + 1);

        // 每把剑都是一个实体，调大这个上限的代价是线性的实体数增长
        int maxTargets = VerdictTuning.fieldMaxTargets();
        for (int i = 0; i < Math.min(targets.size(), maxTargets); i++) {
            LivingEntity target = targets.get(i);
            Vec3 at = target.position();
            // 落点取目标脚下（而不是半身高），于是剑身会穿过它
            VerdictSwordDropEntity drop = VerdictSwordDropEntity.spawn(
                    level(), at,
                    VerdictSwordDropEntity.DEFAULT_FALL_FROM, FALL_TICKS + i * 2,
                    1.0f, 6.0f);
            drop.setTarget(target);
            drop.setFieldEntity(this);        // 伤害归因：把施法者追回来（见该字段的注释）
            drop.setHurt(VerdictDamage.FIELD_HURT);
            // 每次脉冲只让第一把剑打地面涟漪，否则叠成一堆同心环
            drop.setRingOnLand(i == 0);
            drop.setLandParticle(net.minecraft.core.particles.ParticleTypes.CRIT);
        }
    }

    /**
     * 领域内最近的合法目标，按距离升序。必须排除施法者本身，
     * 否则技能会持续打自己（走 setHealth，护甲挡不住）。
     */
    private List<LivingEntity> findTargets(@Nullable Player owner) {
        double r = getRadius();
        AABB box = new AABB(getX() - r, getY() - 4.0, getZ() - r,
                            getX() + r, getY() + 8.0, getZ() + r);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator()
                        && (owner == null || !e.getUUID().equals(owner.getUUID()))
                        && e.distanceToSqr(this) <= r * r);

        candidates.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
        return candidates;
    }

    /** 每次脉冲最多锁定目标数的默认值；实际取值走 {@link VerdictTuning#fieldMaxTargets()}（1~6）。 */
    public static final int MAX_TARGETS_PER_PULSE = 3;

    /** 落剑的下落耗时（tick）。0.45 秒 —— 再慢就不像剑雨了。 */
    public static final int FALL_TICKS = 9;

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putFloat("Radius", getRadius());
        tag.putInt("MaxAge", getMaxAge());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** 在玩家脚下展开裁决领域。 */
    public static VerdictFieldEntity spawn(Level level, Player owner, float radius, int maxAge) {
        VerdictFieldEntity e = new VerdictFieldEntity(EntityTypeRegistry.VERDICT_FIELD.get(), level);
        e.setPos(owner.getX() + CENTER_OFFSET, owner.getY(), owner.getZ() + CENTER_OFFSET);
        e.setOwner(owner);
        e.setRadius(radius);
        e.setMaxAge(maxAge);
        level.addFreshEntity(e);
        return e;
    }
}
