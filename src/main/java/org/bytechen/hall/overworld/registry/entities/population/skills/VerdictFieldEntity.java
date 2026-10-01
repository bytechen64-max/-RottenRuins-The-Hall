package org.bytechen.hall.overworld.registry.entities.population.skills;

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
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.effect.VerdictEffect;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictTuning;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 裁决领域 —— 地面的空中剑阵。
 *
 * <h3>它和另外两个技能的分工</h3>
 * <p>光柱是"一条竖线上所有目标同时吃一下"，突进是"把 30 送到一个方向上"，
 * 两者都是<b>瞬时</b>的。领域是唯一一个<b>持续</b>组件：
 * 它在你移动、被打断、甚至转头看别处的时候仍然在干活。
 * 所以它的定位不是伤害更高，而是"让这片地一直有裁决"——
 * 站桩清场、守点、或者给突进之后的撤退铺路。</p>
 *
 * <h3>为什么剑气是"每 {@value #SWORD_INTERVAL} tick 一道"而不是每 tick 结算</h3>
 * <p>每 tick 对范围内所有目标结算的写法，在一个 10 秒的领域里会打出 200 次伤害 ——
 * 那既不合理也没法平衡。改成脉冲式出剑之后：</p>
 * <ul>
 *   <li><b>伤害可预期</b>：10 秒 ≈ 8 道剑气，单目标总量固定；</li>
 *   <li><b>有节奏</b>：剑气一道一道飞出去，玩家看得见"剑阵在工作"，
 *       而不是一团持续掉血的雾；</li>
 *   <li><b>性能可控</b>：每道剑气是一个 {@link SwordAuraEntity} 实体，数量有上限。</li>
 * </ul>
 *
 * <h3>半径</h3>
 * <p>吃高度系数（{@code HeightFactor.fieldRadiusBlocks}），并且只影响覆盖、不影响伤害 ——
 * {@code FIELD_HURT} 是固定值。</p>
 */
public class VerdictFieldEntity extends Entity {

    // ── 同步数据 ────────────────────────────────────────────────────
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PULSE =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OWNER_PRESENT =
            SynchedEntityData.defineId(VerdictFieldEntity.class, EntityDataSerializers.BOOLEAN);

    // ── 默认值 ──────────────────────────────────────────────────────
    /** 默认半径（格）。地表值，会被高度系数放大。 */
    public static final float DEFAULT_RADIUS = 6.0f;
    /** 默认持续（tick）= 10 秒。 */
    public static final int DEFAULT_MAX_AGE = 200;
    /** 出剑间隔（tick）= 1.2 秒。这是**基准值**，实际间隔见 {@link #swordInterval()}。 */
    public static final int SWORD_INTERVAL = 24;
    /**
     * 出剑间隔的下限（tick）。
     * <p>配置里把 {@code verdictFieldRate} 拉到很高时，间隔会一路变小；
     * 这个下限保证它不会小到"每 tick 一道剑气"，那既卡顿又没有节奏感。</p>
     */
    public static final int MIN_SWORD_INTERVAL = 4;
    /** 水平位置偏移量。与 {@code DomeriteLongsword#castField} 的生成坐标一致。 */
    public static final float CENTER_OFFSET = 0.5f;

    /** 淡入（tick）。 */
    public static final float FADE_IN_TICKS = 6f;
    /** 淡出（tick）。 */
    public static final float FADE_OUT_TICKS = 24f;

    private UUID ownerUUID;
    private int age;

    public VerdictFieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // ══════════════════════════════════════════════════════════════
    //  同步数据
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
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

    // ══════════════════════════════════════════════════════════════
    //  Tick
    // ══════════════════════════════════════════════════════════════

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

        // 出剑节奏：前 swordInterval() 个 tick 用来淡入，所以第一道剑气不会立刻出去
        if (age % swordInterval() == 0 && age > 0) {
            emitSword();
        }

        // 施法者离场 → 领域变暗（视觉反馈），但不立即消失：
        // 已经放下的东西应当把它的 10 秒走完，否则"放完就走"会变成一种规避冷却的玩法。
        Player owner = getOwner();
        boolean present = owner != null
                && owner.isAlive()
                && owner.distanceToSqr(this) < (getRadius() + 6.0) * (getRadius() + 6.0);
        if (present != isOwnerPresent()) {
            entityData.set(DATA_OWNER_PRESENT, present);
        }
    }

    /**
     * 实际的出剑间隔（tick）。
     *
     * <p>两个来源相乘，都只影响"频率"、不影响伤害数值：</p>
     * <ul>
     *   <li>{@link VerdictTuning#fieldRate()} —— 玩家在配置里的整体节奏；</li>
     *   <li>{@link VerdictEffect#fieldRateScale(Player)} —— 裁决层数（打得多顺）。</li>
     * </ul>
     *
     * <p>只读服务端侧的配置与效果，但客户端也要算同一个值才能让地面光纹的
     * 脉冲环与真正的出剑对齐 —— 好在配置本来就是两端一致的文件。</p>
     */
    public int swordInterval() {
        float rate = VerdictTuning.fieldRate();
        Player owner = getOwner();
        if (owner != null) rate *= VerdictEffect.fieldRateScale(owner);
        if (!(rate > 0.01f)) return SWORD_INTERVAL;      // 倍率被设成 0：退回基准，别除零
        return Math.max(MIN_SWORD_INTERVAL, Math.round(SWORD_INTERVAL / rate));
    }

    /**
     * 发一道剑气：找领域内最近的目标，在中心与目标之间生成一道朝目标飞去的剑气。
     *
     * <p>剑气本体是已有的 {@link SwordAuraEntity}，所以这一发不需要任何新资产。</p>
     */
    private void emitSword() {
        Player owner = getOwner();
        LivingEntity target = findNearestTarget(owner);
        if (target == null) return;                      // 没人可打：不出剑（动画仍靠 pulse 推进）

        entityData.set(DATA_PULSE, getPulse() + 1);

        float hurt = VerdictDamage.FIELD_HURT;

        // 剑气从领域中心朝目标方向铺出去
        Vec3 from = new Vec3(getX(), getY() + 1.2, getZ());
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 dir = to.subtract(from);
        float reach = (float) dir.length();
        if (reach > 0.05f) {
            Vec3 unit = dir.scale(1.0 / reach);
            // 用"中心 → 目标"之间的一段区间做剑气路径，最后一段压在目标身上
            for (int i = 0; i < 3; i++) {
                float t = 0.35f + 0.3f * i;
                Vec3 p = from.add(unit.scale(reach * t));
                SwordAuraEntity.spawn(level(), p, 0.5f + 0.15f * i, 0.9f,
                        SwordAuraEntity.APOSTLE_HEIGHT * 0.6f, 0.24f,
                        12, 1.6f);
            }
        }

        // 伤害在出剑的那一瞬结算一次 —— 剑气是"表现"，不是需要命中判定的抛射物。
        // 这样做的代价是没有飞行时间带来的闪避空间，收益是"领域内必定生效"的可预期性，
        // 而后者才是这个技能存在的理由。
        VerdictDamage.strike(target,
                level().damageSources().playerAttack(owner), hurt,
                VerdictDamage.BYPASS_PART, owner);
    }

    /**
     * 领域内最近的合法目标。
     *
     * <p>排除施法者自己 —— {@code getEntitiesOfClass} 会把自己也算进去，
     * 不排除的话这个技能会持续打自己血（走 setHealth 那条，护甲挡不住）。</p>
     */
    @Nullable
    private LivingEntity findNearestTarget(@Nullable Player owner) {
        double r = getRadius();
        AABB box = new AABB(getX() - r, getY() - 4.0, getZ() - r,
                            getX() + r, getY() + 8.0, getZ() + r);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator()
                        && (owner == null || !e.getUUID().equals(owner.getUUID()))
                        && e.distanceToSqr(this) <= r * r);

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = e.distanceToSqr(this);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
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
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putFloat("Radius", getRadius());
        tag.putInt("MaxAge", getMaxAge());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

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
