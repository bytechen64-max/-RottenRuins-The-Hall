package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictBeamEntity;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictTuning;
import org.joml.Matrix4f;

/**
 * 天穹裁决 · 垂直光柱渲染器。
 *
 * <h3>几何</h3>
 * <p>一个沿 +Y 的<b>圆柱侧面</b>（不含上下底盖），底面在实体位置（玩家脚底），
 * 高 {@code length}、半径 {@code radius}。底面不画是刻意的：光柱是从天上降下来的，
 * 给它加一个底面圆盘会立刻读成"一根实体柱子立在地上"，而不是"一束光"。</p>
 *
 * <h3>属性打包</h3>
 * <p>顶点格式用 {@code POSITION_COLOR_NORMAL}（与冲击波一致），三个属性各有分工：</p>
 * <ul>
 *   <li>{@code Position} —— 局部坐标。光柱轴对齐，于是 {@code length(xz)} 就是局部径向距离、
 *       {@code y} 就是高度，片元侧不用额外属性就能算；</li>
 *   <li>{@code Color.rg} —— 当成贴图坐标用：{@code r = 径向归一化}、{@code g = 高度归一化}。
 *       这样将来光柱改成锥形收束（非圆柱）时，片元侧的取 r/g 两行不用动；</li>
 *   <li>{@code Normal} —— 归一化的 {@code (x, 0, z)} 径向方向，
 *       片元用它做"内侧被点亮"的边缘光（正对视线的面接近 0，剪影边缘接近 1）。</li>
 * </ul>
 *
 * <h3>不需要光影兼容的延后回放</h3>
 * <p>光柱<b>不采样屏幕</b>（不像冲击波那样要把主帧缓冲 blit 出来再折射），
 * 它只采样自己的几何与噪声。因此它在光影包的 G-buffer 阶段绘制也不会被"吃掉" ——
 * 这正是它比冲击波省一整套 {@code LateRenderQueue} 的原因。
 * 唯一需要在光影下复核的是加法混合的颜色是否会过曝，那是调参问题，不是管线问题。</p>
 */
public class VerdictBeamRenderer extends EntityRenderer<VerdictBeamEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆周分段。光柱近景会占满屏幕，段数给足。 */
    private static final int SEGMENTS = 48;
    /** 纵向环数。轴向噪声需要足够的纵向分辨率才不像"贴纸"。 */
    private static final int RINGS = 32;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** 光柱整体亮度（加法混合下的强度总开关）。 */
    private static final float BEAM_BRIGHTNESS = 0.72f;

    /** 实际亮度 = 基准 × 配置倍率。 */
    private static float brightness() {
        return BEAM_BRIGHTNESS * VerdictTuning.beamBrightness();
    }

    /** 近白（爆发）：与 DomeriteLongsword.outlineSecondaryColor() 同值。 */
    private static final int CORE_RGB = 0xF0F8FF;
    /** 天蓝（领域）：与 DomeriteLongsword.outlineColor() 同值。 */
    private static final int EDGE_RGB = 0x87CEFA;

    public VerdictBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VerdictBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float intensity = entity.intensity(partialTick);
        if (intensity <= 0.004f) return;

        float radius = entity.getRadius();
        float length = entity.getLength();
        if (radius <= 0.01f || length <= 0.05f) return;

        // ── 着色器必须已加载，否则整个实体静默跳过 ──
        //  SplendidingShaders.onRegisterShaders 里失败会 LOGGER.error 掉具体原因，
        //  这里不做任何兜底绘制：宁可看不见，也不要画出一根颜色错的光柱。
        ShaderInstance shader = SplendidingShaders.verdictBeamShader;
        if (shader == null) return;

        float time = (entity.tickCount + partialTick) * 0.05f;
        // 扫描球：0 → 1 在 70% 的生命里走完，留出尾段只剩静态光柱
        float scanT = Mth.clamp(entity.lifeProgress(partialTick) / 0.7f, 0f, 1f);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        // 必须显式绑着色器：Tesselator.end() 用的是 RenderSystem 当前绑定的着色器，
        // 不是 RenderType 里那个。这里只借用 RenderType 拿缓冲尺寸与行模式，
        // 与 ShockwaveRenderer 的做法保持一致。
        RenderSystem.setShader(() -> shader);

        setUniform(shader, "uTime", time);
        setUniform(shader, "uIntensity", intensity * brightness());
        setUniform(shader, "uRadius", radius);
        setUniform(shader, "uHeight", length);
        setUniform(shader, "uScanT", scanT);
        setUniformRGB(shader, "uCoreColor", CORE_RGB);
        setUniformRGB(shader, "uEdgeColor", EDGE_RGB);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        emitCylinderSide(buffer, matrix, radius, length);
        tess.end();

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ══════════════════════════════════════════════════════════════
    //  几何：圆柱侧面
    // ══════════════════════════════════════════════════════════════

    /**
     * 发出圆柱侧面。底面在 {@code y = 0}，顶面在 {@code y = length}。
     *
     * <p>两套属性同时写：{@code Color.rg} 是归一化贴图坐标，{@code Normal} 是径向方向。
     * 两者都是"每个顶点逐点算出来"的，所以插值到片元之后依然连续。</p>
     */
    private static void emitCylinderSide(BufferBuilder buffer, Matrix4f matrix,
                                          float radius, float length) {
        for (int ring = 0; ring < RINGS; ring++) {
            float h0 = ring / (float) RINGS;
            float h1 = (ring + 1) / (float) RINGS;
            float y0 = length * h0;
            float y1 = length * h1;

            for (int seg = 0; seg < SEGMENTS; seg++) {
                float a0 = TWO_PI * seg / SEGMENTS;
                float a1 = TWO_PI * (seg + 1) / SEGMENTS;

                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

                float x0 = radius * c0, z0 = radius * s0;
                float x1 = radius * c1, z1 = radius * s1;

                // 对应关系：a0/h0 → a0/h1 → a1/h1 → a1/h0
                vertex(buffer, matrix, x0, y0, z0, radius, length);
                vertex(buffer, matrix, x0, y1, z0, radius, length);
                vertex(buffer, matrix, x1, y1, z1, radius, length);

                vertex(buffer, matrix, x0, y0, z0, radius, length);
                vertex(buffer, matrix, x1, y1, z1, radius, length);
                vertex(buffer, matrix, x1, y0, z1, radius, length);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                                float x, float y, float z, float radius, float length) {
        float d = Mth.clamp(Mth.sqrt(x * x + z * z) / Math.max(radius, 1e-4f), 0f, 1f);
        float h = Mth.clamp(y / Math.max(length, 1e-4f), 0f, 1f);
        float nx = x / Math.max(radius, 1e-4f);
        float nz = z / Math.max(radius, 1e-4f);

        buffer.vertex(matrix, x, y, z)
                .color(d, h, 0f, 1f)
                .normal(nx, 0f, nz)
                .endVertex();
    }

    // ══════════════════════════════════════════════════════════════
    //  uniform 工具
    // ══════════════════════════════════════════════════════════════

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    /** 把 0xRRGGBB 拆成 0..1 的三个 float 传给 vec3 uniform。 */
    private static void setUniformRGB(ShaderInstance shader, String name, int rgb) {
        var u = shader.getUniform(name);
        if (u == null) return;
        u.set(((rgb >> 16) & 0xFF) / 255f,
              ((rgb >> 8) & 0xFF) / 255f,
              (rgb & 0xFF) / 255f);
    }

    @Override
    public ResourceLocation getTextureLocation(VerdictBeamEntity entity) {
        return DUMMY_TEXTURE;
    }
}
