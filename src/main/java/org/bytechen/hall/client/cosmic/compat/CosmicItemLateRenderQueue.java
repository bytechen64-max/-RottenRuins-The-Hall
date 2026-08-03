package org.bytechen.hall.client.cosmic.compat;

import org.bytechen.hall.client.cosmic.BakedModelCosmic;
import org.bytechen.hall.client.cosmic.render.CosmicRenderType;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Deferred cosmic render queue for shader-pack compatibility,
 * matching {@code mystery_buding.live.client.compat.oculus.CosmicItemLateRenderQueue} exactly.
 *
 * <h3>How it works</h3>
 * When a shader pack (Oculus + Iris) is active, cosmic starfield layers are
 * NOT drawn immediately during {@code ItemRenderer.render()}.  Instead, the
 * full render state is snapshotted into an {@link Entry} and appended to this
 * queue.  The queue is replayed at two injection points in
 * {@code GameRenderer.renderLevel()}:
 * <ol>
 *   <li><b>Before hand render</b> ({@link #renderNonFirstPerson()}):
 *       Replays world-space cosmic entries with LEQUAL depth + polygon offset
 *       + MAIN_TARGET output.</li>
 *   <li><b>After hand render</b> ({@link #renderAll()}):
 *       Replays first-person cosmic entries with NO_DEPTH_TEST + MAIN_TARGET.</li>
 * </ol>
 *
 * <p>This ensures the cosmic shader writes directly to the main framebuffer,
 * bypassing the shader pack's deferred GBuffer pipeline entirely.</p>
 */
public final class CosmicItemLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * Snapshot a cosmic render call for deferred replay.
     * Must be called on the render thread while the correct
     * modelView/projection matrices are active.
     */
    public static void enqueue(BakedModelCosmic renderer, ItemStack stack,
                               ItemDisplayContext context, PoseStack poseStack,
                               int packedLight, int packedOverlay, BakedModel model) {
        PoseStack.Pose pose = poseStack.last();
        ENTRIES.add(new Entry(
                renderer,
                stack.copy(),
                context,
                new Matrix4f(pose.pose()),
                new Matrix3f(pose.normal()),
                new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()),
                packedLight,
                packedOverlay,
                model
        ));
    }

    /** Phase 1: replay non-first-person entries before hand render. */
    public static void renderNonFirstPerson() {
        renderMatchingEntries(false, false);
    }

    /** Phase 2: replay all remaining entries after hand render. */
    public static void renderAll() {
        renderMatchingEntries(true, true);
    }

    private static void renderMatchingEntries(boolean includeFirstPersonHand,
                                              boolean clearSkippedEntries) {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();

        try {
            LateOutlineRenderState.prepareMainTargetPass();

            Iterator<Entry> iterator = ENTRIES.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (isFirstPersonHandContext(entry.context()) && !includeFirstPersonHand) {
                    if (clearSkippedEntries) {
                        iterator.remove();
                    }
                    continue;
                }

                // Restore matrices from enqueue time (IDENTICAL to Live mod)
                modelViewStack.last().pose().set(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(entry.projection()),
                        VertexSorting.DISTANCE_TO_ORIGIN);

                PoseStack poseStack = new PoseStack();
                poseStack.last().pose().set(entry.pose());
                poseStack.last().normal().set(entry.normal());

                entry.renderer().renderShaderLayer(
                        entry.stack(), entry.context(), poseStack, buffers,
                        entry.packedLight(), entry.packedOverlay(), entry.model(), true);

                // Render corruption layer on top of cosmic
                entry.renderer().renderCorruptionLayer(
                        entry.stack(), entry.context(), poseStack, buffers,
                        entry.packedLight(), entry.packedOverlay(), entry.model(), true);

                // Flush each batch independently
                buffers.endBatch(CosmicRenderType.COSMIC_AFTER_LEVEL);
                buffers.endBatch(CosmicRenderType.COSMIC_HAND_AFTER_LEVEL);
                buffers.endBatch(CosmicRenderType.CORRUPTION_AFTER_LEVEL);
                buffers.endBatch(CosmicRenderType.CORRUPTION_HAND_AFTER_LEVEL);
                iterator.remove();
            }
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            LateOutlineRenderState.finishMainTargetPass();

            if (clearSkippedEntries) {
                ENTRIES.clear();
            }
        }
    }

    private static boolean isFirstPersonHandContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private record Entry(BakedModelCosmic renderer, ItemStack stack,
                         ItemDisplayContext context, Matrix4f pose, Matrix3f normal,
                         Matrix4f modelView, Matrix4f projection,
                         int packedLight, int packedOverlay, BakedModel model) {}

    private CosmicItemLateRenderQueue() {}
}
