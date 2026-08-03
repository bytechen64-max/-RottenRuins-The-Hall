package org.bytechen.hall.mixin;

import org.bytechen.hall.client.rend.glint.OutlineRenderQueue;
import org.bytechen.hall.client.rend.glint.ShaderPackDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects deferred outline replay at two points in {@code GameRenderer.renderLevel()},
 * matching {@code mystery_buding.live.mixin.CosmicAfterLevelMixin} exactly.
 *
 * <h3>Two-phase replay (IDENTICAL to Live mod)</h3>
 * <ol>
 *   <li><b>Phase 1 — FIELD inject on {@code renderHand:Z}:</b>
 *       At this point {@code LevelRenderer.renderLevel()} has already completed,
 *       so ground / third-person / item-frame items have been drawn and their
 *       depth is in the main depth buffer.  First-person hand items have NOT
 *       rendered yet (the {@code renderHand} field is being checked to decide
 *       whether to render them).  We replay world-space outlines now so they
 *       receive correct LEQUAL depth occlusion.</li>
 *   <li><b>Phase 2 — TAIL inject:</b>
 *       The entire {@code renderLevel} method has completed, including the
 *       first-person hand.  We replay remaining (first-person) entries last
 *       so held-item outlines sit on top of everything.</li>
 * </ol>
 *
 * <p>Both phases are guarded by {@link ShaderPackDetector#shouldUseShaderPackPipeline()}
 * and {@link ShaderPackDetector#isShadowPass()}.  When no shader pack is active,
 * outline entries render immediately in {@code ItemRendererMixin} and the queue
 * stays empty.</p>
 *
 * <p>Priority 500 matches the Live mod's {@code CosmicAfterLevelMixin} priority,
 * ensuring our injection runs after Oculus/Iris mixins that may wrap or alter
 * the render pipeline.</p>
 */
@Mixin(value = net.minecraft.client.renderer.GameRenderer.class, priority = 500)
public abstract class OutlineReplayMixin {

    /**
     * Phase 1 — replay world-space (non-first-person) outline entries.
     * <p>
     * Injected at the FIRST read of {@code renderHand} field — this read
     * occurs in the {@code if (this.renderHand)} check AFTER
     * {@code LevelRenderer.renderLevel()} has returned, but BEFORE the
     * first-person hand rendering block.
     * <p>
     * {@code require = 1} (default) — this injection MUST succeed.  If the
     * target cannot be found, the game will crash early rather than silently
     * skipping crucial shader compatibility.
     */
    @Inject(method = "renderLevel",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z",
                    ordinal = 0))
    private void live$renderOutlineNonFirstPerson(float partialTick, long finishTimeNano,
                                                   com.mojang.blaze3d.vertex.PoseStack poseStack,
                                                   CallbackInfo ci) {
        if (!ShaderPackDetector.shouldUseShaderPackPipeline()) return;
        if (ShaderPackDetector.isShadowPass()) return;
        OutlineRenderQueue.renderNonFirstPerson();
    }

    /**
     * Phase 2 — replay all remaining entries (including first-person hand).
     * <p>
     * Injected at TAIL of {@code renderLevel} — the first-person hand has
     * just finished rendering, so held-item outlines draw on top of the
     * completed scene.
     * <p>
     * {@code require = 1} (default) — this injection MUST succeed.
     */
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void live$renderOutlineAfterHand(float partialTick, long finishTimeNano,
                                              com.mojang.blaze3d.vertex.PoseStack poseStack,
                                              CallbackInfo ci) {
        if (!ShaderPackDetector.shouldUseShaderPackPipeline()) return;
        if (ShaderPackDetector.isShadowPass()) return;
        OutlineRenderQueue.renderAll();
    }
}
