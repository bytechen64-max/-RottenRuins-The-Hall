package org.bytechen.hall.client.rend.backplate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 绯红誓约背板的<b>程序化几何</b>（无贴图）。
 *
 * <h3>几何刻意做得极简：一个正多边形的扇形</h3>
 * <p>因为图案全部由片元着色器按<b>形状的距离场</b>算出来（正五边形 / 六芒星 /
 * 旋转条带），几何只要提供"角度"与"半径"两个连续量即可，再细分也没有额外信息。
 * 所以这里就是一个 {@link #SIDES} 段的扇形：圆心 + 一圈边缘点，
 * 每个角段一个三角形。</p>
 *
 * <p>这样做还有一个副作用：<b>环边界与图形边界完全解耦</b>。
 * 改图案只动着色器，改不了几何；反之亦然。三轮设计迭代下来这一点很值 ——
 * 上一版的同心环台地结构（五个不同 Z 深度的环）在改成 billboard 之后
 * 就成了纯粹的浪费，而扇形结构一次都没改过。</p>
 *
 * <h3>坐标约定</h3>
 * <p>几何烘在<b>单位平面</b>里：{@code x, y ∈ [-1, 1]}，
 * 绘制时由调用方（{@link CrimsonVowBackplateRig}）缩放到实际尺寸
 * （0.52 × 0.65 格）。{@code z = 0}。</p>
 *
 * <h3>顶点只带 2 个数</h3>
 * <p>{@code POSITION_TEX} 是最省的格式，逐顶点参数编进 {@code UV0}：</p>
 * <ul>
 *   <li>{@code UV0.x = r} —— 到中心的距离（1.0 = 内切于单位方框的圆，
 *       1.414 = 方框角上）。</li>
 *   <li>{@code UV0.y = θ} —— 方位角（弧度）。</li>
 * </ul>
 *
 * <h3>为什么传裸角度</h3>
 * <p>角度在 {@code 0 / 2π} 接缝处是硬跳变，插值会跨过整个圆、在接缝那个三角形上
 * 算出假扇区。裸角度在接缝处连续，角频率交给片元的 {@code fract()} 处理。
 * 与 {@code DifficultySigilMesh} 里"相位而不是原始角度"记的是同一个坑，
 * 区别只是那边的角频率逐环不同、必须在 CPU 侧乘，这边是常量、放片元更省。</p>
 */
public final class CrimsonVowBackplateMesh {

    /** 方位角采样段数。图案是直线边，段数只影响圆的圆滑度，48 段已经看不出折线。 */
    private static final int SIDES = 48;

    /**
     * 边缘采样半径。取 {@code √2} 让扇形<b>恰好盖住</b>单位方框的四个角，
     * 于是"覆盖整个单位平面"这件事与"扇形的半径"一致，不会出现四角缺一块。
     */
    private static final float EDGE_RADIUS = 1.41421356F;

    /** 每条顶点的 float 个数：x, y, z, u, v。 */
    public static final int VERTEX_STRIDE = 5;

    /** 烘好的顶点数据（三角形列表）。 */
    private static final float[] VERTICES = bake();

    /** 顶点总数。 */
    public static final int VERTEX_COUNT = VERTICES.length / VERTEX_STRIDE;

    private CrimsonVowBackplateMesh() {}

    /**
     * 把烘好的几何写进 {@code consumer}。
     *
     * <p>法线不入栈：本材质是自发光图案，片元不用光照。调用方负责
     * {@code pushPose} / {@code popPose} 与选择 RenderType。</p>
     */
    public static void render(Matrix4f pose, VertexConsumer consumer, int light, int overlay) {
        int i = 0;
        while (i < VERTICES.length) {
            consumer.vertex(pose, VERTICES[i], VERTICES[i + 1], VERTICES[i + 2])
                    .uv(VERTICES[i + 3], VERTICES[i + 4])
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv2(light)
                    .overlayCoords(overlay)
                    .endVertex();
            i += VERTEX_STRIDE;
        }
    }

    /**
     * 烘出顶点数组。
     *
     * <p>绕序固定为「圆心 → 上一段边缘 → 本段边缘」，在单位平面里是逆时针。
     * RenderType 关掉了背面剔除，所以绕序不影响可见性 —— 这里固定它只是为了让
     * 几何本身是自洽的（万一以后要开剔除，不需要回头查）。</p>
     */
    private static float[] bake() {
        float[] data = new float[SIDES * 3 * VERTEX_STRIDE];
        int p = 0;

        for (int s = 0; s < SIDES; s++) {
            // 裸方位角。接缝处连续 —— 见类注释。
            float thetaA = (float) (s * (Math.PI * 2.0) / SIDES);
            float thetaB = (float) ((s + 1) * (Math.PI * 2.0) / SIDES);

            // ① 圆心：r = 0，角度取本段起点（圆心处角度无意义，取连续值即可）
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = thetaA;

            // ② 边缘 A
            data[p++] = Mth.cos(thetaA) * EDGE_RADIUS;
            data[p++] = Mth.sin(thetaA) * EDGE_RADIUS;
            data[p++] = 0.0F;
            data[p++] = EDGE_RADIUS;
            data[p++] = thetaA;

            // ③ 边缘 B
            data[p++] = Mth.cos(thetaB) * EDGE_RADIUS;
            data[p++] = Mth.sin(thetaB) * EDGE_RADIUS;
            data[p++] = 0.0F;
            data[p++] = EDGE_RADIUS;
            data[p++] = thetaB;
        }

        return data;
    }
}
