package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 剑气渲染器 —— 上下两个圆锥底面贴合（底面圆盘不渲染，只画两侧锥面）。
 * {@code STYLE_DEFAULT}：单个白色发光锥对 + 加法混合；{@code STYLE_APOSTLE}：白色外层（放大
 * {@code outerScale} 倍、关深度）+ 黑色内芯（原尺寸、{@code GL_LEQUAL} 不写深度，于是白色只剩一圈轮廓）；
 * {@code STYLE_FROST}：10 层同心壳叠出连续渐变 + 弧形弯曲 + 流动高光 + 方向性溶解，<b>只改顶点色与位置、
 * 不增加任何顶点</b>。
 */
public class SwordAuraRenderer extends EntityRenderer<SwordAuraEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆周分段数 / 每个圆锥的纵向环数（从 apex 到 base）。 */
    private static final int RING_SEGMENTS = 48;
    private static final int VERTICAL_RINGS = 16;

    /**
     * 一层壳：同一个双锥几何按 {@code radiusScale} 缩小后叠在一起，由外到内逐层加深。
     * 每层还带 tip → mid 的颜色与 alpha（{@code t=0} 是锥尖，{@code t=1} 是中段最粗处），
     * 两端 alpha 压到接近 0，刀光就自然淡出而不是被截断。
     */
    private record FrostShell(float radiusScale, int tipColor, int midColor,
                              float tipAlpha, float midAlpha,
                              int segments, int rings) {
    }

    /** 最外层的镶嵌密度：它是可见轮廓，圆周要给足；内层边界被渐变盖住，16 段够圆。 */
    private static final int OUTER_SEGMENTS = 48;
    private static final int OUTER_RINGS = 12;
    private static final int INNER_SEGMENTS = 16;
    private static final int INNER_RINGS = 8;

    /** 最外层：冰白轮廓，也是唯一一层穿透方块的。 */
    private static final FrostShell FROST_OUTER =
            new FrostShell(1.00f, 0xF6FEFF, 0xC9ECFF, 0.02f, 0.92f,
                    OUTER_SEGMENTS, OUTER_RINGS);

    private static FrostShell inner(float radiusScale, int tipColor, int midColor,
                                    float tipAlpha, float midAlpha) {
        return new FrostShell(radiusScale, tipColor, midColor, tipAlpha, midAlpha,
                INNER_SEGMENTS, INNER_RINGS);
    }

    /**
     * 内层，由外到内：冰白 → 浅蓝 → 中蓝 → 深蓝 → 近黑。
     * 层数取 9 且每层 alpha 压到 0.42~0.56：单层盖不满前一层，相邻两层的颜色跳变被 alpha 稀释，
     * 台阶感就看不出来（层数太少是硬切，太多则顶点翻倍而看不出差别）。
     */
    private static final FrostShell[] FROST_INNER = {
            inner(0.88f, 0xEAFBFF, 0xC4EBFF, 0.03f, 0.42f),
            inner(0.80f, 0xE0F7FF, 0xAFE0FF, 0.04f, 0.44f),
            inner(0.72f, 0xD4F1FF, 0x94CFFA, 0.05f, 0.46f),
            inner(0.64f, 0xC6E9FF, 0x7CB6EC, 0.05f, 0.48f),
            inner(0.56f, 0xB6DEFF, 0x5E9AE0, 0.06f, 0.50f),
            inner(0.48f, 0xA2CFFF, 0x3F7BC8, 0.07f, 0.52f),
            inner(0.40f, 0x8AB8F2, 0x23539E, 0.08f, 0.54f),
            inner(0.32f, 0x6E92D4, 0x0F2C63, 0.09f, 0.55f),
            inner(0.24f, 0x4A5E9C, 0x030814, 0.10f, 0.56f),
    };

    /**
     * 弧形弯曲量（格）：锥尖相对中段沿本地 +X 的偏移。直的双锥读作"一根针"，按 {@code (1-t)²} 推开后
     * 两端弯、中间直，整条读作一道弧光。同一偏移量在更细长的刀身上显得更弯，所以它跟长宽比绑在一起。
     */
    private static final float FROST_BEND = 0.90f;

    /** 高光脉冲的轴向宽度（σ，0~1 比例）：0.11 约等于覆盖整条刀光的 1/4 长度。 */
    private static final float PULSE_WIDTH = 0.11f;

    /** 脉冲流速：0.075 ⇒ 约 13 tick 扫完一遍，与刀光 14 tick 的寿命基本对齐。 */
    private static final float PULSE_SPEED = 0.075f;

    private static final float PULSE_STRENGTH = 0.85f;

    /** 溶解边缘宽度（0~1 轴向比例）：前沿是一段过渡带而不是硬线，读作"烧穿 / 散掉"。 */
    private static final float DISSOLVE_EDGE = 0.20f;

    public SwordAuraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SwordAuraEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float scale = entity.getCurrentScale(partialTick);
        float alpha = entity.getCurrentAlpha(partialTick);
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

        // STYLE_VERDICT 走<b>受控朝向</b>（0 = 竖直劈砍 / 1 = 横向扫击）：裁决的剑光必须读得出方向，
        // 按 entity id 派生的随机倾斜会把"领域落剑"与"突进拖尾"两个语义都糊掉。
        if (entity.getStyle() == SwordAuraEntity.STYLE_VERDICT
                || entity.getStyle() == SwordAuraEntity.STYLE_FLAME) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f * entity.getVerdictOrientation()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));
        } else {
            // Y 对称锥必须先绕 Z 倾斜才有视觉变化，再绕 Y 转向
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getPitch()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));
        }

        Matrix4f matrix = poseStack.last().pose();

        if (entity.getStyle() == SwordAuraEntity.STYLE_FROST) {
            float progress = entity.getLifeProgress(partialTick);
            // 寒霜<b>不</b>跟着全局 (1-p)² 淡出：收场交给方向性溶解，用原曲线会还没溶解就淡没了。
            alpha = entity.getAlpha() * (1f - 0.55f * progress);
            float pulsePhase = ((entity.getAge() + Math.max(0f, partialTick)) * PULSE_SPEED) % 1f;
            renderFrost(buffer, tesselator, matrix, halfHeight, baseRadius,
                    scale, entity.getOuterScale(), alpha, pulsePhase, progress);
        } else if (entity.getStyle() == SwordAuraEntity.STYLE_APOSTLE) {
            float outerR = 1f, outerG = 1f, outerB = 1f;
            float coreR = 0f, coreG = 0f, coreB = 0f;

            // 外色层：关深度测试且不写深度 ⇒ 无视地形遮挡、永远可见（长刀劈过墙面的观感来源）。
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();          // SRC_ALPHA / ONE_MINUS_SRC_ALPHA
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius,
                    scale * entity.getOuterScale(), outerR, outerG, outerB, alpha);
            tesselator.end();

            // 内芯：开回 LEQUAL 但不写深度 ⇒ 对①必然通过（①只剩一圈轮廓），对地形则被真实深度挡住。
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius,
                    scale, coreR, coreG, coreB, alpha);
            tesselator.end();

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        } else {
            float r = 1f, g = 1f, b = 1f;
            if (entity.getStyle() == SwordAuraEntity.STYLE_FLAME) {
                r = 0.86f;
                g = 0.10f;
                b = 0.05f;
            }
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius, scale, r, g, b, alpha);
            tesselator.end();
        }

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 画寒霜斩击：多层同心壳叠出由外到内的平滑渐变 + 弧形弯曲 + 流动高光 + 方向性溶解。
     * 最外层关深度测试、不写深度（唯一穿透方块的一层）；内层开回 {@code GL_LEQUAL} 且不写深度
     * （被地形正常遮挡，且因为后画、前面的层没写深度，必然叠在上一层之上）。
     * 用"叠壳"而不是渐变着色器：这份几何只用到 {@code POSITION_COLOR}，没有 UV，梯度只能落在顶点上。
     */
    private void renderFrost(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix,
                             float halfHeight, float baseRadius, float scale,
                             float outerScale, float alpha, float pulsePhase, float progress) {
        // 以"最外层半径"为基准，内层按各自 radiusScale 收进去：外轮廓粗细只由 outerScale 决定。
        float shellBase = baseRadius * outerScale;
        // 弧度跟着实体缩放走，否则临近消失时会出现"弧还在、刀没了"
        float bend = FROST_BEND * scale;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        emitFrostShell(buffer, matrix, halfHeight, shellBase, scale, bend, pulsePhase, progress,
                FROST_OUTER, alpha);
        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (FrostShell shell : FROST_INNER) {
            emitFrostShell(buffer, matrix, halfHeight, shellBase, scale, bend, pulsePhase, progress,
                    shell, alpha);
        }
        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private void emitFrostShell(BufferBuilder buffer, Matrix4f matrix,
                                float halfHeight, float shellBaseRadius, float scale,
                                float bend, float pulsePhase, float progress,
                                FrostShell shell, float alpha) {
        float radius = shellBaseRadius * shell.radiusScale() * scale;
        renderConeSideShaded(buffer, matrix, halfHeight, 0f, radius, bend, pulsePhase, progress,
                true, shell, alpha, false);
        // 下锥底面错开 0.001，避免与上锥底环精确重合形成亮环
        renderConeSideShaded(buffer, matrix, -halfHeight, -0.001f, radius, bend, pulsePhase,
                progress, false, shell, alpha, true);
    }

    /**
     * 方向性溶解的保留度：{@code u} 沿整条刀光（0 = 上锥尖 = 来袭端），一条溶解前沿随生命进度扫过，
     * 前沿之前的被吃掉；边缘是 {@link #DISSOLVE_EDGE} 宽的 smoothstep，所以是"烧穿"而不是硬切。
     */
    private static float dissolveKeep(float u, float progress) {
        float front = -DISSOLVE_EDGE + progress * (1f + 2f * DISSOLVE_EDGE);
        float k = (u - front) / DISSOLVE_EDGE;
        if (k <= 0f) {
            return 0f;
        }
        if (k >= 1f) {
            return 1f;
        }
        return k * k * (3f - 2f * k);          // smoothstep：边缘是"烧穿"，不是硬切
    }

    /**
     * 沿整条刀光流动的高光强度：窄高斯窗口 {@code exp(-d²/2σ²)} 沿轴向扫过，
     * 距离按 {@code d - round(d)} 折回 {@code [-0.5, 0.5]}，于是脉冲循环且两端无缝衔接。
     */
    private static float pulseAt(float u, float phase) {
        float d = u - phase;
        d -= Math.round(d);
        return (float) Math.exp(-(d * d) / (2f * PULSE_WIDTH * PULSE_WIDTH));
    }

    /**
     * 渲染单个圆锥侧面：轴向 tip → mid 渐变 + 弧形弯曲（{@code bend × (1-t)²}）+ 流动高光 + 方向性溶解，
     * 全部只改顶点色与顶点位置。插值权重用 smoothstep 而非线性：线性在两端会有"折角"，细长刀光上会被放大。
     */
    private void renderConeSideShaded(BufferBuilder buffer, Matrix4f matrix,
                                      float apexY, float baseY, float radius,
                                      float bend, float pulsePhase, float progress, boolean upper,
                                      FrostShell shell, float alpha, boolean flipWinding) {
        final int rings = Math.max(1, shell.rings());
        final int segments = Math.max(3, shell.segments());

        float[] ringRadii = new float[rings + 1];
        float[] ringXs = new float[rings + 1];
        float[] ringYs = new float[rings + 1];
        float[] ringR = new float[rings + 1];
        float[] ringG = new float[rings + 1];
        float[] ringB = new float[rings + 1];
        float[] ringA = new float[rings + 1];

        float tipR = channel(shell.tipColor(), 16), tipG = channel(shell.tipColor(), 8),
                tipB = channel(shell.tipColor(), 0);
        float midR = channel(shell.midColor(), 16), midG = channel(shell.midColor(), 8),
                midB = channel(shell.midColor(), 0);

        for (int ring = 0; ring <= rings; ring++) {
            float t = ring / (float) rings;
            float w = t * t * (3f - 2f * t);              // smoothstep
            ringRadii[ring] = radius * t;
            ringYs[ring] = apexY + (baseY - apexY) * t;
            // 弧：锥尖（t=0）偏得最多，中段（t=1）为 0 —— 两半在中间自然接上
            float bow = 1f - t;
            ringXs[ring] = bend * bow * bow;

            float r = Mth.lerp(w, tipR, midR);
            float g = Mth.lerp(w, tipG, midG);
            float b = Mth.lerp(w, tipB, midB);
            // 该环在整条刀光上的位置：0 = 上锥尖（来袭端），0.5 = 中段，1 = 下锥尖
            float u = upper ? 0.5f * t : 1f - 0.5f * t;
            float pulse = pulseAt(u, pulsePhase) * PULSE_STRENGTH;
            ringR[ring] = Mth.lerp(pulse, r, 1f);
            ringG[ring] = Mth.lerp(pulse, g, 1f);
            ringB[ring] = Mth.lerp(pulse, b, 1f);
            ringA[ring] = Mth.lerp(w, shell.tipAlpha(), shell.midAlpha()) * alpha
                    * dissolveKeep(u, progress);
        }

        for (int ring = 0; ring < rings; ring++) {
            float rCurr = ringRadii[ring];
            float rNext = ringRadii[ring + 1];
            float xCurr = ringXs[ring];
            float xNext = ringXs[ring + 1];
            float yCurr = ringYs[ring];
            float yNext = ringYs[ring + 1];
            float cr = ringR[ring], cg = ringG[ring], cb = ringB[ring], ca = ringA[ring];
            float nr = ringR[ring + 1], ng = ringG[ring + 1], nb = ringB[ring + 1], na = ringA[ring + 1];

            for (int seg = 0; seg < segments; seg++) {
                double a0 = 2.0 * Math.PI * seg / segments;
                double a1 = 2.0 * Math.PI * (seg + 1) / segments;

                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                float cx0 = xCurr + rCurr * c0, cz0 = rCurr * s0;
                float cx1 = xCurr + rCurr * c1, cz1 = rCurr * s1;

                float nx0 = xNext + rNext * c0, nz0 = rNext * s0;
                float nx1 = xNext + rNext * c1, nz1 = rNext * s1;

                if (flipWinding) {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, nr, ng, nb, na);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, cr, cg, cb, ca);
                } else {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, nr, ng, nb, na);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);
                }
            }
        }
    }

    private static float channel(int color, int shift) {
        return ((color >> shift) & 0xFF) / 255f;
    }

    private void emitCones(BufferBuilder buffer, Matrix4f matrix,
                           float halfHeight, float baseRadius, float scale,
                           float r, float g, float b, float alpha) {
        renderConeSide(buffer, matrix,
                0, halfHeight, 0,
                0, 0, baseRadius,
                scale, r, g, b, alpha, false);

        // 下锥 base 环取 y=-0.001，避免与上锥 base 环精确重合形成亮环
        renderConeSide(buffer, matrix,
                0, -halfHeight, 0,
                0, -0.001f, baseRadius,
                scale, r, g, b, alpha, true);
    }

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

    @Override
    public ResourceLocation getTextureLocation(SwordAuraEntity entity) {
        return DUMMY_TEXTURE;
    }
}
