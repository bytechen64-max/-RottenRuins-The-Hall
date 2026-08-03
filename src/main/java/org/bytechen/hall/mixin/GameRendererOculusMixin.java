package org.bytechen.hall.mixin;

import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class GameRendererOculusMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ItemInHandRenderer itemInHandRenderer;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private LightTexture lightTexture;

    @Unique
    private Matrix4f itemglint$oculusPose;

    @Unique
    private Matrix3f itemglint$oculusNormal;

    @Unique
    private boolean itemglint$oculusPrepared;

    @Inject(method = "renderItemInHand",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LightTexture;turnOnLightLayer()V",
                    shift = At.Shift.BEFORE),
            require = 0)
    private void itemglint$prepareOculusHandCapture(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick,
                                                    CallbackInfo ci) {
        itemglint$oculusPrepared = false;
        itemglint$oculusPose = null;
        itemglint$oculusNormal = null;

        if (!HeldItemOutlineCompat.isOculusLoaded()) {
            return;
        }

        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            return;
        }

        itemglint$oculusPose = new Matrix4f(poseStack.last().pose());
        itemglint$oculusNormal = new Matrix3f(poseStack.last().normal());
        itemglint$oculusPrepared = true;
    }

    @Inject(method = "renderItemInHand",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LightTexture;turnOffLightLayer()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void itemglint$runOculusHandCapture(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick,
                                                CallbackInfo ci) {
        if (!itemglint$oculusPrepared) {
            return;
        }

        itemglint$oculusPrepared = false;

        if (!HeldItemOutlineCompat.isOculusLoaded()) {
            return;
        }

        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        MultiBufferSource.BufferSource primaryBufferSource = renderBuffers.bufferSource();
        primaryBufferSource.endBatch();

        if (!HeldItemOutlineRenderer.shouldRenderOutlinePass(minecraft)) {
            return;
        }

        MultiBufferSource.BufferSource captureBufferSource = HeldItemOutlineCompat.isEmbeddiumLoaded()
                ? HeldItemOutlineRenderer.getEmbeddiumCaptureBufferSource()
                : primaryBufferSource;
        EntityRenderDispatcher entityRenderDispatcher = minecraft.getEntityRenderDispatcher();
        int packedLight = entityRenderDispatcher.getPackedLightCoords(player, partialTick);
        GameRendererAccessor accessor = (GameRendererAccessor) (Object) this;
        Matrix4f handProjection = ((GameRenderer) (Object) this)
                .getProjectionMatrix(accessor.invokeGetFov(minecraft.gameRenderer.getMainCamera(), partialTick, false));
        HeldItemOutlineRenderer.beginItemInHandRender(handProjection);
        try {
            java.util.List<HeldItemOutlineRenderer.HandEffectTarget> targets = HeldItemOutlineRenderer.getRenderableHands(player);
            for (int index = 0; index < targets.size(); index++) {
                HeldItemOutlineRenderer.HandEffectTarget target = targets.get(index);
                HeldItemOutlineRenderer.HandEffectTarget nextTarget = index + 1 < targets.size() ? targets.get(index + 1) : null;
                boolean batchHands = HeldItemOutlineRenderer.shouldBatchHands(target, nextTarget);
                InteractionHand hand = target.hand();
                Matrix4f captureModelView = itemglint$oculusPose == null ? null : new Matrix4f(itemglint$oculusPose);
                if (!HeldItemOutlineRenderer.beginCapture(minecraft, minecraft.getMainRenderTarget(), hand,
                        batchHands ? null : hand, captureModelView, target.profile(), target.sampledColors())) {
                    continue;
                }

                lightTexture.turnOnLightLayer();
                try {
                    if (itemglint$oculusPose != null) {
                        poseStack.last().pose().set(itemglint$oculusPose);
                    }
                    if (itemglint$oculusNormal != null) {
                        poseStack.last().normal().set(itemglint$oculusNormal);
                    }
                    itemInHandRenderer.renderHandsWithItems(partialTick, poseStack, captureBufferSource, player, packedLight);
                    captureBufferSource.endBatch();
                } finally {
                    HeldItemOutlineRenderer.endCapture();
                    lightTexture.turnOffLightLayer();
                }

                HeldItemOutlineRenderer.composite(minecraft, minecraft.getMainRenderTarget(), hand);
                if (batchHands) {
                    index++;
                }
            }
        } finally {
            HeldItemOutlineRenderer.endItemInHandRender();
        }
        itemglint$oculusPose = null;
        itemglint$oculusNormal = null;
    }

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void itemglint$finishEmbeddiumOculusShaderpackOutline(float partialTick, long nanoTime, PoseStack poseStack,
                                                                  CallbackInfo ci) {
        if (!HeldItemOutlineCompat.shouldUseEmbeddiumOculusPipeline(minecraft)) {
            return;
        }

        HeldItemOutlineRenderer.finishEmbeddiumCompatFrame(minecraft, minecraft.getMainRenderTarget());
    }
}
