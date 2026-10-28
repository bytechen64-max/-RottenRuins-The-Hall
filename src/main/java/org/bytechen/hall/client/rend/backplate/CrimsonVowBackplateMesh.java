package org.bytechen.hall.client.rend.backplate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 绯红誓约背板的<b>程序化几何</b>（无贴图）。
 *
 * <h3>几何刻意做得极简：一个扇形</h3>
 * <p>图案全部由片元着色器按极坐标现算（血月盘 / 誓约符文环 / 冠冕射线 /
 * 心誓脉冲），几何只要提供"板上每个点的平面坐标"即可，再细分也没有额外信息。
 * 所以这里就是一个 {@link #SIDES} 段的扇形：圆心 + 一圈边缘点，
 * 每个角段一个三角形。</p>
 *
 * <p>这样做还有一个副作用：<b>图案边界与几何边界完全解耦</b>。
 * 改图案只动着色器，改不了几何。从最早的三层同心环到现在的血月纹章，
 * 扇形结构一次都没改过。</p>
 *
 * <h3>坐标约定</h3>
 * <p>几何烘在<b>单位平面</b>里：{@code x, y ∈ [-1.414, 1.414]}，
 * {@code z = 0}。绘制时由调用方（{@link CrimsonVowBackplateRig}）按
 * {@code config Scale ÷ SHADER_VISIBLE_RADIUS} 缩放到实际尺寸
 * （默认 1.5 ÷ 0.86 ≈ 1.74 倍，即圆心到可见外缘 1.5 格）。</p>
 *
 * <h3>顶点只带 2 个数：单位平面局部坐标 (x, y)</h3>
 * <p>{@code POSITION_TEX} 是最省的格式，逐顶点参数编进 {@code UV0}：
 * {@code UV0 = (x, y)}，与 {@code Position.xy} 同值。片元里
 * {@code r = length(UV0)}、{@code θ = atan(UV0.y, UV0.x)} 现算。</p>
 *
 * <h3>⚠ 为什么不再直接传 (r, θ)</h3>
 * <p>早先的版本把 {@code UV0 = (r, θ)} 直接插值给片元。但扇形三角形的三个
 * 顶点里，<b>圆心那一点的角度是逐三角形各不相同的</b>（取的是该段起点），
 * 于是每条三角形边界上两侧的 θ 插值结果不相等 —— 48 段就有 48 条角度跳变，
 * 所有依赖 θ 的图案（符文带、刻度环、冠冕射线）都会被撕成"花瓣"。
 * 位置是线性插值正确的量，角度必须由位置在片元里现算。
 * 这与 {@code DifficultySigilMesh} 里"顶点只带相位、角度在片元算"是同一条教训，
 * 只是那边踩的是 {@code 0 / 2π} 接缝，这边踩的是三角形内部的角度扭曲。</p>
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
     *
     * <p>UV0 写的是<b>顶点自己的平面坐标</b>：圆心 (0, 0)、边缘 (x, y)。
     * 不要改成 (r, θ) —— 理由见类注释的警告。</p>
     */
    private static float[] bake() {
        float[] data = new float[SIDES * 3 * VERTEX_STRIDE];
        int p = 0;

        for (int s = 0; s < SIDES; s++) {
            float thetaA = (float) (s * (Math.PI * 2.0) / SIDES);
            float thetaB = (float) ((s + 1) * (Math.PI * 2.0) / SIDES);

            // ① 圆心：(x, y, z) = (0, 0, 0)，UV0 = (0, 0)
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;

            // ② 边缘 A：位置与 UV0 相同
            float ax = Mth.cos(thetaA) * EDGE_RADIUS;
            float ay = Mth.sin(thetaA) * EDGE_RADIUS;
            data[p++] = ax;
            data[p++] = ay;
            data[p++] = 0.0F;
            data[p++] = ax;
            data[p++] = ay;

            // ③ 边缘 B
            float bx = Mth.cos(thetaB) * EDGE_RADIUS;
            float by = Mth.sin(thetaB) * EDGE_RADIUS;
            data[p++] = bx;
            data[p++] = by;
            data[p++] = 0.0F;
            data[p++] = bx;
            data[p++] = by;
        }

        return data;
    }
}
