package org.bytechen.hall.client.rend.glint;

import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.client.rend.SplendidingShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

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

    /**
     * Renders outline expansion around the item silhouette.
     *
     * <p>The RenderType setup callback runs inside {@code buffer.getBuffer()},
     * setting up its own blend/depth state.  We override blend AFTER getting
     * the buffer so ADDITIVE mode sticks.</p>
     *
     * <p>Depth strategy:
     * <ul>
     *   <li>World (ground/3rd/frame): LEQUAL — blocks in front occlude outline</li>
     *   <li>First-person: NO_DEPTH — hand buffer is unreliable under shaders</li>
     * </ul>
     */
    public static void renderWorldOutlinePass(ItemStack stack, ItemDisplayContext context,
                                               PoseStack poseStack, MultiBufferSource buffer,
                                               int combinedLight, int combinedOverlay, BakedModel model) {
        if (ShaderPackDetector.isShadowPass()) return;

        boolean isVertexMode = HeldItemOutlineSettings.getOutlineMode()
                == HeldItemOutlineSettings.OutlineMode.VERTEX_SHADER
                || HeldItemOutlineSettings.getOutlineMode()
                == HeldItemOutlineSettings.OutlineMode.AUTO;

        boolean isWorldCtx = switch (context) {
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, GROUND, NONE, FIXED -> true;
            default -> false;
        };
        if (!isWorldCtx && !isVertexMode) return;

        ICustomOutline custom = stack.getItem() instanceof ICustomOutline c ? c : null;
        GlintEffectProfile profile = GlintRenderManager.getProfile(stack);

        boolean enabled;
        float width, r, g, b, alpha;
        ICustomOutline.BlendMode blend;
        String shaderKey;

        if (custom != null && custom.outlineEnabled(context)) {
            enabled = true;
            width  = custom.outlineWidth();
            int c = custom.outlineColor();
            r = ((c >> 16) & 0xFF) / 255f;
            g = ((c >> 8) & 0xFF) / 255f;
            b = (c & 0xFF) / 255f;
            alpha = ((c >> 24) & 0xFF) / 255f;
            blend = custom.outlineBlend();
            String key = custom.outlineShaderKey();
            shaderKey = key != null ? key : SplendidingShaders.KEY_DEFAULT;
        } else if (profile != null && profile.isWorldOutlineEnabled() && profile.shouldRender(context)) {
            enabled = true;
            width  = profile.getWorldOutlineWidth();
            r = profile.getRed();
            g = profile.getGreen();
            b = profile.getBlue();
            alpha = profile.getAlpha();
            blend = ICustomOutline.BlendMode.ADDITIVE;
            String profileKey = profile.getOutlineShaderKey();
            shaderKey = profileKey != null ? profileKey : SplendidingShaders.KEY_DEFAULT;
        } else {
            return;
        }
        if (!enabled) return;

        // shader pack → deferred replay
        if (ShaderPackDetector.shouldUseShaderPackPipeline()) {
            if (SplendidingShaders.getOutlineShader(shaderKey) == null) return;
            List<BakedQuad> eq = model.getQuads(null, null, RandomSource.create());
            if (eq.isEmpty()) return;
            float[] bb = OutlineRenderQueue.computeBbox(eq);
            OutlineRenderQueue.enqueue(model, custom, profile,
                    new float[]{r, g, b, alpha}, shaderKey, blend, width,
                    bb[0], bb[1],
                    new Matrix4f(poseStack.last().pose()),
                    new Matrix3f(poseStack.last().normal()),
                    new Matrix4f(RenderSystem.getModelViewMatrix()),
                    context);
            return;
        }

        ShaderInstance shader = SplendidingShaders.getOutlineShader(shaderKey);
        // Pick RenderType: LEQUAL depth for world, NONE for first-person
        RenderType outlineType = isWorldCtx
                ? SplendidingShaders.getWorldOutlineRenderType(shaderKey)
                : SplendidingShaders.getOutlineRenderType(shaderKey);
        if (shader == null || outlineType == null) return;

        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create());
        if (quads.isEmpty()) return;

        // Center is always (0.5, 0.5) — item quads are in 0–1 model space
        // after translate(-0.5, -0.5, -0.5).  ArcaneVortex hardcodes this.
        final float cx = 0.5f, cy = 0.5f;

        poseStack.pushPose();
        poseStack.translate(cx, cy, 0f);
        poseStack.scale(1.0f + width, 1.0f + width, 1.0f);
        poseStack.translate(-cx, -cy, 0f);

        if (custom != null) {
            custom.configureOutlineShader(shader);
        } else if (profile != null && "gradient".equals(shaderKey)) {
            int gc = profile.getGradientColorCount();
            setIntUniform(shader, "ColorCount", gc);
            for (int i = 0; i < gc; i++)
                setIntUniform(shader, "Color" + i, profile.getGradientColor(i));
            setFloatUniform(shader, "FlowSpeed", profile.getGradientFlowSpeed());
            setFloatUniform(shader, "GradientSpan", profile.getGradientSpan());
        }
        if (shader.getUniform("OutlineColor") != null)
            shader.safeGetUniform("OutlineColor").set(r, g, b, alpha);
        if (shader.getUniform("TintColor") != null)
            shader.safeGetUniform("TintColor").set(r, g, b, 0.8f);
        long gt = net.minecraft.client.Minecraft.getInstance().level != null
                ? net.minecraft.client.Minecraft.getInstance().level.getGameTime() : 0;
        if (shader.getUniform("Time") != null)
            shader.safeGetUniform("Time").set((gt % 360000L) / 20f);

        // getBuffer runs RenderType setup (NO_DEPTH + TRANSLUCENT blend).
        // Override blend AFTER so ADDITIVE stays.
        VertexConsumer consumer = buffer.getBuffer(outlineType);

        RenderSystem.enableBlend();
        if (blend == ICustomOutline.BlendMode.ADDITIVE)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else
            RenderSystem.defaultBlendFunc();

        // World-space: LEQUAL depth so walls/entities in front occlude outline.
        // First-person: NO_DEPTH (hand depth buffer is unreliable).
        // No depth WRITE in either case — outline never writes depth.
        if (isWorldCtx) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);

        for (BakedQuad quad : quads)
            consumer.putBulkData(poseStack.last(), quad, 1.0f, 1.0f, 1.0f, combinedLight, combinedOverlay);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void setPalette(int i, float r, float g, float b, float a) {
        ShaderInstance s = SplendidingShaders.itemGlintShader;
        if (s != null) s.safeGetUniform("PaletteColor" + i).set(r, g, b, a);
    }

    private static void setIntUniform(ShaderInstance s, String name, int value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    private static void setFloatUniform(ShaderInstance s, String name, float value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }
}
