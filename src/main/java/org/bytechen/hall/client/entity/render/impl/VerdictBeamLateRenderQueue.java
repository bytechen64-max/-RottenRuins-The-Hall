package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 天穹裁决光柱的<b>延后回放队列</b>（光影包兼容）。
 *
 * <h3>为什么需要它</h3>
 * <p>光柱是自定义着色器的自发光体积。光影包（Oculus/Iris）激活时，
 * 世界 pass 里画的东西落在 <b>GBuffer</b> 上，而 GBuffer 的语义是
 * "这个表面的反照率/法线/受光参数" —— 我们的自发光柱会被 pack 当成一块
 * <b>材质</b>重新参与光照与雾，颜色、亮度、混合全部不受控。</p>
 *
 * <p>正确做法是本项目既有那条约定（见 {@link CollapsarHaloLateRenderQueue}
 * 与 {@code docs/shader-pack-compat.md}）：pack 激活时不画，只<b>入队</b>；
 * 等 pack 把最终场景合成到主帧缓冲之后（{@code GameRenderer.renderLevel()} 的 TAIL），
 * 在 {@code MAIN_TARGET} 上回放 —— 那条路径绕开 GBuffer，直接叠到合成好的画面上。</p>
 *
 * <h3>⚠️ 必须快照矩阵，不能在回放时回读 RenderSystem</h3>
 * <p>这是本队列第一版写错的地方，{@link CollapsarHaloLateRenderQueue} 的注释里
 * 早就写明了原因：</p>
 * <blockquote>
 *   投影矩阵也一起快照：回放时必须用<b>当时</b>的投影矩阵才能对上。
 *   第一人称手部渲染会临时换成手部 FOV 的投影，回放时机在它之后，
 *   不重新设一次就有可能整体错位/看不见。
 * </blockquote>
 * <p>我第一版不但没有快照投影，还在回放时重新调
 * {@code gameRenderer.getMainCamera().getPosition()} 取相机位置 ——
 * 那时拿到的已经是<b>下一帧</b>的相机，实体几何用的却是这一帧的坐标，
 * 两者一旦有偏差就会整体错位；相机移动快时甚至直接被裁掉看不见。
 * 这正是"按 F1 就不显示"这类时好时坏的来源。</p>
 *
 * <p>所以现在入队时一次性快照：<b>投影矩阵 + 相机旋转（model-view 的旋转部分）
 * + 相机世界位置</b>，回放时用它们原样重建 pose。</p>
 */
public final class VerdictBeamLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private VerdictBeamLateRenderQueue() {}

    /**
     * 入队一道光柱。<b>必须在渲染线程、实体渲染的那一刻调用</b>，
     * 因为要快照当时的投影矩阵与相机矩阵。
     */
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

    /**
     * 回放全部入队的光柱。由 {@code CosmicAfterLevelMixin} 在
     * {@code renderLevel()} 的 TAIL 调用 —— 那时 pack 已经合成完最终场景。
     */
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
            // 同一帧内所有光柱共享入队时的那份投影（同一帧不会变），设一次即可。
            // 与 CollapsarHaloLateRenderQueue 完全同款做法。
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
     * 回放一道光柱。
     *
     * <p>GL 状态刻意与 {@code rendertype_verdict_beam} 的声明、以及立即绘制那条路径
     * 保持一致 —— 唯一不同的是"写哪个 FBO"：</p>
     * <ul>
     *   <li>加法混合 {@code SRC_ALPHA / ONE}（自发光体，叠亮度）；</li>
     *   <li>{@code LEQUAL} 深度测试（对已合成的场景做遮挡）、写深度；</li>
     *   <li>不剔除背面（玩家可能站在柱内）。</li>
     * </ul>
     * <p>{@code prepareMainTargetPass()} 会把混合重置成默认的
     * {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}，所以这里必须<b>自己再设一遍</b>
     * 加法混合，否则光柱会变成半透明遮挡而不是发光。</p>
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

        // 用<b>入队时快照的</b>相机旋转与相机位置重建 pose，语义与实体渲染路径一致：
        //   相机旋转 × translate(实体世界坐标 − 相机世界坐标) × scale(扩散)
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

    /**
     * 取一个 4x4 矩阵的<b>旋转部分</b>（3x3，列已归一化）。
     *
     * <p>JOML 1.10.5 的 {@code Matrix3f} <b>没有</b> {@code normalize()} 方法
     * （1.10.6+ 才有），所以这里逐列归一化 —— 把缩放从各列除掉，只留方向。</p>
     */
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
