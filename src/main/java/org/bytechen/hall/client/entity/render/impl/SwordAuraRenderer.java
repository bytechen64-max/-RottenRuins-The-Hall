package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 剑气自定义渲染器 —— 两个圆锥底面贴合，白色发光，逐渐缩小并淡出。
 * <p>
 * 几何结构：上圆锥（顶点在上，底面在 y=0） + 下圆锥（顶点在下，底面在 y=0）。
 * 底面（y=0 处的圆盘）不渲染，仅渲染两侧锥面。
 * 使用加法混合实现发光效果，并应用随机 Z+Y 双轴旋转使朝向各不相同。
 */
public class SwordAuraRenderer extends EntityRenderer<SwordAuraEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆周分段数 */
    private static final int RING_SEGMENTS = 48;
    /** 每个圆锥的纵向环数（从 apex 到 base） */
    private static final int VERTICAL_RINGS = 16;

    public SwordAuraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    // ═══════════════════════════════════════════════════════════════
    // 主渲染入口
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(SwordAuraEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float scale = entity.getCurrentScale();
        float alpha = entity.getCurrentAlpha();
        // 生命周期极早期/晚期跳过
        if (scale < 0.005f || alpha < 0.01f) return;

        float halfHeight = entity.getAuraHeight() / 2f;
        float baseRadius = entity.getAuraRadius();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        // 加法混合 → 发光效果
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();

        // ── 应用随机旋转：先绕 Z 倾斜（Y 对称锥必须这样才有视觉变化），再绕 Y 转向 ──
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getPitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));

        Matrix4f matrix = poseStack.last().pose();

        // ── 上圆锥：顶点 (0, +halfHeight, 0) → 底面 y=0 半径 baseRadius ──
        renderConeSide(buffer, matrix,
                0, halfHeight, 0,
                0, 0, baseRadius,
                scale, alpha, false);

        // ─ 下圆锥：顶点 (0, -halfHeight, 0) → base 环 y=-0.001，避免与上锥 base 环精确重合形成亮环 ─
        renderConeSide(buffer, matrix,
                0, -halfHeight, 0,
                0, -0.001f, baseRadius,
                scale, alpha, true);

        poseStack.popPose();

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ═══════════════════════════════════════════════════════════════
    // 圆锥侧面几何生成
    // ═══════════════════════════════════════════════════════════════

    /**
     * 渲染单个圆锥的侧面（不含底面圆盘）。
     */
    private void renderConeSide(BufferBuilder buffer, Matrix4f matrix,
                                 float apexX, float apexY, float apexZ,
                                 float baseX, float baseY, float baseRadius,
                                 float scale, float alpha, boolean flipWinding) {
        float[] ringRadii = new float[VERTICAL_RINGS + 1];
        float[] ringYs = new float[VERTICAL_RINGS + 1];

        for (int ring = 0; ring <= VERTICAL_RINGS; ring++) {
            float t = ring / (float) VERTICAL_RINGS;
            ringRadii[ring] = baseRadius * t * scale;
            ringYs[ring] = apexY + (baseY - apexY) * t;
        }
        float scaledApexX = apexX * scale;
        float scaledApexZ = apexZ * scale;
        float scaledBaseX = baseX * scale;

        for (int ring = 0; ring < VERTICAL_RINGS; ring++) {
            float rCurr = ringRadii[ring];
            float rNext = ringRadii[ring + 1];
            float yCurr = ringYs[ring];
            float yNext = ringYs[ring + 1];

            for (int seg = 0; seg < RING_SEGMENTS; seg++) {
                double a0 = 2.0 * Math.PI * seg / RING_SEGMENTS;
                double a1 = 2.0 * Math.PI * (seg + 1) / RING_SEGMENTS;

                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                float cx0 = scaledBaseX + rCurr * c0, cz0 = scaledApexZ + rCurr * s0;
                float cx1 = scaledBaseX + rCurr * c1, cz1 = scaledApexZ + rCurr * s1;

                float nx0 = scaledBaseX + rNext * c0, nz0 = scaledApexZ + rNext * s0;
                float nx1 = scaledBaseX + rNext * c1, nz1 = scaledApexZ + rNext * s1;

                if (flipWinding) {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, alpha);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, alpha);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, alpha);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, alpha);
                } else {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, alpha);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, alpha);
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, alpha);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, alpha);
                }
            }
        }
    }

    private static void bufferVertex(BufferBuilder buffer, Matrix4f matrix,
                                      float x, float y, float z, float alpha) {
        buffer.vertex(matrix, x, y, z).color(1f, 1f, 1f, alpha).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 纹理位置（占位）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResourceLocation getTextureLocation(SwordAuraEntity entity) {
        return DUMMY_TEXTURE;
    }
}
