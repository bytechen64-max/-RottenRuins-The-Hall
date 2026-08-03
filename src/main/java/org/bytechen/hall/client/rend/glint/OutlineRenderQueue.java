package org.bytechen.hall.client.rend.glint;

import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.client.rend.SplendidingShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.*;

/**
 * Deferred outline replay queue for shader-pack (Oculus + Iris) compatibility,
 * modeled IDENTICALLY after
 * {@code mystery_buding.live.client.compat.oculus.CosmicItemLateRenderQueue}.
 *
 * <h3>Why deferred rendering</h3>
 * When Oculus + Iris shader packs are active, items rendered during normal
 * {@code ItemRenderer.render()} are captured into the shader pack's GBuffer
 * textures.  Custom shaders rendered at that time would be misinterpreted
 * (outline colour treated as emissive/reflective data), causing visual
 * artifacts or complete invisibility.
 *
 * <p>Solution: enqueue outline draw calls during normal rendering, then replay
 * them directly to the main framebuffer AFTER the shader pack's deferred
 * pipeline has finished compositing the scene.</p>
 *
 * <h3>Two-phase replay (IDENTICAL to Live mod)</h3>
 * <ol>
 *   <li><b>{@link #renderNonFirstPerson()}</b> — called when
 *       {@code renderHand} field is first read in
 *       {@code GameRenderer.renderLevel()}.  World-space items (ground,
 *       third-person, item frames) have been drawn, but the first-person
 *       hand has not.  Replays only non-first-person entries with
 *       {@code LEQUAL} depth + polygon offset + {@code MAIN_TARGET}.</li>
 *   <li><b>{@link #renderAll()}</b> — called at TAIL of
 *       {@code GameRenderer.renderLevel()}.  The first-person hand has
 *       just finished rendering.  Replays ALL remaining entries
 *       (first-person hand entries use {@code NO_DEPTH_TEST}).</li>
 * </ol>
 *
 * <h3>Matrix handling (IDENTICAL to Live mod)</h3>
 * The regular shader (with {@code ModelViewMat}/{@code ProjMat} uniforms)
 * is used.  During replay we:
 * <ol>
 *   <li>Restore the model-view matrix via
 *       {@code modelViewStack.last().pose().set(entry.modelView)} +
 *       {@code RenderSystem.applyModelViewMatrix()}.</li>
 *   <li>Restore the projection matrix via
 *       {@code RenderSystem.setProjectionMatrix()}.</li>
 *   <li>Build a fresh {@code PoseStack} with the stored pose + normal,
 *       then apply the bbox expansion transform on top.</li>
 *   <li>Render using the regular shader, which correctly computes
 *       {@code ProjMat × ModelViewMat × vec4(Position, 1.0)}.</li>
 * </ol>
 */
public final class OutlineRenderQueue {
    private static final List<Entry> Q = new ArrayList<>(64);

    private OutlineRenderQueue() {}

    // ── enqueue (matches CosmicItemLateRenderQueue.enqueue exactly) ──

    /**
     * Snapshot all render state at this instant.  Must be called on the render
     * thread while the correct matrices are active in {@link RenderSystem}.
     *
     * @param m         the baked model (for quad extraction)
     * @param cu        optional {@link ICustomOutline} item instance
     * @param p         optional {@link GlintEffectProfile} fallback
     * @param c         outline colour as [r,g,b,a]
     * @param sk        regular shader key (e.g. {@code "default"})
     * @param b         blend mode
     * @param w         outline width (scale factor)
     * @param cx        bounding-box centre X in item-local space
     * @param cy        bounding-box centre Y in item-local space
     * @param pose      {@code poseStack.last().pose()} — model transform
     * @param normal    {@code poseStack.last().normal()} — normal matrix
     * @param modelView {@code RenderSystem.getModelViewMatrix()} — view matrix
     * @param context   the item display context for two-phase routing
     */
    public static void enqueue(BakedModel m, @Nullable ICustomOutline cu, @Nullable GlintEffectProfile p,
            float[] c, String sk, ICustomOutline.BlendMode b, float w,
            float cx, float cy, Matrix4f pose, Matrix3f normal,
            Matrix4f modelView, ItemDisplayContext context) {
        List<BakedQuad> qs = m.getQuads(null, null, net.minecraft.util.RandomSource.create());
        if (qs.isEmpty()) return;
        Q.add(new Entry(qs, cu, p, c.clone(), sk, b, w, cx, cy,
                new Matrix4f(pose),
                new Matrix3f(normal),
                new Matrix4f(modelView),
                new Matrix4f(RenderSystem.getProjectionMatrix()),
                context));
    }

    // ── two-phase replay (matches CosmicItemLateRenderQueue exactly) ──

    /** Phase 1: replay world-space entries before the first-person hand. */
    public static void renderNonFirstPerson() {
        renderMatchingEntries(false, false);
    }

    /** Phase 2: replay all remaining entries after the first-person hand. */
    public static void renderAll() {
        renderMatchingEntries(true, true);
    }

    // ── internal replay (matches CosmicItemLateRenderQueue.renderMatchingEntries) ──

    private static void renderMatchingEntries(boolean includeFirstPerson, boolean clearSkippedEntries) {
        if (Q.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            Q.clear();
            return;
        }

        // Use the game's shared BufferSource — NOT whatever was passed by
        // the caller (which may have been wrapped by a shader pack).
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();

        try {
            LateOutlineRenderState.prepareMainTargetPass();

            Iterator<Entry> iterator = Q.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();

                // Route first-person entries to Phase 2
                if (isFirstPersonContext(entry.context) && !includeFirstPerson) {
                    if (clearSkippedEntries) {
                        iterator.remove();
                    }
                    continue;
                }

                // ── restore matrices (IDENTICAL to CosmicItemLateRenderQueue) ──
                modelViewStack.last().pose().set(entry.modelView);
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(entry.projection),
                        VertexSorting.DISTANCE_TO_ORIGIN);

                // Build fresh PoseStack with stored model transform
                PoseStack poseStack = new PoseStack();
                poseStack.last().pose().set(entry.pose);
                poseStack.last().normal().set(entry.normal);

                // Apply bbox expansion on top of the model transform
                poseStack.translate(entry.cx, entry.cy, 0);
                poseStack.scale(1f + entry.width + 0.003f, 1f + entry.width + 0.003f, 1f);
                poseStack.translate(-entry.cx, -entry.cy, 0);

                drawLateOutline(entry, poseStack, buffers);

                // Flush this specific batch (matches Live's per-entry endBatch)
                boolean isFirstPerson = isFirstPersonContext(entry.context);
                RenderType lateType = isFirstPerson
                        ? SplendidingShaders.getLateHandOutlineRenderType(entry.sk)
                        : SplendidingShaders.getLateWorldOutlineRenderType(entry.sk);
                if (lateType != null) {
                    buffers.endBatch(lateType);
                }

                iterator.remove();
            }
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            LateOutlineRenderState.finishMainTargetPass();

            if (clearSkippedEntries) {
                Q.clear();
            }
        }
    }

    /**
     * Draw a single outline entry using the regular (non-_sp) shader
     * with the appropriate late RenderType.
     *
     * <p>The regular shader's vertex program computes
     * {@code gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0)}.
     * Because we restored modelView/projection via RenderSystem above,
     * the shader uniforms are correct and perspective division works.</p>
     */
    private static void drawLateOutline(Entry e, PoseStack poseStack, MultiBufferSource.BufferSource buf) {
        boolean isFirstPerson = isFirstPersonContext(e.context);

        // Regular (non-_sp) shader — has ModelViewMat/ProjMat uniforms
        ShaderInstance shader = SplendidingShaders.getOutlineShader(e.sk);
        RenderType renderType = isFirstPerson
                ? SplendidingShaders.getLateHandOutlineRenderType(e.sk)
                : SplendidingShaders.getLateWorldOutlineRenderType(e.sk);

        if (shader == null || renderType == null) return;

        // ── uniforms (same as immediate path) ──
        float[] c = e.color;
        if (shader.getUniform("OutlineColor") != null)
            shader.safeGetUniform("OutlineColor").set(c[0], c[1], c[2], c[3]);
        if (shader.getUniform("TintColor") != null)
            shader.safeGetUniform("TintColor").set(c[0], c[1], c[2], 0.8f);
        if (shader.getUniform("Time") != null)
            shader.safeGetUniform("Time").set((System.currentTimeMillis() % 3600000L) / 1000f);
        if (e.cu != null) e.cu.configureOutlineShader(shader);

        // ── blend mode ──
        if (e.blend == ICustomOutline.BlendMode.ADDITIVE)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else
            RenderSystem.defaultBlendFunc();

        // ── draw ──
        // The PoseStack contains model×bbox.  ModelViewMat and ProjMat are
        // set via RenderSystem.  The vertex shader multiplies them together.
        VertexConsumer vc = buf.getBuffer(renderType);
        for (BakedQuad q : e.qs)
            vc.putBulkData(poseStack.last(), q, 1f, 1f, 1f, 0, 0);
    }

    // ── legacy single-phase (kept for backward compat) ──

    /** @deprecated use {@link #renderNonFirstPerson()} + {@link #renderAll()} */
    @Deprecated
    public static void replayAll() {
        if (Q.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        MultiBufferSource.BufferSource buf = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        for (Entry e : Q) replayLegacy(e, buf);
        buf.endBatch();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        Q.clear();
    }

    private static void replayLegacy(Entry e, MultiBufferSource.BufferSource buf) {
        String spKey = SplendidingShaders.spKey(e.sk);
        ShaderInstance s = SplendidingShaders.getOutlineShader(spKey);
        RenderType t = SplendidingShaders.getOutlineRenderType(spKey);
        if (s == null || t == null) return;
        float[] c = e.color;
        if (s.getUniform("OutlineColor") != null) s.safeGetUniform("OutlineColor").set(c[0],c[1],c[2],c[3]);
        if (s.getUniform("TintColor") != null) s.safeGetUniform("TintColor").set(c[0],c[1],c[2],0.8f);
        if (s.getUniform("Time") != null) s.safeGetUniform("Time").set((System.currentTimeMillis()%3600000L)/1000f);
        if (e.cu != null) e.cu.configureOutlineShader(s);
        if (e.blend == ICustomOutline.BlendMode.ADDITIVE)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();

        PoseStack ps = new PoseStack();
        Matrix4f m = new Matrix4f(e.pose);
        m.translate(e.cx, e.cy, 0);
        m.scale(1f + e.width + 0.003f, 1f + e.width + 0.003f, 1f);
        m.translate(-e.cx, -e.cy, 0);
        Matrix4f mvp = new Matrix4f(e.projection).mul(m);
        ps.last().pose().set(mvp);

        VertexConsumer vc = buf.getBuffer(t);
        for (BakedQuad q : e.qs) vc.putBulkData(ps.last(), q, 1f,1f,1f,0,0);
    }

    // ── helpers ──

    private static boolean isFirstPersonContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    public static float[] computeBbox(List<BakedQuad> qs) {
        float mx = Float.MAX_VALUE, my = Float.MAX_VALUE,
              Mx = -Float.MAX_VALUE, My = -Float.MAX_VALUE;
        for (BakedQuad q : qs) {
            int[] v = q.getVertices();
            for (int i = 0; i < 4; i++) {
                int o = i * 9;
                float x = Float.intBitsToFloat(v[o]),
                      y = Float.intBitsToFloat(v[o + 1]);
                mx = Math.min(mx, x); Mx = Math.max(Mx, x);
                my = Math.min(my, y); My = Math.max(My, y);
            }
        }
        return new float[]{(mx + Mx) / 2f, (my + My) / 2f};
    }

    // ── entry (matches CosmicItemLateRenderQueue.Entry exactly) ──

    static class Entry {
        final List<BakedQuad> qs;
        @Nullable final ICustomOutline cu;
        @Nullable final GlintEffectProfile p;
        final float[] color;
        final String sk;
        final ICustomOutline.BlendMode blend;
        final float width, cx, cy;
        final Matrix4f pose;       // poseStack.last().pose() at enqueue
        final Matrix3f normal;     // poseStack.last().normal() at enqueue
        final Matrix4f modelView;  // RenderSystem.getModelViewMatrix() at enqueue
        final Matrix4f projection; // RenderSystem.getProjectionMatrix() at enqueue
        final ItemDisplayContext context;

        Entry(List<BakedQuad> qs, @Nullable ICustomOutline cu, @Nullable GlintEffectProfile p,
              float[] color, String sk, ICustomOutline.BlendMode blend, float w,
              float cx, float cy, Matrix4f pose, Matrix3f normal,
              Matrix4f modelView, Matrix4f projection, ItemDisplayContext context) {
            this.qs = qs; this.cu = cu; this.p = p; this.color = color;
            this.sk = sk; this.blend = blend; this.width = w;
            this.cx = cx; this.cy = cy;
            this.pose = pose; this.normal = normal;
            this.modelView = modelView; this.projection = projection;
            this.context = context;
        }
    }
}
