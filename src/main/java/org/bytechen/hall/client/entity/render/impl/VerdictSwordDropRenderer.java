package org.bytechen.hall.client.entity.render.impl;

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
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictSwordDropEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 裁决落剑渲染器 —— 一道从天而降、落地后插在地上的白色发光剑气。
 *
 * <h3>为什么它不直接用 {@code SwordAuraRenderer}</h3>
 * <p>落剑有两件 {@code SwordAuraEntity} 做不到的事：</p>
 * <ol>
 *   <li><b>它要落下来。</b>{@code SwordAuraEntity} 的几何固定在实体原点，
 *       而落剑的实体位置是<b>落点</b>，位移要按同步的进度重建 ——
 *       也就是渲染时必须把几何往上顶
 *       {@code (1 - progress) * fallFrom} 格。</li>
 *   <li><b>它要"插进地里"。</b>落地动作需要让剑的下半截没入地面：
 *       几何中心只抬剑长的一半，而不是做一个完整的"锥体对"。
 *       完整锥体对在竖直朝向下的读法是一根两头尖的光柱，
 *       而不是一把钉在地上的剑。</li>
 * </ol>
 *
 * <h3>几何：一块扁长带尖的刃，不是双锥体</h3>
 *
 * <p>第一版复用了 {@code SwordAuraEntity} 的双锥体（两个圆锥底面贴合，各占一半高度）。
 * 双锥的<b>侧影是菱形</b>，下落时读起来就是一颗水滴 / 一片花瓣 —— 玩家反馈
 * "看起来很奇怪"，根因就在这里：水滴不是剑。</p>
 *
 * <p>所以这里换成专用几何：沿 Y 轴挤出的一块<b>扁平刃</b>，
 * 截面是"宽 X、窄 Z"的六边形（于是它有厚度、有中脊、侧转时宽度会收窄），
 * 轴向剖面则在底部收成握柄、中部展开、上部收窄、顶端出尖。</p>
 *
 * <pre>
 *   轴高 h ∈ [0, 1]（0 = 剑尖入地的位置）
 *
 *   h = 0.40 ~ 0.85   最宽处（剑身主体，约 bladeWidth/2）
 *   h = 0.15 ~ 0.25   收窄成握柄（约最宽处的 45%）
 *   h = 1.00          收成一点（剑尖，但在下方 —— 见 y 映射）
 * </pre>
 *
 * <h3>y 映射与"入地"的量</h3>
 * <p>几何中心抬到 {@code height × 0.55}，于是 {@code y} 从 {@code -0.55h} 走到
 * {@code +0.45h}。落点在实体位置（地面），剑尖那 0.4 格左右落在 y=0 以下，
 * 读作"钉进地里"，而剑身与护手整根露在外面 —— 上一版把整半截都埋了，
 * 那会让"插在地里"变成"地上露出半截东西"。</p>
 *
 * <p>属性打包与其它世界特效一致：{@code POSITION_COLOR}，
 * 白色 + 加法混合 = 自发光；不写深度以避免把后面的实体剪掉。</p>
 */
public class VerdictSwordDropRenderer extends EntityRenderer<VerdictSwordDropEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 截面段数：六边形截面，6 个侧面。 */
    private static final int SIDES = 6;
    /** 轴向分段。剖面上有握柄/展开/收尖几处折点，16 段足够把它们读出来。 */
    private static final int AXIAL_RINGS = 16;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** 刃的厚度 / 宽度。0.22 让侧转时有明显的宽度变化，又不会看起来像根木棍。 */
    private static final float THICKNESS_RATIO = 0.22f;

    /** 刃宽 / 剑长。见 render 里的说明。 */
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

        // 下落中：亮一档（还在空中、有速度）；落地后：变成静物、暗一档
        float baseAlpha = 0.85f;
        float alpha = baseAlpha * Mth.lerp(0.25f, 1.0f, progress) * hold;
        if (alpha <= 0.01f) return;

        // 刃宽由剑长推出来（不是实体给的 radius）：剑就该是瘦长的，
        // 比例固定成"宽 = 长的 16%"之后，无论剑多长多短，剪影都读得出是刃而不是针。
        // 真实长剑大约 1:10，但那个比例在游戏里会细到看不见，1:6 是"一眼是剑"的下限。
        float height = entity.getSwordHeight() * entity.getSwordScale();
        float width = height * BLADE_WIDTH_RATIO;
        if (height <= 0.05f || width <= 0.01f) return;

        // 落点以上的高度偏移：progress=0 时在 fallFrom 处，=1 时贴地
        float yLift = (1.0f - progress) * entity.getFallFrom();

        // 朝向：把"刃面"平分成几个固定朝向之一，由 entity id 派生（两端一致，无需同步）。
        //  不做完全随机：完全随机会让一排剑的朝向毫无规律，读起来像散落的碎片；
        //  取 45° 的整数倍，则每把剑的刃面要么正对某个轴向、要么斜 45°，
        //  整体看起来是"有意识地插下去"的。
        float spin = ((entity.getId() * 5) % 8) * 45.0f;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        // 深度测试必须显式打开：{@code Tesselator} 这条路线不经过 RenderType 的状态设置，
        // 光靠"上一段渲染恰好留着深度测试"才能被方块正确遮挡。
        // 光柱那边就是因为漏了这两行而"穿透一切"，这里提前补上。
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(0.0, yLift, 0.0);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        // 轴向映射：h ∈ [0,1] ⟼ y ∈ [-BURIED_TIP, height - BURIED_TIP]
        //  （见类注释：剑尖没入地面 BURIED_TIP 格，剑身整根露在外面）
        poseStack.translate(0.0, height - BURIED_TIP, 0.0);

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // 落剑一律是主体（白色剑光）：唯一的另一种用途——布景立柱——随
        // "撤掉展开表现"一起去掉了，所以这里不再有配色分支。
        emitBlade(buffer, matrix, height, width, CORE_R, CORE_G, CORE_B, alpha);
        tess.end();

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 一块扁平长刃的轴高 h 处的半宽（相对最宽处的比例）。
     *
     * <p>三个折点构成"握柄 → 剑身 → 剑尖"的读法：</p>
     * <pre>
     *   h                  半宽
     *   0.00               0.10   底部收口（藏在土里，无所谓）
     *   0.13               0.45   握柄
     *   0.22               0.45   握柄上端
     *   0.30               1.00   展开成剑身
     *   0.80               0.92   剑身略收
     *   0.94               0.30   收窄到剑尖前一刻
     *   1.00               0.00   尖
     * </pre>
     */
    private static float bladeHalfWidth(float h) {
        if (h <= 0.00f) return 0.10f;
        if (h < 0.13f) return Mth.lerp(h / 0.13f, 0.10f, 0.45f);
        if (h < 0.22f) return 0.45f;
        if (h < 0.30f) return Mth.lerp((h - 0.22f) / 0.08f, 0.45f, 1.00f);
        if (h < 0.80f) return Mth.lerp((h - 0.30f) / 0.50f, 1.00f, 0.92f);
        if (h < 0.94f) return Mth.lerp((h - 0.80f) / 0.14f, 0.92f, 0.30f);
        return Mth.lerp((h - 0.94f) / 0.06f, 0.30f, 0.0f);
    }

    /**
     * 勒出扁平六边形截面：宽沿 X、窄沿 Z。
     *
     * <p>三角形的六个顶点角度刻意取 {@code 0 / 60 / 120 / 180 / 240 / 300}，
     * 于是 0° 与 180° 落在 X 轴上（刃口的两个锋），90° 与 270° 方向被压扁 ——
     * 侧影因此是一块"宽度会随侧转而变化的薄板"，而不是一根圆柱。</p>
     */
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

            // 亮度沿轴向变化：剑尖最亮、握柄最暗 —— 光线聚在锋上
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
