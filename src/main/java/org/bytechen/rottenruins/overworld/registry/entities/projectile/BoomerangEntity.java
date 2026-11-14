package org.bytechen.rottenruins.overworld.registry.entities.projectile;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.api.boomerang.IBoomerangItem;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative swept collision, automatic homing return and a bounded lifetime. */
public class BoomerangEntity extends Projectile implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.BOOLEAN);
    private final Set<UUID> hitTargets = new HashSet<>();
    private int flightAge;

    public BoomerangEntity(EntityType<? extends BoomerangEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static BoomerangEntity create(Level level, Player player, ItemStack stack) {
        BoomerangEntity projectile = new BoomerangEntity(EntityTypeRegistry.BOOMERANG.get(), level);
        projectile.entityData.set(ITEM, stack.copyWithCount(1));
        projectile.setOwner(player);
        projectile.setPos(player.getEyePosition().add(0, -0.15D, 0));
        projectile.setDeltaMovement(player.getLookAngle().scale(projectile.behavior().outboundSpeed(projectile.getItem())));
        projectile.updateFlightRotation();
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
        return projectile;
    }

    @Override public void defineSynchedData() {
        entityData.define(ITEM, ItemStack.EMPTY);
        entityData.define(RETURNING, false);
    }
    @Override public ItemStack getItem() { return entityData.get(ITEM); }
    public boolean isReturning() { return entityData.get(RETURNING); }
    private IBoomerangItem behavior() { return (IBoomerangItem) getItem().getItem(); }

    public void beginReturn() {
        if (!isReturning()) {
            entityData.set(RETURNING, true);
            hitTargets.clear();
        }
    }

    @Override public void tick() {
        super.tick();
        if (!(getItem().getItem() instanceof IBoomerangItem)) {
            if (!level().isClientSide) discard();
            return;
        }
        IBoomerangItem behavior = behavior();
        if (level().isClientSide) return;
        Entity owner = getOwner();
        if (++flightAge > behavior.maxLifetimeTicks(getItem()) || !(owner instanceof Player player)
                || !player.isAlive() || player.isSpectator() || player.level() != level()) {
            discard();
            return;
        }
        if (!isReturning() && flightAge >= behavior.outboundTicks(getItem())) beginReturn();
        if (isReturning()) {
            Vec3 delta = player.getEyePosition().add(0, -0.15D, 0).subtract(position());
            double speed = Math.max(0.1D, behavior.returnSpeed(getItem()));
            if (delta.length() <= speed + 0.4D) {
                behavior.onReturned(this, player, getItem());
                discard();
                return;
            }
            setDeltaMovement(delta.normalize().scale(speed));
        }
        Vec3 step = getDeltaMovement();
        updateFlightRotation();
        // Returning through terrain prevents a projectile getting stranded behind a wall.
        HitResult hit = isReturning()
                ? ProjectileUtil.getEntityHitResult(level(), this, position(), position().add(step),
                    getBoundingBox().expandTowards(step).inflate(0.3D), this::canHitEntity)
                : ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean blocked = false;
        if (hit != null && hit.getType() != HitResult.Type.MISS
                && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            onHit(hit);
            blocked = hit.getType() == HitResult.Type.BLOCK;
        }
        if (!isRemoved()) {
            Vec3 start = position();
            Vec3 end = blocked
                    ? hit.getLocation() : start.add(step);
            setPos(end);
            ParticleOptions particle = behavior.trailParticle(getItem());
            if (particle != null && level() instanceof net.minecraft.server.level.ServerLevel server) {
                int count = 2 + random.nextInt(3);
                for (int i = 0; i < count; i++) {
                    Vec3 point = start.lerp(end, random.nextDouble()).add(
                            random.nextGaussian() * 0.13D, random.nextGaussian() * 0.10D,
                            random.nextGaussian() * 0.13D);
                    server.sendParticles(particle, point.x, point.y, point.z, 1, 0.035D, 0.035D, 0.035D, 0.012D);
                }
            }
        }
    }

    private void updateFlightRotation() {
        Vec3 velocity = getDeltaMovement();
        if (velocity.lengthSqr() < 1.0e-8D) return;
        setYRot((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())));
    }

    @Override protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        return super.canHitEntity(target) && target != owner && !hitTargets.contains(target.getUUID())
                && (owner == null || !owner.isAlliedTo(target))
                && (!(owner instanceof Player player) || !(target instanceof Player other) || player.canHarmPlayer(other));
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        hitTargets.add(hit.getEntity().getUUID());
        boolean damaged = hit.getEntity().hurt(damageSources().thrown(this, getOwner()), behavior().throwDamage(getItem()));
        behavior().onEntityHit(this, getItem(), hit, damaged);
        beginReturn();
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        behavior().onBlockHit(this, getItem(), hit);
        beginReturn();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("BoomerangItem", getItem().save(new CompoundTag()));
        tag.putBoolean("Returning", isReturning());
        tag.putInt("FlightAge", flightAge);
        net.minecraft.nbt.ListTag targets = new net.minecraft.nbt.ListTag();
        for (UUID uuid : hitTargets) targets.add(net.minecraft.nbt.NbtUtils.createUUID(uuid));
        tag.put("HitTargets", targets);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ITEM, ItemStack.of(tag.getCompound("BoomerangItem")));
        entityData.set(RETURNING, tag.getBoolean("Returning"));
        flightAge = tag.getInt("FlightAge");
        hitTargets.clear();
        for (net.minecraft.nbt.Tag target : tag.getList("HitTargets", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            hitTargets.add(net.minecraft.nbt.NbtUtils.loadUUID(target));
        }
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
