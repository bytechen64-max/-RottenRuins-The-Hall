package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.infcore.core.evolution.EvolutionManager;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseRefactoredEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * 孕育者（重构种）。
 *
 * <p>吞噬机制：孕育者靠吞噬同类（其他孕育者）积累「吞噬值」，体型随之变大、
 * 血量上限提升；吞噬值达到阈值后按 infcore 进化表（{@code corruption_evo}）
 * 进化为长臂兽或震撼者。死亡时按体型留下放大的腐蚀领域（C9）。</p>
 */
public class BearerEntity extends BaseRefactoredEntity {

    private static final EntityDataAccessor<Float> DATA_DEVOUR_VALUE =
            SynchedEntityData.defineId(BearerEntity.class, EntityDataSerializers.FLOAT);

    private static final int BASE_TICKS_PER_POINT = 12;
    /** 吞噬值达到该阈值后进化 */
    private static final float EVOLVE_THRESHOLD = 2.5F;

    private BearerEntity devouringTarget;
    private int devourTickTimer = 0;

    public BearerEntity(EntityType<? extends BearerEntity> entityType, Level level,
                        Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.14F);
        this.setRunSpeed(0.18F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(30);
        this.setFakeDeathDuration(30);
        // C9 辐照领域：靠吞噬同类长大后，死后污染范围更广
        this.enableDeathField(3.0F, 200, 3.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DEVOUR_VALUE, 1.0F);
    }

    // =========================================================================
    // 吞噬
    // =========================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isAlive()) {
            if (this.devouringTarget == null) {
                if (this.tickCount % 10 == 0) findPotentialVictim();
            } else {
                handleDevouringProcess();
            }
        }
    }

    private void findPotentialVictim() {
        List<BearerEntity> neighbors = this.level().getEntitiesOfClass(
                BearerEntity.class, this.getBoundingBox().inflate(0.2D));

        for (BearerEntity other : neighbors) {
            if (other == this || !other.isAlive()) continue;

            float myVal = this.getDevourValue();
            float otherVal = other.getDevourValue();

            if (myVal > otherVal || (myVal == otherVal && this.getUUID().compareTo(other.getUUID()) > 0)) {
                this.startDevouring(other);
                break;
            }
        }
    }

    private void startDevouring(BearerEntity victim) {
        this.devouringTarget = victim;
        float ratio = Math.max(1.0F, this.getDevourValue() / victim.getDevourValue());
        int dynamicTicksPerPoint = Math.max(1, Math.round(BASE_TICKS_PER_POINT / ratio));
        this.devourTickTimer = (int) (victim.getHealth() * dynamicTicksPerPoint);
    }

    private void handleDevouringProcess() {
        if (this.devouringTarget == null) return;
        boolean isTouching = this.getBoundingBox().inflate(0.8D)
                .intersects(this.devouringTarget.getBoundingBox());

        if (!this.devouringTarget.isAlive() || !isTouching) {
            resetDevourStatus();
            return;
        }

        this.devourTickTimer--;

        if (this.devourTickTimer <= 0) {
            finalizeDevour(this.devouringTarget);
            resetDevourStatus();
        }
    }

    private void finalizeDevour(BearerEntity victim) {
        float victimVal = victim.getDevourValue();
        float currentVal = this.getDevourValue();

        this.setDevourValue(currentVal + (victimVal * 0.5F));

        float percentage = this.random.nextFloat() * (0.05F - 0.03F) + 0.03F;
        double healthBonus = victim.getMaxHealth() * percentage;

        var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() + healthBonus);
        }
        this.heal((float) healthBonus);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(victim, (byte) 3);
        }
        victim.discard();

        // 吞噬值达标 → 按 infcore 进化表进化为长臂兽 / 震撼者
        if (this.getDevourValue() >= EVOLVE_THRESHOLD && this.level() instanceof ServerLevel serverLevel) {
            EvolutionManager.applyEvolution(serverLevel, this, this.getFaction().evolutionRuleType());
        }
    }

    private void resetDevourStatus() {
        this.devouringTarget = null;
        this.devourTickTimer = 0;
    }

    public float getDevourValue() {
        return this.entityData.get(DATA_DEVOUR_VALUE);
    }

    public void setDevourValue(float value) {
        this.entityData.set(DATA_DEVOUR_VALUE, value);
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float boxScale = (float) Math.sqrt(this.getDevourValue());
        return super.getDimensions(pose).scale(boxScale + 0.35F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_DEVOUR_VALUE.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    // =========================================================================
    // 收尾 / 属性
    // =========================================================================

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 孕育者不近战，只吞噬同类
        return false;
    }

    @Override
    public void push(Entity entity) {
        if (entity == this.devouringTarget) {
            super.push(entity);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        } else {
            super.push(entity);
        }
    }

    @Override
    public boolean isPushable() {
        return this.devouringTarget == null;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putFloat("DevourValue", this.getDevourValue());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("DevourValue")) {
            this.setDevourValue(compound.getFloat("DevourValue"));
        }
    }
}
