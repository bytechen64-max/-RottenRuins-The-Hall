package org.bytechen.hall.overworld.registry.entities.population.infected;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.utils.entity.EntityParticleUtils;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 畸骸骷髅发射的追踪型生物实体。
 * <p>
 * 不是弹射物（Projectile），而是普通 Entity：
 * 会自动追踪目标，碰到目标 / 方块 / 被攻击击杀时自爆（小型爆炸，不破坏方块）。
 */
public class InfSkeletonArrowEntity extends Entity implements IAutoRenderableEntity {

    private static final float SPEED = 0.55F;
    private static final float EXPLOSION_RADIUS = 1.5F;
    private static final int MAX_LIFE = 200;
    /** 转向强度：越小转弯越柔和 */
    private static final double STEER_STRENGTH = 0.035D;
    /** 速度阻尼：让速度变化更平滑 */
    private static final double DAMPING = 0.985D;

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "geo/inf_skeleton_arrow.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/entity/inf_skeleton_arrow.png");
    // 没有独立的 animation 文件，借用现有 idle 动画文件避免 GeckoLib 加载空路径
    private static final ResourceLocation DUMMY_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "animations/inf_skeleton.animation.json");

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

        if (++life > MAX_LIFE) {
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

            if (getBoundingBox().inflate(0.2D).intersects(target.getBoundingBox())
                    || toTarget.lengthSqr() < 0.25D) {
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
        if (target != null && target.isAlive()) return;
        if (targetUUID != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(targetUUID);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                target = living;
            } else {
                target = null;
            }
        }
    }

    private void explode() {
        if (exploded) return;
        exploded = true;
        if (!level().isClientSide()) {
            // 小型爆炸，NONE 表示不破坏方块，但会伤害实体
            level().explode(this, getX(), getY(), getZ(), EXPLOSION_RADIUS, Level.ExplosionInteraction.NONE);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && !exploded) {
            explode();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
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
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        if (tag.hasUUID("TargetUUID")) targetUUID = tag.getUUID("TargetUUID");
        life = tag.getInt("Life");
        exploded = tag.getBoolean("Exploded");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        if (targetUUID != null) tag.putUUID("TargetUUID", targetUUID);
        tag.putInt("Life", life);
        tag.putBoolean("Exploded", exploded);
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
        return TEXTURE;
    }

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

    public static InfSkeletonArrowEntity spawn(Level level, LivingEntity shooter, LivingEntity target, Vec3 pos) {
        InfSkeletonArrowEntity arrow = new InfSkeletonArrowEntity(EntityTypeRegistry.INF_SKELETON_ARROW.get(), level);
        arrow.setPos(pos);
        arrow.setOwner(shooter);
        arrow.setTarget(target);
        arrow.setDeltaMovement(target.getBoundingBox().getCenter().subtract(pos).normalize().scale(SPEED));
        level.addFreshEntity(arrow);
        return arrow;
    }
}
