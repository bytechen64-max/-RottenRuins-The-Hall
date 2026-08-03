package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity.CollapseType;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity.CollapsePhase;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 坍缩实体渲染器 —— 支持 4 种坍缩渲染类型，全部通过程序化几何绘制。
 *
 * <pre>
 * 类型 0 - ENDER_DRAGON       : 末影龙死亡式光芒四射，光束旋转
 * 类型 1 - HYPERCUBE          : 四维超立方体 3D 投影
 * 类型 2 - ICOSAHEDRON        : 二十面体（线框 + 面片）
 * 类型 3 - TRANSFORMING_PRISM : 多棱柱变换（3→20 棱）
 * </pre>
 */
public class CollapseRenderer extends EntityRenderer<CollapseEntity> {

    private static final ResourceLocation DUMMY = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    // ────────── 通用常量 ──────────
    private static final float TWO_PI = (float) (2.0 * Math.PI);
    private static final float PI = (float) Math.PI;
    private static final float HALF_PI = PI / 2f;

    public CollapseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    // ═══════════════════════════════════════════════════════════════
    // 实体渲染入口（no-op；实际渲染在 CollapseRenderHandler 中）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(CollapseEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // no-op: 渲染由 CollapseRenderHandler 通过 RenderLevelStageEvent 处理
    }

    @Override
    public ResourceLocation getTextureLocation(CollapseEntity entity) {
        return DUMMY;
    }

    // ═══════════════════════════════════════════════════════════════
    // 静态渲染入口（由 CollapseRenderHandler 调用）
    // ═══════════════════════════════════════════════════════════════

    public static void renderOne(CollapseEntity entity, float partialTick, PoseStack ps,
                                  MultiBufferSource bufferSource) {
        CollapsePhase phase = entity.getPhase();
        if (phase == CollapsePhase.DEAD) return;

        float spawnProgress = entity.getPhaseProgress();
        float endFade = entity.getEndFadeProgress();

        // 结束阶段：若完全淡出则跳过
        if (phase == CollapsePhase.END && endFade < 0.001f) return;
        // 生成阶段：若刚开头则跳过
        if (phase == CollapsePhase.SPAWN && spawnProgress < 0.001f) return;

        CollapseType type = entity.getCollapseType();

        ps.pushPose();
        ps.translate(entity.getX(), entity.getY(), entity.getZ());

        switch (type) {
            case ENDER_DRAGON -> renderEnderDragon(entity, partialTick, ps, spawnProgress, endFade, phase, bufferSource);
            case HYPERCUBE -> renderHypercube(entity, partialTick, ps, spawnProgress, endFade, phase, bufferSource);
            case ICOSAHEDRON -> renderIcosahedron(entity, partialTick, ps, spawnProgress, endFade, phase, bufferSource);
            case TRANSFORMING_PRISM -> renderTransformingPrism(entity, partialTick, ps, spawnProgress, endFade, phase, bufferSource);
        }

        ps.popPose();
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染类型 0: 末影龙死亡式光芒四射
    // ═══════════════════════════════════════════════════════════════

    private static final int RING_COUNT = 6;

    private static void renderEnderDragon(CollapseEntity entity, float partialTick, PoseStack ps,
                                           float spawnProgress, float endFade, CollapsePhase phase,
                                           MultiBufferSource bufferSource) {
        float r1 = entity.color1R(), g1 = entity.color1G(), b1 = entity.color1B();
        float r2 = entity.color2R(), g2 = entity.color2G(), b2 = entity.color2B();

        float age = entity.tickCount + partialTick;
        float scale = getSpawnScale(spawnProgress, phase) * endFade;
        float alpha = getAlpha(spawnProgress, endFade, phase);

        float whiteR = Math.min(1f, r1 * 1.3f);
        float whiteG = Math.min(1f, g1 * 1.3f);
        float whiteB = Math.min(1f, b1 * 1.3f);

        VertexConsumer buf = bufferSource.getBuffer(CollapseRenderType.COLLAPSE);

        Matrix4f mat = ps.last().pose();

        float convergeHeight = 8f * scale;

        renderAscendingPillar(buf, mat, age, scale, alpha, whiteR, whiteG, whiteB, r1, g1, b1);
        renderConvergingBeams(buf, mat, age, scale, alpha, convergeHeight, r1, g1, b1, r2, g2, b2);
        renderSweepingBeams(buf, mat, age, scale, alpha, r1, g1, b1, whiteR, whiteG, whiteB);
        renderBaseEnergyRings(buf, mat, age, scale, alpha, r1, g1, b1, r2, g2, b2);
        renderAscendingRings(buf, mat, age, scale, alpha, convergeHeight, r1, g1, b1, whiteR, whiteG, whiteB);
        renderPulseWaves(buf, mat, age, scale, alpha, r1, g1, b1, whiteR, whiteG, whiteB);

        renderGlowSphere(buf, mat, 0, 0, 0, 0.8f * scale, whiteR, whiteG, whiteB, alpha * 0.9f, 20, 40);
        renderGlowSphere(buf, mat, 0, 0, 0, 0.45f * scale, r1, g1, b1, alpha * 0.7f, 12, 24);
        float corePulse = 1f + (float) Math.sin(age * 0.3f) * 0.2f;
        renderGlowSphere(buf, mat, 0, 0, 0, 0.15f * scale * corePulse, 1f, 1f, 1f, alpha, 8, 16);

        renderOrbitalArcs(buf, mat, age, scale, alpha, r1, g1, b1, r2, g2, b2, whiteR, whiteG, whiteB);
        renderGroundRaySpikes(buf, mat, age, scale, alpha, r1, g1, b1, whiteR, whiteG, whiteB);
    }

    // ──── 上升光柱 ────

    private static void renderAscendingPillar(VertexConsumer buf, Matrix4f mat,
                                               float age, float scale, float alpha,
                                               float wr, float wg, float wb, float r, float g, float b) {
        float pillarHeight = 10f * scale;
        float pillarRadius = 0.35f * scale;
        int pillarSegs = 32;
        int heightSegs = 20;

        for (int hs = 0; hs < heightSegs; hs++) {
            float t0 = (float) hs / heightSegs;
            float t1 = (float) (hs + 1) / heightSegs;
            float y0 = t0 * pillarHeight;
            float y1 = t1 * pillarHeight;

            // 光柱越往上越窄且越白
            float shrink = 1f - t1 * 0.6f;
            float r0 = pillarRadius * shrink;
            float r1 = pillarRadius * (shrink - 0.01f);
            // 颜色渐变：底部偏彩色，顶部偏白
            float colorMix = t1;
            float cr = r + (wr - r) * colorMix;
            float cg = g + (wg - g) * colorMix;
            float cb = b + (wb - b) * colorMix;

            // 光柱亮度脉动
            float pulse = 1f + (float) Math.sin(age * 0.15f + t0 * 3f) * 0.15f;
            float a = alpha * (1f - t1 * 0.5f) * pulse;

            for (int s = 0; s < pillarSegs; s++) {
                float a0 = TWO_PI * s / pillarSegs;
                float a1 = TWO_PI * (s + 1) / pillarSegs;
                float cx0 = (float) Math.cos(a0), sx0 = (float) Math.sin(a0);
                float cx1 = (float) Math.cos(a1), sx1 = (float) Math.sin(a1);

                buf.vertex(mat, cx0 * r0, y0, sx0 * r0).color(cr, cg, cb, a).endVertex();
                buf.vertex(mat, cx0 * r1, y1, sx0 * r1).color(wr, wg, wb, a * 0.7f).endVertex();
                buf.vertex(mat, cx1 * r1, y1, sx1 * r1).color(wr, wg, wb, a * 0.7f).endVertex();

                buf.vertex(mat, cx0 * r0, y0, sx0 * r0).color(cr, cg, cb, a).endVertex();
                buf.vertex(mat, cx1 * r1, y1, sx1 * r1).color(wr, wg, wb, a * 0.7f).endVertex();
                buf.vertex(mat, cx1 * r0, y0, sx1 * r0).color(cr, cg, cb, a).endVertex();
            }
        }

        // 光柱顶端发光球
        renderGlowSphere(buf, mat, 0, pillarHeight, 0, 0.5f * scale, wr, wg, wb, alpha * 0.8f, 12, 24);
    }

    // ──── 汇聚光束 ────

    private static void renderConvergingBeams(VertexConsumer buf, Matrix4f mat,
                                               float age, float scale, float alpha,
                                               float convergeY, float r1, float g1, float b1,
                                               float r2, float g2, float b2) {
        int beams = 10;
        float baseSpread = 7f * scale;

        for (int i = 0; i < beams; i++) {
            float baseAngle = TWO_PI * i / beams;
            // 每束光不同的旋转速率
            float rotSpeed = 0.6f + (i % 3) * 0.25f;
            float yaw = baseAngle + age * 0.015f * rotSpeed;

            // 地面出发点（半径可呼吸式缩放）
            float breathR = baseSpread * (0.85f + (float) Math.sin(age * 0.08f + i * 0.9f) * 0.15f);
            float sx = (float) Math.cos(yaw) * breathR;
            float sz = (float) Math.sin(yaw) * breathR;

            // 光束从地面外圈汇聚到汇聚点
            renderTaperedBeam(buf, mat,
                    sx, 0f, sz,
                    0f, convergeY, 0f,
                    0.2f * scale, 0.03f * scale,
                    r1, g1, b1, r2, g2, b2,
                    alpha * 0.7f, age * 0.04f + i, 12);

            // 每束光旁边的次级细光线
            float sx2 = (float) Math.cos(yaw + 0.08f) * breathR * 1.05f;
            float sz2 = (float) Math.sin(yaw + 0.08f) * breathR * 1.05f;
            renderTaperedBeam(buf, mat,
                    sx2, 0f, sz2,
                    0f, convergeY * 0.85f, 0f,
                    0.06f * scale, 0.01f * scale,
                    r2, g2, b2, 0.9f, 0.9f, 0.9f,
                    alpha * 0.5f, age * 0.05f + i, 8);
        }
    }

    // ──── 旋转扫射光束 ────

    private static void renderSweepingBeams(VertexConsumer buf, Matrix4f mat,
                                              float age, float scale, float alpha,
                                              float r1, float g1, float b1,
                                              float wr, float wg, float wb) {
        int sweeperCount = 8;
        float sweepRadius = 2.5f * scale;
        float sweepLength = 9f * scale;

        for (int i = 0; i < sweeperCount; i++) {
            float baseAngle = TWO_PI * i / sweeperCount;
            // 扫射光速旋转
            float sweepAngle = baseAngle + age * 0.06f * (1f + i * 0.15f);

            // 光束仰角在 ~15° - 75° 之间摆动
            float pitchOsc = (float) Math.sin(age * 0.04f + i * 1.5f);
            float pitch = HALF_PI * (0.15f + pitchOsc * 0.35f);

            float cosA = (float) Math.cos(sweepAngle);
            float sinA = (float) Math.sin(sweepAngle);
            float cosP = (float) Math.cos(pitch);
            float sinP = (float) Math.sin(pitch);

            float dx = cosA * cosP;
            float dy = sinP;
            float dz = sinA * cosP;

            // 光束起点的轨道偏移（使扫射光束看起来自然错落）
            float orbitX = cosA * sweepRadius * 0.3f;
            float orbitZ = sinA * sweepRadius * 0.3f;

            renderTaperedBeam(buf, mat,
                    orbitX, 0.5f * scale, orbitZ,
                    dx * sweepLength + orbitX, dy * sweepLength + 0.5f * scale, dz * sweepLength + orbitZ,
                    0.12f * scale, 0.02f * scale,
                    r1, g1, b1, wr, wg, wb,
                    alpha * 0.6f, age * 0.03f + i, 10);
        }
    }

    // ──── 底部能量环 ────

    private static void renderBaseEnergyRings(VertexConsumer buf, Matrix4f mat,
                                                float age, float scale, float alpha,
                                                float r1, float g1, float b1, float r2, float g2, float b2) {
        for (int ring = 0; ring < RING_COUNT; ring++) {
            // 每个光环有不同倾斜角、旋转速度、半径
            float tiltX = (float) Math.sin(age * 0.03f + ring * 1.0f) * 0.4f;
            float tiltZ = (float) Math.cos(age * 0.025f + ring * 0.8f) * 0.4f;
            float rotSpeed = 0.8f + ring * 0.2f;
            float ringRadius = (1.5f + ring * 0.6f) * scale;
            float ringAlpha = alpha * (0.5f - ring * 0.06f);
            float thickness = (0.04f + ring * 0.015f) * scale;

            int segs = 72;
            // 使用中间矩阵进行倾斜和旋转
            org.joml.Matrix4f ringMat = new org.joml.Matrix4f(mat);
            // 绕 X 倾斜
            ringMat.rotate((float) Math.sin(age * 0.03f + ring * 1.0f) * 0.5f, 1, 0, 0);
            ringMat.rotate((float) Math.cos(age * 0.025f + ring * 0.8f) * 0.5f, 0, 0, 1);
            ringMat.rotate(age * 0.04f * rotSpeed, 0, 1, 0);

            float mix = ring / (float) (RING_COUNT - 1);
            float cr = r1 + (r2 - r1) * mix;
            float cg = g1 + (g2 - g1) * mix;
            float cb = b1 + (b2 - b1) * mix;

            for (int s = 0; s < segs; s++) {
                float a0 = TWO_PI * s / segs;
                float a1 = TWO_PI * (s + 1) / segs;
                float ca0 = (float) Math.cos(a0), sa0 = (float) Math.sin(a0);
                float ca1 = (float) Math.cos(a1), sa1 = (float) Math.sin(a1);

                float ir = ringRadius - thickness;
                float or = ringRadius + thickness;

                float ix0 = ca0 * ir, iz0 = sa0 * ir;
                float ix1 = ca1 * ir, iz1 = sa1 * ir;
                float ox0 = ca0 * or, oz0 = sa0 * or;
                float ox1 = ca1 * or, oz1 = sa1 * or;

                ringVert(buf, ringMat, ox0, 0, oz0, cr, cg, cb, ringAlpha);
                ringVert(buf, ringMat, ix0, 0, iz0, cr, cg, cb, ringAlpha * 0.6f);
                ringVert(buf, ringMat, ix1, 0, iz1, cr, cg, cb, ringAlpha * 0.6f);

                ringVert(buf, ringMat, ox0, 0, oz0, cr, cg, cb, ringAlpha);
                ringVert(buf, ringMat, ix1, 0, iz1, cr, cg, cb, ringAlpha * 0.6f);
                ringVert(buf, ringMat, ox1, 0, oz1, cr, cg, cb, ringAlpha);
            }
        }
    }

    // ──── 上升能量环 ────

    private static void renderAscendingRings(VertexConsumer buf, Matrix4f mat,
                                               float age, float scale, float alpha,
                                               float maxHeight, float r1, float g1, float b1,
                                               float wr, float wg, float wb) {
        int ringCount = 8;
        for (int i = 0; i < ringCount; i++) {
            // 每个环以不同相位周期性从底部向上发射
            float phase = (age * 0.08f + i * 0.125f * TWO_PI) % TWO_PI;
            // 用 sin 映射到 0→1→0 完成一次上下之旅，经过平滑处理
            float t = (float) ((Math.sin(phase) + 1.0) / 2.0);
            float y = t * maxHeight;
            // 环在顶部时半径最小（汇聚点），在底部和中间时最大
            float expandFactor = (float) Math.sin(t * PI);
            float ringR = (1.5f + expandFactor * 2.5f) * scale;
            float ringAlpha = alpha * 0.55f * expandFactor;
            float thick = 0.04f * scale;

            if (ringAlpha < 0.02f) continue;

            int segs = 64;
            float cr = r1 + (wr - r1) * t;
            float cg = g1 + (wg - g1) * t;
            float cb = b1 + (wb - b1) * t;

            // 环自身旋转
            float rot = age * 0.05f + i * 0.5f;
            float cosR = (float) Math.cos(rot), sinR = (float) Math.sin(rot);

            for (int s = 0; s < segs; s++) {
                float a0 = TWO_PI * s / segs;
                float a1 = TWO_PI * (s + 1) / segs;
                float ca0 = (float) Math.cos(a0), sa0 = (float) Math.sin(a0);
                float ca1 = (float) Math.cos(a1), sa1 = (float) Math.sin(a1);

                float ir = ringR - thick;
                float or = ringR + thick;

                float ix0 = ca0 * ir, iz0 = sa0 * ir;
                float ix1 = ca1 * ir, iz1 = sa1 * ir;
                float ox0 = ca0 * or, oz0 = sa0 * or;
                float ox1 = ca1 * or, oz1 = sa1 * or;

                // 绕 Y 旋转
                float rix0 = ix0 * cosR - iz0 * sinR, riz0 = ix0 * sinR + iz0 * cosR;
                float rix1 = ix1 * cosR - iz1 * sinR, riz1 = ix1 * sinR + iz1 * cosR;
                float rox0 = ox0 * cosR - oz0 * sinR, roz0 = ox0 * sinR + oz0 * cosR;
                float rox1 = ox1 * cosR - oz1 * sinR, roz1 = ox1 * sinR + oz1 * cosR;

                ringVert(buf, mat, rox0, y, roz0, cr, cg, cb, ringAlpha);
                ringVert(buf, mat, rix0, y, riz0, cr, cg, cb, ringAlpha * 0.5f);
                ringVert(buf, mat, rix1, y, riz1, cr, cg, cb, ringAlpha * 0.5f);

                ringVert(buf, mat, rox0, y, roz0, cr, cg, cb, ringAlpha);
                ringVert(buf, mat, rix1, y, riz1, cr, cg, cb, ringAlpha * 0.5f);
                ringVert(buf, mat, rox1, y, roz1, cr, cg, cb, ringAlpha);
            }
        }
    }

    // ──── 球形脉冲波 ────

    private static void renderPulseWaves(VertexConsumer buf, Matrix4f mat,
                                          float age, float scale, float alpha,
                                          float r1, float g1, float b1,
                                          float wr, float wg, float wb) {
        int waveCount = 4;
        for (int i = 0; i < waveCount; i++) {
            // 每个脉冲波有不同周期
            float cycle = 2.5f + i * 0.8f;
            float phase = (age % cycle) / cycle;
            float waveR = phase * 8f * scale;
            float waveAlpha = alpha * (1f - phase) * (1f - phase) * 0.3f;

            if (waveAlpha < 0.01f) continue;

            int latSegs = 16;
            int lonSegs = 32;
            for (int la = 0; la < latSegs; la++) {
                float phi0 = -HALF_PI + PI * la / latSegs;
                float phi1 = -HALF_PI + PI * (la + 1) / latSegs;
                float cp0 = (float) Math.cos(phi0), sp0 = (float) Math.sin(phi0);
                float cp1 = (float) Math.cos(phi1), sp1 = (float) Math.sin(phi1);

                for (int lo = 0; lo < lonSegs; lo++) {
                    float th0 = TWO_PI * lo / lonSegs;
                    float th1 = TWO_PI * (lo + 1) / lonSegs;
                    float cth0 = (float) Math.cos(th0), sth0 = (float) Math.sin(th0);
                    float cth1 = (float) Math.cos(th1), sth1 = (float) Math.sin(th1);

                    float x0 = cp0 * cth0 * waveR, y0 = sp0 * waveR, z0 = cp0 * sth0 * waveR;
                    float x1 = cp0 * cth1 * waveR, y1 = sp0 * waveR, z1 = cp0 * sth1 * waveR;
                    float x2 = cp1 * cth0 * waveR, y2 = sp1 * waveR, z2 = cp1 * sth0 * waveR;
                    float x3 = cp1 * cth1 * waveR, y3 = sp1 * waveR, z3 = cp1 * sth1 * waveR;

                    ringVert(buf, mat, x0, y0, z0, wr, wg, wb, waveAlpha);
                    ringVert(buf, mat, x2, y2, z2, wr, wg, wb, waveAlpha);
                    ringVert(buf, mat, x1, y1, z1, wr, wg, wb, waveAlpha);

                    ringVert(buf, mat, x1, y1, z1, wr, wg, wb, waveAlpha);
                    ringVert(buf, mat, x2, y2, z2, wr, wg, wb, waveAlpha);
                    ringVert(buf, mat, x3, y3, z3, wr, wg, wb, waveAlpha);
                }
            }
        }
    }

    // ──── 环绕光弧 ────

    private static void renderOrbitalArcs(VertexConsumer buf, Matrix4f mat,
                                            float age, float scale, float alpha,
                                            float r1, float g1, float b1,
                                            float r2, float g2, float b2,
                                            float wr, float wg, float wb) {
        int arcCount = 5;
        for (int i = 0; i < arcCount; i++) {
            float orbitR = (2f + i * 0.8f) * scale;
            float orbitSpeed = 0.04f + i * 0.015f;
            float orbitAngle = age * orbitSpeed;
            float orbY = (1.5f + i * 1.2f) * scale;

            // 在轨道不同位置散布小光点
            int dots = 6;
            for (int d = 0; d < dots; d++) {
                float dotAngle = orbitAngle + TWO_PI * d / dots;
                float dx = (float) Math.cos(dotAngle) * orbitR;
                float dz = (float) Math.sin(dotAngle) * orbitR;

                // 小光点呼吸式闪烁
                float flicker = 0.5f + 0.5f * (float) Math.sin(age * 0.2f + i * 3.1f + d * 1.7f);
                float dotAlpha = alpha * 0.4f * flicker;
                float dotR = 0.08f * scale * (0.7f + flicker * 0.3f);

                renderGlowSphere(buf, mat, dx, orbY, dz, dotR, wr, wg, wb, dotAlpha, 6, 12);
            }

            // 部分光环之间画弧线连接
            if (i < arcCount - 1) {
                float nextR = (2f + (i + 1) * 0.8f) * scale;
                float nextY = (1.5f + (i + 1) * 1.2f) * scale;
                int arcPts = 16;
                float arcAlpha = alpha * 0.2f;
                for (int p = 0; p < arcPts - 1; p++) {
                    float t0 = (float) p / (arcPts - 1);
                    float t1 = (float) (p + 1) / (arcPts - 1);
                    float ang0 = orbitAngle + t0 * TWO_PI * 0.3f;
                    float ang1 = orbitAngle + t1 * TWO_PI * 0.3f;
                    float rr0 = orbitR + (nextR - orbitR) * t0;
                    float rr1 = orbitR + (nextR - orbitR) * t1;
                    float yy0 = orbY + (nextY - orbY) * t0;
                    float yy1 = orbY + (nextY - orbY) * t1;

                    float px0 = (float) Math.cos(ang0) * rr0;
                    float pz0 = (float) Math.sin(ang0) * rr0;
                    float px1 = (float) Math.cos(ang1) * rr1;
                    float pz1 = (float) Math.sin(ang1) * rr1;

                    float mixR = r1 + (r2 - r1) * t0;
                    float mixG = g1 + (g2 - g1) * t0;
                    float mixB = b1 + (b2 - b1) * t0;

                    ringVert(buf, mat, px0, yy0, pz0, mixR, mixG, mixB, arcAlpha);
                    ringVert(buf, mat, px1, yy1, pz1, mixR, mixG, mixB, arcAlpha * 0.5f);
                    ringVert(buf, mat, px1, yy1 + 0.05f * scale, pz1, mixR, mixG, mixB, arcAlpha * 0.5f);

                    ringVert(buf, mat, px0, yy0, pz0, mixR, mixG, mixB, arcAlpha);
                    ringVert(buf, mat, px1, yy1 + 0.05f * scale, pz1, mixR, mixG, mixB, arcAlpha * 0.5f);
                    ringVert(buf, mat, px0, yy0 + 0.05f * scale, pz0, mixR, mixG, mixB, arcAlpha * 0.3f);
                }
            }
        }
    }

    // ──── 地面散射光刺 ────

    private static void renderGroundRaySpikes(VertexConsumer buf, Matrix4f mat,
                                                float age, float scale, float alpha,
                                                float r1, float g1, float b1,
                                                float wr, float wg, float wb) {
        int spikeCount = 24;
        float maxSpikeLen = 5f * scale;

        for (int i = 0; i < spikeCount; i++) {
            float angle = TWO_PI * i / spikeCount + age * 0.02f;
            // 光刺长度周期性变化
            float lenVar = 0.5f + 0.5f * (float) Math.sin(age * 0.07f + i * 1.3f);
            float spikeLen = maxSpikeLen * (0.3f + lenVar * 0.7f);
            float spikeAlpha = alpha * 0.5f * lenVar;

            float cx = (float) Math.cos(angle);
            float cz = (float) Math.sin(angle);

            // 从中心向外辐射的三角形光刺
            float innerW = 0.08f * scale;
            float outerW = 0.01f * scale;

            // 三角形 = 中心窄边 → 尖端
            float perpX = -cz, perpZ = cx;

            float i0x = cx * 0.1f + perpX * innerW;
            float i0z = cz * 0.1f + perpZ * innerW;
            float i1x = cx * 0.1f - perpX * innerW;
            float i1z = cz * 0.1f - perpZ * innerW;
            float ox = cx * spikeLen;
            float oz = cz * spikeLen;

            // 光刺颜色：基部偏彩色，尖端偏白
            ringVert(buf, mat, i0x, 0.02f * scale, i0z, r1, g1, b1, spikeAlpha);
            ringVert(buf, mat, i1x, 0.02f * scale, i1z, r1, g1, b1, spikeAlpha);
            ringVert(buf, mat, ox, 0.02f * scale, oz, wr, wg, wb, spikeAlpha * 0.1f);
        }
    }

    // ──── 锥形光束渲染辅助 ────

    /** 绘制从 (sx,sy,sz) 到 (ex,ey,ez) 的锥形光束（近端粗，远端细） */
    private static void renderTaperedBeam(VertexConsumer buf, Matrix4f mat,
                                            float sx, float sy, float sz,
                                            float ex, float ey, float ez,
                                            float startWidth, float endWidth,
                                            float rStart, float gStart, float bStart,
                                            float rEnd, float gEnd, float bEnd,
                                            float alpha, float seed, int segments) {
        float dx = ex - sx, dy = ey - sy, dz = ez - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        float ndx = dx / len, ndy = dy / len, ndz = dz / len;

        // 构建正交基
        float ux, uy, uz;
        if (Math.abs(ndy) < 0.99f) {
            ux = -ndz; uy = 0; uz = ndx;
        } else {
            ux = ndz; uy = 0; uz = -ndx;
        }
        float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= uLen; uy /= uLen; uz /= uLen;

        float vx = ndy * uz - ndz * uy;
        float vy = ndz * ux - ndx * uz;
        float vz = ndx * uy - ndy * ux;

        int segs = segments;
        for (int s = 0; s < segs; s++) {
            float t0 = (float) s / segs;
            float t1 = (float) (s + 1) / segs;

            float w0 = startWidth + (endWidth - startWidth) * t0;
            float w1 = startWidth + (endWidth - startWidth) * t1;

            float fade0 = 1f - t0 * t0 * 0.7f;
            float fade1 = 1f - t1 * t1 * 0.7f;

            // 螺旋波动
            float twist = (float) Math.sin(t0 * 8f + seed) * w0 * 0.3f;
            float twist1 = (float) Math.sin(t1 * 8f + seed) * w1 * 0.3f;

            float px0 = sx + ndx * t0 * len;
            float py0 = sy + ndy * t0 * len;
            float pz0 = sz + ndz * t0 * len;
            float px1 = sx + ndx * t1 * len;
            float py1 = sy + ndy * t1 * len;
            float pz1 = sz + ndz * t1 * len;

            float mixR0 = rStart + (rEnd - rStart) * t0;
            float mixG0 = gStart + (gEnd - gStart) * t0;
            float mixB0 = bStart + (bEnd - bStart) * t0;
            float mixR1 = rStart + (rEnd - rStart) * t1;
            float mixG1 = gStart + (gEnd - gStart) * t1;
            float mixB1 = bStart + (bEnd - bStart) * t1;

            float t0x = px0 + ux * (w0 + twist) + vx * twist;
            float t0y = py0 + uy * (w0 + twist) + vy * twist;
            float t0z = pz0 + uz * (w0 + twist) + vz * twist;
            float b0x = px0 - ux * (w0 - twist) - vx * twist;
            float b0y = py0 - uy * (w0 - twist) - vy * twist;
            float b0z = pz0 - uz * (w0 - twist) - vz * twist;

            float t1x = px1 + ux * (w1 + twist1) + vx * twist1;
            float t1y = py1 + uy * (w1 + twist1) + vy * twist1;
            float t1z = pz1 + uz * (w1 + twist1) + vz * twist1;
            float b1x = px1 - ux * (w1 - twist1) - vx * twist1;
            float b1y = py1 - uy * (w1 - twist1) - vy * twist1;
            float b1z = pz1 - uz * (w1 - twist1) - vz * twist1;

            float a0 = alpha * fade0;
            float a1 = alpha * fade1;

            // 双面四边形
            ringVert(buf, mat, t0x, t0y, t0z, mixR0, mixG0, mixB0, a0);
            ringVert(buf, mat, b0x, b0y, b0z, mixR0, mixG0, mixB0, a0);
            ringVert(buf, mat, b1x, b1y, b1z, mixR1, mixG1, mixB1, a1);

            ringVert(buf, mat, t0x, t0y, t0z, mixR0, mixG0, mixB0, a0);
            ringVert(buf, mat, b1x, b1y, b1z, mixR1, mixG1, mixB1, a1);
            ringVert(buf, mat, t1x, t1y, t1z, mixR1, mixG1, mixB1, a1);

            ringVert(buf, mat, t0x, t0y, t0z, mixR0, mixG0, mixB0, a0);
            ringVert(buf, mat, b1x, b1y, b1z, mixR1, mixG1, mixB1, a1);
            ringVert(buf, mat, b0x, b0y, b0z, mixR0, mixG0, mixB0, a0);

            ringVert(buf, mat, t0x, t0y, t0z, mixR0, mixG0, mixB0, a0);
            ringVert(buf, mat, t1x, t1y, t1z, mixR1, mixG1, mixB1, a1);
            ringVert(buf, mat, b1x, b1y, b1z, mixR1, mixG1, mixB1, a1);
        }
    }

    private static void ringVert(VertexConsumer buf, Matrix4f mat,
                                  float x, float y, float z, float r, float g, float b, float a) {
        buf.vertex(mat, x, y, z).color(r, g, b, a).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染类型 1: 四维超立方体投影
    // ═══════════════════════════════════════════════════════════════

    /** 4D 超立方体 16 个顶点（所有 ±1 组合） */
    private static final float[][] TESSERACT_VERTICES_4D;
    /** 超立方体 32 条边（索引对，汉明距离=1） */
    private static final int[][] TESSERACT_EDGES;

    static {
        // 生成 16 个顶点
        TESSERACT_VERTICES_4D = new float[16][4];
        for (int i = 0; i < 16; i++) {
            TESSERACT_VERTICES_4D[i][0] = (i & 1) != 0 ? 1f : -1f;
            TESSERACT_VERTICES_4D[i][1] = (i & 2) != 0 ? 1f : -1f;
            TESSERACT_VERTICES_4D[i][2] = (i & 4) != 0 ? 1f : -1f;
            TESSERACT_VERTICES_4D[i][3] = (i & 8) != 0 ? 1f : -1f;
        }

        // 生成 32 条边（汉明距离 = 1）
        java.util.List<int[]> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int j = i + 1; j < 16; j++) {
                int diff = 0;
                int xor = i ^ j;
                // 检查是否是 2 的幂（汉明距离 = 1）
                if (xor != 0 && (xor & (xor - 1)) == 0) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        TESSERACT_EDGES = edges.toArray(new int[0][]);
    }

    private static final float EDGE_THICKNESS = 0.04f;

    private static void renderHypercube(CollapseEntity entity, float partialTick, PoseStack ps,
                                         float spawnProgress, float endFade, CollapsePhase phase,
                                         MultiBufferSource bufferSource) {
        float r1 = entity.color1R(), g1 = entity.color1G(), b1 = entity.color1B();
        float r2 = entity.color2R(), g2 = entity.color2G(), b2 = entity.color2B();

        float age = entity.tickCount + partialTick;
        float scale = getSpawnScale(spawnProgress, phase) * endFade;
        float alpha = getAlpha(spawnProgress, endFade, phase);

        // 4D 旋转角度（在 XW, YW, ZW 三个平面上旋转产生有趣的 3D 投影）
        float angleXW = age * 0.03f;
        float angleYW = age * 0.04f;
        float angleZW = age * 0.025f;

        // 将 16 个 4D 顶点旋转并投影到 3D
        float[][] projected3D = new float[16][3];
        for (int i = 0; i < 16; i++) {
            float x = TESSERACT_VERTICES_4D[i][0];
            float y = TESSERACT_VERTICES_4D[i][1];
            float z = TESSERACT_VERTICES_4D[i][2];
            float w = TESSERACT_VERTICES_4D[i][3];

            // 4D 旋转：XW 平面
            float cosA = (float) Math.cos(angleXW);
            float sinA = (float) Math.sin(angleXW);
            float nx = x * cosA - w * sinA;
            float nw = x * sinA + w * cosA;
            x = nx; w = nw;

            // YW 平面
            cosA = (float) Math.cos(angleYW);
            sinA = (float) Math.sin(angleYW);
            float ny = y * cosA - w * sinA;
            nw = y * sinA + w * cosA;
            y = ny; w = nw;

            // ZW 平面
            cosA = (float) Math.cos(angleZW);
            sinA = (float) Math.sin(angleZW);
            float nz = z * cosA - w * sinA;
            nw = z * sinA + w * cosA;
            z = nz; w = nw;

            // 4D→3D 透视投影
            float distance = 3.5f;
            float factor = distance / (distance + w);
            projected3D[i][0] = x * factor * scale * 2.5f;
            projected3D[i][1] = y * factor * scale * 2.5f;
            projected3D[i][2] = z * factor * scale * 2.5f;
        }

        VertexConsumer buf = bufferSource.getBuffer(CollapseRenderType.COLLAPSE);

        Matrix4f mat = ps.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camDir = new Vec3(cam.getLookVector());

        // 渲染 32 条边为相机朝向的厚四边形
        for (int[] edge : TESSERACT_EDGES) {
            float[] va = projected3D[edge[0]];
            float[] vb = projected3D[edge[1]];

            Vec3 a = new Vec3(va[0], va[1], va[2]);
            Vec3 b = new Vec3(vb[0], vb[1], vb[2]);

            Vec3 edgeDir = b.subtract(a).normalize();
            Vec3 perp = edgeDir.cross(camDir);
            double pLen = perp.length();
            if (pLen < 0.001) perp = new Vec3(1, 0, 0);
            else perp = perp.scale(1.0 / pLen);
            perp = perp.scale(EDGE_THICKNESS * scale);

            Vec3 v0 = a.add(perp);
            Vec3 v1 = a.subtract(perp);
            Vec3 v2 = b.subtract(perp);
            Vec3 v3 = b.add(perp);

            // 沿边的渐变颜色
            float ar = r1, ag = g1, ab = b1;
            float br = r2, bg = g2, bb = b2;

            // 双面四边形
            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(ar, ag, ab, alpha).endVertex();
            buf.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).color(ar, ag, ab, alpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(br, bg, bb, alpha).endVertex();

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(ar, ag, ab, alpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(br, bg, bb, alpha).endVertex();
            buf.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).color(br, bg, bb, alpha).endVertex();

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(ar, ag, ab, alpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(br, bg, bb, alpha).endVertex();
            buf.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).color(ar, ag, ab, alpha).endVertex();

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(ar, ag, ab, alpha).endVertex();
            buf.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).color(br, bg, bb, alpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(br, bg, bb, alpha).endVertex();
        }

        // 顶点处发光小球
        for (float[] v : projected3D) {
            renderGlowSphere(buf, mat, v[0], v[1], v[2], 0.1f * scale, r1, g1, b1, alpha * 0.6f, 8, 16);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染类型 2: 二十面体
    // ═══════════════════════════════════════════════════════════════

    private static final float ICOSA_RADIUS = 2.5f;
    private static final float ICOSA_EDGE_THICK = 0.05f;
    private static final float[][] ICOSA_VERTS;
    private static final int[][] ICOSA_EDGES_LIST;
    private static final int[][] ICOSA_FACES;

    static {
        // 构建二十面体顶点（黄金矩形法）
        float phi = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);
        float[][] raw = {
                {-1,  phi,  0}, { 1,  phi,  0}, {-1, -phi,  0}, { 1, -phi,  0},
                { 0, -1,  phi}, { 0,  1,  phi}, { 0, -1, -phi}, { 0,  1, -phi},
                { phi,  0, -1}, { phi,  0,  1}, {-phi,  0, -1}, {-phi,  0,  1},
        };
        ICOSA_VERTS = new float[12][3];
        for (int i = 0; i < 12; i++) {
            float len = (float) Math.sqrt(raw[i][0] * raw[i][0] + raw[i][1] * raw[i][1] + raw[i][2] * raw[i][2]);
            ICOSA_VERTS[i][0] = raw[i][0] / len * ICOSA_RADIUS;
            ICOSA_VERTS[i][1] = raw[i][1] / len * ICOSA_RADIUS;
            ICOSA_VERTS[i][2] = raw[i][2] / len * ICOSA_RADIUS;
        }

        // 20 个三角形面（按顶点索引）
        int[] faceTriplets = {
                0, 11, 5,   0, 5, 1,   0, 1, 7,   0, 7, 10,   0, 10, 11,
                3, 9, 4,   3, 4, 2,   3, 2, 6,   3, 6, 8,   3, 8, 9,
                1, 5, 9,   5, 11, 4,   11, 10, 2,   10, 7, 6,   7, 1, 8,
                4, 9, 5,   2, 4, 11,   6, 2, 10,   8, 6, 7,   9, 8, 1,
        };
        ICOSA_FACES = new int[20][3];
        for (int t = 0; t < 20; t++) {
            ICOSA_FACES[t][0] = faceTriplets[t * 3];
            ICOSA_FACES[t][1] = faceTriplets[t * 3 + 1];
            ICOSA_FACES[t][2] = faceTriplets[t * 3 + 2];
        }

        // 30 条唯一边
        java.util.Set<Long> edgeSet = new java.util.LinkedHashSet<>();
        for (int t = 0; t < 20; t++) {
            int a = faceTriplets[t * 3], b = faceTriplets[t * 3 + 1], c = faceTriplets[t * 3 + 2];
            addEdge(edgeSet, a, b);
            addEdge(edgeSet, b, c);
            addEdge(edgeSet, c, a);
        }
        ICOSA_EDGES_LIST = new int[edgeSet.size()][2];
        int idx = 0;
        for (long e : edgeSet) {
            ICOSA_EDGES_LIST[idx][0] = (int) (e >> 32);
            ICOSA_EDGES_LIST[idx][1] = (int) (e & 0xFFFFFFFFL);
            idx++;
        }
    }

    private static void addEdge(java.util.Set<Long> set, int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        set.add(((long) lo << 32) | (hi & 0xFFFFFFFFL));
    }

    private static void renderIcosahedron(CollapseEntity entity, float partialTick, PoseStack ps,
                                           float spawnProgress, float endFade, CollapsePhase phase,
                                           MultiBufferSource bufferSource) {
        float r1 = entity.color1R(), g1 = entity.color1G(), b1 = entity.color1B();
        float r2 = entity.color2R(), g2 = entity.color2G(), b2 = entity.color2B();

        float age = entity.tickCount + partialTick;
        float scale = getSpawnScale(spawnProgress, phase) * endFade;
        float alpha = getAlpha(spawnProgress, endFade, phase);

        ps.pushPose();

        // 二十面体自身旋转
        ps.mulPose(new Quaternionf().rotateAxis(age * 0.03f, 1, 0.3f, 0.5f));

        Matrix4f mat = ps.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camDir = new Vec3(cam.getLookVector());

        VertexConsumer buf = bufferSource.getBuffer(CollapseRenderType.COLLAPSE);

        // ── 面片渲染（半透明填充） ──
        float faceAlpha = alpha * 0.25f;
        for (int[] face : ICOSA_FACES) {
            float[] a = ICOSA_VERTS[face[0]];
            float[] b = ICOSA_VERTS[face[1]];
            float[] c = ICOSA_VERTS[face[2]];

            float ax = a[0] * scale, ay = a[1] * scale, az = a[2] * scale;
            float bx = b[0] * scale, by = b[1] * scale, bz = b[2] * scale;
            float cx = c[0] * scale, cy = c[1] * scale, cz = c[2] * scale;

            // 正面
            buf.vertex(mat, ax, ay, az).color(r1, g1, b1, faceAlpha).endVertex();
            buf.vertex(mat, bx, by, bz).color(r2, g2, b2, faceAlpha).endVertex();
            buf.vertex(mat, cx, cy, cz).color(r1, g1, b1, faceAlpha).endVertex();

            // 反面
            buf.vertex(mat, ax, ay, az).color(r1, g1, b1, faceAlpha).endVertex();
            buf.vertex(mat, cx, cy, cz).color(r1, g1, b1, faceAlpha).endVertex();
            buf.vertex(mat, bx, by, bz).color(r2, g2, b2, faceAlpha).endVertex();
        }

        // ── 线框边渲染（相机朝向厚边） ──
        float thick = ICOSA_EDGE_THICK * scale;
        for (int[] edge : ICOSA_EDGES_LIST) {
            float[] ra = ICOSA_VERTS[edge[0]];
            float[] rb = ICOSA_VERTS[edge[1]];

            Vec3 va = new Vec3(ra[0] * scale, ra[1] * scale, ra[2] * scale);
            Vec3 vb = new Vec3(rb[0] * scale, rb[1] * scale, rb[2] * scale);

            Vec3 edgeDir = vb.subtract(va).normalize();
            Vec3 perp = edgeDir.cross(camDir);
            double pLen = perp.length();
            if (pLen < 0.001) perp = new Vec3(1, 0, 0);
            else perp = perp.scale(1.0 / pLen);
            perp = perp.scale(thick);

            Vec3 v0 = va.add(perp);
            Vec3 v1 = va.subtract(perp);
            Vec3 v2 = vb.subtract(perp);
            Vec3 v3 = vb.add(perp);

            float edgeAlpha = alpha * 0.9f;

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r1, g1, b1, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).color(r1, g1, b1, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r2, g2, b2, edgeAlpha).endVertex();

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r1, g1, b1, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r2, g2, b2, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).color(r2, g2, b2, edgeAlpha).endVertex();

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r1, g1, b1, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r2, g2, b2, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).color(r1, g1, b1, edgeAlpha).endVertex();

            buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r1, g1, b1, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).color(r2, g2, b2, edgeAlpha).endVertex();
            buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r2, g2, b2, edgeAlpha).endVertex();
        }

        ps.popPose();
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染类型 3: 变换多棱柱（3→20 棱连续变换）
    // ═══════════════════════════════════════════════════════════════

    private static final float PRISM_HEIGHT = 4.0f;
    private static final float PRISM_RADIUS = 2.0f;
    private static final int PRISM_MIN_SIDES = 3;
    private static final int PRISM_MAX_SIDES = 20;

    private static void renderTransformingPrism(CollapseEntity entity, float partialTick, PoseStack ps,
                                                  float spawnProgress, float endFade, CollapsePhase phase,
                                                  MultiBufferSource bufferSource) {
        float r1 = entity.color1R(), g1 = entity.color1G(), b1 = entity.color1B();
        float r2 = entity.color2R(), g2 = entity.color2G(), b2 = entity.color2B();

        float age = entity.tickCount + partialTick;
        float scale = getSpawnScale(spawnProgress, phase) * endFade;
        float alpha = getAlpha(spawnProgress, endFade, phase);

        // 棱数在 3~20 之间连续变化
        float sidesFloat = PRISM_MIN_SIDES + (PRISM_MAX_SIDES - PRISM_MIN_SIDES)
                * ((float) Math.sin(age * 0.03f) * 0.5f + 0.5f);
        int currentSides = Math.max(PRISM_MIN_SIDES, Math.min(PRISM_MAX_SIDES,
                Math.round(sidesFloat)));

        float halfH = PRISM_HEIGHT / 2f * scale;
        float rad = PRISM_RADIUS * scale;

        ps.pushPose();
        // 棱柱绕 Y 轴自转
        ps.mulPose(new Quaternionf().rotateAxis(age * 0.04f, 0, 1, 0));

        Matrix4f mat = ps.last().pose();

        VertexConsumer buf = bufferSource.getBuffer(CollapseRenderType.COLLAPSE);

        // 计算当前棱数的顶点位置
        float[][] topVerts = new float[currentSides][3];
        float[][] botVerts = new float[currentSides][3];

        for (int i = 0; i < currentSides; i++) {
            double angle = TWO_PI * i / currentSides;
            float x = (float) Math.cos(angle) * rad;
            float z = (float) Math.sin(angle) * rad;
            topVerts[i][0] = x;
            topVerts[i][1] = halfH;
            topVerts[i][2] = z;
            botVerts[i][0] = x;
            botVerts[i][1] = -halfH;
            botVerts[i][2] = z;
        }

        // ── 侧面渲染 ──
        float sideAlpha = alpha * 0.6f;
        for (int i = 0; i < currentSides; i++) {
            int j = (i + 1) % currentSides;

            float t0x = topVerts[i][0], t0y = topVerts[i][1], t0z = topVerts[i][2];
            float t1x = topVerts[j][0], t1y = topVerts[j][1], t1z = topVerts[j][2];
            float b0x = botVerts[i][0], b0y = botVerts[i][1], b0z = botVerts[i][2];
            float b1x = botVerts[j][0], b1y = botVerts[j][1], b1z = botVerts[j][2];

            // 四边形 = 两个三角形（双面）
            buf.vertex(mat, t0x, t0y, t0z).color(r1, g1, b1, sideAlpha).endVertex();
            buf.vertex(mat, b0x, b0y, b0z).color(r2, g2, b2, sideAlpha).endVertex();
            buf.vertex(mat, b1x, b1y, b1z).color(r2, g2, b2, sideAlpha).endVertex();

            buf.vertex(mat, t0x, t0y, t0z).color(r1, g1, b1, sideAlpha).endVertex();
            buf.vertex(mat, b1x, b1y, b1z).color(r2, g2, b2, sideAlpha).endVertex();
            buf.vertex(mat, t1x, t1y, t1z).color(r1, g1, b1, sideAlpha).endVertex();

            // 反面
            buf.vertex(mat, t0x, t0y, t0z).color(r1, g1, b1, sideAlpha).endVertex();
            buf.vertex(mat, b1x, b1y, b1z).color(r2, g2, b2, sideAlpha).endVertex();
            buf.vertex(mat, b0x, b0y, b0z).color(r2, g2, b2, sideAlpha).endVertex();

            buf.vertex(mat, t0x, t0y, t0z).color(r1, g1, b1, sideAlpha).endVertex();
            buf.vertex(mat, t1x, t1y, t1z).color(r1, g1, b1, sideAlpha).endVertex();
            buf.vertex(mat, b1x, b1y, b1z).color(r2, g2, b2, sideAlpha).endVertex();
        }

        // ── 顶面三角形扇 ──
        float capAlpha = alpha * 0.4f;
        for (int i = 0; i < currentSides; i++) {
            int j = (i + 1) % currentSides;
            buf.vertex(mat, topVerts[i][0], topVerts[i][1], topVerts[i][2]).color(r1, g1, b1, capAlpha).endVertex();
            buf.vertex(mat, topVerts[j][0], topVerts[j][1], topVerts[j][2]).color(r1, g1, b1, capAlpha).endVertex();
            buf.vertex(mat, 0, halfH, 0).color(r2, g2, b2, capAlpha * 0.5f).endVertex();

            // 反面
            buf.vertex(mat, topVerts[i][0], topVerts[i][1], topVerts[i][2]).color(r1, g1, b1, capAlpha).endVertex();
            buf.vertex(mat, 0, halfH, 0).color(r2, g2, b2, capAlpha * 0.5f).endVertex();
            buf.vertex(mat, topVerts[j][0], topVerts[j][1], topVerts[j][2]).color(r1, g1, b1, capAlpha).endVertex();
        }

        // ── 底面三角形扇 ──
        for (int i = 0; i < currentSides; i++) {
            int j = (i + 1) % currentSides;
            buf.vertex(mat, botVerts[i][0], botVerts[i][1], botVerts[i][2]).color(r1, g1, b1, capAlpha).endVertex();
            buf.vertex(mat, 0, -halfH, 0).color(r2, g2, b2, capAlpha * 0.5f).endVertex();
            buf.vertex(mat, botVerts[j][0], botVerts[j][1], botVerts[j][2]).color(r1, g1, b1, capAlpha).endVertex();

            buf.vertex(mat, botVerts[i][0], botVerts[i][1], botVerts[i][2]).color(r1, g1, b1, capAlpha).endVertex();
            buf.vertex(mat, botVerts[j][0], botVerts[j][1], botVerts[j][2]).color(r1, g1, b1, capAlpha).endVertex();
            buf.vertex(mat, 0, -halfH, 0).color(r2, g2, b2, capAlpha * 0.5f).endVertex();
        }

        // ── 边框线（棱线） ──
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camDir = new Vec3(cam.getLookVector());
        float edgeThick = 0.04f * scale;
        float edgeAlpha = alpha * 0.8f;

        for (int i = 0; i < currentSides; i++) {
            // 垂直棱
            Vec3 va = new Vec3(topVerts[i][0], topVerts[i][1], topVerts[i][2]);
            Vec3 vb = new Vec3(botVerts[i][0], botVerts[i][1], botVerts[i][2]);
            renderThickEdge(buf, mat, va, vb, camDir, edgeThick, r1, g1, b1, edgeAlpha);
        }

        // 顶面轮廓边
        for (int i = 0; i < currentSides; i++) {
            int j = (i + 1) % currentSides;
            Vec3 va = new Vec3(topVerts[i][0], topVerts[i][1], topVerts[i][2]);
            Vec3 vb = new Vec3(topVerts[j][0], topVerts[j][1], topVerts[j][2]);
            renderThickEdge(buf, mat, va, vb, camDir, edgeThick * 0.8f, r2, g2, b2, edgeAlpha * 0.7f);
        }

        // 底面轮廓边
        for (int i = 0; i < currentSides; i++) {
            int j = (i + 1) % currentSides;
            Vec3 va = new Vec3(botVerts[i][0], botVerts[i][1], botVerts[i][2]);
            Vec3 vb = new Vec3(botVerts[j][0], botVerts[j][1], botVerts[j][2]);
            renderThickEdge(buf, mat, va, vb, camDir, edgeThick * 0.8f, r2, g2, b2, edgeAlpha * 0.7f);
        }

        ps.popPose();
    }

    // ═══════════════════════════════════════════════════════════════
    // 通用渲染辅助方法
    // ═══════════════════════════════════════════════════════════════

    /** 渲染相机朝向的厚边（四边形） */
    private static void renderThickEdge(VertexConsumer buf, Matrix4f mat,
                                         Vec3 a, Vec3 b, Vec3 camDir,
                                         float thickness, float r, float g, float blu, float alpha) {
        Vec3 edgeDir = b.subtract(a).normalize();
        Vec3 perp = edgeDir.cross(camDir);
        double pLen = perp.length();
        if (pLen < 0.001) perp = new Vec3(1, 0, 0);
        else perp = perp.scale(1.0 / pLen);
        perp = perp.scale(thickness);

        Vec3 v0 = a.add(perp);
        Vec3 v1 = a.subtract(perp);
        Vec3 v2 = b.subtract(perp);
        Vec3 v3 = b.add(perp);

        buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r, g, blu, alpha).endVertex();

        buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).color(r, g, blu, alpha).endVertex();

        buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).color(r, g, blu, alpha).endVertex();

        buf.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).color(r, g, blu, alpha).endVertex();
        buf.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).color(r, g, blu, alpha).endVertex();
    }

    /** 渲染发光小球（UV 球） */
    private static void renderGlowSphere(VertexConsumer buf, Matrix4f mat,
                                          float cx, float cy, float cz,
                                          float radius, float r, float g, float b, float alpha,
                                          int latSegs, int lonSegs) {
        for (int la = 0; la < latSegs; la++) {
            float phi0 = -HALF_PI + PI * la / latSegs;
            float phi1 = -HALF_PI + PI * (la + 1) / latSegs;
            float cp0 = (float) Math.cos(phi0), sp0 = (float) Math.sin(phi0);
            float cp1 = (float) Math.cos(phi1), sp1 = (float) Math.sin(phi1);

            for (int lo = 0; lo < lonSegs; lo++) {
                float th0 = TWO_PI * lo / lonSegs;
                float th1 = TWO_PI * (lo + 1) / lonSegs;
                float cth0 = (float) Math.cos(th0), sth0 = (float) Math.sin(th0);
                float cth1 = (float) Math.cos(th1), sth1 = (float) Math.sin(th1);

                float x0 = cx + cp0 * cth0 * radius, y0 = cy + sp0 * radius, z0 = cz + cp0 * sth0 * radius;
                float x1 = cx + cp0 * cth1 * radius, y1 = cy + sp0 * radius, z1 = cz + cp0 * sth1 * radius;
                float x2 = cx + cp1 * cth0 * radius, y2 = cy + sp1 * radius, z2 = cz + cp1 * sth0 * radius;
                float x3 = cx + cp1 * cth1 * radius, y3 = cy + sp1 * radius, z3 = cz + cp1 * sth1 * radius;

                vert(buf, mat, x0, y0, z0, r, g, b, alpha);
                vert(buf, mat, x2, y2, z2, r, g, b, alpha);
                vert(buf, mat, x1, y1, z1, r, g, b, alpha);

                vert(buf, mat, x1, y1, z1, r, g, b, alpha);
                vert(buf, mat, x2, y2, z2, r, g, b, alpha);
                vert(buf, mat, x3, y3, z3, r, g, b, alpha);
            }
        }
    }

    private static void vert(VertexConsumer buf, Matrix4f mat,
                             float x, float y, float z, float r, float g, float b, float a) {
        buf.vertex(mat, x, y, z).color(r, g, b, a).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 动画辅助方法
    // ═══════════════════════════════════════════════════════════════

    /** 获取生成阶段的缩放比例（0→1） */
    private static float getSpawnScale(float spawnProgress, CollapsePhase phase) {
        if (phase == CollapsePhase.SPAWN) {
            // 缓出三次方
            float t = Math.max(0, Math.min(1, spawnProgress));
            return 1f - (1f - t) * (1f - t) * (1f - t);
        }
        if (phase == CollapsePhase.END) return 1f;
        return 1f;
    }

    /** 获取当前 alpha（含生成淡入和结束淡出） */
    private static float getAlpha(float spawnProgress, float endFade, CollapsePhase phase) {
        if (phase == CollapsePhase.SPAWN) {
            float t = Math.max(0, Math.min(1, spawnProgress));
            return t * t * (3f - 2f * t); // smoothstep
        }
        if (phase == CollapsePhase.END) {
            return endFade;
        }
        return 1f;
    }

}
