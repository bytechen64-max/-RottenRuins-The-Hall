package org.bytechen.rottenruins.overworld.registry.entities.base;

import org.bytechen.rottenruins.client.entity.IAutoRenderableProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 腐蚀弹射物抽象基类，支持 GeckoLib 动画和统一注册。
 */
public abstract class AbstractCorruptionProjectile extends ThrowableProjectile implements IAutoRenderableProjectile {

    private static final EntityDataAccessor<Integer> DATA_ADAPTATION_COUNT =
            SynchedEntityData.defineId(AbstractCorruptionProjectile.class, EntityDataSerializers.INT);

    protected ResourceLocation model;
    protected ResourceLocation texture;
    protected ResourceLocation animation;

    public void setModel(ResourceLocation model) { this.model = model; }
    public void setTexture(ResourceLocation texture) { this.texture = texture; }
    public void setAnimation(ResourceLocation animation) { this.animation = animation; }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AbstractCorruptionProjectile(EntityType<? extends ThrowableProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public AbstractCorruptionProjectile(EntityType<? extends ThrowableProjectile> entityType, double x, double y, double z, Level level) {
        super(entityType, x, y, z, level);
    }

    public AbstractCorruptionProjectile(EntityType<? extends ThrowableProjectile> entityType, LivingEntity shooter, Level level) {
        super(entityType, shooter, level);
    }

    @Override
    public void defineSynchedData() {
        this.entityData.define(DATA_ADAPTATION_COUNT, 0);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) return;
        if (result.getEntity() instanceof LivingEntity target) {
            onHitLivingEntity(target);
        }
    }

    /**
     * 子类实现具体的命中效果（伤害、药水效果等）
     */
    protected abstract void onHitLivingEntity(LivingEntity target);

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // 默认无额外效果，可被子类覆盖
    }

    // ========== GeckoLib 实现 ==========
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        // 提供一个简单的飞行动画控制器（可选）
        registrar.add(new AnimationController<>(this, "fly_controller", 0, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("fly"));
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ========== IGeoResources 实现 ==========
    @Override
    public ResourceLocation model() { return model; }
    @Override
    public ResourceLocation texture() { return texture; }
    @Override
    public ResourceLocation animation() { return animation; }

    // ========== NBT 持久化 ==========
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CorruptionProjAdaptCount", getAdaptationCount());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAdaptationCount(tag.getInt("CorruptionProjAdaptCount"));
    }

    // ========== 适应计数（可选，用于渐进难度） ==========
    public int getAdaptationCount() {
        return this.entityData.get(DATA_ADAPTATION_COUNT);
    }

    public void setAdaptationCount(int count) {
        this.entityData.set(DATA_ADAPTATION_COUNT, count);
    }
}