package org.bytechen.rottenruins.client.rend.backplate;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 绯红誓约背板的程序化几何：{@link #SIDES} 段扇形，图案由片元着色器按极坐标现算（单位平面，z=0）。
 * 顶点只带 (x,y) 写进 UV0；不要改传 (r,θ)——圆心角度逐三角形不同，会把依赖 θ 的图案撕成花瓣。
 */
public final class CrimsonVowBackplateMesh {

    /** 方位角采样段数；图案是直线边，48 段已看不出折线。 */
    private static final int SIDES = 48;

    /** 边缘半径 √2：让扇形恰好盖住单位方框的四个角。 */
    private static final float EDGE_RADIUS = 1.41421356F;

    public static final int VERTEX_STRIDE = 5;

    private static final float[] VERTICES = bake();

    public static final int VERTEX_COUNT = VERTICES.length / VERTEX_STRIDE;

    private CrimsonVowBackplateMesh() {}

    /** 法线不入栈（自发光图案，片元不用光照）；push/popPose 与 RenderType 由调用方负责。 */
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

    /** 绕序固定为「圆心 → 上一段边缘 → 本段边缘」；RenderType 关了背面剔除，仅为自洽。 */
    private static float[] bake() {
        float[] data = new float[SIDES * 3 * VERTEX_STRIDE];
        int p = 0;

        for (int s = 0; s < SIDES; s++) {
            float thetaA = (float) (s * (Math.PI * 2.0) / SIDES);
            float thetaB = (float) ((s + 1) * (Math.PI * 2.0) / SIDES);

            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;
            data[p++] = 0.0F;

            float ax = Mth.cos(thetaA) * EDGE_RADIUS;
            float ay = Mth.sin(thetaA) * EDGE_RADIUS;
            data[p++] = ax;
            data[p++] = ay;
            data[p++] = 0.0F;
            data[p++] = ax;
            data[p++] = ay;

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
