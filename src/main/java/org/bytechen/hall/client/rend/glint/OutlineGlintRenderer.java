package org.bytechen.hall.client.rend.glint;

import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.client.rend.SplendidingShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品表面的 GUI 辉光（glint）叠加层。
 *
 * <p>注意：<b>描边已经从这里搬走了。</b> 旧实现里这个类还有一个
 * {@code renderWorldOutlinePass} —— 它把物品模型整体放大 6% 再重画一遍当描边，
 * 等于给整件物品（连同内部）盖了一层颜色，而且声明的 RenderType 是
 * {@code NO_DEPTH_TEST}，所以既穿墙又会把宇宙星空层整片糊住。
 * 现在描边由 {@link ItemOutlinePipeline} 负责：屏幕空间的「剪影遮罩 + 环形膨胀」，
 * 只画在物品剪影外侧，跟贴在表面的星空层永不重叠。</p>
 */
public final class OutlineGlintRenderer {

    private OutlineGlintRenderer() {}

    public static void renderGlintPass(ItemStack stack, ItemDisplayContext context,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int combinedLight, int combinedOverlay, BakedModel model) {
        ICustomOutline custom = stack.getItem() instanceof ICustomOutline c ? c : null;
        ICustomOutline.GlintSettings gs = custom != null ? custom.glintSettings() : null;

        float r, g, b, a, sr, sg, sb, sa;
        float csMode, speed, intensity, bloomS, bloomR;
        int paletteSize;

        if (gs != null) {
            boolean show = switch (context) {
                case GUI, FIXED -> gs.showInGui();
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                     THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> gs.showWhenHeld();
                case GROUND, NONE -> gs.showInWorld();
                default -> false;
            };
            if (!show) return;

            r = ((gs.color() >> 16) & 0xFF) / 255f;
            g = ((gs.color() >> 8) & 0xFF) / 255f;
            b = (gs.color() & 0xFF) / 255f;
            a = ((gs.color() >> 24) & 0xFF) / 255f;
            sr = ((gs.secondaryColor() >> 16) & 0xFF) / 255f;
            sg = ((gs.secondaryColor() >> 8) & 0xFF) / 255f;
            sb = (gs.secondaryColor() & 0xFF) / 255f;
            sa = ((gs.secondaryColor() >> 24) & 0xFF) / 255f;
            csMode  = gs.colorMode().shaderValue;
            speed   = gs.speed();
            intensity = gs.intensity();
            bloomS  = gs.bloomStrength();
            bloomR  = gs.bloomRadius();
            paletteSize = 0;
        } else {
            GlintEffectProfile profile = GlintRenderManager.getProfile(stack);
            if (profile == null || !profile.shouldRender(context)) return;

            r = profile.getRed(); g = profile.getGreen(); b = profile.getBlue();
            a = profile.getAlpha();
            sr = profile.getSecondaryRed(); sg = profile.getSecondaryGreen();
            sb = profile.getSecondaryBlue(); sa = profile.getSecondaryAlpha();
            csMode = profile.getColorMode().shaderValue;
            speed = profile.getSpeed(); intensity = profile.getIntensity();
            bloomS = profile.getBloomStrength(); bloomR = profile.getBloomRadius();
            paletteSize = profile.getPaletteSize();
            for (int i = 0; i < GlintEffectProfile.MAX_PALETTE_COLORS; i++) {
                setPalette(i, i < paletteSize ? profile.getPaletteR(i) : 1f,
                             i < paletteSize ? profile.getPaletteG(i) : 1f,
                             i < paletteSize ? profile.getPaletteB(i) : 1f,
                             i < paletteSize ? profile.getPaletteA(i) : 1f);
            }
        }

        ShaderInstance shader = SplendidingShaders.itemGlintShader;
        RenderType glintType = SplendidingShaders.getItemGlintRenderType();
        if (shader == null || glintType == null) return;

        shader.safeGetUniform("GlintColor").set(r, g, b, a);
        shader.safeGetUniform("SecondaryColor").set(sr, sg, sb, sa);
        shader.safeGetUniform("ColorMode").set(csMode);
        shader.safeGetUniform("GlintSpeed").set(speed);
        shader.safeGetUniform("GlintIntensity").set(intensity);
        shader.safeGetUniform("BloomStrength").set(bloomS);
        shader.safeGetUniform("BloomRadius").set(bloomR);
        shader.safeGetUniform("PaletteSize").set((float) paletteSize);
        for (int i = paletteSize; i < ICustomOutline.GlintSettings.MAX_PALETTE_COLORS; i++)
            setPalette(i, 1f, 1f, 1f, 1f);
        shader.safeGetUniform("Time").set((System.currentTimeMillis() / 1000.0f) % 10000.0f);

        VertexConsumer consumer = buffer.getBuffer(glintType);
        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create());
        for (BakedQuad quad : quads)
            consumer.putBulkData(poseStack.last(), quad, 1.0f, 1.0f, 1.0f, combinedLight, combinedOverlay);
    }

    private static void setPalette(int i, float r, float g, float b, float a) {
        ShaderInstance s = SplendidingShaders.itemGlintShader;
        if (s != null) s.safeGetUniform("PaletteColor" + i).set(r, g, b, a);
    }
}
