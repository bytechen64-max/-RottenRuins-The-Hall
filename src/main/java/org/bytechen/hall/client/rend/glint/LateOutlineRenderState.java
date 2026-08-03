package org.bytechen.hall.client.rend.glint;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/**
 * GL state guard for deferred outline rendering passes, modeled after
 * {@code mystery_buding.live.client.compat.oculus.LateShaderLayerState}.
 * <p>
 * When shader packs (Oculus + Iris) are active, the deferred rendering pipeline
 * captures the scene into intermediate GBuffer targets.  Our custom outline
 * shaders must write directly to the main framebuffer, bypassing those
 * intermediate targets entirely.
 * <p>
 * This guard is applied before and after every deferred outline replay batch
 * to reset GL state that the shader pack or vanilla pipeline may have altered
 * (scissor, depth test/mask, color mask, shader color, blend function).
 * <p>
 * Usage:
 * <pre>{@code
 * LateOutlineRenderState.prepareMainTargetPass();
 * try {
 *     // ... replay deferred outline entries ...
 * } finally {
 *     LateOutlineRenderState.finishMainTargetPass();
 * }
 * }</pre>
 */
public final class LateOutlineRenderState {

    /**
     * Prepare the main framebuffer for a late outline rendering pass.
     * <ul>
     *   <li>Binds the main render target for writing (bypasses shader pack GBuffer)</li>
     *   <li>Disables scissor test (shader packs often leave it enabled with stale rects)</li>
     *   <li>Enables depth test with depth writes (so LEQUAL can read existing depth)</li>
     *   <li>Enables all color channels</li>
     *   <li>Resets shader color to white</li>
     *   <li>Restores default blend function</li>
     * </ul>
     */
    public static void prepareMainTargetPass() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Clean up GL state after a late outline rendering pass.
     * Restores the main target binding and resets key state flags
     * so subsequent vanilla/shader-pack passes start from a known-good baseline.
     */
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
