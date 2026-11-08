package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.OlympiadCubeEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * <b>奥数立方</b>的渲染器 —— 和寂寒白日那颗三棱锥同一套画法：<b>只画棱</b>、
 * 辉光 + 芯两层、加法混合、面全透明。三种形态各一套棱线：
 *
 * <ul>
 *   <li>{@link OlympiadCubeEntity#FORM_TETRA 三棱锥}：4 顶点 / 6 棱；</li>
 *   <li>{@link OlympiadCubeEntity#FORM_CUBE 立方体}：8 顶点 / 12 棱；</li>
 *   <li>{@link OlympiadCubeEntity#FORM_ICO 正二十面体}：12 顶点 / 30 棱 / <b>20 个正三角面</b>
 *       —— 五个柏拉图立体之一，是最"球"的那个多面体，展开成大范围时边缘也不会显得方。</li>
 * </ul>
 *
 * <h3>两种状态</h3>
 * <ul>
 *   <li><b>飞行中</b>：半径 {@value #FLY_RADIUS} 格（和寂寒白日周身那颗一个量级），
 *       自转，尾迹由实体自己撒粒子；</li>
 *   <li><b>展开后</b>：整体放大到 {@code OlympiadCubeEntity.EXPAND_RADIUS}（3 格），
 *       再补三圈水平"纬线"，读起来是一团正在张开的领域而不是一颗大石头。</li>
 * </ul>
 *
 * <p>尺寸不自己算：直接读实体上的同步字段，所以服务端的判定半径与画面必然一致。</p>
 */
public class OlympiadCubeRenderer extends EntityRenderer<OlympiadCubeEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 飞行阶段的外接球半径（格）—— 与寂寒白日那颗三棱锥同量级。 */
    private static final float FLY_RADIUS = 0.25f;

    /** 辉光层棱宽（格，半宽）；展开后按比例放大。 */
    private static final float GLOW_HALF_WIDTH = 0.026f;

    /** 芯层棱宽（格，半宽）。 */
    private static final float CORE_HALF_WIDTH = 0.011f;

    /** 辉光层颜色：奥术紫。 */
    private static final float GLOW_R = 0.45f;
    private static final float GLOW_G = 0.27f;
    private static final float GLOW_B = 0.95f;

    /** 芯层颜色：淡紫白。 */
    private static final float CORE_R = 0.90f;
    private static final float CORE_G = 0.82f;
    private static final float CORE_B = 1.00f;

    /** 飞行时每秒自转角度（度）。 */
    private static final float SPIN_DEG_PER_SECOND = 140.0f;

    /** 展开后每秒自转角度（度）—— 慢下来，读作"领域"而不是"陀螺"。 */
    private static final float EXPANDED_SPIN_DEG_PER_SECOND = 18.0f;

    // ── 拖尾 ──

    /** 拖尾辉光层半宽（格）：比立方体的棱粗一截，形成"光晕尾巴"。 */
    private static final float TRAIL_GLOW_HALF_WIDTH = 0.055f;

    /** 拖尾芯层半宽（格）。 */
    private static final float TRAIL_CORE_HALF_WIDTH = 0.018f;

    /** 拖尾辉光层不透明度。 */
    private static final float TRAIL_GLOW_ALPHA = 0.30f;

    /** 拖尾芯层不透明度。 */
    private static final float TRAIL_CORE_ALPHA = 0.75f;

    /** 兜底直尾的长度（格）：只在刚发射、还没攒到轨迹采样的那 1~2 tick 用。 */
    private static final float FALLBACK_TRAIL_LENGTH = 0.9f;

    // ── 正二十面体的顶点（必须声明在 ICO_EDGES 之前） ──
    //
    //  ⚠ 顺序是有意义的：静态字段按<b>声明顺序</b>初始化，
    //  而 ICO_EDGES = buildIcosaEdges() 要读 ICO_RAW。
    //  把 ICO_RAW 写在它后面会读到 null，<clinit> 直接抛 NPE
    //  （真实踩过一次：整个客户端在 EntityRenderers 注册阶段崩掉）。

    /** 正二十面体的顶点数。 */
    private static final int ICO_VERTEX_COUNT = 12;

    /** 黄金比 {@code φ = (1+√5)/2}：正二十面体顶点的构造常数。 */
    private static final float PHI = (1.0f + (float) Math.sqrt(5.0d)) / 2.0f;

    /** 正二十面体的 12 个顶点（未归一化；棱表按这组坐标的最近距离算）。 */
    private static final Vec3[] ICO_RAW = {
            new Vec3(0.0d, 1.0d, PHI), new Vec3(0.0d, -1.0d, PHI),
            new Vec3(0.0d, 1.0d, -PHI), new Vec3(0.0d, -1.0d, -PHI),
            new Vec3(1.0d, PHI, 0.0d), new Vec3(-1.0d, PHI, 0.0d),
            new Vec3(1.0d, -PHI, 0.0d), new Vec3(-1.0d, -PHI, 0.0d),
            new Vec3(PHI, 0.0d, 1.0d), new Vec3(-PHI, 0.0d, 1.0d),
            new Vec3(PHI, 0.0d, -1.0d), new Vec3(-PHI, 0.0d, -1.0d)
    };

    // ── 各形态的棱（顶点索引对） ──

    /** 三棱锥：0 顶点 + 1..3 底角。 */
    private static final int[][] TETRA_EDGES = {
            {0, 1}, {0, 2}, {0, 3}, {1, 2}, {2, 3}, {3, 1}
    };

    /** 立方体：0..3 底面环序，4..7 顶面同序。 */
    private static final int[][] CUBE_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    /** 正二十面体的 30 条棱（由 ICO_RAW 的最近距离推导，见 buildIcosaEdges）。 */
    private static final int[][] ICO_EDGES = buildIcosaEdges();

    public OlympiadCubeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(OlympiadCubeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        boolean expanded = entity.isExpanded();
        float radius = expanded ? (float) OlympiadCubeEntity.EXPAND_RADIUS : FLY_RADIUS;
        // 棱宽<b>不随尺寸变化</b>：展开到 8 格时棱还是飞行时那么细。
        // 按比例放大的话 3 格→8 格的场会出现几十格长、几十厘米粗的光柱，糊满屏幕。

        double seconds = (entity.level().getGameTime() + partialTick) / 20.0d;
        float spinDeg = expanded ? EXPANDED_SPIN_DEG_PER_SECOND : SPIN_DEG_PER_SECOND;
        float spin = (float) Math.toRadians(seconds * spinDeg);
        float tilt = (float) Math.toRadians(seconds * (expanded ? 6.0d : 37.0d));

        Vec3[] vertices = verticesOf(entity.getForm(), radius, spin, tilt);
        int[][] edges = edgesOf(entity.getForm());

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
        Matrix4f matrix = poseStack.last().pose();

        float pulse = 0.75f + 0.25f * (float) Math.sin(seconds * 3.0d);

        // ① 拖尾（画在棱线之前，让立方体压在拖尾上）
        if (!expanded) {
            renderTrail(buffer, tesselator, poseStack, entity, partialTick);
        }

        // ② 辉光层
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : edges) {
            emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                    GLOW_HALF_WIDTH, GLOW_R, GLOW_G, GLOW_B, 0.40f * pulse);
        }
        if (expanded) {
            // 展开态补三圈纬线：读作张开的领域，而不是一颗大石头
            emitLatitudeRings(buffer, matrix, radius, 0.35f * pulse);
        }
        tesselator.end();

        // ③ 芯层
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : edges) {
            emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                    CORE_HALF_WIDTH, CORE_R, CORE_G, CORE_B, 0.80f * pulse);
        }
        tesselator.end();

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════════
    // 几何
    // ══════════════════════════════════════════════════════════════

    private static int[][] edgesOf(int form) {
        return switch (form) {
            case OlympiadCubeEntity.FORM_CUBE -> CUBE_EDGES;
            case OlympiadCubeEntity.FORM_ICO -> ICO_EDGES;
            default -> TETRA_EDGES;
        };
    }

    /** 按形态生成顶点（已应用自转 + 俯仰倾斜）。 */
    private static Vec3[] verticesOf(int form, float radius, float spin, float tilt) {
        return switch (form) {
            case OlympiadCubeEntity.FORM_CUBE -> cubeVertices(radius, spin, tilt);
            case OlympiadCubeEntity.FORM_ICO -> icoVertices(radius, spin, tilt);
            default -> tetraVertices(radius, spin, tilt);
        };
    }

    /** 正四面体（顶点朝上），与寂寒白日那个同构。 */
    private static Vec3[] tetraVertices(float radius, float spin, float tilt) {
        float baseRadius = radius * (float) (Math.sqrt(8.0) / 3.0);
        float baseY = -radius / 3.0f;
        Vec3[] out = new Vec3[4];
        out[0] = transform(new Vec3(0.0d, radius, 0.0d), spin, tilt);
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(90.0d + 120.0d * i);
            out[1 + i] = transform(new Vec3(baseRadius * Math.cos(angle), baseY,
                    baseRadius * Math.sin(angle)), spin, tilt);
        }
        return out;
    }

    /** 立方体：半边长 = radius / √3，这样外接球半径正好是 radius。 */
    private static Vec3[] cubeVertices(float radius, float spin, float tilt) {
        float half = radius / (float) Math.sqrt(3.0d);
        int[][] ring = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        Vec3[] out = new Vec3[8];
        for (int i = 0; i < 4; i++) {
            double x = ring[i][0] * half;
            double z = ring[i][1] * half;
            out[i] = transform(new Vec3(x, -half, z), spin, tilt);
            out[i + 4] = transform(new Vec3(x, half, z), spin, tilt);
        }
        return out;
    }

    /**
     * <b>正二十面体</b>的 12 个顶点。
     *
     * <p>顶点取经典构造：{@code (0, ±1, ±φ)}、{@code (±1, ±φ, 0)}、{@code (±φ, 0, ±1)}
     * （{@code φ = (1+√5)/2}），归一化到外接球半径 {@code radius}。
     * 这 12 个点两两之间只有三种距离，最短的那一档恰好构成 <b>30 条棱</b>、
     * 每个顶点连 5 条 —— 也就是标准正二十面体的棱图，所以棱表由
     * {@link #buildIcosaEdges()} 按"最短距离"自动生成，不用手抄 30 对索引。</p>
     */
    private static Vec3[] icoVertices(float radius, float spin, float tilt) {
        Vec3[] out = new Vec3[ICO_VERTEX_COUNT];
        for (int i = 0; i < ICO_VERTEX_COUNT; i++) {
            Vec3 unit = ICO_RAW[i].normalize().scale(radius);
            out[i] = transform(unit, spin, tilt);
        }
        return out;
    }

    /**
     * 正二十面体的 30 条棱：在 12 个顶点里取所有"距离 = 最短距离"的顶点对。
     *
     * <p>只用几何判据而不是手抄索引表，是为了让"顶点集合 → 棱图"这条推导留在代码里：
     * 改顶点顺序或换成别的多面体时，棱会自动跟着走。</p>
     */
    private static int[][] buildIcosaEdges() {
        double minSqr = Double.MAX_VALUE;
        double[][] dist = new double[ICO_VERTEX_COUNT][ICO_VERTEX_COUNT];
        for (int i = 0; i < ICO_VERTEX_COUNT; i++) {
            for (int j = i + 1; j < ICO_VERTEX_COUNT; j++) {
                dist[i][j] = ICO_RAW[i].distanceToSqr(ICO_RAW[j]);
                dist[j][i] = dist[i][j];
                minSqr = Math.min(minSqr, dist[i][j]);
            }
        }
        java.util.List<int[]> edges = new java.util.ArrayList<>(30);
        for (int i = 0; i < ICO_VERTEX_COUNT; i++) {
            for (int j = i + 1; j < ICO_VERTEX_COUNT; j++) {
                if (Math.abs(dist[i][j] - minSqr) < 1.0E-6d) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        return edges.toArray(new int[0][]);
    }

    /** 先绕 Y 自转，再绕 X 倾斜（顺序固定，结果可复现）。 */
    private static Vec3 transform(Vec3 local, float spin, float tilt) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        double x = local.x * cs - local.z * sn;
        double z = local.x * sn + local.z * cs;
        double y = local.y;

        double ct = Math.cos(tilt), st = Math.sin(tilt);
        double y2 = y * ct - z * st;
        double z2 = y * st + z * ct;
        return new Vec3(x, y2, z2);
    }

    /** 展开态的三圈"纬线"（y = 0 / ±R/2），让 3 格大的场看起来是满的。 */
    private static void emitLatitudeRings(BufferBuilder buffer, Matrix4f matrix, float radius,
                                          float alpha) {
        float[] heights = {0.0f, 0.55f, -0.55f};
        for (float hFactor : heights) {
            float y = hFactor * radius;
            float r = radius * (float) Math.sqrt(Math.max(0.0d, 1.0d - hFactor * hFactor * 0.3025d));
            int segments = 40;
            for (int i = 0; i < segments; i++) {
                double a0 = 2.0d * Math.PI * i / segments;
                double a1 = 2.0d * Math.PI * (i + 1) / segments;
                Vec3 p0 = new Vec3(r * Math.cos(a0), y, r * Math.sin(a0));
                Vec3 p1 = new Vec3(r * Math.cos(a1), y, r * Math.sin(a1));
                emitEdge(buffer, matrix, p0, p1, GLOW_HALF_WIDTH * 0.8f,
                        GLOW_R, GLOW_G, GLOW_B, alpha);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 拖尾（自己画的细长尾迹）
    // ══════════════════════════════════════════════════════════════

    /**
     * 画<b>细长拖尾</b> —— 替代原来那颗借来的潜影贝 {@code END_ROD} 粒子。
     *
     * <h3>数据从哪来</h3>
     * <p>实体在客户端每 tick 往 {@code getClientTrail()} 里记一个位置（最近
     * {@code OlympiadCubeEntity.TRAIL_POINTS} 个 tick）。这里按同一套插值规则把
     * 世界坐标换算成"相对实体"的坐标，再接上实体<b>当前</b>的插值位置作为最新一点 ——
     * 于是拖尾是一条跟着真实飞行轨迹（包括追踪时的拐弯）的折线，而不是一条直线。</p>
     *
     * <h3>关于坐标系：<b>不要</b>再补任何旋转</h3>
     * <p>{@code EntityRenderDispatcher.render(...)} 的字节码里只有
     * {@code pushPose() → translate(位置 + getRenderOffset)}，然后就调用
     * {@code EntityRenderer.render(...)} —— <b>没有</b> yaw 旋转（那个旋转在
     * {@code LivingEntityRenderer} 里，只有生物模型才有）。本渲染器继承的是
     * {@code EntityRenderer}，所以拿到的 pose 已经是"原点在实体位置、坐标轴与世界轴平行"。</p>
     *
     * <p>之前这里多补了一次 {@code rotate(Y, -(180 - entityYaw))} 想"抵消"，结果反而把整条
     * 尾巴绕 Y 转了 {@code 180 - yaw}。而 {@code yaw} 是发射时按视线设好、之后再没更新过的
     * 常量 —— 于是拖尾变成一条<b>方向固定、贴在侧面、完全不跟飞行轨迹</b>的直线。
     * 记住这条：<b>这里的 pose 是世界的，世界坐标差可以直接当本地坐标用。</b></p>
     *
     * <h3>形状</h3>
     * <p>从尾部（最旧、最细、全透明）渐变到立方体（最新、最粗、最亮），
     * 每段画成两片互相垂直的细长梯形（十字截面）—— 从任何角度看都是一条线，
     * 不需要镜头方向的公告板计算。辉光层（粗、紫）+ 芯层（细、近白）两层相加
     * 就是"一道发光的细长尾"。</p>
     */
    private void renderTrail(BufferBuilder buffer, Tesselator tesselator, PoseStack poseStack,
                             OlympiadCubeEntity entity, float partialTick) {
        Deque<Vec3> trail = entity.getClientTrail();

        // 渲染原点 = 插值后的实体位置（与 EntityRenderDispatcher 用的那个一致；
        // 本渲染器没有覆写 getRenderOffset，所以偏移恒为 0）
        Vec3 origin = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));

        List<Vec3> points = new ArrayList<>(trail.size() + 2);
        for (Vec3 p : trail) {
            points.add(p.subtract(origin));
        }
        points.add(Vec3.ZERO);          // 最新一点永远落在立方体中心

        if (points.size() < 3) {
            // 兜底：刚发射的 1~2 tick 还没攒到轨迹，用当前速度方向补一条短直尾，
            // 免得"发射瞬间没有拖尾"显得突兀。
            Vec3 dir = entity.getDeltaMovement();
            if (dir.lengthSqr() < 1.0E-6d) {
                return;
            }
            points.add(0, dir.normalize().scale(-FALLBACK_TRAIL_LENGTH));
        }

        Matrix4f matrix = poseStack.last().pose();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitTrailLayer(buffer, matrix, points, TRAIL_GLOW_HALF_WIDTH,
                GLOW_R, GLOW_G, GLOW_B, TRAIL_GLOW_ALPHA);
        tesselator.end();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitTrailLayer(buffer, matrix, points, TRAIL_CORE_HALF_WIDTH,
                CORE_R, CORE_G, CORE_B, TRAIL_CORE_ALPHA);
        tesselator.end();
    }

    /** 把一条折线发出一层（宽度与亮度按"越新越粗越亮"的平方曲线渐变）。 */
    private static void emitTrailLayer(BufferBuilder buffer, Matrix4f matrix, List<Vec3> points,
                                       float maxHalfWidth, float r, float g, float b, float maxAlpha) {
        int segments = points.size() - 1;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float w0 = maxHalfWidth * t0 * t0;
            float w1 = maxHalfWidth * t1 * t1;
            if (w1 <= 1.0e-4f) {
                continue;
            }
            emitTaperedSegment(buffer, matrix, points.get(i), points.get(i + 1), w0, w1,
                    r, g, b, maxAlpha * t0 * t0, maxAlpha * t1 * t1);
        }
    }

    /** 一段拖尾：两片互相垂直的梯形（十字截面），宽度在两端分别是 {@code wa} / {@code wb}。 */
    private static void emitTaperedSegment(BufferBuilder buffer, Matrix4f matrix,
                                           Vec3 a, Vec3 b, float wa, float wb,
                                           float r, float g, float bl, float aa, float ab) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-9d) {
            return;
        }
        axis = axis.normalize();

        Vec3 helper = Math.abs(axis.y) < 0.99d ? new Vec3(0.0d, 1.0d, 0.0d) : new Vec3(1.0d, 0.0d, 0.0d);
        Vec3 u = axis.cross(helper);
        if (u.lengthSqr() < 1.0E-9d) {
            u = axis.cross(new Vec3(0.0d, 0.0d, 1.0d));
        }
        u = u.normalize();
        Vec3 v = axis.cross(u).normalize();

        taperQuad(buffer, matrix, a, b, u, wa, wb, r, g, bl, aa, ab);
        taperQuad(buffer, matrix, a, b, v, wa, wb, r, g, bl, aa, ab);
    }

    private static void taperQuad(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 dir,
                                  float wa, float wb, float r, float g, float bl, float aa, float ab) {
        Vec3 oa = dir.scale(wa);
        Vec3 ob = dir.scale(wb);
        vertex(buffer, matrix, a.subtract(oa), r, g, bl, aa);
        vertex(buffer, matrix, b.subtract(ob), r, g, bl, ab);
        vertex(buffer, matrix, b.add(ob), r, g, bl, ab);
        vertex(buffer, matrix, a.add(oa), r, g, bl, aa);
    }

    // ══════════════════════════════════════════════════════════════
    // 棱的绘制（十字 ribbons）
    // ══════════════════════════════════════════════════════════════

    private static void emitEdge(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b,
                                 float halfWidth, float r, float g, float bl, float alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-9d) {
            return;
        }
        axis = axis.normalize();

        Vec3 helper = Math.abs(axis.y) < 0.99d ? new Vec3(0.0d, 1.0d, 0.0d) : new Vec3(1.0d, 0.0d, 0.0d);
        Vec3 u = axis.cross(helper);
        if (u.lengthSqr() < 1.0E-9d) {
            u = axis.cross(new Vec3(0.0d, 0.0d, 1.0d));
        }
        u = u.normalize();
        Vec3 v = axis.cross(u).normalize();

        quad(buffer, matrix, a, b, u.scale(halfWidth), r, g, bl, alpha);
        quad(buffer, matrix, a, b, v.scale(halfWidth), r, g, bl, alpha);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 offset,
                             float r, float g, float bl, float alpha) {
        vertex(buffer, matrix, a.subtract(offset), r, g, bl, alpha);
        vertex(buffer, matrix, b.subtract(offset), r, g, bl, alpha);
        vertex(buffer, matrix, b.add(offset), r, g, bl, alpha);
        vertex(buffer, matrix, a.add(offset), r, g, bl, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                               float r, float g, float b, float alpha) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(OlympiadCubeEntity entity) {
        return DUMMY_TEXTURE;
    }
}
