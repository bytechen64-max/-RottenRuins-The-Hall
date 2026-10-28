package org.bytechen.hall.client.fx.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.bytechen.hall.client.fx.ItemFxBlend;
import org.bytechen.hall.client.fx.ItemFxDomain;
import org.bytechen.hall.client.fx.ItemFxEffect;
import org.bytechen.hall.client.fx.ItemFxFieldPool;
import org.bytechen.hall.client.fx.ItemFxFieldSpec;
import org.bytechen.hall.client.fx.ItemFxFormat;
import org.bytechen.hall.client.fx.ItemFxQuad;
import org.bytechen.hall.client.fx.ItemFxRegistry;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;
import org.bytechen.hall.utils.ModUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 电弧（electric arcs）—— 框架效果，<b>无状态</b>。
 *
 * <h3>为什么它是一个正式效果，而不是描边着色器里的一段 if</h3>
 * <p>因为它需要一张<b>自己的场</b>：一个"到剪影轮廓有多远"的距离代理 {@code arcEdge}。
 * 早先我把电弧的 reach 挂在描边光雾链的模糊图上，而那张图的模糊跨度只有 52px ——
 * 于是电弧的带宽被**旧算法的中间产物**锁死，"怎么调都扩散不出去"。
 * 场归自己之后，跨度就是这里说了算：1/{@value #EDGE_DEN} 分辨率 + 两步大步长模糊
 * ≈ 200px 跨度，带宽才真正可以用像素来调。</p>
 *
 * <h3>场的构成</h3>
 * <ul>
 *   <li>{@code arcEdgeH} —— 第一趟（横向）模糊的中间结果，1/{@value #EDGE_DEN} 分辨率；</li>
 *   <li>{@code arcEdge} —— 第二趟（纵向）结果，就是合成时采样的距离代理：
 *       {@code 0 = 远在外侧，0.5 = 恰在轮廓上，1 = 深入内部}。</li>
 * </ul>
 * <p>两者都是每帧重建的（{@code scaled}），因为剪影本身每帧都在变。</p>
 */
public final class ArcFxEffect implements ItemFxEffect {

    public static final String ID = "arc";

    public static final String FIELD_EDGE_H = "arcEdgeH";
    public static final String FIELD_EDGE = "arcEdge";

    /** 距离代理的分辨率分母（8 = 1/8 分辨率）。越小跨度越大、边缘越软。 */
    public static final int EDGE_DEN = 8;

    /** 合成时剪裁框的外扩像素：必须覆盖"跨界带"的外侧那一半，否则会被剪掉。 */
    public static final int SCISSOR_PAD_PX = 220;

    private static final List<ItemFxFieldSpec> FIELDS = List.of(
            ItemFxFieldSpec.scaled(FIELD_EDGE_H, ItemFxFormat.RGBA8, EDGE_DEN, 320),
            ItemFxFieldSpec.scaled(FIELD_EDGE, ItemFxFormat.RGBA8, EDGE_DEN, 320));

    private static final ArcFxEffect INSTANCE = new ArcFxEffect();

    static {
        ItemFxRegistry.register(INSTANCE);
    }

    private ArcFxEffect() { }

    public static ArcFxEffect instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemFxDomain domain() {
        return ItemFxDomain.SILHOUETTE;
    }

    @Override
    public List<ItemFxFieldSpec> fields() {
        return FIELDS;
    }

    @Override
    public ItemFxBlend blend() {
        // 电弧是自发光：加法。用 alpha 混合会把暗部涂成灰块。
        return ItemFxBlend.ADD;
    }

    @Override
    public float order() {
        // 火体（150）之上、硬边火芯（200）之下
        return 180.0F;
    }

    // ──────────────────────────────────────────────────────────────
    //  场：把剪影模糊成"大跨度距离代理"
    // ──────────────────────────────────────────────────────────────

    /**
     * 用全分辨率剪影搭出 {@code arcEdge}。
     *
     * <p>复用描边那支模糊着色器（它已经吃了 {@code TexelStep} / {@code BlurScale}），
     * 所以不需要新的模糊程序 —— 需要换的只是<b>步长</b>：在 1/{@value #EDGE_DEN} 分辨率上
     * 用 4 ~ 6 个纹素的步长，两趟就得到约 200px 的跨度。</p>
     *
     * @return 是否成功（着色器或场不可用时返回 false，调用方跳过合成）
     */
    public static boolean buildEdge(RenderTarget main, @Nullable TextureTarget mask) {
        ShaderInstance blur = SplendidingShaders.outlineHaloBlurShader;
        TextureTarget a = ItemFxFieldPool.get(ID,
                ItemFxFieldSpec.scaled(FIELD_EDGE_H, ItemFxFormat.RGBA8, EDGE_DEN, 320), main);
        TextureTarget b = ItemFxFieldPool.get(ID,
                ItemFxFieldSpec.scaled(FIELD_EDGE, ItemFxFormat.RGBA8, EDGE_DEN, 320), main);
        if (blur == null || a == null || b == null || mask == null) return false;

        int previousFramebuffer = org.lwjgl.opengl.GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);

        RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        int fw = Math.max(1, main.width);
        int fh = Math.max(1, main.height);
        int dw = Math.max(1, fw / EDGE_DEN);
        int dh = Math.max(1, fh / EDGE_DEN);

        // ── 一趟降采样 + 三趟原地扩散（步长逐趟加倍，全程都很小）──
        //
        // ★ 为什么不再用"一趟 5 个目标纹素"去一次跨出 200px：
        //   那样采样点会被 CLAMP_TO_EDGE 抓住，把远处的边缘内容拖进来，
        //   整片场看起来就像**平移**了（用户看到的就是这个）。
        //   模糊核本身是对称的（中心 + ±s…±4s，我读过 outline_halo_blur.fsh 确认），
        //   所以偏移只可能来自"步长过大 + 边界吸附"。
        //   逐趟 1 源纹素 → 2 → 4 → 8 目标纹素，累计跨度 ≈ 120px，居中且无边界拖拽。
        blur.setSampler("Source", mask.getColorTextureId());
        ItemFxQuad.setVec2(blur, "TexelStep", 1.0F / fw, 1.0F / fh);
        ItemFxQuad.setFloat(blur, "BlurScale", 1.0F);
        a.bindWrite(true);
        ItemFxQuad.draw(blur);

        TextureTarget src = a;
        for (int pass = 0; pass < 3; pass++) {
            float step = (float) (2 << pass);   // 2 / 4 / 8 个目标纹素
            TextureTarget dst = (src == a) ? b : a;
            blur.setSampler("Source", src.getColorTextureId());
            ItemFxQuad.setVec2(blur, "TexelStep", step / dw, step / dh);
            ItemFxQuad.setFloat(blur, "BlurScale", 0.5F);
            dst.bindWrite(true);
            ItemFxQuad.draw(blur);
            src = dst;
        }

        // ★ bindWrite 过就必须把 framebuffer 和 viewport 一起还原（项目里为此崩过、也出现过 GUI 画到左下角）
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        return true;
    }

    /**
     * 数字诊断（{@code arcDebugView = 2} 时每帧一次）：把"距离场轮廓的位置"和
     * "剪影轮廓的位置"对比着打进日志。
     *
     * <p>偏移问题不能靠眼睛判 —— 细丝本身是乱的。这里直接读回距离场中间那一行：
     * 高值区间应当与剪影在那一行上的范围对应。对不上 = 场偏了；对得上 = 偏移在合成阶段。</p>
     */
    public static void logEdgeScan(RenderTarget main) {
        TextureTarget edge = ItemFxFieldPool.get(ID,
                ItemFxFieldSpec.scaled(FIELD_EDGE, ItemFxFormat.RGBA8, EDGE_DEN, 320), main);
        if (edge == null || edge.frameBufferId <= 0) {
            ModUtils.LOGGER.info("[ItemArc] 诊断：距离场还不存在");
            return;
        }
        int dw = Math.max(1, main.width / EDGE_DEN);
        int dh = Math.max(1, main.height / EDGE_DEN);
        java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(dw * 4);
        int previousRead = org.lwjgl.opengl.GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, edge.frameBufferId);
        GL11.glReadBuffer(org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadPixels(0, dh / 2, dw, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, previousRead);
        int first = -1;
        int last = -1;
        int mid = -1;
        for (int x = 0; x < dw; x++) {
            int v = buf.get(x * 4) & 0xFF;
            if (v > 128) {
                if (first < 0) first = x;
                last = x;
            }
            if (mid < 0 && v >= 124 && v <= 132) {
                mid = x;
            }
        }
        ModUtils.LOGGER.info("[ItemArc] 诊断：距离场中间行（1/{} 分辨率 y={}/{}）高值区间 x=[{}..{}] → 全分辨率 [{}, {}]；0.5 等值线首个 x={} → 全分辨率 {}",
                EDGE_DEN, dh / 2, dh, first, last, first * EDGE_DEN, last * EDGE_DEN, mid, mid * EDGE_DEN);
    }

    // ──────────────────────────────────────────────────────────────
    //  合成
    // ──────────────────────────────────────────────────────────────

    /**
     * 把电弧合成到主 RT。调用方负责：绑定主 RT、设置剪裁框、开启加法混合。
     *
     * @param originU 该物品屏幕中心的 UV（噪声坐标原点 —— 用屏幕 UV 当噪声坐标的话，
     *                镜头一动图案就会从物品上滑过去）
     */
    public static void composite(RenderTarget main, float originU, float originV,
                                 float colorR, float colorG, float colorB,
                                 float color2R, float color2G, float color2B,
                                 float strength, float time) {
        ShaderInstance shader = SplendidingShaders.arcCompositeShader;
        TextureTarget edge = ItemFxFieldPool.get(ID,
                ItemFxFieldSpec.scaled(FIELD_EDGE, ItemFxFormat.RGBA8, EDGE_DEN, 320), main);
        if (shader == null || edge == null) return;

        SplendidingConfig cfg = config();
        shader.setSampler("Edge", edge.getColorTextureId());
        ItemFxQuad.setVec2(shader, "ScreenSize", (float) main.width, (float) main.height);
        ItemFxQuad.setVec2(shader, "ArcNoiseOrigin", originU, originV);
        ItemFxQuad.setFloat(shader, "Time", time);
        ItemFxQuad.setFloat(shader, "ArcScale", cfg == null ? 26F : cfg.arcScale);
        ItemFxQuad.setFloat(shader, "ArcLine", (cfg == null ? 2.5F : cfg.arcLinePx) / Math.max(1F, (float) main.height));
        ItemFxQuad.setFloat(shader, "ArcThreshold", cfg == null ? 0.5F : cfg.arcThreshold);
        ItemFxQuad.setFloat(shader, "ArcGain", strength * (cfg == null ? 2F : cfg.arcGain));
        ItemFxQuad.setFloat(shader, "ArcSpeed", cfg == null ? 1.6F : cfg.arcSpeed);
        ItemFxQuad.setFloat(shader, "ArcBand", cfg == null ? 0.60F : Math.max(0.05F, cfg.arcBandScale));
        ItemFxQuad.setFloat(shader, "ArcSub", cfg == null ? 0.12F : cfg.arcSub);
        ItemFxQuad.setFloat(shader, "ArcAniso", cfg == null ? 1F : cfg.arcAniso);
        ItemFxQuad.setFloat(shader, "ArcTonemap", cfg == null ? 1F : cfg.arcTonemap);
        ItemFxQuad.setFloat(shader, "ArcDebug", cfg == null ? 0F : Mth.clamp(cfg.arcDebugView, 0, 1));
        if (shader.getUniform("ArcColorA") != null) {
            shader.getUniform("ArcColorA").set(colorR, colorG, colorB);
        }
        if (shader.getUniform("ArcColorB") != null) {
            shader.getUniform("ArcColorB").set(color2R, color2G, color2B);
        }
        ItemFxQuad.draw(shader);
    }

    @Nullable
    private static SplendidingConfig config() {
        try {
            var holder = ConfigHelper.configHolder;
            return holder == null ? null : holder.get();
        } catch (Throwable t) {
            return null;
        }
    }
}
