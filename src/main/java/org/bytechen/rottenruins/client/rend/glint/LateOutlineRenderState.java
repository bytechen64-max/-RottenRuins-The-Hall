package org.bytechen.rottenruins.client.rend.glint;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

// Deferred outline passes bypass shader-pack (Oculus/Iris) GBuffer targets: write to the main
// framebuffer and reset scissor/depth/color/blend per batch.
public final class LateOutlineRenderState {

    public static void prepareMainTargetPass() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    public static void finishMainTargetPass() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    private LateOutlineRenderState() {}
}
