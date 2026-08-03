package org.bytechen.hall.mixin;

import org.bytechen.hall.client.cosmic.compat.CosmicItemLateRenderQueue;
import org.bytechen.hall.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.hall.client.entity.render.impl.BlackHoleLateRenderQueue;
import org.bytechen.hall.client.entity.render.impl.ShockwaveLateRenderQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects cosmic deferred replay at two points in {@code GameRenderer.renderLevel()},
 * matching {@code mystery_buding.live.mixin.CosmicAfterLevelMixin} exactly.
 *
 * <h3>Two-phase replay</h3>
 * <ol>
 *   <li><b>Before hand render</b> ({@code renderHand} field read):
 *       Replay world-space cosmic entries.  Main scene depth is written,
 *       first-person hand hasn't rendered yet.</li>
 *   <li><b>TAIL of {@code renderLevel}</b>:
 *       Replay all remaining entries including first-person hands.</li>
 * </ol>
 */
@Mixin(value = GameRenderer.class, priority = 500)
public abstract class CosmicAfterLevelMixin {

    /**
     * Phase 1: replay world-space cosmic entries before the first-person
     * hand renders (IDENTICAL to Live's CosmicAfterLevelMixin).
     */
    @Inject(method = "renderLevel",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z",
                    ordinal = 0))
    private void live$renderCosmicBeforeHand(float partialTick, long finishTimeNano,
                                              PoseStack poseStack, CallbackInfo ci) {
        if (CosmicItemShaderCompat.isOculusShaderPackActive()) {
            CosmicItemLateRenderQueue.renderNonFirstPerson();
        }
    }

    /**
     * Phase 2: replay all remaining cosmic entries (including first-person
     * hands) and deferred shockwaves after the entire renderLevel method
     * completes.  At this point the shader pack has composed the final
     * scene to the main framebuffer, so scene-copy textures capture the
     * correct image.
     */
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void live$renderCosmicAfterHand(float partialTick, long finishTimeNano,
                                             PoseStack poseStack, CallbackInfo ci) {
        if (CosmicItemShaderCompat.isOculusShaderPackActive()) {
            CosmicItemLateRenderQueue.renderAll();
        }
        ShockwaveLateRenderQueue.renderAll();
        BlackHoleLateRenderQueue.renderAll();
    }
}
