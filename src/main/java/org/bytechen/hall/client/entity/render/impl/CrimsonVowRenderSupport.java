package org.bytechen.hall.client.entity.render.impl;

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
 * 绯红誓约「起誓」系特效的<b>共用零件</b>。
 *
 * <h3>为什么要有这一层</h3>
 * <p>蓄力法阵、巨型法阵、终末光柱这三样东西共用：</p>
 * <ul>
 *   <li>同一个圆盘/圆柱的几何生成器（只差"喂哪几个 uniform"）；</li>
 *   <li>同一套 uniform 写入工具（都要判空 —— 被 GLSL 优化掉的 uniform
 *       {@code getUniform()} 会返回 null，直接 set 就是 NPE）；</li>
 *   <li>同一套<b>视基</b>（把世界方向与视空间方向互相转换的三个向量）。</li>
 * </ul>
 *
 * <h3>视基：为什么不直接用 ModelViewMat</h3>
 * <p>本项目的世界空间特效都遵守同一条契约：Java 侧把
 * {@code translate(-camera)} 折进 pose，用 {@code pose.last().pose()} 变换
 * 顶点，于是顶点着色器里的 {@code Position} 已经是<b>视空间</b>坐标。
 * 片元阶段要从它反解"这条视线在世界空间指向哪"。</p>
 *
 * <p>这里刻意<b>不</b>依赖 Minecraft 自动绑定的 {@code ModelViewMat}：
 * 那个值是 {@code RenderSystem.getModelViewMatrix()} 的快照，而原版在整条
 * 世界渲染路径（地形 / 实体 / {@code AFTER_TRIPWIRE_BLOCKS}）上都没有更新
 * 过它 —— 它保留的是上一帧 GUI 那趟留下的近单位阵。用它做旋转会得到
 * "某些机器看起来能跑、另一些机器整块不可见"这种最难查的症状。</p>
 *
 * <p>于是这里从 {@code PoseStack} 的法线矩阵里直接取旋转部分 {@code R}
 * （pose 是刚体变换，法线矩阵就等于 R），再算出<b>视空间三个轴在世界空间里
 * 的方向</b> —— 也就是 R 的三行：</p>
 * <pre>
 *   uViewRight = (m00, m10, m20)   视空间 +X 在世界空间的方向
 *   uViewUp    = (m01, m11, m21)   视空间 +Y
 *   uViewFwd   = (m02, m12, m22)   视空间 +Z
 * </pre>
 * <p>有了这三个向量，两个方向就都通了（{@code Rᵀ·e_x = uViewRight}）：</p>
 * <pre>
 *   视空间方向 → 世界方向：  uViewRight*d.x + uViewUp*d.y + uViewFwd*d.z
 *   世界点     → 视空间：   (dot(uViewRight,d), dot(uViewUp,d), dot(uViewFwd,d))
 * </pre>
 * <p>两者用的是同一组基，所以不可能出现"方向对了、深度错一格"的错位。</p>
 */
public final class CrimsonVowRenderSupport {

    private CrimsonVowRenderSupport() {}

    /** 网格半径 / 逻辑半径（多出来的一圈留给 halo）。 */
    public static final float HALO_RATIO = 1.18f;

    /** 圆盘分段数。 */
    public static final int DISC_SEGMENTS = 96;
    /** 圆盘同心环数。 */
    public static final int DISC_RINGS = 6;
    /** 圆柱侧面分段数。 */
    public static final int CYL_SEGMENTS = 56;

    private static final float TWO_PI = (float) (2.0 * Math.PI);

    // ── 色板：与 CrimsonVow 的物品描边/名字同一套 ──
    /** 亮粉 0xFFFF4FB8。 */
    public static final float[] COLOR_A = { 1.000f, 0.310f, 0.722f };
    /** 紫 0xFF9B4DFF。 */
    public static final float[] COLOR_B = { 0.608f, 0.302f, 1.000f };
    /** 雾粉紫 0xFFE8A6FF。 */
    public static final float[] COLOR_C = { 0.910f, 0.651f, 1.000f };

    // ══════════════════════════════════════════════════════════════
    //  视基
    // ══════════════════════════════════════════════════════════════

    /**
     * 从"已经 translate 过 -camera 的 pose"里取出三个视基向量。
     *
     * @param pose   pose 栈顶（世界 → 视空间）
     * @param out    长度 ≥ 9 的数组，依次写入 Right / Up / Fwd
     */
    public static void viewBasis(PoseStack.Pose pose, float[] out) {
        Matrix3f r = pose.normal();
        // JOML 的命名是 m<列><行>，所以 R 的第 0 行是 (m00, m10, m20)。
        out[0] = r.m00(); out[1] = r.m10(); out[2] = r.m20();
        out[3] = r.m01(); out[4] = r.m11(); out[5] = r.m21();
        out[6] = r.m02(); out[7] = r.m12(); out[8] = r.m22();
    }

    /** 世界方向 {@code (0,1,0)} 在视空间下的表示（= R·e_y = R 的第 1 列）。 */
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

    // ══════════════════════════════════════════════════════════════
    //  uniform 工具（一律判空 —— 见类注释）
    // ══════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════
    //  几何
    // ══════════════════════════════════════════════════════════════

    /**
     * 发一个水平圆盘（三角扇网格）。
     *
     * <p>属性打包：{@code Color.rg = (径向归一化, 角度归一化)}，
     * {@code Normal.xy} 同值。片元只需要 r/g，Normal 留着是为了让
     * 顶点着色器把同一份数据同时暴露成 vLocal 之外的通道（便于以后按径向做
     * 方向性东西时不用改顶点格式）。</p>
     *
     * <p>⚠️ 最后一个扇区的 {@code a1} 必须是 <b>1.0</b>（而不是 0.0）——
     * 片元里的 {@code cos(a*TAU)} 依赖 a 在整圈上单调递增。写 0.0 会在
     * 接缝处出现一道永远修不掉的裂缝。</p>
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
     * 发一个开口的（可带锥度的）圆柱侧面，沿高度分成若干环。
     *
     * <h3>属性打包（改过一次，原因很重要）</h3>
     * <ul>
     *   <li>{@code Normal.x = 顶点的世界 Y（格）} —— 光柱图案的唯一标尺；</li>
     *   <li>{@code Color.g = 角度归一化} —— 片元里用来还原柱面法线与螺旋相位。</li>
     * </ul>
     * <p>端盖刻意不发：柱体的上下边界本来就该是软的（着色器里靠前缘光斑与
     * 尾端淡出收口），加端盖只会得到两个硬边圆盘。</p>
     *
     * <p>⚠️ 高度<b>不能</b>放在 {@code Color.r} 里。{@code POSITION_COLOR_NORMAL}
     * 的 Color 是 4 个 <b>ubyte</b>：写进去的值先乘 255 再截成字节。第一版把
     * "柱长 34 格"写进 Color.r，存进去是 {@code (byte)(34*255) = 222}，读回来
     * 0.8706 —— 一根柱子上所有顶点<b>同一个值</b>，片元里的 hN 恒为 0.87，
     * 六条螺旋只剩一条、柱头光斑与枪口爆闪全灭，画面上是一根均匀发亮的管子。
     * 离屏台子把 Color.r 直接输出成颜色时整根柱子是同一个灰值，一眼确认。</p>
     *
     * <p>Normal 是 3 个 <b>float</b>，0..100+ 格都精确，所以世界高度走 Normal.x。</p>
     *
     * <h3>为什么要 rings 分环</h3>
     * <p>轮廓波动是<b>顶点阶段</b>把顶点沿半径推开来实现的。只有上下两条棱边的话
     * 侧影仍然是直的（两个端点连一条线），推不出任何起伏。分环之后侧面才有
     * 折点，波动才看得见 —— 这也正是本方法存在的理由。</p>
     *
     * <h3>为什么底半径与顶半径分开给</h3>
     * <p>"上细下粗"的锥度必须由几何给出，不能只靠片元里按高度衰减亮度：
     * 那样轮廓仍是直筒，只是上边暗一点。分开给之后，顶点阶段按环号直接插值，
     * 是一根真的锥柱。顶半径给 0.01 就是一根锥（光柱底部的"枪尖"）。</p>
     *
     * @param baseY     底面中心的<b>世界</b> y（不是相对坐标）
     * @param topY      顶面中心的<b>世界</b> y
     * @param baseRadius 底面半径（格）
     * @param topRadius  顶面半径（格）
     * @param rings     沿高度的分段数（≥1；1 就退化成原来那种直筒四边形带）
     */
    public static void emitCylinder(BufferBuilder buffer, Matrix4f mat,
                                    float cx, float baseY, float cz,
                                    float baseRadius, float topRadius, float topY,
                                    int rings) {
        int rn = Math.max(1, rings);
        for (int ring = 0; ring < rn; ring++) {
            // t0/t1 是这一环的上下参数；半径与高度都按它线性插值。
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

                // ⚠️ 绕序必须与高度一起保持一致：环是从下往上发的，
                //    所以每段的三个顶点也要"先下后上"，否则法线朝向会翻，
                //    开着剔除时半根柱子就没了。
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
     * 画一段**任意朝向**的圆管（斜着落下来的陨石拖尾）。
     *
     * <p>和 {@link #emitCylinder} 的区别只在顶点里那个"沿轴参数"：
     * 竖直圆柱塞的是世界 Y，这里塞的是 <b>0..1 的轴向参数 t</b>
     * （片元里把 {@code uHeadY=0, uSpan=1} 传进去，`rel` 就直接是 t）。
     * 于是同一支拖尾着色器既能画竖直的火柱，也能画斜的尾迹。</p>
     *
     * <p>轴的正交基：先拿一个不平行的参考向量叉乘得到 u，再 u×axis 得到 v。
     * 选参考向量时要避开与轴平行的情况，否则叉乘退化成零向量。</p>
     */
    public static void emitTube(BufferBuilder buffer, Matrix4f mat,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float r0, float r1, int rings) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float alen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (alen < 1.0e-4f) return;
        ax /= alen; ay /= alen; az /= alen;

        // 参考向量：轴接近竖直时换一个，避免叉乘退化
        float rx = 0f, ry = 1f, rz = 0f;
        if (Math.abs(ay) > 0.99f) { rx = 1f; ry = 0f; }
        // u = normalize(axis × ref)
        float ux = ay * rz - az * ry;
        float uy = az * rx - ax * rz;
        float uz = ax * ry - ay * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0e-4f) return;
        ux /= ul; uy /= ul; uz /= ul;
        // v = axis × u
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

    /** 圆管顶点：Normal.x 放**轴向参数 t**（0 头 → 1 尾）。 */
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
                // Color.r 留 0：光柱这一路的高度不在这里（见上面的 8 bit 说明），
                // 片元只读 Color.g 当角度。
                .color(0f, aNorm, 0f, 1f)
                // Normal.x = 世界 Y（float，无数值回绕）
                .normal(worldY, aNorm, 0f)
                .endVertex();
    }

    /**
     * 发一个世界空间轴对齐包围盒的<b>全部 12 个三角形</b>。
     *
     * <p>调用方会把它当"体积的包围壳"用：关深度测试、剔正面，
     * 于是每个被覆盖的像素得到恰好一个片元，光栅阶段不参与遮挡判定
     * （遮挡由片元里的 SDF 步进 + 深度纹理比较完成）。</p>
     */
    public static void emitBox(BufferBuilder buffer, Matrix4f mat,
                               float cx, float cy, float cz, float size) {
        float x0 = cx - size, x1 = cx + size;
        float y0 = cy - size, y1 = cy + size;
        float z0 = cz - size, z1 = cz + size;

        // -Z / +Z
        quad(buffer, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(buffer, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        // -X / +X
        quad(buffer, mat, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(buffer, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        // -Y / +Y
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

    // ══════════════════════════════════════════════════════════════
    //  地形采样
    // ══════════════════════════════════════════════════════════════

    /**
     * 求 {@code (x, z)} 处、从 {@code baseY} 向下第一块可站立表面的顶面高度。
     *
     * <p>用碰撞盒顶面而不是 {@code by + 1}：台阶、栅栏、雪层的顶面与整格
     * 不同，只有碰撞盒高度才贴得准。找不到（悬空 / 未加载）时返回 {@code baseY}，
     * 保证特效永远画得出来。</p>
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
