package org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.render.IAutoRenderableEntity;
import org.bytechen.rottenruins.api.anomaly.AnomalyDamageSource;
import org.bytechen.rottenruins.api.infection.IInfectedEntity;
import org.bytechen.rottenruins.content.anomaly.HallAnomalyAttacks;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.utils.entity.EntityParticleUtils;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;
import java.util.Comparator;

/**
 * 畸骸骷髅发射的追踪型生物实体。
 * <p>
 * 不是弹射物（Projectile），而是普通 Entity：
 * 普通型碰到目标 / 方块 / 被攻击时自爆；ghost 型穿透并持续接触伤害，5 秒后消失。
 */
public class InfSkeletonArrowEntity extends Entity implements IAutoRenderableEntity {
    private static final EntityDataAccessor<Boolean> GHOST =
            SynchedEntityData.defineId(InfSkeletonArrowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final float GHOST_CONTACT_DAMAGE = 2.0F;

    private static final float SPEED = 0.55F;
    private static final float EXPLOSION_RADIUS = 1.5F;
    private static final int MAX_LIFE = 200;
    private static final int GHOST_MAX_LIFE = 100;
    /** 转向强度：越小转弯越柔和 */
    private static final double STEER_STRENGTH = 0.035D;
    /** 速度阻尼：让速度变化更平滑 */
    private static final double DAMPING = 0.985D;

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "geo/hall_skeleton_arrow.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/entity/hall_skeleton_arrow.png");
    private static final ResourceLocation GHOST_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/entity/hall_skeleton_arrow_ghost.png");
    // 没有独立的 animation 文件，借用现有 idle 动画文件避免 GeckoLib 加载空路径
    private static final ResourceLocation DUMMY_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "animations/hall_skeleton.animation.json");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID ownerUUID;
    private UUID targetUUID;
    private LivingEntity owner;
    private LivingEntity target;
    /** 平滑后的追踪点，避免直接锁死目标导致转向生硬 */
    private Vec3 aimPoint;
    private int life;
    private boolean exploded;

    // 客户端平滑插值：服务端位置更新不会直接瞬移，而是分步补间
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;
    private int lerpSteps;

    public InfSkeletonArrowEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
    }

    public boolean isGhost() { return entityData.get(GHOST); }

    public void setGhost(boolean ghost) {
        entityData.set(GHOST, ghost);
        this.noPhysics = ghost;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
        this.targetUUID = target.getUUID();
        this.aimPoint = target.getBoundingBox().getCenter();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (teleport || steps <= 0) {
            super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
            this.lerpSteps = 0;
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            // 客户端分步补间，让服务端位置更新看起来更平滑
            if (lerpSteps > 0) {
                double x = getX() + (lerpX - getX()) / (double) lerpSteps;
                double y = getY() + (lerpY - getY()) / (double) lerpSteps;
                double z = getZ() + (lerpZ - getZ()) / (double) lerpSteps;
                float yRot = getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / (float) lerpSteps;
                float xRot = getXRot() + (lerpXRot - getXRot()) / (float) lerpSteps;
                --lerpSteps;
                setPos(x, y, z);
                setRot(yRot, xRot);
            }

            // 客户端用项目里的粒子生成器给自己生成末地烛粒子
            EntityParticleUtils.spawnParticles(this, ParticleTypes.END_ROD,
                    0.4D, 0.2D, 1.0F);
            return;
        }

        if (exploded) {
            discard();
            return;
        }

        if (++life > (isGhost() ? GHOST_MAX_LIFE : MAX_LIFE)) {
            discard();
            return;
        }

        resolveTarget();

        // 自动追踪目标（追踪点也做平滑，避免生硬地锁死目标当前位置）
        if (target != null && target.isAlive()) {
            Vec3 targetPos = target.getBoundingBox().getCenter();
            if (aimPoint == null) {
                aimPoint = targetPos;
            } else {
                aimPoint = aimPoint.lerp(targetPos, 0.08D);
            }

            Vec3 toTarget = aimPoint.subtract(position());

            if (!isGhost() && (getBoundingBox().inflate(0.2D).intersects(target.getBoundingBox())
                    || toTarget.lengthSqr() < 0.25D)) {
                explode();
                return;
            }

            Vec3 current = getDeltaMovement();
            Vec3 desired = toTarget.normalize().scale(SPEED);
            // 加速度式转向：先阻尼旧速度，再叠加目标方向，避免瞬间改变方向
            Vec3 motion = current.scale(DAMPING).add(desired.scale(STEER_STRENGTH));
            double speed = motion.length();
            if (speed > SPEED) {
                motion = motion.scale(SPEED / speed);
            }
            setDeltaMovement(motion);
        }

        Vec3 motion = getDeltaMovement();

        if (isGhost()) {
            Vec3 start = getBoundingBox().getCenter();
            // Ghosts pass through terrain and creatures without detonating or stopping pursuit.
            setPos(position().add(motion));
            damageGhostContacts(start, getBoundingBox().getCenter(), motion);
            if (life >= GHOST_MAX_LIFE) discard();
            return;
        }

        // 碰到方块前预检测，避免高速穿透
        if (!level().noCollision(this, getBoundingBox().move(motion))) {
            explode();
            return;
        }

        move(MoverType.SELF, motion);

        if (horizontalCollision || verticalCollision) {
            explode();
        }
    }

    private void resolveTarget() {
        if (owner == null && ownerUUID != null && level() instanceof ServerLevel server) {
            if (server.getEntity(ownerUUID) instanceof LivingEntity living) owner = living;
        }
        if (target != null && target.isAlive() && (!isGhost() || canGhostHit(target))) return;
        target = null;
        if (targetUUID != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(targetUUID);
            if (entity instanceof LivingEntity living && living.isAlive() && (!isGhost() || canGhostHit(living))) {
                target = living;
            } else {
                target = null;
            }
        }
        if (!isGhost() || target != null || tickCount % 10 != 0) return;
        if (owner instanceof Mob mob && mob.getTarget() != null && canGhostHit(mob.getTarget())) {
            setTarget(mob.getTarget());
        } else {
            level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(32), this::canGhostHit)
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(this::setTarget);
        }
    }

    private boolean canGhostHit(LivingEntity living) {
        if (!living.isAlive() || !living.isAttackable() || living == owner
                || living.getUUID().equals(ownerUUID)) return false;
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return !(living instanceof IInfectedEntity infected
                && RottenFaction.HALL.matches(infected.getInfectionType()));
    }

    private void damageGhostContacts(Vec3 start, Vec3 end, Vec3 motion) {
        AABB search = getBoundingBox().expandTowards(motion.reverse());
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, search, this::canGhostHit)) {
            // Swept body collision, so moving through a small creature still counts exactly once this tick.
            AABB expanded = victim.getBoundingBox().inflate(getBbWidth() * 0.5D, getBbHeight() * 0.5D,
                    getBbWidth() * 0.5D);
            if (!expanded.contains(start) && expanded.clip(start, end).isEmpty()) continue;
            victim.invulnerableTime = 0;
            victim.hurt(new AnomalyDamageSource(damageSources().mobProjectile(this, owner),
                    ownerUUID == null ? getUUID() : ownerUUID, HallAnomalyAttacks.GHOST), GHOST_CONTACT_DAMAGE);
            victim.invulnerableTime = 0;
        }
    }

    private void explode() {
        if (exploded || isGhost()) return;
        exploded = true;
        if (!level().isClientSide()) {
            // 小型爆炸，NONE 表示不破坏方块，但会伤害实体
            DamageSource source = new AnomalyDamageSource(damageSources().explosion(this, null),
                    ownerUUID == null ? getUUID() : ownerUUID, HallAnomalyAttacks.COLD_HEAVY);
            level().explode(this, source, null, getX(), getY(), getZ(), EXPLOSION_RADIUS,
                    false, Level.ExplosionInteraction.NONE);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!isRemoved() && isGhost() && level() instanceof ServerLevel server
                && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            spawnGhostDeathParticles(server);
        }
        super.remove(reason);
    }

    /** One outward spray on expiry/death; unloading and dimension transfer do not count as death. */
    private void spawnGhostDeathParticles(ServerLevel server) {
        Vec3 center = getBoundingBox().getCenter();
        Vec3 drift = getDeltaMovement().scale(0.25D);
        for (int i = 0; i < 24; i++) {
            double yaw = random.nextDouble() * Math.PI * 2.0D;
            double vertical = random.nextDouble() * 2.0D - 1.0D;
            double horizontal = Math.sqrt(1.0D - vertical * vertical);
            double speed = 0.12D + random.nextDouble() * 0.18D;
            Vec3 velocity = new Vec3(Math.cos(yaw) * horizontal, vertical, Math.sin(yaw) * horizontal)
                    .scale(speed).add(drift).add(0.0D, 0.04D, 0.0D);
            // count=0 sends this exact velocity, rather than randomizing a cloud on the client.
            server.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                    0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
        server.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z,
                8, 0.15D, 0.15D, 0.15D, 0.08D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isGhost()) return false;
        if (!level().isClientSide() && !exploded) {
            explode();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isGhost();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public void defineSynchedData() {
        entityData.define(GHOST, false);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        if (tag.hasUUID("TargetUUID")) targetUUID = tag.getUUID("TargetUUID");
        life = tag.getInt("Life");
        exploded = tag.getBoolean("Exploded");
        setGhost(tag.getBoolean("Ghost"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        if (targetUUID != null) tag.putUUID("TargetUUID", targetUUID);
        tag.putInt("Life", life);
        tag.putBoolean("Exploded", exploded);
        tag.putBoolean("Ghost", isGhost());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public ResourceLocation model() {
        return MODEL;
    }

    @Override
    public ResourceLocation texture() {
        return isGhost() ? GHOST_TEXTURE : TEXTURE;
    }

    @Override
    public boolean translucentTexture() { return isGhost(); }

    @Override
    public ResourceLocation animation() {
        return DUMMY_ANIMATION;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        // 没有动画
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static InfSkeletonArrowEntity spawn(Level level, LivingEntity shooter, LivingEntity target, Vec3 pos, boolean ghost) {
        InfSkeletonArrowEntity arrow = new InfSkeletonArrowEntity(EntityTypeRegistry.INF_SKELETON_ARROW.get(), level);
        arrow.setGhost(ghost);
        arrow.setPos(pos);
        arrow.setOwner(shooter);
        arrow.setTarget(target);
        arrow.setDeltaMovement(target.getBoundingBox().getCenter().subtract(pos).normalize().scale(SPEED));
        level.addFreshEntity(arrow);
        return arrow;
    }
}
