package org.bytechen.hall.mixin;

import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class LevelRendererEmbeddiumMixin {
    @Inject(method = "renderLevel",
            at = @At(value = "CONSTANT", args = "stringValue=translucent"),
            require = 0)
    private void itemglint$runEmbeddiumOculusSolidCompatPass(PoseStack poseStack, float tickDelta, long finishTimeNano,
                                                              boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                                              LightTexture lightTexture, Matrix4f projectionMatrix,
                                                              CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HeldItemOutlineCompat.shouldUseEmbeddiumOculusPipeline(minecraft)) {
            HeldItemOutlineRenderer.renderOculusShaderpackCompatPass(
                    minecraft,
                    poseStack,
                    tickDelta,
                    camera,
                    gameRenderer,
                    minecraft.gameRenderer.itemInHandRenderer,
                    lightTexture,
                    projectionMatrix,
                    true
            );
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void itemglint$markEmbeddiumCompatFrameReady(PoseStack poseStack, float tickDelta, long finishTimeNano,
                                                          boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                                          LightTexture lightTexture, Matrix4f projectionMatrix,
                                                          CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HeldItemOutlineCompat.shouldUseEmbeddiumOculusPipeline(minecraft)) {
            HeldItemOutlineRenderer.renderOculusShaderpackCompatPass(
                minecraft,
                poseStack,
                tickDelta,
                camera,
                gameRenderer,
                minecraft.gameRenderer.itemInHandRenderer,
                lightTexture,
                projectionMatrix,
                false
            );
        }
    }
}
