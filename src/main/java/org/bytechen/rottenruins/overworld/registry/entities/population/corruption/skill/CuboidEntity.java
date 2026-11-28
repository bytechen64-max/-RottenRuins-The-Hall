package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Flexible cuboid projectile with smooth movement, resizable hitbox, and per-tick hit callback.
 * Supports two movement modes: targeted (eased lerp) and launched (constant velocity).
 * Owner is excluded from hit detection. Auto-discards after maxAge (default 10s).
 */
public class CuboidEntity extends Entity {

    private static final EntityDataAccessor<Float> BOX_W =
            SynchedEntityData.defineId(CuboidEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOX_H =
            SynchedEntityData.defineId(CuboidEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOX_D =
            SynchedEntityData.defineId(CuboidEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(CuboidEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private enum MoveMode { NONE, TARGETED, LAUNCHED }

    private MoveMode moveMode = MoveMode.NONE;
    private Vec3 startPos = Vec3.ZERO;
    private Vec3 targetPos = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private int moveStartTick = -1;
    private int moveDuration = 0;
    private int maxAge = 200;
    private int age = 0;
    private Vec3 prevPos = Vec3.ZERO;

    @Nullable
    private Entity cachedOwner;

    @Nullable
    private BiConsumer<CuboidEntity, LivingEntity> onHit;

    public CuboidEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public void setSize(float width, float height, float depth) {
        this.entityData.set(BOX_W, Math.max(0.1f, width));
        this.entityData.set(BOX_H, Math.max(0.1f, height));
        this.entityData.set(BOX_D, Math.max(0.1f, depth));
        this.refreshDimensions();
    }

    public float getBoxWidth() { return this.entityData.get(BOX_W); }
    public float getBoxHeight() { return this.entityData.get(BOX_H); }
    public float getBoxDepth() { return this.entityData.get(BOX_D); }

    public void setOwner(@Nullable Entity owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.cachedOwner = owner;
        } else {
            this.entityData.set(OWNER_UUID, Optional.empty());
            this.cachedOwner = null;
        }
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    @Nullable
    public Entity getOwner() {
        if (cachedOwner != null && !cachedOwner.isRemoved()) return cachedOwner;
        cachedOwner = findOwner();
        return cachedOwner;
    }

    @Nullable
    private Entity findOwner() {
        UUID ownerId = getOwnerUUID();
        if (ownerId == null) return null;
        Entity owner = this.level().getPlayerByUUID(ownerId);
        if (owner != null) return owner;
        AABB searchArea = this.getBoundingBox().inflate(64.0);
        List<Entity> found = this.level().getEntitiesOfClass(Entity.class, searchArea,
                e -> e.getUUID().equals(ownerId));
        return found.isEmpty() ? null : found.get(0);
    }

    public void setOnHit(@Nullable BiConsumer<CuboidEntity, LivingEntity> callback) {
        this.onHit = callback;
    }

    public void setMaxAge(int ticks) {
        this.maxAge = Math.max(1, ticks);
    }

    /** Targeted movement: eased interpolation from current position to target, stops at target. */
    public void moveTo(Vec3 target, int duration) {
        this.startPos = this.position();
        this.targetPos = target;
        this.moveStartTick = this.tickCount;
        this.moveDuration = Math.max(1, duration);
        this.moveMode = MoveMode.TARGETED;
    }

    /** Launched projectile: continuous movement in the given direction at constant speed. */
    public void launch(Vec3 direction, float speed) {
        this.velocity = direction.normalize().scale(speed);
        this.moveMode = MoveMode.LAUNCHED;
        this.startPos = this.position();
        this.moveStartTick = this.tickCount;
    }

    /** Render-smooth interpolated position between previous and current tick. */
    public Vec3 getRenderPos(float pt) {
        double x = Mth.lerp(pt, prevPos.x, this.getX());
        double y = Mth.lerp(pt, prevPos.y, this.getY());
        double z = Mth.lerp(pt, prevPos.z, this.getZ());
        return new Vec3(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(Math.max(getBoxWidth(), getBoxDepth()), getBoxHeight());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(2.0);
    }

    @Override
    public void tick() {
        prevPos = this.position();
        super.tick();

        if (!this.level().isClientSide) {
            age++;
            if (age >= maxAge) {
                this.discard();
                return;
            }
        }

        switch (moveMode) {
            case TARGETED -> tickTargeted();
            case LAUNCHED -> tickLaunched();
        }

        if (!this.level().isClientSide && onHit != null) {
            Entity owner = getOwner();
            AABB area = this.getBoundingBox();
            List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != owner);
            for (LivingEntity target : targets) {
                onHit.accept(this, target);
            }
        }
    }

    private void tickTargeted() {
        float progress = moveDuration > 0
                ? Mth.clamp((float) (this.tickCount - moveStartTick) / moveDuration, 0f, 1f)
                : 1f;
        float eased = easeInOutCubic(progress);
        this.setPos(
                Mth.lerp(eased, startPos.x, targetPos.x),
                Mth.lerp(eased, startPos.y, targetPos.y),
                Mth.lerp(eased, startPos.z, targetPos.z));
        if (progress >= 1f) {
            this.moveMode = MoveMode.NONE;
        }
    }

    private void tickLaunched() {
        Vec3 current = this.position();
        this.setPos(current.x + velocity.x, current.y + velocity.y, current.z + velocity.z);
    }

    /** Ease-in-out cubic: slow start, fast middle, slow end. */
    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    @Override
    public void defineSynchedData() {
        this.entityData.define(BOX_W, 1.0f);
        this.entityData.define(BOX_H, 1.0f);
        this.entityData.define(BOX_D, 1.0f);
        this.entityData.define(OWNER_UUID, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (BOX_W.equals(key) || BOX_H.equals(key) || BOX_D.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BoxW")) setSize(tag.getFloat("BoxW"), getBoxHeight(), getBoxDepth());
        if (tag.contains("BoxH")) setSize(getBoxWidth(), tag.getFloat("BoxH"), getBoxDepth());
        if (tag.contains("BoxD")) setSize(getBoxWidth(), getBoxHeight(), tag.getFloat("BoxD"));
        if (tag.hasUUID("OwnerUUID")) this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        this.maxAge = tag.getInt("MaxAge");
        this.age = tag.getInt("Age");
        String modeStr = tag.getString("MoveMode");
        if (!modeStr.isEmpty()) {
            moveMode = MoveMode.valueOf(modeStr);
            moveStartTick = tag.getInt("MoveStartTick");
            moveDuration = tag.getInt("MoveDuration");
            startPos = new Vec3(tag.getDouble("SX"), tag.getDouble("SY"), tag.getDouble("SZ"));
            if (moveMode == MoveMode.TARGETED) {
                targetPos = new Vec3(tag.getDouble("TX"), tag.getDouble("TY"), tag.getDouble("TZ"));
            } else if (moveMode == MoveMode.LAUNCHED) {
                velocity = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ"));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("BoxW", getBoxWidth());
        tag.putFloat("BoxH", getBoxHeight());
        tag.putFloat("BoxD", getBoxDepth());
        UUID uuid = getOwnerUUID();
        if (uuid != null) tag.putUUID("OwnerUUID", uuid);
        tag.putInt("MaxAge", maxAge);
        tag.putInt("Age", age);
        tag.putString("MoveMode", moveMode.name());
        if (moveMode != MoveMode.NONE) {
            tag.putInt("MoveStartTick", moveStartTick);
            tag.putInt("MoveDuration", moveDuration);
            tag.putDouble("SX", startPos.x); tag.putDouble("SY", startPos.y); tag.putDouble("SZ", startPos.z);
            if (moveMode == MoveMode.TARGETED) {
                tag.putDouble("TX", targetPos.x); tag.putDouble("TY", targetPos.y); tag.putDouble("TZ", targetPos.z);
            } else if (moveMode == MoveMode.LAUNCHED) {
                tag.putDouble("VX", velocity.x); tag.putDouble("VY", velocity.y); tag.putDouble("VZ", velocity.z);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
