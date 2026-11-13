package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 绯红誓约「起誓」系特效的共用零件：圆盘/圆柱几何、uniform 写入、视基换算。
 * 视基取 pose 法线矩阵的旋转部分 R（pose 为刚体变换，R 的三行即视空间三轴的世界方向），
 * 不用 ModelViewMat —— 原版世界渲染路径从不更新它，用它做旋转会整块不可见。
 * uniform 一律判空：被 GLSL 优化掉的 uniform 在 getUniform() 上返回 null。
 */
public final class CrimsonVowRenderSupport {

    private CrimsonVowRenderSupport() {}

    /** 网格半径 / 逻辑半径（多出来的一圈留给 halo）。 */
    public static final float HALO_RATIO = 1.18f;

    public static final int DISC_SEGMENTS = 96;
    public static final int DISC_RINGS = 6;
    public static final int CYL_SEGMENTS = 56;

    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** 亮粉 0xFFFF4FB8。 */
    public static final float[] COLOR_A = { 1.000f, 0.310f, 0.722f };
    /** 紫 0xFF9B4DFF。 */
    public static final float[] COLOR_B = { 0.608f, 0.302f, 1.000f };
    /** 雾粉紫 0xFFE8A6FF。 */
    public static final float[] COLOR_C = { 0.910f, 0.651f, 1.000f };

    //  视基

    /** 从"已经 translate 过 -camera 的 pose"里取三个视基向量，写入 out：Right / Up / Fwd（长度 ≥ 9）。 */
    public static void viewBasis(PoseStack.Pose pose, float[] out) {
        Matrix3f r = pose.normal();
        // JOML 的命名是 m<列><行>，所以 R 的第 0 行是 (m00, m10, m20)。
        out[0] = r.m00(); out[1] = r.m10(); out[2] = r.m20();
        out[3] = r.m01(); out[4] = r.m11(); out[5] = r.m21();
        out[6] = r.m02(); out[7] = r.m12(); out[8] = r.m22();
    }

    /** 世界方向 {@code (0,1,0)} 在视空间下的表示（= R 的第 1 列）。 */
    public static void worldUpInView(PoseStack.Pose pose, float[] out3) {
        Matrix3f r = pose.normal();
        out3[0] = r.m10(); out3[1] = r.m11(); out3[2] = r.m12();
    }

    /** 世界点 → 视空间点（用 {@link #viewBasis} 给出的三个基向量）。 */
    public static void worldToView(float[] basis, double wx, double wy, double wz,
                                   double camX, double camY, double camZ, float[] out3) {
        float dx = (float) (wx - camX), dy = (float) (wy - camY), dz = (float) (wz - camZ);
        out3[0] = basis[0] * dx + basis[1] * dy + basis[2] * dz;
        out3[1] = basis[3] * dx + basis[4] * dy + basis[5] * dz;
        out3[2] = basis[6] * dx + basis[7] * dy + basis[8] * dz;
    }

    public static void setUniform(ShaderInstance sh, String name, float v) {
        if (sh == null) return;
        var u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    public static void setUniform2(ShaderInstance sh, String name, float a, float b) {
        if (sh == null) return;
        var u = sh.getUniform(name);
        if (u != null) u.set(a, b);
    }

    public static void setUniform3(ShaderInstance sh, String name, float a, float b, float c) {
        if (sh == null) return;
        var u = sh.getUniform(name);
        if (u != null) u.set(a, b, c);
    }

    public static void setUniform3(ShaderInstance sh, String name, float[] v) {
        setUniform3(sh, name, v[0], v[1], v[2]);
    }

    /** 写入 {@code uColorA/uColorB/uColorC} 三个色板 uniform（三个着色器共用）。 */
    public static void setPalette(ShaderInstance sh) {
        setUniform3(sh, "uColorA", COLOR_A);
        setUniform3(sh, "uColorB", COLOR_B);
        setUniform3(sh, "uColorC", COLOR_C);
    }

    /**
     * 发一个水平圆盘（三角扇网格）。Color.rg = (径向归一化, 角度归一化)，Normal.xy 同值。
     * ⚠ 最后一个扇区的 a1 必须是 1.0（不是 0.0）：片元的 cos(a*TAU) 依赖 a 单调递增，写 0.0 会留下一道修不掉的接缝裂缝。
     */
    public static void emitDisc(BufferBuilder buffer, Matrix4f mat,
                                float cx, float cy, float cz, float meshRadius) {
        for (int ring = 0; ring < DISC_RINGS; ring++) {
            float r0 = ring / (float) DISC_RINGS;
            float r1 = (ring + 1) / (float) DISC_RINGS;
            for (int seg = 0; seg < DISC_SEGMENTS; seg++) {
                float a0 = seg / (float) DISC_SEGMENTS;
                float a1 = (seg + 1) / (float) DISC_SEGMENTS;
                discVertex(buffer, mat, cx, cy, cz, r0, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a1, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r0, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a1, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r0, a1, meshRadius);
            }
        }
    }

    private static void discVertex(BufferBuilder buffer, Matrix4f mat,
                                   float cx, float cy, float cz,
                                   float rNorm, float aNorm, float meshRadius) {
        float angle = aNorm * TWO_PI;
        float x = cx + Mth.cos(angle) * rNorm * meshRadius;
        float z = cz + Mth.sin(angle) * rNorm * meshRadius;
        buffer.vertex(mat, x, cy, z)
                .color(rNorm, aNorm, 0f, 1f)
                .normal(rNorm, aNorm, 0f)
                .endVertex();
    }

    /**
     * 发一个开口（可带锥度）的圆柱侧面，沿高度分 rings 环 —— 分环是轮廓波动的前提。
     * Normal.x = 顶点的世界 Y（格），Color.g = 角度归一化；端盖刻意不发（上下边界靠着色器软化）。
     * ⚠ 高度不能放 Color.r：POSITION_COLOR_NORMAL 的 Color 是 4 个 ubyte（值×255 后截断），
     *    世界高度 34 写进去读回恒为 0.87，螺旋与光斑全丢；Normal 是 float，所以高度走 Normal.x。
     * baseY / topY 是<b>世界</b> y（不是相对坐标）；rings ≥ 1，1 退化成直筒四边形带。
     */
    public static void emitCylinder(BufferBuilder buffer, Matrix4f mat,
                                    float cx, float baseY, float cz,
                                    float baseRadius, float topRadius, float topY,
                                    int rings) {
        int rn = Math.max(1, rings);
        for (int ring = 0; ring < rn; ring++) {
            float t0 = ring / (float) rn;
            float t1 = (ring + 1) / (float) rn;
            float r0 = Mth.lerp(t0, baseRadius, topRadius);
            float r1 = Mth.lerp(t1, baseRadius, topRadius);
            float y0 = Mth.lerp(t0, baseY, topY);
            float y1 = Mth.lerp(t1, baseY, topY);

            for (int seg = 0; seg < CYL_SEGMENTS; seg++) {
                float a0 = seg / (float) CYL_SEGMENTS;
                float a1 = (seg + 1) / (float) CYL_SEGMENTS;
                float ang0 = a0 * TWO_PI;
                float ang1 = a1 * TWO_PI;
                float x00 = cx + Mth.cos(ang0) * r0;
                float z00 = cz + Mth.sin(ang0) * r0;
                float x01 = cx + Mth.cos(ang1) * r0;
                float z01 = cz + Mth.sin(ang1) * r0;
                float x10 = cx + Mth.cos(ang0) * r1;
                float z10 = cz + Mth.sin(ang0) * r1;
                float x11 = cx + Mth.cos(ang1) * r1;
                float z11 = cz + Mth.sin(ang1) * r1;

                // 环从下往上发，每段顶点也要"先下后上"：绕序反了法线朝向会翻，开剔除时半根柱子就没了。
                cylVertex(buffer, mat, x00, y0, z00, y0, a0);
                cylVertex(buffer, mat, x01, y0, z01, y0, a1);
                cylVertex(buffer, mat, x11, y1, z11, y1, a1);

                cylVertex(buffer, mat, x00, y0, z00, y0, a0);
                cylVertex(buffer, mat, x11, y1, z11, y1, a1);
                cylVertex(buffer, mat, x10, y1, z10, y1, a0);
            }
        }
    }

    /**
     * 画一段**任意朝向**的圆管（斜落下的陨石拖尾）。与 {@link #emitCylinder} 只差顶点里的沿轴参数：
     * 这里塞的是 0..1 的轴向 t（片元传 uHeadY=0 / uSpan=1），所以同一支着色器也能画竖直火柱。
     */
    public static void emitTube(BufferBuilder buffer, Matrix4f mat,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float r0, float r1, int rings) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float alen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (alen < 1.0e-4f) return;
        ax /= alen; ay /= alen; az /= alen;

        // 参考向量：轴接近竖直时换一个，否则叉乘退化成零向量
        float rx = 0f, ry = 1f, rz = 0f;
        if (Math.abs(ay) > 0.99f) { rx = 1f; ry = 0f; }
        float ux = ay * rz - az * ry;
        float uy = az * rx - ax * rz;
        float uz = ax * ry - ay * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0e-4f) return;
        ux /= ul; uy /= ul; uz /= ul;
        float vx = ay * uz - az * uy;
        float vy = az * ux - ax * uz;
        float vz = ax * uy - ay * ux;

        int rn = Math.max(1, rings);
        for (int ring = 0; ring < rn; ring++) {
            float t0 = ring / (float) rn;
            float t1 = (ring + 1) / (float) rn;
            float rr0 = Mth.lerp(t0, r0, r1);
            float rr1 = Mth.lerp(t1, r0, r1);
            float cx0 = x0 + ax * alen * t0, cy0 = y0 + ay * alen * t0, cz0 = z0 + az * alen * t0;
            float cx1 = x0 + ax * alen * t1, cy1 = y0 + ay * alen * t1, cz1 = z0 + az * alen * t1;
            for (int seg = 0; seg < CYL_SEGMENTS; seg++) {
                float a0 = seg / (float) CYL_SEGMENTS;
                float a1 = (seg + 1) / (float) CYL_SEGMENTS;
                float ang0 = a0 * TWO_PI;
                float ang1 = a1 * TWO_PI;
                float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0);
                float c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);
                float x00 = cx0 + (ux * c0 + vx * s0) * rr0;
                float y00 = cy0 + (uy * c0 + vy * s0) * rr0;
                float z00 = cz0 + (uz * c0 + vz * s0) * rr0;
                float x01 = cx0 + (ux * c1 + vx * s1) * rr0;
                float y01 = cy0 + (uy * c1 + vy * s1) * rr0;
                float z01 = cz0 + (uz * c1 + vz * s1) * rr0;
                float x10 = cx1 + (ux * c0 + vx * s0) * rr1;
                float y10 = cy1 + (uy * c0 + vy * s0) * rr1;
                float z10 = cz1 + (uz * c0 + vz * s0) * rr1;
                float x11 = cx1 + (ux * c1 + vx * s1) * rr1;
                float y11 = cy1 + (uy * c1 + vy * s1) * rr1;
                float z11 = cz1 + (uz * c1 + vz * s1) * rr1;

                tubeVertex(buffer, mat, x00, y00, z00, t0, a0);
                tubeVertex(buffer, mat, x01, y01, z01, t0, a1);
                tubeVertex(buffer, mat, x11, y11, z11, t1, a1);
                tubeVertex(buffer, mat, x00, y00, z00, t0, a0);
                tubeVertex(buffer, mat, x11, y11, z11, t1, a1);
                tubeVertex(buffer, mat, x10, y10, z10, t1, a0);
            }
        }
    }

    /** 圆管顶点：Normal.x 放轴向参数 t（0 头 → 1 尾）。 */
    private static void tubeVertex(BufferBuilder buffer, Matrix4f mat,
                                   float x, float y, float z, float t, float aNorm) {
        buffer.vertex(mat, x, y, z)
                .color(0f, aNorm, 0f, 1f)
                .normal(t, aNorm, 0f)
                .endVertex();
    }

    private static void cylVertex(BufferBuilder buffer, Matrix4f mat,
                                  float x, float y, float z, float worldY, float aNorm) {
        buffer.vertex(mat, x, y, z)
                // Color.r 留 0（ubyte 装不下世界高度，见上）；片元只读 Color.g 当角度。Normal.x = 世界 Y。
                .color(0f, aNorm, 0f, 1f)
                .normal(worldY, aNorm, 0f)
                .endVertex();
    }

    /**
     * 发一个世界空间 AABB 的全部 12 个三角形。调用方关深度测试、剔正面，
     * 于是每个被覆盖的像素恰好得到一个片元，遮挡由片元里的 SDF 步进 + 深度纹理比较完成。
     */
    public static void emitBox(BufferBuilder buffer, Matrix4f mat,
                               float cx, float cy, float cz, float size) {
        float x0 = cx - size, x1 = cx + size;
        float y0 = cy - size, y1 = cy + size;
        float z0 = cz - size, z1 = cz + size;

        quad(buffer, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(buffer, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        quad(buffer, mat, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(buffer, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, mat, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
        quad(buffer, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
    }

    private static void quad(BufferBuilder b, Matrix4f mat,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz) {
        boxVertex(b, mat, ax, ay, az);
        boxVertex(b, mat, bx, by, bz);
        boxVertex(b, mat, cx, cy, cz);
        boxVertex(b, mat, ax, ay, az);
        boxVertex(b, mat, cx, cy, cz);
        boxVertex(b, mat, dx, dy, dz);
    }

    private static void boxVertex(BufferBuilder b, Matrix4f mat, float x, float y, float z) {
        b.vertex(mat, x, y, z).color(1f, 1f, 1f, 1f).normal(0f, 1f, 0f).endVertex();
    }

    /**
     * 求 {@code (x, z)} 处、从 {@code baseY} 向下第一块可站立表面的顶面高度（用碰撞盒顶面而非 by+1，
     * 台阶/栅栏/雪层才贴得准）；找不到（悬空/未加载）时返回 {@code baseY}，保证特效永远画得出来。
     */
    public static float groundYAt(Level level, double x, double baseY, double z) {
        if (level == null) return (float) baseY;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int from = Mth.floor(baseY) + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = from; y >= from - 6; y--) {
            pos.set(bx, y, bz);
            if (!level.hasChunkAt(pos)) break;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            return (float) (y + shape.max(Direction.Axis.Y));
        }
        return (float) baseY;
    }
}
