package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 裁决领域（地面光纹）的延后回放队列（光影包兼容）：与光柱同理，自定义着色器的贴地圆盘在光影包下会被写进 GBuffer，
 * 被 pack 当作贴地材质重新参与光照与雾，而它本是不该受光照影响的自发光图案。
 * 几何契约：调用方已在 AFTER_TRIPWIRE_BLOCKS 里 translate(-camera)、顶点喂世界坐标，故重建的 pose 是「相机旋转 × translate(−相机位置)」。
 * 矩阵必须入队时快照：回放发生在第一人称手部之后，投影矩阵可能已被换过，且 getMainCamera() 拿到的是下一帧的相机。
 */
public final class VerdictFieldLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private VerdictFieldLateRenderQueue() {}

    /** 入队一个领域；必须在渲染线程、主 pass 那一刻调用，因为要快照当时的投影矩阵与相机矩阵。 */
    public static void enqueue(Matrix4f projection, Matrix4f modelView,
                               double camX, double camY, double camZ,
                               float worldX, float groundY, float worldZ,
                               float radius, float meshRadius,
                               float time, float intensity, float pulsePhase,
                               float life, float ownerPresent) {
        ENTRIES.add(new Entry(
                new Matrix4f(projection),
                VerdictBeamLateRenderQueue.rotationOf(modelView),
                camX, camY, camZ,
                worldX, groundY, worldZ, radius, meshRadius,
                time, intensity, pulsePhase, life, ownerPresent));
    }

    /** 回放全部入队的领域；由 {@code CosmicAfterLevelMixin} 在 {@code renderLevel()} 的 TAIL 调用。 */
    public static void renderAll() {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        ShaderInstance shader = SplendidingShaders.verdictFieldShader;
        if (shader == null) {
            ENTRIES.clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f(ENTRIES.get(0).projection()),
                    VertexSorting.DISTANCE_TO_ORIGIN);

            PoseStack pose = new PoseStack();
            pose.last().pose().set(ENTRIES.get(0).viewRotation());
            pose.pushPose();
            pose.translate(-ENTRIES.get(0).camX(), -ENTRIES.get(0).camY(), -ENTRIES.get(0).camZ());
            Matrix4f matrix = pose.last().pose();

            for (Entry e : ENTRIES) {
                replay(e, shader, matrix);
            }
            pose.popPose();
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            LateOutlineRenderState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    /** 回放一个领域；GL 状态与 renderOne 的立即绘制一致，只是目标 FBO 不同。prepareMainTargetPass 会重置混合。 */
    private static void replay(Entry e, ShaderInstance shader, Matrix4f matrix) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);       // 贴地圆盘不写深度，避免挡住地面之上的东西
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        VerdictFieldRenderer.drawDeferred(shader, matrix,
                e.worldX(), e.groundY(), e.worldZ(),
                e.radius(), e.meshRadius(),
                e.time(), e.intensity(), e.pulsePhase(), e.life(), e.ownerPresent());
    }

    private record Entry(Matrix4f projection, org.joml.Matrix3f viewRotation,
                         double camX, double camY, double camZ,
                         float worldX, float groundY, float worldZ,
                         float radius, float meshRadius,
                         float time, float intensity, float pulsePhase,
                         float life, float ownerPresent) {}
}
