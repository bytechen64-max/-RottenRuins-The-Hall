package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 裁决领域（地面光纹）的<b>延后回放队列</b>（光影包兼容）。
 *
 * <h3>为什么需要它</h3>
 * <p>与光柱同一个原因：自定义着色器的贴地圆盘在光影包下会被写进 <b>GBuffer</b>，
 * 被 pack 当成一块"贴在地面的材质"重新参与光照与雾，颜色与亮度全部失控 ——
 * 而地面光纹本来就是不应该被光照影响的自发光图案。</p>
 *
 * <h3>与光柱那条队列的唯一区别：几何喂世界坐标</h3>
 * <p>{@code VerdictFieldRenderHandler} 在
 * {@code RenderLevelStageEvent.AFTER_TRIPWIRE_BLOCKS} 里调用本渲染器时，
 * 自己 push 了一层并 {@code translate(-camera)}，顶点喂的是<b>世界坐标</b>。
 * 回放沿用同一套契约，所以重建的 pose 是
 * {@code 相机旋转 × translate(−相机位置)}。</p>
 *
 * <h3>⚠️ 矩阵必须快照</h3>
 * <p>与 {@link VerdictBeamLateRenderQueue} 同一条理由（那条类的注释里写了完整推导）：
 * 回放发生在第一人称手部渲染之后，那时投影矩阵可能已经被换过；
 * 而且 {@code getMainCamera()} 拿到的会是<b>下一帧</b>的相机。
 * 所以入队时快照投影矩阵、相机旋转与相机位置，回放时原样使用。</p>
 */
public final class VerdictFieldLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private VerdictFieldLateRenderQueue() {}

    /**
     * 入队一个领域。<b>必须在渲染线程、主 pass 的那一刻调用</b>，
     * 因为要快照当时的投影矩阵与相机矩阵。
     */
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

    /**
     * 回放全部入队的领域。由 {@code CosmicAfterLevelMixin} 在
     * {@code renderLevel()} 的 TAIL 调用。
     */
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

    /**
     * 回放一个领域。
     *
     * <p>GL 状态与 {@code VerdictFieldRenderer.renderOne} 的立即绘制保持一致，
     * 只有目标 FBO 不同。{@code prepareMainTargetPass()} 会把混合重置成默认值，
     * 所以加法混合要在这里再设一遍。</p>
     */
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
