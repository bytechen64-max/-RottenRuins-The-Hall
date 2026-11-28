package org.bytechen.rottenruins.mixin;

import org.bytechen.rottenruins.client.rend.glint.ItemOutlinePipeline;
import org.bytechen.rottenruins.client.rend.glint.ShaderPackDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 物品描边：遮罩在当帧捕获（那一刻场景深度才对），只把全屏合成推迟到 renderLevel TAIL。
// 捕获点绑 renderHand 字段读取处；合成不能也在那一行——光影的最终合成晚于它，描边会被覆盖。
// priority 500 与参照模组一致，保证排在 Oculus/Iris 的包装 mixin 之后。
@Mixin(value = net.minecraft.client.renderer.GameRenderer.class, priority = 500)
public abstract class OutlineReplayMixin {

    @Inject(method = "renderLevel",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z",
                    ordinal = 0))
    private void live$captureSceneSilhouettes(float partialTick, long finishTimeNano,
                                              com.mojang.blaze3d.vertex.PoseStack poseStack,
                                              CallbackInfo ci) {
        if (ShaderPackDetector.isShadowPass()) return;
        ItemOutlinePipeline.captureSceneSilhouettes();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void live$compositeOutline(float partialTick, long finishTimeNano,
                                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                                       CallbackInfo ci) {
        if (ShaderPackDetector.isShadowPass()) return;
        ItemOutlinePipeline.composite();
    }
}
