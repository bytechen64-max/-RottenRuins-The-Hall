package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictSwordDropEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 裁决落剑渲染器 —— 一道从天而降、落地后插在地上的白色发光剑气。
 * <p>不复用 {@code SwordAuraRenderer}：落剑的实体位置是<b>落点</b>，渲染时要按同步进度把几何上顶
 * {@code (1 - progress) * fallFrom} 格；落地还要只抬起剑长一半，让下半截没入地面。
 * <p>几何是沿 Y 轴挤出的扁平刃（截面为宽 X、窄 Z 的六边形，轴向剖面收柄 → 展开 → 收尖），
 * 不是双锥体 —— 双锥的侧影是菱形，下落时读起来像水滴/花瓣。几何中心抬 {@code height × 0.55}，
 * 于是剑尖那约 0.4 格落在 y=0 以下，读作「钉进地里」。
 * <p>属性打包与其它世界特效一致：{@code POSITION_COLOR} 白色 + 加法混合 = 自发光。
 */
public class VerdictSwordDropRenderer extends EntityRenderer<VerdictSwordDropEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 截面段数：六边形截面，6 个侧面。 */
    private static final int SIDES = 6;
    /** 轴向分段；握柄/展开/收尖几处折点，16 段足够读出来。 */
    private static final int AXIAL_RINGS = 16;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** 刃的厚度 / 宽度。0.22 让侧转时有明显的宽度变化，又不会看起来像根木棍。 */
    private static final float THICKNESS_RATIO = 0.22f;

    /** 刃宽 / 剑长。 */
    private static final float BLADE_WIDTH_RATIO = 0.16f;

    /** 剑尖入地的深度（格）。太大就变成"只露出半截"。 */
    private static final float BURIED_TIP = 0.42f;

    /** 剑的白色（与 DomeriteLongsword.outlineSecondaryColor() 同值）。 */
    private static final float CORE_R = 0.941f, CORE_G = 0.973f, CORE_B = 1.0f;

    public VerdictSwordDropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VerdictSwordDropEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float progress = entity.getFallProgress(partialTick);
        float hold = entity.getHoldIntensity();
        if (hold <= 0.01f) return;

        float baseAlpha = 0.85f;
        float alpha = baseAlpha * Mth.lerp(0.25f, 1.0f, progress) * hold;
        if (alpha <= 0.01f) return;

        // 刃宽由剑长推出来（不是实体给的 radius）：固定成 1:6 之后剪影总读得出是刃而不是针
        //（真实长剑约 1:10，那个比例在游戏里会细到看不见）。
        float height = entity.getSwordHeight() * entity.getSwordScale();
        float width = height * BLADE_WIDTH_RATIO;
        if (height <= 0.05f || width <= 0.01f) return;

        // 落点以上的高度偏移：progress=0 时在 fallFrom 处，=1 时贴地
        float yLift = (1.0f - progress) * entity.getFallFrom();

        // 朝向取 45° 的整数倍、由 entity id 派生（两端一致、无需同步）；完全随机会读起来像散落的碎片。
        float spin = ((entity.getId() * 5) % 8) * 45.0f;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        // 深度测试必须显式打开：Tesselator 这条路线不经过 RenderType 的状态设置，
        // 漏了它就会像光柱那样「穿透一切」。
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(0.0, yLift, 0.0);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        // 轴向映射：h ∈ [0,1] ⟼ y ∈ [-BURIED_TIP, height - BURIED_TIP]，剑尖没入地面 BURIED_TIP 格
        poseStack.translate(0.0, height - BURIED_TIP, 0.0);

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        emitBlade(buffer, matrix, height, width, CORE_R, CORE_G, CORE_B, alpha);
        tess.end();

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 轴高 h 处的半宽（相对最宽处）：0.13~0.22 是握柄、0.30 展开成剑身、0.94 后收向剑尖。 */
    private static float bladeHalfWidth(float h) {
        if (h <= 0.00f) return 0.10f;
        if (h < 0.13f) return Mth.lerp(h / 0.13f, 0.10f, 0.45f);
        if (h < 0.22f) return 0.45f;
        if (h < 0.30f) return Mth.lerp((h - 0.22f) / 0.08f, 0.45f, 1.00f);
        if (h < 0.80f) return Mth.lerp((h - 0.30f) / 0.50f, 1.00f, 0.92f);
        if (h < 0.94f) return Mth.lerp((h - 0.80f) / 0.14f, 0.92f, 0.30f);
        return Mth.lerp((h - 0.94f) / 0.06f, 0.30f, 0.0f);
    }

    /** 勒出扁平六边形截面：宽沿 X、窄沿 Z。
     *  六个顶点角度取 0/60/…/300，于是 0° 与 180° 落在 X 轴上（刃口两个锋）、90°/270° 方向被压扁，
     *  侧影因此是「宽度随侧转变化的薄板」而不是一根圆柱。 */
    private static void emitBlade(BufferBuilder buffer, Matrix4f matrix,
                                  float height, float width,
                                  float red, float green, float blue, float alpha) {
        float halfWidth = width * 0.5f;
        float halfThick = halfWidth * THICKNESS_RATIO;

        for (int ring = 0; ring < AXIAL_RINGS; ring++) {
            float h0 = ring / (float) AXIAL_RINGS;
            float h1 = (ring + 1) / (float) AXIAL_RINGS;
            float y0 = height * h0;
            float y1 = height * h1;

            float w0 = bladeHalfWidth(h0);
            float w1 = bladeHalfWidth(h1);
            float glow0 = 0.55f + 0.65f * h0;
            float glow1 = 0.55f + 0.65f * h1;

            for (int side = 0; side < SIDES; side++) {
                float a0 = TWO_PI * side / SIDES;
                float a1 = TWO_PI * (side + 1) / SIDES;

                float x00 = halfWidth * w0 * Mth.cos(a0);
                float z00 = halfThick * w0 * Mth.sin(a0);
                float x01 = halfWidth * w1 * Mth.cos(a0);
                float z01 = halfThick * w1 * Mth.sin(a0);
                float x10 = halfWidth * w0 * Mth.cos(a1);
                float z10 = halfThick * w0 * Mth.sin(a1);
                float x11 = halfWidth * w1 * Mth.cos(a1);
                float z11 = halfThick * w1 * Mth.sin(a1);

                vertex(buffer, matrix, x00, y0, z00, red, green, blue, alpha * glow0);
                vertex(buffer, matrix, x01, y1, z01, red, green, blue, alpha * glow1);
                vertex(buffer, matrix, x11, y1, z11, red, green, blue, alpha * glow1);

                vertex(buffer, matrix, x00, y0, z00, red, green, blue, alpha * glow0);
                vertex(buffer, matrix, x11, y1, z11, red, green, blue, alpha * glow1);
                vertex(buffer, matrix, x10, y0, z10, red, green, blue, alpha * glow0);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                               float x, float y, float z,
                               float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(VerdictSwordDropEntity entity) {
        return DUMMY_TEXTURE;
    }
}
