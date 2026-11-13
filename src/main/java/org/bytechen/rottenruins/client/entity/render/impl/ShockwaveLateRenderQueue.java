package org.bytechen.rottenruins.client.entity.render.impl;

import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
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
 * Deferred shockwave render queue for shader-pack (Oculus/Iris) compatibility: at {@code AFTER_TRIPWIRE_BLOCKS}
 * the main framebuffer holds raw GBuffer data, so scene copy + sphere draw defer to {@code renderLevel()} TAIL.
 * Flow: {@link ShockwaveRenderer#renderOne} snapshots params and enqueues; the TAIL replay draws to it.
 */
public final class ShockwaveLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ShockwaveLateRenderQueue() {}

    /** Enqueue a shockwave for deferred rendering. Must be called on the render thread. */
    public static void enqueue(Matrix4f pose, Matrix3f normal,
                                float cx, float cy, float cz,
                                float radius, float maxRadius,
                                float alpha, float lifeProgress,
                                float ringPosition, float ringWidth) {
        ENTRIES.add(new Entry(
                new Matrix4f(pose), new Matrix3f(normal),
                cx, cy, cz, radius, maxRadius, alpha, lifeProgress, ringPosition, ringWidth));
    }

    /** Replay all enqueued shockwaves; called at {@code renderLevel()} TAIL after the pack composed the scene. */
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

    private record Entry(Matrix4f pose, Matrix3f normal,
                         float cx, float cy, float cz,
                         float radius, float maxRadius,
                         float alpha, float lifeProgress,
                         float ringPosition, float ringWidth) {}
}
