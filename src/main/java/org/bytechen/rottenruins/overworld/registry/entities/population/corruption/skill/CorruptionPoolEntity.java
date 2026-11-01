package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;

import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 腐蚀液池：一片持续存在的腐蚀地面，周期性伤害并施加腐蚀效果。
 *
 * <p>用于「酸液瘴气弹」「辐照领域」「酸液喷射」等一切"落点留一片东西"的技能。
 * 半径、持续时间、每跳伤害与间隔全部运行时可调，因此一个实体就能承载多种技能表现。
 *
 * <p>渲染走 {@code SkillProjectileRenderer}（空渲染）+ 客户端粒子，
 * 因此不需要任何贴图或模型资源。
 */
public class CorruptionPoolEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(CorruptionPoolEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(CorruptionPoolEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(CorruptionPoolEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(CorruptionPoolEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> INTERVAL =
            SynchedEntityData.defineId(CorruptionPoolEntity.class, EntityDataSerializers.INT);

    private static final DustParticleOptions POOL_DUST =
            new DustParticleOptions(new Vector3f(0.75F, 0.06F, 0.06F), 1.4F);
    private static final DustParticleOptions POOL_DUST_DARK =
            new DustParticleOptions(new Vector3f(0.35F, 0.02F, 0.02F), 1.8F);

    private int age = 0;
    private int damageTimer = 0;

    @Nullable
    private LivingEntity cachedOwner;

    /** 本 tick 已被命中的目标，避免同一 tick 多次结算 */
    private final List<UUID> hurtThisTick = new ArrayList<>();

    public CorruptionPoolEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // =========================================================================
    // 配置
    // =========================================================================

    public void setOwner(@Nullable LivingEntity owner) {
        this.cachedOwner = owner;
        this.entityData.set(OWNER_UUID, owner == null ? Optional.empty() : Optional.of(owner.getUUID()));
    }

    @Nullable
    public LivingEntity getOwner() {
        if (cachedOwner != null && cachedOwner.isAlive() && !cachedOwner.isRemoved()) return cachedOwner;
        UUID id = this.entityData.get(OWNER_UUID).orElse(null);
        if (id == null) return null;
        if (this.level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(id) instanceof LivingEntity living) {
            cachedOwner = living;
        }
        return cachedOwner;
    }

    public void setRadius(float radius) {
        this.entityData.set(RADIUS, Math.max(0.5F, radius));
        this.refreshDimensions();
    }

    public float getRadius() {
        return this.entityData.get(RADIUS);
    }

    public void setLifetime(int ticks) {
        this.entityData.set(LIFETIME, Math.max(1, ticks));
    }

    public int getLifetime() {
        return this.entityData.get(LIFETIME);
    }

    public void setDamagePerInterval(float damage) {
        this.entityData.set(DAMAGE, Math.max(0F, damage));
    }

    public float getDamagePerInterval() {
        return this.entityData.get(DAMAGE);
    }

    /** 伤害间隔（tick），20 = 每秒一跳 */
    public void setDamageInterval(int ticks) {
        this.entityData.set(INTERVAL, Math.max(1, ticks));
    }

    public int getDamageInterval() {
        return this.entityData.get(INTERVAL);
    }

    // =========================================================================
    // 尺寸 / 生命周期
    // =========================================================================

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float d = getRadius() * 2.0F;
        return EntityDimensions.scalable(d, 0.4F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (RADIUS.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            clientTick();
            return;
        }

        age++;
        if (age >= getLifetime()) {
            this.discard();
            return;
        }

        if (damageTimer > 0) damageTimer--;
        if (damageTimer <= 0) {
            applyPoolDamage();
            damageTimer = getDamageInterval();
        }

        // 每 5 tick 补一次粒子，保证远端玩家也能看到池子
        if (age % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
            spawnServerParticles(serverLevel, 6);
        }
    }

    private void applyPoolDamage() {
        hurtThisTick.clear();
        float damage = getDamagePerInterval();
        LivingEntity owner = getOwner();

        for (LivingEntity target : targetsInPool()) {
            hurtThisTick.add(target.getUUID());

            boolean hit = owner != null
                    ? org.bytechen.rottenruins.utils.combat.CorruptionDamageUtil.TrueHurt(
                            owner, target, 0, damage, 1, true)
                    : target.hurt(target.damageSources().magic(), damage) || true;

            if (hit) {
                target.addEffect(new MobEffectInstance(
                        CorruptionEffects.CORRUPTION.get(), 60, 0, false, true));
            }
        }
    }

    /** 池内的合法目标：排除主人自己与同族腐蚀生物 */
    private List<LivingEntity> targetsInPool() {
        double r = getRadius();
        AABB area = new AABB(
                this.getX() - r, this.getY() - 1.0, this.getZ() - r,
                this.getX() + r, this.getY() + 2.0, this.getZ() + r);

        LivingEntity owner = getOwner();
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (e == owner) continue;
            if (hurtThisTick.contains(e.getUUID())) continue;
            if (CorruptionSkillUtil.isCorruptionAlly(e)) continue;
            if (e instanceof net.minecraft.world.entity.player.Player player
                    && (player.isCreative() || player.isSpectator())) continue;
            result.add(e);
        }
        return result;
    }

    // =========================================================================
    // 客户端表现（纯粒子，无需渲染器与贴图）
    // =========================================================================

    private void clientTick() {
        int count = 3 + (int) (getRadius() * 2);
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(this.random.nextDouble()) * getRadius();
            double px = this.getX() + Math.cos(angle) * r;
            double pz = this.getZ() + Math.sin(angle) * r;

            this.level().addParticle(POOL_DUST,
                    px, this.getY() + 0.08, pz,
                    (this.random.nextDouble() - 0.5) * 0.02, 0.01 + this.random.nextDouble() * 0.02,
                    (this.random.nextDouble() - 0.5) * 0.02);
        }

        if (this.random.nextInt(4) == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(this.random.nextDouble()) * getRadius();
            this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                    this.getX() + Math.cos(angle) * r, this.getY() + 0.15,
                    this.getZ() + Math.sin(angle) * r,
                    0, 0.02, 0);
        }
    }

    private void spawnServerParticles(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(this.random.nextDouble()) * getRadius();
            level.sendParticles(POOL_DUST_DARK,
                    this.getX() + Math.cos(angle) * r,
                    this.getY() + 0.1,
                    this.getZ() + Math.sin(angle) * r,
                    1, 0, 0.02, 0, 0.0);
        }
    }

    /** 渲染插值（供自定义渲染器使用；当前走粒子方案，保留以备扩展） */
    public Vec3 poolCenter() {
        return new Vec3(this.getX(), this.getY(), this.getZ());
    }

    // =========================================================================
    // 同步数据 / 存档
    // =========================================================================

    @Override
    public void defineSynchedData() {
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(RADIUS, 3.0F);
        this.entityData.define(LIFETIME, 160);
        this.entityData.define(DAMAGE, 4.0F);
        this.entityData.define(INTERVAL, 20);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("Lifetime")) setLifetime(tag.getInt("Lifetime"));
        if (tag.contains("Damage")) setDamagePerInterval(tag.getFloat("Damage"));
        if (tag.contains("Interval")) setDamageInterval(tag.getInt("Interval"));
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        this.age = tag.getInt("Age");
        this.damageTimer = tag.getInt("DamageTimer");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", getRadius());
        tag.putInt("Lifetime", getLifetime());
        tag.putFloat("Damage", getDamagePerInterval());
        tag.putInt("Interval", getDamageInterval());
        UUID id = this.entityData.get(OWNER_UUID).orElse(null);
        if (id != null) tag.putUUID("OwnerUUID", id);
        tag.putInt("Age", age);
        tag.putInt("DamageTimer", damageTimer);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
