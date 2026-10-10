package org.bytechen.hall.client.entity.render.impl;

import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Deferred shockwave render queue for shader-pack (Oculus/Iris) compatibility.
 *
 * <h3>Why</h3>
 * Shockwave rendering needs a scene-copy texture (blit from main framebuffer)
 * and draws custom-shaded sphere geometry.  Under shader packs the main
 * framebuffer at {@code AFTER_TRIPWIRE_BLOCKS} time contains raw GBuffer data,
 * not the composed final scene.  By deferring the scene copy + draw to
 * {@code GameRenderer.renderLevel()} TAIL, we capture the fully composed
 * scene and write directly to the main framebuffer (bypassing GBuffers).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@link ShockwaveRenderer#renderOne} detects shader pack →
 *       snapshots all computed params and enqueues here.</li>
 *   <li>{@code CosmicAfterLevelMixin} TAIL calls {@link #renderAll()}.</li>
 *   <li>Replay: do scene copy from the (now composed) main framebuffer,
 *       then draw the shockwave sphere with late-deferred GL state.</li>
 * </ol>
 *
 * @see ShockwaveRenderer
 * @see org.bytechen.hall.mixin.CosmicAfterLevelMixin
 */
public final class ShockwaveLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ShockwaveLateRenderQueue() {}

    /**
     * Enqueue a shockwave for deferred rendering.
     * Must be called on the render thread.
     */
    public static void enqueue(Matrix4f pose, Matrix3f normal,
                                float cx, float cy, float cz,
                                float radius, float maxRadius,
                                float alpha, float lifeProgress,
                                float ringPosition, float ringWidth) {
        ENTRIES.add(new Entry(
                new Matrix4f(pose), new Matrix3f(normal),
                cx, cy, cz, radius, maxRadius, alpha, lifeProgress, ringPosition, ringWidth));
    }

    /**
     * Replay all enqueued shockwaves.  Called from
     * {@code CosmicAfterLevelMixin} at {@code renderLevel()} TAIL,
     * after the shader pack has composed the final scene to the
     * main framebuffer.
     */
    public static void renderAll() {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();

        try {
            for (Entry e : ENTRIES) {
                ShockwaveRenderer.renderOneDeferred(
                        e.pose, e.normal, e.cx, e.cy, e.cz, e.radius, e.maxRadius,
                        e.alpha, e.lifeProgress, e.ringPosition, e.ringWidth);
            }
        } finally {
            LateOutlineRenderState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    // ── internal ──────────────────────────────────────────────

    private record Entry(Matrix4f pose, Matrix3f normal,
                         float cx, float cy, float cz,
                         float radius, float maxRadius,
                         float alpha, float lifeProgress,
                         float ringPosition, float ringWidth) {}
}
