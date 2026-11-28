package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 天穹裁决光柱的延后回放队列（光影包兼容）：光影激活时光柱会被 GBuffer 当成一块材质重新参与光照与雾，所以只入队，
 * 等 pack 把最终场景合成到主帧缓冲之后（renderLevel() 的 TAIL）再在 MAIN_TARGET 上回放。入队时必须快照投影矩阵 +
 * 相机旋转 + 相机世界位置，回放时不能回读 RenderSystem：手部渲染会换手部 FOV 的投影，下一帧相机位置也对不上。
 */
public final class VerdictBeamLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private VerdictBeamLateRenderQueue() {}

    /** 入队一道光柱；必须在渲染线程、实体渲染的那一刻调用（要快照当时的矩阵）。 */
    public static void enqueue(Matrix4f projection, Matrix4f modelView,
                               double entityX, double entityY, double entityZ,
                               double camX, double camY, double camZ,
                               float baseRadius, float baseLength,
                               float scaleXZ, float scaleY,
                               float time, float intensity, float scanT,
                               float lifeProgress, float ageSeconds,
                               float backCull) {
        ENTRIES.add(new Entry(
                new Matrix4f(projection), rotationOf(modelView),
                entityX, entityY, entityZ, camX, camY, camZ,
                baseRadius, baseLength, scaleXZ, scaleY,
                time, intensity, scanT, lifeProgress, ageSeconds, backCull));
    }

    /** 回放全部光柱；由 {@code CosmicAfterLevelMixin} 在 {@code renderLevel()} 的 TAIL 调用。 */
    public static void renderAll() {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        ShaderInstance shader = SplendidingShaders.verdictBeamShader;
        if (shader == null) {
            ENTRIES.clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f(ENTRIES.get(0).projection()),
                    VertexSorting.DISTANCE_TO_ORIGIN);

            for (Entry e : ENTRIES) {
                replay(e, shader);
            }
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            LateOutlineRenderState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    /**
     * 回放一道光柱。GL 状态与 {@code rendertype_verdict_beam} 及立即绘制路径一致，只换目标 FBO：加法混合
     * SRC_ALPHA/ONE、LEQUAL 深度、写深度、不剔除背面（玩家可能站在柱内）。{@code prepareMainTargetPass()}
     * 会把混合重置为默认，故必须自己再设一遍加法混合，否则光柱会变成半透明遮挡而不是发光。
     */
    private static void replay(Entry e, ShaderInstance shader) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 用入队时快照的相机旋转与位置重建 pose：相机旋转 × (实体世界坐标 − 相机世界坐标) × scale。
        PoseStack pose = new PoseStack();
        pose.last().pose().set(e.viewRotation());
        pose.translate(e.entityX() - e.camX(), e.entityY() - e.camY(), e.entityZ() - e.camZ());
        pose.scale(e.scaleXZ(), e.scaleY(), e.scaleXZ());

        VerdictBeamRenderer.applyUniforms(shader, e.baseRadius(), e.baseLength(),
                e.time(), e.intensity(), e.scanT(), e.lifeProgress(), e.ageSeconds(),
                pose.last().normal(), e.backCull());
        VerdictBeamRenderer.emitGeometry(shader, pose.last().pose(),
                e.baseRadius(), e.baseLength());
    }

    /** 取 4x4 的旋转部分（逐列归一化）：JOML 1.10.5 的 Matrix3f 没有 normalize()（1.10.6+ 才有）。 */
    static Matrix3f rotationOf(Matrix4f m) {
        Matrix3f r = new Matrix3f(m);
        for (int c = 0; c < 3; c++) {
            float x = r.get(c, 0), y = r.get(c, 1), z = r.get(c, 2);
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1.0e-6f) {
                r.set(c, 0, x / len);
                r.set(c, 1, y / len);
                r.set(c, 2, z / len);
            }
        }
        return r;
    }

    private record Entry(Matrix4f projection, Matrix3f viewRotation,
                         double entityX, double entityY, double entityZ,
                         double camX, double camY, double camZ,
                         float baseRadius, float baseLength,
                         float scaleXZ, float scaleY,
                         float time, float intensity, float scanT,
                         float lifeProgress, float ageSeconds,
                         float backCull) {}
}
