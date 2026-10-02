package org.bytechen.hall.overworld.registry.entities.population.ecological;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * heavy_bomb 投放的炸弹实体。
 * <p>
 * 不是弹射物，而是普通 Entity：从高处自然下落，触地爆炸并生成冲击波。
 */
public class HeavyBombTntEntity extends Entity implements IAutoRenderableEntity {

    private static final float GRAVITY = 0.08F;
    private static final float AIR_FRICTION = 0.99F;
    private static final float EXPLOSION_RADIUS = 4.0F;
    private static final int MAX_LIFE = 200;

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "geo/hall_heavy_bomb_tnt.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/entity/heavy_bomb_tnt.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "animations/hall_heavy_bomb.animation.json");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private LivingEntity owner;
    private UUID ownerUUID;
    private int life;
    private boolean exploded;

    // 客户端平滑插值
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;
    private int lerpSteps;

    public HeavyBombTntEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
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

        Vec3 motion = getDeltaMovement().add(0, -GRAVITY, 0).scale(AIR_FRICTION);
        setDeltaMovement(motion);

        // 触地/撞方块前预检测
        if (!level().noCollision(this, getBoundingBox().move(motion))) {
            explode();
            return;
        }

        move(MoverType.SELF, motion);

        if (horizontalCollision || verticalCollision || onGround()) {
            explode();
        }
    }

    private void explode() {
        if (exploded) return;
        exploded = true;
        if (!level().isClientSide()) {
            // 爆炸伤害来源指定为投放它的 heavy_bomb
            DamageSource damageSource = owner != null
                    ? level().damageSources().explosion(owner, this)
                    : level().damageSources().explosion(this, null);
            level().explode(owner, damageSource, null,
                    getX(), getY(), getZ(), EXPLOSION_RADIUS, false,
                    Level.ExplosionInteraction.NONE);
            // 生成冲击波实体：扩散更快，生命周期覆盖完整淡出
            ShockwaveEntity.spawn(level(), position(), 14.0F, 8.0F, 30, 1.0F);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
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
        life = tag.getInt("Life");
        exploded = tag.getBoolean("Exploded");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
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
        return ANIMATION;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        // 没有动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static HeavyBombTntEntity spawn(Level level, Vec3 pos, LivingEntity owner) {
        HeavyBombTntEntity bomb = new HeavyBombTntEntity(EntityTypeRegistry.HEAVY_BOMB_TNT.get(), level);
        bomb.setPos(pos);
        bomb.setOwner(owner);
        level.addFreshEntity(bomb);
        return bomb;
    }
}
