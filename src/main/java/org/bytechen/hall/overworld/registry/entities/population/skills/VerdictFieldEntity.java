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

        // 施法者在不在场先判定 —— 它决定这一 tick 到底出不出剑
        Player owner = getOwner();
        boolean present = owner != null
                && owner.isAlive()
                && owner.distanceToSqr(this) < (getRadius() + 6.0) * (getRadius() + 6.0);
        if (present != isOwnerPresent()) {
            entityData.set(DATA_OWNER_PRESENT, present);
        }

        // ── 出剑节奏：前 swordInterval() 个 tick 用来淡入，所以第一道剑气不会立刻出去 ──
        //
        //  离场之后<b>停止出剑</b>，而不只是把光纹调暗。
        //  原本的做法是"照常打，只是视觉暗一点" —— 玩家读不出因果关系，
        //  只会觉得"这个技能有时候没伤害"。让阵法在主人离开后熄火，
        //  "你走太远了"这件事就由<b>行为</b>说清楚了。
        //  领域本身仍然把它的 10 秒走完（见类注释：不该变成规避冷却的玩法）。
        if (!present) return;

        if (age % swordInterval() == 0 && age > 0) {
            emitSword();
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
     * 发一轮剑气：锁定领域内最近的 {@value #MAX_TARGETS_PER_PULSE} 个目标，
     * 在每个目标头顶生成一道<b>从天而降的落剑</b>。
     *
     * <h3>为什么从"3 道平行剑气"改成"落剑"</h3>
     * <p>原本的做法是在"领域中心 → 目标"的连线上铺 3 道 {@code SwordAuraEntity}，
     * 位置固定在连线的 35%/65%/95% 处。<b>那三把剑是水平排开的</b>，
     * 而且伤害在生成的那一瞬就结算了 —— 于是画面上永远是"几道平行的白光
     * 飘向目标"，而怪已经在掉血。既读不出方向（谁打谁），也读不出因果
     * （剑还没到、血已经掉了）。</p>
     *
     * <p>改成落剑之后三件事同时解决：</p>
     * <ul>
     *   <li><b>方向</b>：剑是竖着从目标头顶钉下来的，谁被锁了一眼就能看出来；</li>
     *   <li><b>因果</b>：伤害在<b>落地那一帧</b>结算（见
     *       {@code VerdictSwordDropEntity.onLand}），"剑落下 → 怪掉血"完全同步；</li>
     *   <li><b>阵法感</b>：多把剑以错开的时间落下，加上展开时那 7 道立柱，
     *       整片区域读作"天上一直在往下落剑"。</li>
     * </ul>
     *
     * <p>代价是伤害比原本晚约 0.45 秒。这是有意的取舍：领域是 10 秒的持续技能，
     * 半秒的延迟换来可读性，很划算；而"必定生效但看不见"才是更难接受的。</p>
     */
    private void emitSword() {
        Player owner = getOwner();
        List<LivingEntity> targets = findTargets(owner);
        if (targets.isEmpty()) return;                   // 没人可打：不出剑（动画仍靠 pulse 推进）

        entityData.set(DATA_PULSE, getPulse() + 1);

        // 一次脉冲最多几把剑同时落下。可配置（见 verdictFieldMaxTargets），
        // 因为"每把剑是一个实体"—— 调大它的代价是线性的实体数增长。
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
            // 每次脉冲只给第一把剑打地面涟漪：三把剑各自打一圈会叠成一堆同心环，
            // 而"这次脉冲的冲击点"本来就只有一个。
            drop.setRingOnLand(i == 0);
            drop.setLandParticle(net.minecraft.core.particles.ParticleTypes.CRIT);
        }
    }

    /**
     * 领域内最近的若干个合法目标，按距离升序。
     *
     * <p>排除施法者自己 —— {@code getEntitiesOfClass} 会把自己也算进去，
     * 不排除的话这个技能会持续打自己血（走 setHealth 那条，护甲挡不住）。</p>
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

    /**
     * 每次脉冲最多锁定的目标数<b>默认值</b>。
     * <p>真正的取值走 {@link VerdictTuning#fieldMaxTargets()}（可在配置里改，1~6）。</p>
     */
    public static final int MAX_TARGETS_PER_PULSE = 3;

    /** 落剑的下落耗时（tick）。0.45 秒 —— 再慢就不像剑雨了。 */
    public static final int FALL_TICKS = 9;

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
