package org.bytechen.hall.overworld.registry.entities.population.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;

public class ShockwaveEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SPREAD_SPEED =
            SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_RADIUS =
            SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ALPHA =
            SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RING_POS =
            SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RING_WID =
            SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);

    private static final float DEF_SPEED = 3f, DEF_RADIUS = 8f, DEF_ALPHA = 1f;
    private static final float DEF_RING_POS = 0.08f, DEF_RING_WID = 0.95f;
    private static final int DEF_LIFE = 60;

    private int age;

    public ShockwaveEntity(EntityType<?> type, Level level) { super(type, level); noCulling = true; }

    @Override protected void defineSynchedData() {
        entityData.define(DATA_SPREAD_SPEED, DEF_SPEED);
        entityData.define(DATA_MAX_RADIUS, DEF_RADIUS);
        entityData.define(DATA_LIFETIME, DEF_LIFE);
        entityData.define(DATA_ALPHA, DEF_ALPHA);
        entityData.define(DATA_RING_POS, DEF_RING_POS);
        entityData.define(DATA_RING_WID, DEF_RING_WID);
    }

    public float getSpreadSpeed()  { return entityData.get(DATA_SPREAD_SPEED); }
    public float getMaxRadius()    { return entityData.get(DATA_MAX_RADIUS); }
    public int   getLifetime()     { return entityData.get(DATA_LIFETIME); }
    public float getAlpha()        { return entityData.get(DATA_ALPHA); }
    public float getRingPosition() { return entityData.get(DATA_RING_POS); }
    public float getRingWidth()    { return entityData.get(DATA_RING_WID); }
    public int   getAge()          { return age; }
    public float getLifeProgress() { int lt = getLifetime(); return lt <= 0 ? 1f : Math.min(age / (float) lt, 1f); }

    public void setSpreadSpeed(float v)  { entityData.set(DATA_SPREAD_SPEED, v); }
    public void setMaxRadius(float v)    { entityData.set(DATA_MAX_RADIUS, v); }
    public void setLifetime(int v)       { entityData.set(DATA_LIFETIME, v); }
    public void setAlpha(float v)        { entityData.set(DATA_ALPHA, v); }
    public void setRingPosition(float v) { entityData.set(DATA_RING_POS, v); }
    public void setRingWidth(float v)    { entityData.set(DATA_RING_WID, v); }

    @Override public void tick() {
        if (isPassenger() && getVehicle() != null && !getVehicle().isPassengerOfSameVehicle(this)) stopRiding();
        age++;
        float rawR = age / 20f * getSpreadSpeed();
        if (!level().isClientSide() && (age >= getLifetime() || rawR >= getMaxRadius())) discard();
    }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()  { return false; }
    @Override public boolean isAttackable(){ return false; }

    @Override protected void readAdditionalSaveData(CompoundTag t) {
        age = t.getInt("Age");
        if (t.contains("Speed"))  setSpreadSpeed(t.getFloat("Speed"));
        if (t.contains("Radius")) setMaxRadius(t.getFloat("Radius"));
        if (t.contains("Life"))   setLifetime(t.getInt("Life"));
        if (t.contains("Alpha"))  setAlpha(t.getFloat("Alpha"));
        if (t.contains("RPos"))   setRingPosition(t.getFloat("RPos"));
        if (t.contains("RWid"))   setRingWidth(t.getFloat("RWid"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("Age", age); t.putFloat("Speed", getSpreadSpeed()); t.putFloat("Radius", getMaxRadius());
        t.putInt("Life", getLifetime()); t.putFloat("Alpha", getAlpha());
        t.putFloat("RPos", getRingPosition()); t.putFloat("RWid", getRingWidth());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    public static ShockwaveEntity spawn(Level level, Vec3 pos,
                                         float speed, float radius, int life, float alpha,
                                         float ringPos, float ringWid) {
        ShockwaveEntity e = new ShockwaveEntity(EntityTypeRegistry.SHOCKWAVE.get(), level);
        e.setPos(pos);
        e.setSpreadSpeed(speed); e.setMaxRadius(radius); e.setLifetime(life);
        e.setAlpha(alpha); e.setRingPosition(ringPos); e.setRingWidth(ringWid);
        level.addFreshEntity(e);
        return e;
    }
    public static ShockwaveEntity spawn(Level level, Vec3 pos, float speed, float radius, int life, float alpha) {
        return spawn(level, pos, speed, radius, life, alpha, DEF_RING_POS, DEF_RING_WID);
    }
}
