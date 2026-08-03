package org.bytechen.hall.mixin;

import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.c2s.PacketSyncKeyframe;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;


@Mixin(value = AnimationController.class, remap = false)
public abstract class MixinAnimationController<T extends GeoAnimatable> {

    @Shadow
    protected AnimationController.CustomKeyframeHandler<T> customKeyframeHandler;

    @Inject(method = "setCustomInstructionKeyframeHandler", at = @At("RETURN"))
    private void wrapAndSendPacket(AnimationController.CustomKeyframeHandler<T> newHandler, CallbackInfoReturnable<AnimationController<T>> cir) {
        AnimationController.CustomKeyframeHandler<T> originalHandler = this.customKeyframeHandler;
        this.customKeyframeHandler = (event) -> {
            // 1. 执行原有逻辑
            if (originalHandler != null) {
                originalHandler.handle(event);
            }
            T animatable = event.getAnimatable();
            if (animatable instanceof Entity entity) {
                String instructionName = event.getKeyframeData().getInstructions();
                int entityId = entity.getId();
                NetworkHelper.sendToServer(new PacketSyncKeyframe(entityId, instructionName.replace(";", "")));
            }
        };
    }
}