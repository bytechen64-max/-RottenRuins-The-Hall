package org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills;

import net.minecraft.core.particles.ParticleOptions;
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
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 裁决领域的落剑 —— 从天而降、落地后插在地上的剑气；每把剑自带计时器，错开落地时间才被读成「剑阵」。
 * 实体位置就是最终落点，本身不移动：{@code getFallProgress()} 由渲染侧用 {@code y + (1 - progress) * getFallFrom()} 重建，
 * 省掉每 tick 一次位置包（一次剑阵有 6~8 把剑）。只有指明了目标的剑才在落地那一刻结算伤害。
 */
public class VerdictSwordDropEntity extends Entity {

    // 同步数据
    private static final EntityDataAccessor<Float> DATA_FALL_FROM =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FALL_TICKS =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.INT);

    // 默认值
    /** 默认下落高度（格）。够高才有「从天而降」的读法，又不至于落到一半领域就结束了。 */
    public static final float DEFAULT_FALL_FROM = 16.0f;
    /** 默认下落耗时（tick）= 9，即 0.45 秒；再慢就不是「剑雨」而是「飘落」了。 */
    public static final int DEFAULT_FALL_TICKS = 9;
    /** 默认总存活（tick）：落完之后还要插在地上留一会儿。 */
    public static final int DEFAULT_MAX_AGE = 40;
    /** 默认剑长（格）。 */
    public static final float DEFAULT_HEIGHT = 5.0f;

    /** 落剑用途。原本还有 USE_STRUCTURE（阵列装饰立柱），已移除；保留常量因为它同时进存档与同步数据，删掉会让旧存档字段变野值。 */
    public static final int USE_STRIKE = 1;

    private int age;
    private @Nullable UUID targetUUID;
    private float hurt = VerdictDamage.FIELD_HURT;
    private boolean damageDone;
    /** 落地时是否打一圈空间涟漪（每次脉冲只给第一把剑，避免多把剑叠成一堆环）。 */
    private boolean ringOnLand = true;
    private ParticleOptions landParticle;
    /** 领域实体 id（-1 = 无）：领域只存 owner 的 UUID，伤害归因需要 Player 引用，没有它领域击杀不记在玩家头上。 */
    private int fieldEntityId = -1;

    public VerdictSwordDropEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    //  同步数据

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_FALL_FROM, DEFAULT_FALL_FROM);
        entityData.define(DATA_FALL_TICKS, DEFAULT_FALL_TICKS);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_SCALE, 1.0f);
        entityData.define(DATA_HEIGHT, DEFAULT_HEIGHT);
        entityData.define(DATA_STYLE, USE_STRIKE);
    }

    /** 从落点往上多少格开始下落。 */
    public float getFallFrom()      { return entityData.get(DATA_FALL_FROM); }
    /** 下落耗时（tick）。 */
    public int   getFallTicks()     { return entityData.get(DATA_FALL_TICKS); }
    public int   getMaxAge()        { return entityData.get(DATA_MAX_AGE); }
    public float getSwordScale()    { return entityData.get(DATA_SCALE); }
    public float getSwordHeight()   { return entityData.get(DATA_HEIGHT); }
    public int   getUse()           { return entityData.get(DATA_STYLE); }
    public int   getAge()           { return age; }

    public void setFallFrom(float v)   { entityData.set(DATA_FALL_FROM, Math.max(1.0f, v)); }
    public void setFallTicks(int v)    { entityData.set(DATA_FALL_TICKS, Math.max(1, v)); }
    public void setMaxAge(int v)       { entityData.set(DATA_MAX_AGE, Math.max(1, v)); }
    public void setSwordScale(float v) { entityData.set(DATA_SCALE, Math.max(0.01f, v)); }
    public void setSwordHeight(float v){ entityData.set(DATA_HEIGHT, Math.max(0.5f, v)); }
    public void setUse(int v)          { entityData.set(DATA_STYLE, USE_STRIKE); }

    // 剑宽不在这里：由渲染器按「宽 = 长 × BLADE_WIDTH_RATIO」推出，两处各存一份必然不一致。

    /** 0 → 1 的下落进度（带 partialTick）。1 表示已经落地。 */
    public float getFallProgress(float partialTick) {
        int ft = getFallTicks();
        if (ft <= 0) return 1f;
        return Math.min((age + Math.max(0f, partialTick)) / ft, 1f);
    }

    public float getHoldIntensity() {
        int rest = Math.max(1, getMaxAge() - getFallTicks());
        float t = Math.max(0f, (age - getFallTicks()) / (float) rest);
        return 1f - Math.max(0f, t) * Math.max(0f, t);       // 二次淡出，尾巴收得干净
    }

    //  目标与伤害

    public void setTarget(@Nullable LivingEntity target) {
        this.targetUUID = target == null ? null : target.getUUID();
    }

    public void setHurt(float hurt) { this.hurt = hurt; }

    public void setRingOnLand(boolean v) { this.ringOnLand = v; }

    public void setLandParticle(@Nullable ParticleOptions p) { this.landParticle = p; }

    /** 记下投出这把剑的领域实体；需在 {@code addFreshEntity} 之前调用（落地结算不在同一 tick，但顺序写对更稳）。 */
    public void setFieldEntity(@Nullable VerdictFieldEntity field) {
        this.fieldEntityId = field == null ? -1 : field.getId();
    }

    /** 通过领域实体反查施法者；任何一环缺失都返回 null（伤害照样结算，只是没有归因）。 */
    @Nullable
    private Player resolveCaster() {
        if (fieldEntityId < 0) return null;
        return level().getEntity(fieldEntityId) instanceof VerdictFieldEntity field
                ? field.getOwner() : null;
    }

    @Nullable
    private LivingEntity resolveTarget() {
        if (targetUUID == null) return null;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(64.0))) {
            if (e.getUUID().equals(targetUUID)) return e;
        }
        return null;
    }

    //  Tick

    @Override
    public void tick() {
        age++;

        if (level().isClientSide()) {
            // 客户端只负责在下落时留一条细光尘，让「剑从哪来」可读
            if (age < getFallTicks()) {
                Vec3 p = position().add(0, (1.0 - getFallProgress(0f)) * getFallFrom(), 0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        p.x, p.y, p.z, 0.0, 0.08, 0.0);
            }
            return;
        }

        int ft = getFallTicks();
        if (!damageDone && age >= ft) {
            damageDone = true;
            onLand();
        }

        if (age >= getMaxAge()) discard();
    }

    /** 落地：结算伤害 + 打反馈。刻意把伤害放在落地而非生成时 —— 剑还在空中、怪已经掉血是最易察觉的错位；代价是 9 tick 延迟。 */
    private void onLand() {
        Vec3 at = position();

        if (ringOnLand) {
            VerdictFeedback.impactRing(level(), at.add(0, 0.05, 0),
                    2.2f, 2.6f, 0.55f);
        }
        if (landParticle != null) {
            VerdictFeedback.burst(level(), at.add(0, 0.25, 0), landParticle, 8, 0.14);
        }

        // 只有指明了目标的剑才结算伤害（阵列立柱是纯装饰）
        if (targetUUID == null) return;

        LivingEntity target = resolveTarget();
        if (target == null || !target.isAlive()) return;

        // 归因：拿不到施法者时退化为 magic + null，伤害形状不变，只是这一下不算玩家的击杀。
        Player caster = resolveCaster();
        var source = caster != null ? caster.damageSources().playerAttack(caster)
                                    : level().damageSources().magic();
        VerdictDamage.strike(target, source, hurt, VerdictDamage.BYPASS_PART, caster);
        VerdictFeedback.landed(target, 0.6f);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override
    public boolean isPickable()   { return false; }
    @Override
    public boolean isAttackable() { return false; }

    /** 剔除用的包围盒必须向上扩到下落起点，否则剑落到半空时玩家一转头就会看到它消失。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBox().expandTowards(0, getFallFrom() + 2.0, 0).inflate(1.5);
    }

    //  存档

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        damageDone = tag.getBoolean("Landed");
        if (tag.hasUUID("Target")) targetUUID = tag.getUUID("Target");
        if (tag.contains("FallFrom")) setFallFrom(tag.getFloat("FallFrom"));
        if (tag.contains("FallTicks")) setFallTicks(tag.getInt("FallTicks"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.contains("Scale")) setSwordScale(tag.getFloat("Scale"));
        if (tag.contains("Height")) setSwordHeight(tag.getFloat("Height"));
        if (tag.contains("Use")) setUse(tag.getInt("Use"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Landed", damageDone);
        if (targetUUID != null) tag.putUUID("Target", targetUUID);
        tag.putFloat("FallFrom", getFallFrom());
        tag.putInt("FallTicks", getFallTicks());
        tag.putInt("MaxAge", getMaxAge());
        tag.putFloat("Scale", getSwordScale());
        tag.putFloat("Height", getSwordHeight());
        tag.putInt("Use", getUse());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    //  静态工厂

    /** 在落点生成一道落剑；多把剑错开 {@code fallTicks} 就是「剑雨」的节奏来源。 */
    public static VerdictSwordDropEntity spawn(Level level, Vec3 landing,
                                              float fallFrom, int fallTicks,
                                              float scale, float height) {
        VerdictSwordDropEntity e = new VerdictSwordDropEntity(
                EntityTypeRegistry.VERDICT_SWORD_DROP.get(), level);
        e.setPos(landing);
        e.setFallFrom(fallFrom);
        e.setFallTicks(fallTicks);
        e.setUse(USE_STRIKE);
        e.setSwordScale(scale);
        e.setSwordHeight(height);
        // 存活 = 落完 + 停留 22 tick（1.1 秒）：够看清它插在哪，又不至于占着一堆实体。
        e.setMaxAge(fallTicks + 22);
        level.addFreshEntity(e);
        return e;
    }
}
