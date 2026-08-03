package org.bytechen.hall.overworld.registry.entities.base;

import org.bytechen.hall.client.entity.IAutoRenderableProjectile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public abstract class AbstractHallProjectile extends ThrowableProjectile implements IAutoRenderableProjectile {

    private ResourceLocation model, texture, animation;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected AbstractHallProjectile(EntityType<? extends ThrowableProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof LivingEntity target) onHitLivingEntity(target);
        this.discard();
    }

    protected abstract void onHitLivingEntity(LivingEntity target);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "fly_controller", 0, state -> {
            state.setAndContinue(RawAnimation.begin().thenLoop("fly"));
            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public ResourceLocation model()     { return this.model; }
    @Override public ResourceLocation texture()   { return this.texture; }
    @Override public ResourceLocation animation() { return this.animation; }
    public void setModel(ResourceLocation m)      { this.model = m; }
    public void setTexture(ResourceLocation t)    { this.texture = t; }
    public void setAnimation(ResourceLocation a)  { this.animation = a; }
}
