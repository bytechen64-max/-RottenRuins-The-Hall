package org.bytechen.rottenruins.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 虚化：把带 ETHEREAL 效果的实体换成半透明渲染类型。 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererEtherealMixin {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void rottenruins$etherealRenderType(LivingEntity entity, boolean bodyVisible, boolean translucent,
                                               boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (!CustomEffects.hasEffect(entity, RegisterCustomEffect.ETHEREAL)) {
            return;
        }
        // 贴图要从渲染器本身取；getTextureLocation 由 EntityRenderer 声明，不能 @Shadow。
        @SuppressWarnings({"rawtypes", "unchecked"})
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) {
            return;
        }
        cir.setReturnValue(RenderType.entityTranslucent(renderer.getTextureLocation(entity), false));
    }
}
