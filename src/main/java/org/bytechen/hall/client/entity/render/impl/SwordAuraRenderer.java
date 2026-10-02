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
 * 剑气自定义渲染器 —— 两个圆锥底面贴合，逐渐缩小并淡出。
 *
 * <h3>两种样式</h3>
 * <ul>
 *   <li>{@link SwordAuraEntity#STYLE_DEFAULT}：原有样式 —— 单个白色发光圆锥对，
 *       加法混合，用在虚空剑/碎骨/畸骸玩家上，<b>行为与之前完全一致</b>。</li>
 *   <li>{@link SwordAuraEntity#STYLE_APOSTLE}：使徒斩击样式，三层叠加：
 *       <ol>
 *         <li><b>白色外层</b>：把同一组圆锥按 {@code outerScale}（默认 1.45×）放大，
 *             用普通 alpha 混合画成实心白 —— 它是轮廓的"白边"；</li>
 *         <li><b>黑色内芯</b>：原尺寸的圆锥对，颜色纯黑，画完白色之后再用
 *             {@code GL_ALWAYS} 深度函数绘制（且不写深度），于是它<b>永远压在白色之上</b>，
 *             白色只剩下一圈轮廓 —— 也就是 outline 观感；</li>
 *         <li><b>空间扭曲</b>：以剑气为中心的一层屏幕空间折射，由
 *             {@link ApostleSlashRenderHandler} 在实体全部画完之后单独绘制
 *             （必须晚于实体的原因见该类注释）。</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <p>几何结构：上圆锥（顶点在上，底面在 y=0） + 下圆锥（顶点在下，底面在 y=0）。
 * 底面（y=0 处的圆盘）不渲染，仅渲染两侧锥面。
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

        float scale = entity.getCurrentScale(partialTick);
        float alpha = entity.getCurrentAlpha(partialTick);
        // 生命周期极早期/晚期跳过
        if (scale < 0.005f || alpha < 0.01f) return;

        float halfHeight = entity.getAuraHeight() / 2f;
        float baseRadius = entity.getAuraRadius();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();

        // ── 应用旋转 ──
        //  STYLE_VERDICT（天穹裁决）走一条<b>受控朝向</b>的路：
        //  俯仰由 setVerdictOrientation 给定（0 = 竖直劈砍 / 1 = 横向扫击），
        //  而不是按 entity id 派生一个随机倾斜。
        //  理由：裁决的剑光必须读得出方向 —— 领域落剑是"竖着钉下来"、
        //  突进拖尾是"顺着冲势铺开"，随机歪斜会把这两个语义都糊掉。
        if (entity.getStyle() == SwordAuraEntity.STYLE_VERDICT) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f * entity.getVerdictOrientation()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));
        } else {
            // ── 原有：先绕 Z 倾斜（Y 对称锥必须这样才有视觉变化），再绕 Y 转向 ──
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getPitch()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));
        }

        Matrix4f matrix = poseStack.last().pose();

        if (entity.getStyle() == SwordAuraEntity.STYLE_APOSTLE) {
            // ── ① 白色外层：放大后画实心白，作为轮廓白边 ──
            //    「仅白色穿透方块」：白色层<b>关掉深度测试</b>且不写深度，
            //    于是它无视地形遮挡、永远可见（长刀劈过墙面的观感来源就是这一层）。
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();          // SRC_ALPHA / ONE_MINUS_SRC_ALPHA
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius,
                    scale * entity.getOuterScale(), 1f, 1f, 1f, alpha);
            tesselator.end();

            // ── ② 黑色内芯：不穿透方块，且永远压在白色之上 ──
            //    黑色层开回正常深度测试（LEQUAL）但不写深度：
            //      · 它对白色层→ 白色没写深度，LEQUAL 必然通过，且它后画 ⇒ 白色只剩一圈轮廓（outline）；
            //      · 它对地形→ 墙体/方块的真实深度会正常挡住它 ⇒ 黑芯不穿透方块。
            //    用条件深度测试而不是 GL_ALWAYS，正是为了把"black 穿透"这条去掉；
            //    两层的层级关系仍然由绘制顺序保证，不存在共面 z-fighting。
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius,
                    scale, 0f, 0f, 0f, alpha);
            tesselator.end();

            // 恢复 GL 状态（不恢复会污染后续所有实体渲染）
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        } else {
            // ── 原有样式：白色 + 加法混合 = 发光 ──
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius, scale, 1f, 1f, 1f, alpha);
            tesselator.end();
        }

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ═══════════════════════════════════════════════════════════════
    // 圆锥几何
    // ═══════════════════════════════════════════════════════════════

    /** 一次性发出上下两个圆锥的侧面（不含底面圆盘）。 */
    private void emitCones(BufferBuilder buffer, Matrix4f matrix,
                           float halfHeight, float baseRadius, float scale,
                           float r, float g, float b, float alpha) {
        // ── 上圆锥：顶点 (0, +halfHeight, 0) → 底面 y=0 半径 baseRadius ──
        renderConeSide(buffer, matrix,
                0, halfHeight, 0,
                0, 0, baseRadius,
                scale, r, g, b, alpha, false);

        // ── 下圆锥：顶点 (0, -halfHeight, 0) → base 环 y=-0.001，避免与上锥 base 环精确重合形成亮环 ──
        renderConeSide(buffer, matrix,
                0, -halfHeight, 0,
                0, -0.001f, baseRadius,
                scale, r, g, b, alpha, true);
    }

    /**
     * 渲染单个圆锥的侧面（不含底面圆盘）。
     */
    private void renderConeSide(BufferBuilder buffer, Matrix4f matrix,
                                 float apexX, float apexY, float apexZ,
                                 float baseX, float baseY, float baseRadius,
                                 float scale, float r, float g, float b, float alpha,
                                 boolean flipWinding) {
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
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, r, g, b, alpha);
                } else {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, r, g, b, alpha);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);
                }
            }
        }
    }

    private static void bufferVertex(BufferBuilder buffer, Matrix4f matrix,
                                      float x, float y, float z,
                                      float r, float g, float b, float alpha) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, alpha).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 纹理位置（占位）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResourceLocation getTextureLocation(SwordAuraEntity entity) {
        return DUMMY_TEXTURE;
    }
}
