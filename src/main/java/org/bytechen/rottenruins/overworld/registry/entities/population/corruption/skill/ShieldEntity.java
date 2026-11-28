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

/**
 * Icosahedron shield that follows its owner, damages entities inside, and returns damage as healing.
 */
public class ShieldEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(ShieldEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(ShieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MAX_AGE =
            SynchedEntityData.defineId(ShieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE_PER_TICK =
            SynchedEntityData.defineId(ShieldEntity.class, EntityDataSerializers.FLOAT);

    private Vec3 prevPos = Vec3.ZERO;
    private int age = 0;

    @Nullable
    private Entity cachedOwner;

    public ShieldEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

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

    public void setRadius(float r) {
        this.entityData.set(RADIUS, Math.max(0.5f, r));
        this.refreshDimensions();
    }

    public float getRadius() {
        return this.entityData.get(RADIUS);
    }

    public void setMaxAge(int ticks) {
        this.entityData.set(MAX_AGE, Math.max(1, ticks));
    }

    public int getMaxAge() {
        return this.entityData.get(MAX_AGE);
    }

    public void setDamagePerTick(float dmg) {
        this.entityData.set(DAMAGE_PER_TICK, dmg);
    }

    public float getDamagePerTick() {
        return this.entityData.get(DAMAGE_PER_TICK);
    }

    public Vec3 getRenderPos(float pt) {
        double x = Mth.lerp(pt, prevPos.x, this.getX());
        double y = Mth.lerp(pt, prevPos.y, this.getY());
        double z = Mth.lerp(pt, prevPos.z, this.getZ());
        return new Vec3(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float d = getRadius() * 2;
        return EntityDimensions.scalable(d, d);
    }

    @Override
    public void tick() {
        super.tick();
        prevPos = this.position();
        age++;

        if (!this.level().isClientSide) {
            serverTick();
        }
    }

    private void serverTick() {
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.isRemoved()) {
            this.discard();
            return;
        }

        this.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5f, owner.getZ());

        if (age >= getMaxAge()) {
            this.discard();
            return;
        }

        float dmg = getDamagePerTick();
        if (dmg <= 0) return;

        AABB area = this.getBoundingBox();
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != owner);

        float totalHeal = 0;
        for (LivingEntity target : targets) {
            target.hurt(this.level().damageSources().magic(), dmg);
            totalHeal += dmg;
        }

        if (totalHeal > 0 && owner instanceof LivingEntity livingOwner) {
            livingOwner.heal(totalHeal);
        }
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

    @Override
    public void defineSynchedData() {
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(RADIUS, 2.0f);
        this.entityData.define(MAX_AGE, 2000);
        this.entityData.define(DAMAGE_PER_TICK, 70f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (RADIUS.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) setOwner(null);
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.contains("Dmg")) setDamagePerTick(tag.getFloat("Dmg"));
        this.age = tag.getInt("Age");
        if (tag.hasUUID("OwnerUUID")) this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        UUID uuid = getOwnerUUID();
        if (uuid != null) tag.putUUID("OwnerUUID", uuid);
        tag.putFloat("Radius", getRadius());
        tag.putInt("MaxAge", getMaxAge());
        tag.putFloat("Dmg", getDamagePerTick());
        tag.putInt("Age", age);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
