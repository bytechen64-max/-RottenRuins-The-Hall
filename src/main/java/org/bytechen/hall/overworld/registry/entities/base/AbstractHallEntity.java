package org.bytechen.hall.overworld.registry.entities.base;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.network.c2s.IKeyframeHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.infcore.api.IInfectedEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public abstract class AbstractHallEntity extends Monster implements IAutoRenderableEntity, IKeyframeHandler, IInfectedEntity {

    public ResourceLocation model, texture, animation;
    protected float idleAnimSpeed = 1.0f;
    protected float walkAnimSpeed = 1.0f;
    protected float runAnimSpeed = 1.0f;
    protected float dieAnimSpeed = 1.0f;

    private static final EntityDataAccessor<Boolean> IS_WALKING =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_WALK_ANIM_SYNCED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> WALK_SPEED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RUN_SPEED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WALK_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RUN_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> IDLE_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIE_ANIM_SPEED =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_FAKE_DYING =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FAKE_DEATH_DURATION =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURST_SCALE_DURATION =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BURST_SCALE_RATE =
            SynchedEntityData.defineId(AbstractHallEntity.class, EntityDataSerializers.FLOAT);

    private int fakeDeathTimer = 0;
    protected int fakeDeathDuration = 60;
    protected int burstScaleDuration = 60;
    protected float burstScaleRate = 2.0f;
    protected int fakeDeathChance = 0;
    protected boolean fakeDeathEnabled = false;
    protected boolean hasWalkAnim = false;
    protected float walkSpeed = 0.6f;
    protected float runSpeed = 1.2f;
    protected boolean stopAiAtDist = true;
    protected boolean defaultControllersEnabled = true;

    protected int doHurtTime = 20;
    public int doHurtCooldown = 0;
    protected float doHurtDistance = 3.0f;

    @Override
    public ResourceLocation getInfectionType() {
        return new ResourceLocation(HallMod.MODID,"hall");
    }

    public enum BurstRenderMode { ANIMATION_ONLY, SCALE_ONLY, ANIMATION_AND_SCALE, NONE }
    protected BurstRenderMode burstRenderMode = BurstRenderMode.ANIMATION_ONLY;

    public boolean isActuallyMoving = false;
    private int movingCooldown = 0;
    private long clientBurstStartTime = -1;

    protected static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation ANIM_RUN  = RawAnimation.begin().thenLoop("run");
    protected static final RawAnimation ANIM_DIE  = RawAnimation.begin().thenPlay("die");

    private final AnimatableInstanceCache defaultCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private AABB cachedBoundingBox = null;
    private long lastBoundingBoxUpdateTick = 0;
    private static final int BOUNDING_BOX_CACHE_DURATION = 5;

    protected AbstractHallEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        if (!level.isClientSide) {
            HallEntityManager.register(this);
        }
        applyMoveSpeed();
    }

    public AbstractHallEntity(EntityType<? extends Monster> entityType, Level level, Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
    }



    public static AttributeSupplier.Builder createBaseAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_WALKING, true);
        this.entityData.define(HAS_WALK_ANIM_SYNCED, false);
        this.entityData.define(WALK_SPEED, 0.6f);
        this.entityData.define(RUN_SPEED, 1.2f);
        this.entityData.define(WALK_ANIM_SPEED, 1.0f);
        this.entityData.define(RUN_ANIM_SPEED, 1.0f);
        this.entityData.define(IDLE_ANIM_SPEED, 1.0f);
        this.entityData.define(IS_FAKE_DYING, false);
        this.entityData.define(FAKE_DEATH_DURATION, 60);
        this.entityData.define(BURST_SCALE_DURATION, 60);
        this.entityData.define(BURST_SCALE_RATE, 2.0f);
        this.entityData.define(DIE_ANIM_SPEED, 1.0f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFakeDying()) return false;
        if (!level().isClientSide && fakeDeathEnabled) {
            if (this.getHealth() - amount <= 0.0f && tryFakeDeath()) return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isDeadOrDying() { return !isFakeDying() && super.isDeadOrDying(); }

    @Override
    public void die(DamageSource source) {
        if (isFakeDying()) return;
        if (!level().isClientSide && fakeDeathEnabled && tryFakeDeath()) return;
        super.die(source);
    }

    @Override
    public void tick() {
        if (firstTick) applyMoveSpeed();
        if (!this.level().isClientSide()) updateWalkRunState();
        if (this.level().isClientSide()) updateMovingState();
        super.tick();

        if (isFakeDying()) {
            if (!this.level().isClientSide() && fakeDeathTimer > 0) {
                fakeDeathTimer--;
                if (fakeDeathTimer == 0) {
                    onBurstEnd();
                    this.entityData.set(IS_FAKE_DYING, false);
                    this.remove(RemovalReason.KILLED);
                }
            }
            return;
        }

        if (!this.level().isClientSide()) {
            if (doHurtCooldown > 0) doHurtCooldown--;
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                double distSq = getBoundingBoxDistanceSqrExact(target);
                if (distSq <= doHurtDistance * doHurtDistance && this.hasLineOfSight(target)) {
                    if (doHurtCooldown == 0 && doHurtGoal(target)) doHurtCooldown = doHurtTime;
                } else if (this.getNavigation().isDone()) {
                    this.getNavigation().moveTo(target, 1.0);
                }
            }
        }
    }

    private void updateWalkRunState() {
        // 没有 walk 动画时，始终按 walk 速度移动（与 GodSickNeo AbstractGSEntity 一致）
        if (!hasWalkAnimation()) {
            if (!isWalking()) {
                this.entityData.set(IS_WALKING, true);
                applyMoveSpeed();
            }
            return;
        }
        LivingEntity target = this.getTarget();
        boolean shouldWalk = target == null || !target.isAlive();
        if (this.isWalking() != shouldWalk) { this.entityData.set(IS_WALKING, shouldWalk); applyMoveSpeed(); }
    }

    public void applyMoveSpeed() {
        AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getBaseValue() != getCurrentMoveSpeed()) attr.setBaseValue(getCurrentMoveSpeed());
    }

    private void updateMovingState() {
        if (getDeltaMovement().horizontalDistanceSqr() > 1e-4) { movingCooldown = 5; isActuallyMoving = true; }
        else { if (movingCooldown > 0) movingCooldown--; else isActuallyMoving = false; }
    }

    protected boolean doHurtGoal(LivingEntity target) { return getAttack(target); }
    protected abstract boolean getAttack(LivingEntity target);

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.tickCount % 20 == 0
                && this.level() instanceof ServerLevel serverLevel && stopAiAtDist) {
            int simDistance = serverLevel.getServer().getPlayerList().getSimulationDistance();
            double activeRange = (simDistance - 1) * 16.0D;
            if (serverLevel.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), activeRange)) {
                if (this.isNoAi()) this.setNoAi(false);
            } else if (!serverLevel.players().isEmpty()) {
                // 有玩家在线但 isAlive 范围内没有 → 太远，暂停 AI
                if (!this.isNoAi()) this.setNoAi(true);
            }
            // 无人在线时保持 AI 运行，不影响其他怪物正常刷新
        }
        super.aiStep();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "movement_controller", 3, state -> {
            if (isDeadOrDying() || isFakeDying()) return PlayState.STOP;
            if (!isActuallyMoving) { state.setControllerSpeed(getIdleAnimSpeed()); return state.setAndContinue(ANIM_IDLE); }
            if (hasWalkAnimation()) {
                if (isWalking()) { state.setControllerSpeed(getWalkAnimSpeed()); return state.setAndContinue(ANIM_WALK); }
                else { state.setControllerSpeed(getRunAnimSpeed()); return state.setAndContinue(ANIM_RUN); }
            }
            state.setControllerSpeed(getWalkAnimSpeed());
            return state.setAndContinue(ANIM_WALK);
        }));
        AnimationController<AbstractHallEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        (isFakeDying() && shouldBurstPlayAnim()) ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return defaultCache; }
    @Override public void onKeyframeTrigger(String key) {}

    public boolean isFakeDying() { return this.entityData.get(IS_FAKE_DYING); }
    public boolean shouldBurstScale() { return burstRenderMode == BurstRenderMode.SCALE_ONLY || burstRenderMode == BurstRenderMode.ANIMATION_AND_SCALE; }
    public boolean shouldBurstPlayAnim() { return burstRenderMode == BurstRenderMode.ANIMATION_ONLY || burstRenderMode == BurstRenderMode.ANIMATION_AND_SCALE; }

    public float getBurstIntensity(float partialTick) {
        int scaleDuration = getBurstScaleDuration();
        if (!isFakeDying() || !shouldBurstScale() || scaleDuration <= 0) return 0.0f;
        float elapsed;
        if (level().isClientSide()) { if (clientBurstStartTime < 0) return 0.0f; elapsed = (level().getGameTime() - clientBurstStartTime) + partialTick; }
        else { elapsed = (getFakeDeathDuration() - fakeDeathTimer) + partialTick; }
        return Math.max(0.0f, elapsed / (float) scaleDuration);
    }

    public boolean tryFakeDeath() {
        if (!fakeDeathEnabled || isFakeDying()) return false;
        if (fakeDeathChance > 0 && this.getRandom().nextInt(100) >= fakeDeathChance) return false;
        startFakeDeath();
        return true;
    }

    protected void startFakeDeath() {
        if (isFakeDying()) return;
        this.entityData.set(IS_FAKE_DYING, true);
        this.fakeDeathTimer = this.getFakeDeathDuration();
        this.setHealth(this.getMaxHealth());
        if (shouldBurstPlayAnim()) this.triggerAnim("die_controller", "die_trigger");
        onBurstStart();
    }

    protected void onBurstStart() {}
    protected void onBurstEnd() {}

    @Override public void travel(Vec3 travelVector) { if (isFakeDying()) { super.travel(Vec3.ZERO); return; } super.travel(travelVector); }

    public boolean isWalking() { return this.entityData.get(IS_WALKING); }
    public boolean hasWalkAnimation() { return this.entityData.get(HAS_WALK_ANIM_SYNCED); }
    public void setHasWalkAnim(boolean value) { this.hasWalkAnim = value; this.entityData.set(HAS_WALK_ANIM_SYNCED, value); applyMoveSpeed(); }
    public float getWalkSpeed() { return this.entityData.get(WALK_SPEED); }
    public void setWalkSpeed(float speed) { this.walkSpeed = speed; this.entityData.set(WALK_SPEED, speed); applyMoveSpeed(); }
    public float getRunSpeed() { return this.entityData.get(RUN_SPEED); }
    public void setRunSpeed(float speed) { this.runSpeed = speed; this.entityData.set(RUN_SPEED, speed); applyMoveSpeed(); }
    public float getWalkAnimSpeed() { return this.entityData.get(WALK_ANIM_SPEED); }
    public void setWalkAnimSpeed(float speed) { this.walkAnimSpeed = speed; this.entityData.set(WALK_ANIM_SPEED, speed); }
    public float getRunAnimSpeed() { return this.entityData.get(RUN_ANIM_SPEED); }
    public void setRunAnimSpeed(float speed) { this.runAnimSpeed = speed; this.entityData.set(RUN_ANIM_SPEED, speed); }
    public float getIdleAnimSpeed() { return this.entityData.get(IDLE_ANIM_SPEED); }
    public void setIdleAnimSpeed(float speed) { this.idleAnimSpeed = speed; this.entityData.set(IDLE_ANIM_SPEED, speed); }
    public float getDieAnimSpeed() { return this.entityData.get(DIE_ANIM_SPEED); }
    public void setDieAnimSpeed(float speed) { this.dieAnimSpeed = speed; this.entityData.set(DIE_ANIM_SPEED, speed); }
    public float getCurrentMoveSpeed() {
        // 没有 walk 动画时始终用 walk 速度（与 GodSickNeo AbstractGSEntity 一致）
        if (!hasWalkAnimation()) {
            return getWalkSpeed();
        }
        return isWalking() ? getWalkSpeed() : getRunSpeed();
    }
    public void setFakeDeathEnabled(boolean enabled) { this.fakeDeathEnabled = enabled; }
    public void setFakeDeathChance(int chance) { this.fakeDeathChance = chance; }
    public void setFakeDeathDuration(int duration) { this.fakeDeathDuration = duration; this.entityData.set(FAKE_DEATH_DURATION, duration); }
    public int getFakeDeathDuration() { return this.entityData.get(FAKE_DEATH_DURATION); }
    public void setBurstScaleDuration(int duration) { this.burstScaleDuration = duration; this.entityData.set(BURST_SCALE_DURATION, duration); }
    public int getBurstScaleDuration() { return this.entityData.get(BURST_SCALE_DURATION); }
    public void setBurstScaleRate(float rate) { this.burstScaleRate = rate; this.entityData.set(BURST_SCALE_RATE, rate); }
    public float getBurstScaleRate() { return this.entityData.get(BURST_SCALE_RATE); }
    public void setDoHurtDistance(float distance) { this.doHurtDistance = distance; }
    public float getDoHurtDistance() { return doHurtDistance; }
    public int getDoHurtTime() { return doHurtTime; }
    public void setDoHurtTime(int doHurtTime) { this.doHurtTime = doHurtTime; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && IS_FAKE_DYING.equals(key)) {
            if (isFakeDying()) this.clientBurstStartTime = level().getGameTime(); else this.clientBurstStartTime = -1;
        }
    }

    @Override public ResourceLocation model()     { return this.model; }
    @Override public ResourceLocation texture()   { return this.texture; }
    @Override public ResourceLocation animation() { return this.animation; }

    public double getBoundingBoxDistanceSqrExact(Entity entity) {
        AABB a = getCachedBoundingBox(), b = entity.getBoundingBox();
        double dx = 0, dy = 0, dz = 0;
        if (a.maxX < b.minX) dx = b.minX - a.maxX; else if (b.maxX < a.minX) dx = a.minX - b.maxX;
        if (a.maxY < b.minY) dy = b.minY - a.maxY; else if (b.maxY < a.minY) dy = a.minY - b.maxY;
        if (a.maxZ < b.minZ) dz = b.minZ - a.maxZ; else if (b.maxZ < a.minZ) dz = a.minZ - b.maxZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private AABB getCachedBoundingBox() {
        long t = this.level().getGameTime();
        if (cachedBoundingBox == null || t - lastBoundingBoxUpdateTick > BOUNDING_BOX_CACHE_DURATION) { cachedBoundingBox = this.getBoundingBox(); lastBoundingBoxUpdateTick = t; }
        return cachedBoundingBox;
    }

    public boolean randomPercentage(int percentage) { return this.getRandom().nextInt(100) < percentage; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean doHurtTarget(Entity entity) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("IsWalking", isWalking());
        nbt.putBoolean("HasWalkAnim", this.hasWalkAnim);
        nbt.putFloat("WalkSpeed", this.walkSpeed);
        nbt.putFloat("RunSpeed", this.runSpeed);
        nbt.putFloat("WalkAnimSpeed", this.walkAnimSpeed);
        nbt.putFloat("RunAnimSpeed", this.runAnimSpeed);
        nbt.putFloat("IdleAnimSpeed", this.idleAnimSpeed);
        nbt.putFloat("DieAnimSpeed", this.dieAnimSpeed);
        nbt.putBoolean("IsFakeDying", isFakeDying());
        nbt.putInt("FakeDeathTimer", this.fakeDeathTimer);
        nbt.putInt("FakeDeathDuration", this.fakeDeathDuration);
        nbt.putInt("BurstScaleDuration", this.burstScaleDuration);
        nbt.putFloat("BurstScaleRate", this.burstScaleRate);
        nbt.putInt("DoHurtTime", this.doHurtTime);
        nbt.putFloat("DoHurtDistance", this.doHurtDistance);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("IsWalking")) this.entityData.set(IS_WALKING, nbt.getBoolean("IsWalking"));
        if (nbt.contains("HasWalkAnim")) { this.hasWalkAnim = nbt.getBoolean("HasWalkAnim"); this.entityData.set(HAS_WALK_ANIM_SYNCED, this.hasWalkAnim); }
        if (nbt.contains("IsFakeDying")) this.entityData.set(IS_FAKE_DYING, nbt.getBoolean("IsFakeDying"));
        if (nbt.contains("FakeDeathTimer")) this.fakeDeathTimer = nbt.getInt("FakeDeathTimer");
        if (nbt.contains("FakeDeathDuration")) this.setFakeDeathDuration(nbt.getInt("FakeDeathDuration"));
        if (nbt.contains("BurstScaleDuration")) this.setBurstScaleDuration(nbt.getInt("BurstScaleDuration"));
        if (nbt.contains("BurstScaleRate")) this.setBurstScaleRate(nbt.getFloat("BurstScaleRate"));
        if (nbt.contains("WalkSpeed")) { this.walkSpeed = nbt.getFloat("WalkSpeed"); this.entityData.set(WALK_SPEED, this.walkSpeed); }
        if (nbt.contains("RunSpeed")) { this.runSpeed = nbt.getFloat("RunSpeed"); this.entityData.set(RUN_SPEED, this.runSpeed); }
        if (nbt.contains("WalkAnimSpeed")) { this.walkAnimSpeed = nbt.getFloat("WalkAnimSpeed"); this.entityData.set(WALK_ANIM_SPEED, this.walkAnimSpeed); }
        if (nbt.contains("RunAnimSpeed")) { this.runAnimSpeed = nbt.getFloat("RunAnimSpeed"); this.entityData.set(RUN_ANIM_SPEED, this.runAnimSpeed); }
        if (nbt.contains("IdleAnimSpeed")) { this.idleAnimSpeed = nbt.getFloat("IdleAnimSpeed"); this.entityData.set(IDLE_ANIM_SPEED, this.idleAnimSpeed); }
        if (nbt.contains("DieAnimSpeed")) { this.dieAnimSpeed = nbt.getFloat("DieAnimSpeed"); this.entityData.set(DIE_ANIM_SPEED, this.dieAnimSpeed); }
        if (nbt.contains("DoHurtTime")) this.doHurtTime = nbt.getInt("DoHurtTime");
        if (nbt.contains("DoHurtDistance")) this.doHurtDistance = nbt.getFloat("DoHurtDistance");
        applyMoveSpeed();
    }
}
