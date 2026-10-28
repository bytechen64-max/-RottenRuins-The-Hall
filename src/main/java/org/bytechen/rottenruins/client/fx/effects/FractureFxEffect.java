package org.bytechen.rottenruins.client.fx.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.bytechen.rottenruins.client.fx.ItemFxBlend;
import org.bytechen.rottenruins.client.fx.ItemFxDomain;
import org.bytechen.rottenruins.client.fx.ItemFxEffect;
import org.bytechen.rottenruins.client.fx.ItemFxFieldPool;
import org.bytechen.rottenruins.client.fx.ItemFxFieldSpec;
import org.bytechen.rottenruins.client.fx.ItemFxFormat;
import org.bytechen.rottenruins.client.fx.ItemFxQuad;
import org.bytechen.rottenruins.client.fx.ItemFxRegistry;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 裂纹（fracture）—— 框架效果，<b>无状态</b>，而且**几乎没有自己的场**。
 *
 * <h3>它用什么数学</h3>
 * <p>Voronoi 单元边界：{@code |F2 − F1|} 在单元边界上趋近 0，于是
 * {@code 1 − smoothstep(0, 线宽, F2−F1)} 就是一条贴着边界的等宽缝，而且天然
 * <b>首尾相连、彼此分叉</b> —— 碎裂的观感全部来自这里。描边那套"形状的等距偏移"
 * 结构上做不出分叉，所以这不是"换个笔刷"，是换了一种几何。</p>
 *
 * <h3>它需要什么场</h3>
 * <p>只需要一张"到物品轮廓的距离代理"（{@code fracEdge}，1/{@value #EDGE_DEN} 分辨率、
 * 每帧重建）：用来让裂纹<b>从边缘往内推进</b>（{@code CrackProgress}）。
 * 构建方式与电弧那张同源（都是把剪影模糊成大跨度距离代理）——
 * 但按框架的约定，场是按 {@code (effectId, fieldName)} 归属的，所以这里各自持有，
 * 不跨效果共享（共享会让两个效果的开关互相牵连）。</p>
 */
public final class FractureFxEffect implements ItemFxEffect {

    public static final String ID = "fracture";

    public static final String FIELD_EDGE_H = "fracEdgeH";
    public static final String FIELD_EDGE = "fracEdge";

    /** 距离代理的分辨率分母（8 = 1/8 分辨率）。 */
    public static final int EDGE_DEN = 8;

    /** 合成剪裁框的外扩像素。 */
    public static final int SCISSOR_PAD_PX = 64;

    private static final List<ItemFxFieldSpec> FIELDS = List.of(
            ItemFxFieldSpec.scaled(FIELD_EDGE_H, ItemFxFormat.RGBA8, EDGE_DEN, 320),
            ItemFxFieldSpec.scaled(FIELD_EDGE, ItemFxFormat.RGBA8, EDGE_DEN, 320));

    private static final FractureFxEffect INSTANCE = new FractureFxEffect();

    static {
        ItemFxRegistry.register(INSTANCE);
    }

    private FractureFxEffect() { }

    public static FractureFxEffect instance() {
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
        // 裂缝在发光：加法
        return ItemFxBlend.ADD;
    }

    @Override
    public float order() {
        // 电弧（180）之上、硬边环（200）之下：先有电光，再有裂纹压在上面
        return 190.0F;
    }

    /**
     * 建"到轮廓的距离代理"：与 {@link ArcFxEffect#buildEdge} 同一套做法
     * （一趟降采样 + 三趟原地扩散，步长 1→2→4→8 目标纹素，全程居中）。
     *
     * <p>为什么要这么啰嗦：早先用"一趟大跨步"的写法，采样点会被 CLAMP_TO_EDGE 抓住、
     * 把远处内容拖进来，整片场看起来像平移了。</p>
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

        blur.setSampler("Source", mask.getColorTextureId());
        ItemFxQuad.setVec2(blur, "TexelStep", 1.0F / fw, 1.0F / fh);
        ItemFxQuad.setFloat(blur, "BlurScale", 1.0F);
        a.bindWrite(true);
        ItemFxQuad.draw(blur);

        TextureTarget src = a;
        for (int pass = 0; pass < 3; pass++) {
            float step = (float) (2 << pass);
            TextureTarget dst = (src == a) ? b : a;
            blur.setSampler("Source", src.getColorTextureId());
            ItemFxQuad.setVec2(blur, "TexelStep", step / dw, step / dh);
            ItemFxQuad.setFloat(blur, "BlurScale", 0.5F);
            dst.bindWrite(true);
            ItemFxQuad.draw(blur);
            src = dst;
        }

        // 还原 framebuffer + viewport（两条都必须还，项目里为此崩过）
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        return true;
    }

    /**
     * 合成裂纹。调用方负责绑定主 RT、设置剪裁框与加法混合。
     *
     * @param itemUv 物品的局部基（{@code originU, originV, basisXU, basisXV, basisYU, basisYV}）：
     *               着色器用它把屏幕 UV 换算回**物品局部坐标**，图案才会钉在物品上。
     *               为 null 时退回屏幕坐标（图案会滑动，仅作兜底）。
     */
    public static void composite(RenderTarget main, @Nullable TextureTarget mask,
                                 float originU, float originV, float time,
                                 float colorR, float colorG, float colorB,
                                 float seed, @Nullable float[] itemUv) {
        ShaderInstance shader = SplendidingShaders.fractureCompositeShader;
        TextureTarget edge = ItemFxFieldPool.get(ID,
                ItemFxFieldSpec.scaled(FIELD_EDGE, ItemFxFormat.RGBA8, EDGE_DEN, 320), main);
        if (shader == null || edge == null || mask == null) return;

        SplendidingConfig cfg = config();
        shader.setSampler("Mask", mask.getColorTextureId());
        shader.setSampler("Edge", edge.getColorTextureId());
        ItemFxQuad.setVec2(shader, "ScreenSize", (float) main.width, (float) main.height);
        ItemFxQuad.setVec2(shader, "NoiseOrigin", originU, originV);
        ItemFxQuad.setFloat(shader, "Time", time);
        ItemFxQuad.setFloat(shader, "CrackScale", cfg == null ? 14F : cfg.crackScale);
        ItemFxQuad.setFloat(shader, "CrackWidth", (cfg == null ? 2.0F : cfg.crackLinePx) / Math.max(1F, (float) main.height));
        ItemFxQuad.setFloat(shader, "CrackGlow", cfg == null ? 0.55F : cfg.crackGlow);
        ItemFxQuad.setFloat(shader, "CrackGlowWidth", cfg == null ? 3.5F : cfg.crackGlowWidth);
        ItemFxQuad.setFloat(shader, "CrackReach", cfg == null ? 0.55F : cfg.crackReach);
        ItemFxQuad.setFloat(shader, "CrackProgress", cfg == null ? 1F : Mth.clamp(cfg.crackProgress, 0F, 1F));
        ItemFxQuad.setFloat(shader, "CrackSeed", seed);
        // 物品局部基：把屏幕 UV 换算回物品坐标，图案才会钉在物品上（否则镜头一动就滑）
        if (itemUv != null && itemUv.length >= 6) {
            ItemFxQuad.setVec2(shader, "ItemOrigin", itemUv[0], itemUv[1]);
            ItemFxQuad.setVec2(shader, "ItemBasisX", itemUv[2], itemUv[3]);
            ItemFxQuad.setVec2(shader, "ItemBasisY", itemUv[4], itemUv[5]);
        } else {
            ItemFxQuad.setVec2(shader, "ItemOrigin", originU, originV);
            ItemFxQuad.setVec2(shader, "ItemBasisX", 0F, 0F);   // 行列式为 0 → 着色器退回屏幕坐标
            ItemFxQuad.setVec2(shader, "ItemBasisY", 0F, 0F);
        }
        if (shader.getUniform("CrackColor") != null) {
            shader.getUniform("CrackColor").set(colorR, colorG, colorB);
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
