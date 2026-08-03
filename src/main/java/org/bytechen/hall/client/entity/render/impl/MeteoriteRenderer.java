package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.overworld.registry.entities.population.skills.MeteoriteEntity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 陨石自定义渲染器 —— 程序化水滴多面体 + 平滑拖尾光带。
 * <p>
 * 不使用 GeckoLib 模型，所有几何体通过 {@link Tesselator} 动态绘制。
 */
public class MeteoriteRenderer extends EntityRenderer<MeteoriteEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    // ────────── 水滴网格参数 ──────────
    private static final int RINGS = 20;
    private static final int SEGMENTS = 14;
    private static final int RING_SEGMENTS = 48;
    private static final float DROP_HEIGHT = 1.6f;
    private static final float DROP_MAX_RADIUS = 0.38f;

    // ────────── 二十面体边框参数 ──────────
    /** 二十面体包围半径（明显大于水滴本体，确保边框清晰可见） */
    private static final float ICOSA_RADIUS = 1.0f;
    /** 边框线的视觉粗细（世界单位） */
    private static final float EDGE_THICKNESS = 0.025f;
    /** 二十面体自转速度（弧度/tick） */
    private static final float ICOSA_SPIN_X = 0.03f;
    private static final float ICOSA_SPIN_Y = 0.05f;
    private static final float ICOSA_SPIN_Z = 0.02f;

    // ────────── 二十面体坠地动画时间轴（fadeTimer 170→0） ──────────
    /** 总 fade 持续时间 = 30展开 + 60保持 + 20收缩 + 60停留 */
    private static final int FADE_TOTAL = 170;
    /** 展开阶段：3 次缓出脉冲，共 30 tick */
    private static final int EXPAND_DURATION = 30;
    /** 收缩阶段：3 次缓出脉冲，共 20 tick */
    private static final int SHRINK_DURATION = 20;
    /** 保持阶段：60 tick（FADE_TOTAL - EXPAND_DURATION - SHRINK_DURATION - LINGER_DURATION） */
    private static final int LINGER_DURATION = 60;
    private static final int HOLD_DURATION = FADE_TOTAL - EXPAND_DURATION - SHRINK_DURATION - LINGER_DURATION;
    /** 展开/收缩的最大倍数 */
    private static final float MAX_SCALE = 10.0f;
    /** 二十面体 12 个顶点 */
    private static final float[][] ICOSA_VERTICES = new float[12][3];
    /** 二十面体 30 条边（顶点索引对） */
    private static final int[][] ICOSA_EDGES = new int[30][2];

    // ────────── 预计算的网格 ──────────
    private static final float[][][] RING_VERTICES = new float[RINGS][SEGMENTS][3];

    static {
        buildDropMesh();
        buildIcosahedron();
    }

    public MeteoriteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    // ═══════════════════════════════════════════════════════════════
    // 水滴轮廓函数
    // ═══════════════════════════════════════════════════════════════

    private static float dropRadius(float t) {
        double sinVal = Math.sin(Math.PI * t);
        double asymmetry = 0.7 + 0.3 * t;
        return DROP_MAX_RADIUS * (float) (Math.pow(sinVal, 0.65) * asymmetry);
    }

    // ═══════════════════════════════════════════════════════════════
    // 预计算水滴网格顶点
    // ═══════════════════════════════════════════════════════════════

    private static void buildDropMesh() {
        for (int ring = 0; ring < RINGS; ring++) {
            float t = ring / (float) (RINGS - 1);
            float y = (t - 0.5f) * DROP_HEIGHT;
            float r = dropRadius(t);

            for (int seg = 0; seg < SEGMENTS; seg++) {
                double angle = 2.0 * Math.PI * seg / SEGMENTS;
                float x = r * (float) Math.cos(angle);
                float z = r * (float) Math.sin(angle);
                RING_VERTICES[ring][seg][0] = x;
                RING_VERTICES[ring][seg][1] = y;
                RING_VERTICES[ring][seg][2] = z;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 二十面体预计算
    // ═══════════════════════════════════════════════════════════════

    /**
     * 用三条黄金矩形构建正二十面体的 12 个顶点，
     * 再归一化到球面，最后推导出 30 条边。
     */
    private static void buildIcosahedron() {
        float phi = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);

        // 三条黄金矩形上的 12 个点
        float[][] raw = {
                {-1,  phi,  0}, { 1,  phi,  0}, {-1, -phi,  0}, { 1, -phi,  0},
                { 0, -1,  phi}, { 0,  1,  phi}, { 0, -1, -phi}, { 0,  1, -phi},
                { phi,  0, -1}, { phi,  0,  1}, {-phi,  0, -1}, {-phi,  0,  1},
        };

        // 归一化到单位球面
        for (int i = 0; i < 12; i++) {
            float len = (float) Math.sqrt(raw[i][0] * raw[i][0] + raw[i][1] * raw[i][1] + raw[i][2] * raw[i][2]);
            ICOSA_VERTICES[i][0] = raw[i][0] / len * ICOSA_RADIUS;
            ICOSA_VERTICES[i][1] = raw[i][1] / len * ICOSA_RADIUS;
            ICOSA_VERTICES[i][2] = raw[i][2] / len * ICOSA_RADIUS;
        }

        // 二十面体的 30 条边：每个顶点连接 5 个邻居（距离最近的非对跖点）
        // 直接用二十面体各面的边来构造：20 个三角形面 × 3 条边 / 2 = 30
        // 这样比距离检测更可靠
        int[] faceTriplets = {
                // 围绕顶点 0 的 5 个三角形
                0, 11, 5,   0, 5, 1,   0, 1, 7,   0, 7, 10,   0, 10, 11,
                // 围绕顶点 3 的 5 个三角形
                3, 9, 4,   3, 4, 2,   3, 2, 6,   3, 6, 8,   3, 8, 9,
                // 中间带 — 10 个三角形
                1, 5, 9,   5, 11, 4,   11, 10, 2,   10, 7, 6,   7, 1, 8,
                4, 9, 5,   2, 4, 11,   6, 2, 10,   8, 6, 7,   9, 8, 1,
        };

        // 从三角形面提取唯一边
        java.util.Set<Long> edgeSet = new java.util.LinkedHashSet<>();
        for (int t = 0; t < 20; t++) {
            int a = faceTriplets[t * 3];
            int b = faceTriplets[t * 3 + 1];
            int c = faceTriplets[t * 3 + 2];
            addEdge(edgeSet, a, b);
            addEdge(edgeSet, b, c);
            addEdge(edgeSet, c, a);
        }

        int idx = 0;
        for (long edge : edgeSet) {
            ICOSA_EDGES[idx][0] = (int) (edge >> 32);
            ICOSA_EDGES[idx][1] = (int) (edge & 0xFFFFFFFFL);
            idx++;
        }
    }

    private static void addEdge(java.util.Set<Long> edgeSet, int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        edgeSet.add(((long) lo << 32) | (hi & 0xFFFFFFFFL));
    }

    // ═══════════════════════════════════════════════════════════════
    // 主渲染入口
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(MeteoriteEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        // 滞留消退阶段：拖尾 + 尾部圆环 + 二十面体 + AoE 扩散圆环
        if (entity.isFading()) {
            renderTrail(entity, partialTick, poseStack);
            renderTailRings(entity, partialTick, poseStack);
            renderIcosahedronBorder(entity, partialTick, poseStack);
            renderAoeRings(entity, partialTick, poseStack);
            return;
        }

        // 飞行阶段：拖尾 + 尾部圆环 + 水滴本体 + 二十面体边框
        renderTrail(entity, partialTick, poseStack);
        renderTailRings(entity, partialTick, poseStack);
        renderIcosahedronBorder(entity, partialTick, poseStack);
        renderDropBody(entity, partialTick, poseStack);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // ═══════════════════════════════════════════════════════════════
    // 水滴多面体本体渲染
    // ═══════════════════════════════════════════════════════════════

    private void renderDropBody(MeteoriteEntity entity, float partialTick, PoseStack poseStack) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();

        // 根据速度方向旋转水滴
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 0.0001) {
            applyVelocityRotation(poseStack, velocity);
        }

        Matrix4f matrix = poseStack.last().pose();

        for (int ring = 0; ring < RINGS - 1; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                int nextSeg = (seg + 1) % SEGMENTS;

                float[] a = RING_VERTICES[ring][seg];
                float[] b = RING_VERTICES[ring][nextSeg];
                float[] c = RING_VERTICES[ring + 1][seg];
                float[] d = RING_VERTICES[ring + 1][nextSeg];

                addTriangle(buffer, matrix, a, c, b);
                addTriangle(buffer, matrix, b, c, d);
            }
        }

        closeCap(buffer, matrix, 0, true);
        closeCap(buffer, matrix, RINGS - 1, false);

        poseStack.popPose();

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ═══════════════════════════════════════════════════════════════
    // 二十面体白色边框渲染（相机朝向厚边）
    // ═══════════════════════════════════════════════════════════════

    private void renderIcosahedronBorder(MeteoriteEntity entity, float partialTick, PoseStack poseStack) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // ── 计算坠地动画 scale ──
        float scale = 1.0f;
        float alpha = 0.8f;
        if (entity.isFading()) {
            float timer = entity.getFadeTimer() - partialTick;
            scale = computeImpactScale(timer);
            // 停留期：二十面体 alpha 缓出到 0
            if (timer <= LINGER_DURATION) {
                alpha = 0.8f * (timer / LINGER_DURATION);
            }
        }

        // 缩放后的顶点厚度
        float thick = EDGE_THICKNESS * scale;

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();

        // 与水滴本体保持相同的朝向
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 0.0001) {
            applyVelocityRotation(poseStack, velocity);
        }

        // 二十面体自身旋转（基于实体年龄）
        float age = entity.tickCount + partialTick;
        poseStack.mulPose(com.mojang.math.Axis.XP.rotation(age * ICOSA_SPIN_X));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation(age * ICOSA_SPIN_Y));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(age * ICOSA_SPIN_Z));

        Matrix4f matrix = poseStack.last().pose();

        // 相机方向用于构建边的厚度
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camDir = new Vec3(camera.getLookVector());

        // 绘制全部 30 条边为相机朝向的厚四边形
        for (int[] edge : ICOSA_EDGES) {
            float[] ra = ICOSA_VERTICES[edge[0]];
            float[] rb = ICOSA_VERTICES[edge[1]];

            // ICOSA_VERTICES 已预缩放到 ICOSA_RADIUS，这里再乘 scale
            Vec3 va = new Vec3(ra[0] * scale, ra[1] * scale, ra[2] * scale);
            Vec3 vb = new Vec3(rb[0] * scale, rb[1] * scale, rb[2] * scale);

            // 边方向
            Vec3 edgeDir = vb.subtract(va).normalize();
            // 垂直于边方向且面向相机的偏移方向
            Vec3 perp = edgeDir.cross(camDir).normalize().scale(thick);

            // 四个角
            Vec3 v0 = va.add(perp);
            Vec3 v1 = va.subtract(perp);
            Vec3 v2 = vb.subtract(perp);
            Vec3 v3 = vb.add(perp);

            // 双面四边形
            bufferVertex(buffer, matrix, v0, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v1, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v2, 1, 1, 1, alpha);

            bufferVertex(buffer, matrix, v0, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v2, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v3, 1, 1, 1, alpha);

            bufferVertex(buffer, matrix, v0, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v2, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v1, 1, 1, 1, alpha);

            bufferVertex(buffer, matrix, v0, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v3, 1, 1, 1, alpha);
            bufferVertex(buffer, matrix, v2, 1, 1, 1, alpha);
        }

        poseStack.popPose();

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 坠地后二十面体缩放动画。
     * <p>
     * 时间轴（fadeTimer 从 FADE_TOTAL=170 → 0）：
     * <ol>
     *   <li>170→140：3 段 cubic-ease-out，1x → 10x（展开 30t）</li>
     *   <li>140→80：保持 10x（60t）</li>
     *   <li>80→60：3 段 cubic-ease-out，10x → 1x（收缩 20t）</li>
     *   <li>60→0：保持 1x，alpha 渐隐（停留 60t）</li>
     * </ol>
     */
    private static float computeImpactScale(float fadeTimer) {
        fadeTimer = Math.max(0, Math.min(FADE_TOTAL, fadeTimer));
        float expandEnd = (float)(SHRINK_DURATION + HOLD_DURATION + LINGER_DURATION); // 140
        float holdEnd = (float)(SHRINK_DURATION + LINGER_DURATION);                    // 80
        float shrinkEnd = (float) LINGER_DURATION;                                      // 60
        if (fadeTimer > expandEnd) {
            float elapsed = FADE_TOTAL - fadeTimer;
            return multiEaseOutScale(elapsed, EXPAND_DURATION, 1.0f, MAX_SCALE, 3);
        }
        if (fadeTimer > holdEnd) {
            return MAX_SCALE;
        }
        if (fadeTimer > shrinkEnd) {
            float elapsed = holdEnd - fadeTimer;
            return multiEaseOutScale(elapsed, SHRINK_DURATION, MAX_SCALE, 1.0f, 3);
        }
        return 1.0f; // 停留期
    }

    /**
     * 将一段总时长划分为 {@code pulses} 个等长阶段，
     * 每段内做 cubic-ease-out 插值，形成阶梯式缓出。
     */
    private static float multiEaseOutScale(float elapsed, float totalDuration,
                                           float from, float to, int pulses) {
        if (totalDuration <= 0) return to;
        float progress = Math.min(elapsed / totalDuration, 1.0f);
        float segSize = 1.0f / pulses;
        int seg = (int) (progress * pulses);
        if (seg >= pulses) seg = pulses - 1;
        float t = (progress - seg * segSize) / segSize;
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        float segStart = from + (to - from) * seg * segSize;
        float segEnd   = from + (to - from) * (seg + 1) * segSize;
        return segStart + (segEnd - segStart) * eased;
    }

    private void applyVelocityRotation(PoseStack poseStack, Vec3 velocity) {
        Vec3 dir = velocity.normalize();
        Vec3 yAxis = new Vec3(0, 1, 0);
        Vec3 rotAxis = yAxis.cross(dir);
        double rotAxisLen = rotAxis.length();

        if (rotAxisLen < 0.0001) {
            if (dir.y < 0) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));
            }
            return;
        }

        rotAxis = rotAxis.normalize();
        double angle = Math.acos(yAxis.dot(dir));

        poseStack.mulPose(new org.joml.Quaternionf()
                .rotateAxis((float) angle, (float) rotAxis.x, (float) rotAxis.y, (float) rotAxis.z));
    }

    // ═══════════════════════════════════════════════════════════════
    // 拖尾渲染
    // ═══════════════════════════════════════════════════════════════

    private void renderTrail(MeteoriteEntity entity, float partialTick, PoseStack poseStack) {
        List<Vec3> trail = entity.getTrailPositions();
        if (trail.size() < 2) return;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 forward = new Vec3(camera.getLookVector());
        Vec3 camUp = new Vec3(camera.getUpVector());
        Vec3 camRight = forward.cross(camUp).normalize();

        Matrix4f matrix = poseStack.last().pose();
        Vec3 entityPos = entity.getPosition(partialTick);
        int trailSize = trail.size();

        // 滞留消退阶段：拖尾在落地后 30tick 内快速淡出
        float fadeAlpha;
        if (entity.isFading()) {
            float elapsed = FADE_TOTAL - entity.getFadeTimer();
            fadeAlpha = Math.max(0, 1.0f - elapsed / 30f);
        } else {
            fadeAlpha = 1.0f;
        }

        for (int i = 0; i < trailSize - 1; i++) {
            Vec3 p0 = trail.get(i).subtract(entityPos);
            Vec3 p1 = trail.get(i + 1).subtract(entityPos);

            float ageProgress0 = 1.0f - (float) i / trailSize;
            float ageProgress1 = 1.0f - (float) (i + 1) / trailSize;

            float width0 = (0.06f + 0.12f * ageProgress0) * fadeAlpha;
            float width1 = (0.06f + 0.12f * ageProgress1) * fadeAlpha;

            float alpha0 = 0.9f * ageProgress0 * fadeAlpha;
            float alpha1 = 0.9f * ageProgress1 * fadeAlpha;

            Vec3 rightOffset0 = camRight.scale(width0);
            Vec3 rightOffset1 = camRight.scale(width1);

            Vec3 v0 = p0.add(rightOffset0);
            Vec3 v1 = p0.subtract(rightOffset0);
            Vec3 v2 = p1.subtract(rightOffset1);
            Vec3 v3 = p1.add(rightOffset1);

            bufferVertex(buffer, matrix, v0, 1, 1, 1, alpha0);
            bufferVertex(buffer, matrix, v1, 1, 1, 1, alpha0);
            bufferVertex(buffer, matrix, v2, 1, 1, 1, alpha1);

            bufferVertex(buffer, matrix, v0, 1, 1, 1, alpha0);
            bufferVertex(buffer, matrix, v2, 1, 1, 1, alpha1);
            bufferVertex(buffer, matrix, v3, 1, 1, 1, alpha1);
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ═══════════════════════════════════════════════════════════════
    // 尾部圆环扩散渲染
    // ═══════════════════════════════════════════════════════════════

    private void renderTailRings(MeteoriteEntity entity, float partialTick, PoseStack poseStack) {
        List<MeteoriteEntity.TailRing> rings = entity.getTailRings();
        if (rings.isEmpty()) return;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        Vec3 entityPos = entity.getPosition(partialTick);

        for (MeteoriteEntity.TailRing ring : rings) {
            float lifeProgress = (float) ring.age / MeteoriteEntity.RING_MAX_AGE;
            // 淡出：先亮后暗，寿命末期全透明
            float alpha = (1.0f - lifeProgress) * (1.0f - lifeProgress) * 0.9f;

            // 环越老越大：初始 0.3，最终扩散到 ~1.6
            float baseR = 0.28f + lifeProgress * 1.35f;
            float innerR = baseR;
            float outerR = baseR + 0.06f;

            Vec3 center = ring.position.subtract(entityPos);
            Vec3 n = ring.normal;

            // 构建圆环平面的两个正交基向量
            Vec3 arbitrary = Math.abs(n.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = n.cross(arbitrary).normalize();
            Vec3 v = n.cross(u).normalize();

            for (int seg = 0; seg < RING_SEGMENTS; seg++) {
                double a0 = 2.0 * Math.PI * seg / RING_SEGMENTS;
                double a1 = 2.0 * Math.PI * (seg + 1) / RING_SEGMENTS;

                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                // 内圈顶点
                Vec3 i0 = center.add(u.scale(c0 * innerR)).add(v.scale(s0 * innerR));
                Vec3 i1 = center.add(u.scale(c1 * innerR)).add(v.scale(s1 * innerR));
                // 外圈顶点
                Vec3 o0 = center.add(u.scale(c0 * outerR)).add(v.scale(s0 * outerR));
                Vec3 o1 = center.add(u.scale(c1 * outerR)).add(v.scale(s1 * outerR));

                // 四边形 = 2 个三角形（单面，加法混合下双面可见）
                bufferVertex(buffer, matrix, o0, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, i0, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, i1, 1, 1, 1, alpha);

                bufferVertex(buffer, matrix, o0, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, i1, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, o1, 1, 1, 1, alpha);
            }
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ═══════════════════════════════════════════════════════════════
    // AoE 扩散圆环渲染（水平，从中心向外扩散）
    // ═══════════════════════════════════════════════════════════════

    private void renderAoeRings(MeteoriteEntity entity, float partialTick, PoseStack poseStack) {
        List<MeteoriteEntity.AoeRing> rings = entity.getAoeRings();
        if (rings.isEmpty()) return;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        Vec3 entityPos = entity.getPosition(partialTick);
        float yOffset = 0.05f; // 略高于地面防止 z-fighting

        for (MeteoriteEntity.AoeRing ring : rings) {
            float lifeProgress = (float) ring.age / MeteoriteEntity.AOE_RING_MAX_AGE;
            // alpha 从 0.5 淡到 0
            float alpha = (1.0f - lifeProgress) * (1.0f - lifeProgress) * 0.5f;
            if (alpha < 0.01f) continue;

            // 半径从初始值线形扩大到最大
            float currentR = MeteoriteEntity.AOE_RING_START_R + ring.age * MeteoriteEntity.AOE_RING_SPEED;
            float innerR = currentR;
            float outerR = currentR + 0.08f * (1.0f + lifeProgress * 2.0f); // 越老越粗

            Vec3 center = new Vec3(ring.position.x, ring.position.y + yOffset, ring.position.z)
                    .subtract(entityPos);

            for (int seg = 0; seg < RING_SEGMENTS; seg++) {
                double a0 = 2.0 * Math.PI * seg / RING_SEGMENTS;
                double a1 = 2.0 * Math.PI * (seg + 1) / RING_SEGMENTS;

                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                Vec3 i0 = center.add(c0 * innerR, 0, s0 * innerR);
                Vec3 i1 = center.add(c1 * innerR, 0, s1 * innerR);
                Vec3 o0 = center.add(c0 * outerR, 0, s0 * outerR);
                Vec3 o1 = center.add(c1 * outerR, 0, s1 * outerR);

                bufferVertex(buffer, matrix, o0, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, i0, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, i1, 1, 1, 1, alpha);

                bufferVertex(buffer, matrix, o0, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, i1, 1, 1, 1, alpha);
                bufferVertex(buffer, matrix, o1, 1, 1, 1, alpha);
            }
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void addTriangle(BufferBuilder buffer, Matrix4f matrix,
                                    float[] a, float[] b, float[] c) {
        float r = 1f, g = 1f, blu = 1f, alpha = 1f;
        buffer.vertex(matrix, a[0], a[1], a[2]).color(r, g, blu, alpha).endVertex();
        buffer.vertex(matrix, b[0], b[1], b[2]).color(r, g, blu, alpha).endVertex();
        buffer.vertex(matrix, c[0], c[1], c[2]).color(r, g, blu, alpha).endVertex();
    }

    private static void bufferVertex(BufferBuilder buffer, Matrix4f matrix,
                                     Vec3 v, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) v.x, (float) v.y, (float) v.z).color(r, g, b, a).endVertex();
    }

    private static void closeCap(BufferBuilder buffer, Matrix4f matrix, int ring, boolean bottom) {
        // 中心点：向球心方向偏移
        float ringY = RING_VERTICES[ring][0][1];
        float tipY = bottom ? (ringY - 0.02f) : (ringY + 0.02f);

        for (int seg = 0; seg < SEGMENTS; seg++) {
            int nextSeg = (seg + 1) % SEGMENTS;
            float[] a = RING_VERTICES[ring][seg];
            float[] b = RING_VERTICES[ring][nextSeg];

            if (bottom) {
                // 在网格坐标系中直接绘制
                addTriangleRaw(buffer, matrix, a[0], a[1], a[2], 0, tipY, 0, b[0], b[1], b[2]);
            } else {
                addTriangleRaw(buffer, matrix, a[0], a[1], a[2], b[0], b[1], b[2], 0, tipY, 0);
            }
        }
    }

    private static void addTriangleRaw(BufferBuilder buffer, Matrix4f matrix,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3) {
        float r = 1f, g = 1f, blu = 1f, alpha = 1f;
        buffer.vertex(matrix, x1, y1, z1).color(r, g, blu, alpha).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(r, g, blu, alpha).endVertex();
        buffer.vertex(matrix, x3, y3, z3).color(r, g, blu, alpha).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 纹理位置（占位）
    // ═══════════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(MeteoriteEntity entity) {
        return DUMMY_TEXTURE;
    }
}
