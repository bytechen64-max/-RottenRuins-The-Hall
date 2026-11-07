package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;

import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.utils.combat.CorruptionDamageUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;

/**
 * 通用腐蚀弹体 —— 一个实体覆盖「吐息 / 箭雨 / 喷射 / 酸弹」等所有直线远程技能。
 *
 * <p>设计目标：让普通生物挂远程技能时<b>不需要新增实体类型、贴图或模型</b>。
 * 全部表现参数（伤害、粒子色、尺寸、重力、命中效果、是否留腐蚀池）都在发射前配置，
 * 服务端配置项写入同步数据，客户端只负责按配色画粒子。
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * CorruptionDart dart = CorruptionDart.shoot(level, shooter, target,
 *         0.75F,                 // 初速度
 *         CorruptionDart.RED,    // 配色
 *         0.28F,                 // 视觉半径
 *         true);                 // 是否受重力
 * dart.setDamage(6.0F);
 * dart.setHitEffect(CorruptionEffects.CORRUPTION.get(), 100, 0);
 * level.addFreshEntity(dart);
 * }</pre>
 */
public class CorruptionDart extends ThrowableProjectile {

    /** 腐蚀红：吐息 / 箭雨 / 血爆 */
    public static final int RED    = 0xFFFF2A2A;
    /** 酸液绿：喷射 / 酸弹 */
    public static final int ACID   = 0xFF7CFF3A;
    /** 腐植褐：瘴气 */
    public static final int MIASMA = 0xFFB07A3C;

    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(CorruptionDart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> VISUAL_RADIUS =
            SynchedEntityData.defineId(CorruptionDart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> USE_GRAVITY =
            SynchedEntityData.defineId(CorruptionDart.class, EntityDataSerializers.BOOLEAN);

    // ---- 仅服务端使用的配置（不需要同步） ----
    private float damage = 6.0F;
    private int maxLifetime = 120;
    private boolean pierce = false;
    private boolean leavePool = false;
    private float poolRadius = 2.5F;
    private int poolLifetime = 100;
    private float poolDamage = 3.0F;

    @Nullable private MobEffect hitEffect;
    private int hitEffectDuration = 100;
    private int hitEffectAmplifier = 0;

    private final Set<UUID> alreadyHit = new HashSet<>();
    private int age = 0;

    public CorruptionDart(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public CorruptionDart(EntityType<? extends ThrowableProjectile> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    // =========================================================================
    // 静态工厂
    // =========================================================================

    /** 本弹体在实体注册表中的类型（统一入口，避免各生物硬编码） */
    private static EntityType<CorruptionDart> type() {
        return CorruptionEntityTypes.CORRUPTION_DART.get();
    }

    /**
     * 朝目标瞄准并发射（自动提前量补偿抛物线）。
     *
     * @param level        世界
     * @param shooter      发射者
     * @param target       目标
     * @param speed        初速度
     * @param color        配色（见本类常量）
     * @param visualRadius 视觉半径
     * @param gravity      是否受重力
     */
    public static CorruptionDart shoot(Level level, LivingEntity shooter, LivingEntity target,
                                       float speed, int color, float visualRadius, boolean gravity) {
        CorruptionDart dart = new CorruptionDart(type(), shooter, level);
        dart.setColor(color);
        dart.setVisualRadius(visualRadius);
        dart.setUseGravity(gravity);
        dart.setPos(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());

        double dx = target.getX() - dart.getX();
        double dy = target.getY(0.35) - dart.getY();
        double dz = target.getZ() - dart.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (gravity) {
            // 简易抛物线抬升：距离越远抬得越高
            dy += horizontal * 0.12;
        }
        dart.shoot(dx, dy, dz, speed, gravity ? 0.5F : 0.0F);
        return dart;
    }

    /** 朝指定方向发射（用于箭雨 / 喷射这类固定方向） */
    public static CorruptionDart launch(Level level, LivingEntity shooter, Vec3 direction,
                                        float speed, int color, float visualRadius, boolean gravity) {
        CorruptionDart dart = new CorruptionDart(type(), shooter, level);
        dart.setColor(color);
        dart.setVisualRadius(visualRadius);
        dart.setUseGravity(gravity);
        dart.setPos(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());
        Vec3 d = direction.normalize().scale(speed);
        dart.setDeltaMovement(d);
        return dart;
    }

    // =========================================================================
    // 配置
    // =========================================================================

    public void setColor(int argb) {
        this.entityData.set(COLOR, argb);
    }

    public int getColor() {
        return this.entityData.get(COLOR);
    }

    public void setVisualRadius(float radius) {
        this.entityData.set(VISUAL_RADIUS, Math.max(0.05F, radius));
    }

    public float getVisualRadius() {
        return this.entityData.get(VISUAL_RADIUS);
    }

    public void setUseGravity(boolean gravity) {
        this.entityData.set(USE_GRAVITY, gravity);
        this.setNoGravity(!gravity);
    }

    public boolean useGravity() {
        return this.entityData.get(USE_GRAVITY);
    }

    public CorruptionDart setDamage(float damage) {
        this.damage = damage;
        return this;
    }

    public CorruptionDart setMaxLifetime(int ticks) {
        this.maxLifetime = Math.max(1, ticks);
        return this;
    }

    /** 命中后是否继续飞行（箭雨 / 穿透弹用） */
    public CorruptionDart setPierce(boolean pierce) {
        this.pierce = pierce;
        return this;
    }

    /** 命中或落地后留下一片腐蚀池 */
    public CorruptionDart withPool(float radius, int lifetime, float damagePerTick) {
        this.leavePool = true;
        this.poolRadius = radius;
        this.poolLifetime = lifetime;
        this.poolDamage = damagePerTick;
        return this;
    }

    public CorruptionDart setHitEffect(MobEffect effect, int duration, int amplifier) {
        this.hitEffect = effect;
        this.hitEffectDuration = duration;
        this.hitEffectAmplifier = amplifier;
        return this;
    }

    // =========================================================================
    // 飞行逻辑
    // =========================================================================

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            spawnTrailParticles();
            return;
        }

        age++;
        if (age >= maxLifetime) {
            onExpire();
            return;
        }

        // 主动检测路径上的实体，避免"穿透型"弹体高速飞过时漏判
        if (pierce) {
            Vec3 from = this.position().subtract(this.getDeltaMovement());
            AABB sweep = new AABB(from, this.position()).inflate(0.6);
            for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, sweep)) {
                if (!alreadyHit.contains(target.getUUID())) {
                    applyHit(target);
                }
            }
        }
    }

    private void spawnTrailParticles() {
        int color = getColor();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), getVisualRadius() * 3.0F);

        Vec3 pos = this.position();
        for (int i = 0; i < 3; i++) {
            this.level().addParticle(dust,
                    pos.x + (this.random.nextDouble() - 0.5) * 0.25,
                    pos.y + (this.random.nextDouble() - 0.5) * 0.25,
                    pos.z + (this.random.nextDouble() - 0.5) * 0.25,
                    0, 0, 0);
        }
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 0, -0.01, 0);
        }
    }

    // =========================================================================
    // 命中处理
    // =========================================================================

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) return;
        if (result.getEntity() instanceof LivingEntity living) {
            applyHit(living);
        }
        finishHit(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide) return;
        finishHit(result.getLocation());
    }

    @Override
    protected void onHit(HitResult result) {
        // 命中判定全部交给 onHitEntity / onHitBlock，这里留空以避免重复结算
    }

    private void applyHit(LivingEntity target) {
        Entity owner = this.getOwner();
        if (target == owner) return;
        if (CorruptionSkillUtil.isCorruptionAlly(target)) return;
        if (target instanceof net.minecraft.world.entity.player.Player player
                && (player.isCreative() || player.isSpectator())) return;

        alreadyHit.add(target.getUUID());

        if (owner instanceof LivingEntity livingOwner) {
            CorruptionDamageUtil.TrueHurt(livingOwner, target, 0, damage, 1, true);
        } else {
            target.hurt(target.damageSources().magic(), damage);
        }

        if (hitEffect != null) {
            target.addEffect(new MobEffectInstance(hitEffect, hitEffectDuration, hitEffectAmplifier, false, true));
        }

        CorruptionSkillFx.impact(this.level(), target.position(), getColor(), getVisualRadius());
    }

    private void finishHit(Vec3 at) {
        if (this.level() instanceof ServerLevel serverLevel) {
            CorruptionSkillFx.impact(serverLevel, at, getColor(), getVisualRadius());
            if (leavePool) {
                CorruptionSkillUtil.spawnPoolAt(
                        serverLevel, at, poolRadius, poolLifetime, poolDamage, 20,
                        getOwner() instanceof LivingEntity living ? living : null);
            }
        }
        if (!pierce) {
            this.discard();
        }
    }

    private void onExpire() {
        if (leavePool && this.level() instanceof ServerLevel serverLevel) {
            CorruptionSkillUtil.spawnPoolAt(
                    serverLevel, this.position(), poolRadius, poolLifetime, poolDamage, 20,
                    getOwner() instanceof LivingEntity living ? living : null);
        }
        this.discard();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target)
                && target != this.getOwner()
                && !(target instanceof LivingEntity livingTarget
                    && CorruptionSkillUtil.isCorruptionAlly(livingTarget));
    }

    // =========================================================================
    // 同步数据 / 存档
    // =========================================================================

    @Override
    public void defineSynchedData() {
        this.entityData.define(COLOR, RED);
        this.entityData.define(VISUAL_RADIUS, 0.3F);
        this.entityData.define(USE_GRAVITY, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DartColor", getColor());
        tag.putFloat("DartRadius", getVisualRadius());
        tag.putBoolean("DartGravity", useGravity());
        tag.putFloat("DartDamage", damage);
        tag.putInt("DartAge", age);
        tag.putInt("DartMaxLifetime", maxLifetime);
        tag.putBoolean("DartLeavePool", leavePool);
        tag.putFloat("DartPoolRadius", poolRadius);
        tag.putInt("DartPoolLifetime", poolLifetime);
        tag.putFloat("DartPoolDamage", poolDamage);
        if (hitEffect != null) {
            var key = ForgeRegistries.MOB_EFFECTS.getKey(hitEffect);
            if (key != null) {
                tag.putString("DartEffect", key.toString());
                tag.putInt("DartEffectDuration", hitEffectDuration);
                tag.putInt("DartEffectAmplifier", hitEffectAmplifier);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DartColor")) setColor(tag.getInt("DartColor"));
        if (tag.contains("DartRadius")) setVisualRadius(tag.getFloat("DartRadius"));
        if (tag.contains("DartGravity")) setUseGravity(tag.getBoolean("DartGravity"));
        if (tag.contains("DartDamage")) damage = tag.getFloat("DartDamage");
        age = tag.getInt("DartAge");
        if (tag.contains("DartMaxLifetime")) maxLifetime = tag.getInt("DartMaxLifetime");
        leavePool = tag.getBoolean("DartLeavePool");
        if (tag.contains("DartPoolRadius")) poolRadius = tag.getFloat("DartPoolRadius");
        if (tag.contains("DartPoolLifetime")) poolLifetime = tag.getInt("DartPoolLifetime");
        if (tag.contains("DartPoolDamage")) poolDamage = tag.getFloat("DartPoolDamage");
        if (tag.contains("DartEffect")) {
            var effect = ForgeRegistries.MOB_EFFECTS.getValue(
                    net.minecraft.resources.ResourceLocation.tryParse(tag.getString("DartEffect")));
            if (effect != null) {
                hitEffect = effect;
                hitEffectDuration = tag.getInt("DartEffectDuration");
                hitEffectAmplifier = tag.getInt("DartEffectAmplifier");
            }
        }
    }
}
