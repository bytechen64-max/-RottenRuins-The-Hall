package org.bytechen.hall.client.cosmic;

import org.bytechen.hall.api.CosmicStyle;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.bytechen.hall.client.mask.IMaskLayerCarrier;
import org.bytechen.hall.client.cosmic.render.CosmicRenderType;
import org.bytechen.hall.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.hall.client.cosmic.render.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.bytechen.hall.client.rend.twitch.ItemTwitchHelper;
import org.bytechen.hall.client.rend.twitch.ITwitchItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BakedModelCosmic extends BakedModelRendererBase implements IMaskLayerCarrier {

    private final ResourceLocation maskTexture;
    private ResourceLocation corruptionMaskTexture;
    private float opacityOverride = -1.0f;
    private CosmicStyle styleOverride = null;
    private boolean cosmicEnabled = true;
    private boolean corruptionEnabled = false;
    private boolean twitchEnabled = true;

    /**
     * 与星空层叠加的 mask 效果层，来自同一个模型 JSON 的 {@code "mask_layers"}。
     *
     * <p>放在这个类里而不是新加一个包装类，是因为「一个模型只能有一个 loader」——
     * 已经用了 {@code hall:cosmic} 的物品没法再挂第二个 loader 去声明附加层。</p>
     */
    private List<MaskLayerSpec> maskLayers = List.of();

    public BakedModelCosmic(BakedModel inner, ResourceLocation maskTexture) {
        super(inner); this.maskTexture = maskTexture;
    }

    @Override
    public List<MaskLayerSpec> maskLayers() {
        return maskLayers;
    }

    public void setMaskLayers(List<MaskLayerSpec> layers) {
        this.maskLayers = (layers == null || layers.isEmpty()) ? List.of() : List.copyOf(layers);
    }

    public void setOpacity(float v) { this.opacityOverride = v; }
    public float getOpacity() { return opacityOverride; }
    public void setStyle(CosmicStyle s) { this.styleOverride = s; }
    public CosmicStyle getStyle() { return styleOverride; }
    public ResourceLocation getMaskTexture() { return maskTexture; }

    public void setCorruptionMask(ResourceLocation mask) { this.corruptionMaskTexture = mask; this.corruptionEnabled = true; }
    public ResourceLocation getCorruptionMask() { return corruptionMaskTexture != null ? corruptionMaskTexture : maskTexture; }

    /** Whether cosmic starfield rendering should happen for this model. */
    public boolean isCosmicEnabled() { return cosmicEnabled; }
    public void setCosmicEnabled(boolean v) { this.cosmicEnabled = v; }

    /** Whether corruption (RGB split) rendering should happen for this model. */
    public boolean isCorruptionEnabled() { return corruptionEnabled; }
    public void setCorruptionEnabled(boolean v) { this.corruptionEnabled = v; }

    /**
     * Whether the glitch-twitch pose distortion should apply to this model.
     *
     * <p>Historically every {@code BakedModelCosmic} twitched unconditionally
     * (see {@code ItemTwitchHelper.shouldTwitch}).  That is right for
     * {@code void_sword}, but a model that wants a clean surface — e.g.
     * {@code crimson_vow} with its deep-pink plasma — can turn it off with
     * {@code "cosmic": { "twitch": false }}.
     */
    public boolean isTwitchEnabled() { return twitchEnabled; }
    public void setTwitchEnabled(boolean v) { this.twitchEnabled = v; }

    @Override public boolean isCustomRenderer() { return false; }

    /** No-op: vanilla renders quads via getQuads() → inner. */
    @Override
    public void render(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                       PoseStack ps, MultiBufferSource buf, int light, int overlay,
                       BakedModel model) {}

    /** Render cosmic layer directly (used by CosmicItemLateRenderQueue). */
    public void renderShaderLayer(ItemStack stack, ItemDisplayContext ctx,
                                  PoseStack ps, MultiBufferSource buf,
                                  int light, int overlay, BakedModel model,
                                  boolean lateRender) {
        if (!cosmicEnabled) return;
        if (CosmicShaders.cosmicShader == null) return;

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        CosmicShaders.markCosmicSpritesActive();

        float yaw = 0, pitch = 0, scale = 1;
        if (CosmicShaders.cosmicInventoryRender || ctx == ItemDisplayContext.GUI)
            scale = 100.0F;
        else if (mc.player != null) {
            yaw   =  (float) (mc.player.getYRot() * 2.0 * Math.PI / 360.0);
            pitch = -(float) (mc.player.getXRot() * 2.0 * Math.PI / 360.0);
        }

        if (CosmicShaders.timeUniform != null) {
            // 时间源：游戏 tick 数。星空层靠 time*0.0002 / sin(time*0.006) 之类的
            // 小系数把它压得极慢；Blocks 图案（CRIMSON_VOW）也只用 sin() 拿它当
            // 动画相位（blockTimeScale 再压一次），不像上一版等离子那样把 time
            // 直接当大相位用 —— 所以 20 Hz 的量化在这里不可见，不需要额外插值。
            CosmicShaders.timeUniform.set((float) (mc.level.getGameTime() % Integer.MAX_VALUE));
        }
        if (CosmicShaders.yawUniform != null)         CosmicShaders.yawUniform.set(yaw);
        if (CosmicShaders.pitchUniform != null)       CosmicShaders.pitchUniform.set(pitch);
        if (CosmicShaders.externalScaleUniform != null) CosmicShaders.externalScaleUniform.set(scale);
        if (CosmicShaders.opacityUniform != null)
            CosmicShaders.opacityUniform.set(opacityOverride >= 0f ? opacityOverride : 1.0F);
        if (CosmicShaders.useTypeUniform != null)
            CosmicShaders.useTypeUniform.set(styleOverride != null ? styleOverride.shaderValue : 0);
        if (CosmicShaders.cosmicuvsUniform != null)
            CosmicShaders.cosmicuvsUniform.set(CosmicShaders.COSMIC_UVS);

        RenderType rt = lateRender ? lateRenderType(ctx) : CosmicRenderType.COSMIC;
        TextureAtlasSprite sprite = atlas.getSprite(maskTexture);
        // 遮罩 sprite 在图集里的 UV 矩形（水面湍流那道 style 用它把图集 UV 折回 0..1）。
        // 必须在 getBuffer/endBatch 之前设 —— Uniform.set 只是记在对象上，真正上传在 flush 时。
        CosmicShaders.setMaskSlice(sprite);
        VertexConsumer vc = buf.getBuffer(rt);
        mc.getItemRenderer().renderQuadList(ps, vc,
                CosmicRenderUtils.bakeItem(sprite), stack, light, overlay);
        if (!lateRender && buf instanceof MultiBufferSource.BufferSource bs)
            bs.endBatch(rt);
    }

    private static RenderType lateRenderType(ItemDisplayContext ctx) {
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        return fp ? CosmicRenderType.COSMIC_HAND_AFTER_LEVEL
                : CosmicRenderType.COSMIC_AFTER_LEVEL;
    }

    private static RenderType lateCorruptionRenderType(ItemDisplayContext ctx) {
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        return fp ? CosmicRenderType.CORRUPTION_HAND_AFTER_LEVEL
                : CosmicRenderType.CORRUPTION_AFTER_LEVEL;
    }

    /**
     * Render the corruption layer (RGB split + scanlines + colour shift)
     * on top of the base item texture, using the cosmic mask.
     *
     * @param lateRender true when called from {@link org.bytechen.hall.client.cosmic.compat.CosmicItemLateRenderQueue}
     */
    public void renderCorruptionLayer(ItemStack stack, ItemDisplayContext ctx,
                                      PoseStack ps, MultiBufferSource buf,
                                      int light, int overlay, BakedModel model,
                                      boolean lateRender) {
        if (!corruptionEnabled) return;
        // 第二道门：入队时 wrapper.setCorruptionMask() 会把 corruptionEnabled 打开，
        // 而 ItemTwitchItem.disabled() 是物品层面的明确声明 —— 两者冲突时以物品为准。
        if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ITwitchItem ti && ti.twitchDisabled()) return;
        if (CosmicShaders.corruptionShader == null) return;

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

        float gameTime = mc.level != null ? (float) (mc.level.getGameTime() % Integer.MAX_VALUE) : 0f;
        if (CosmicShaders.corruptionTimeUniform != null)
            CosmicShaders.corruptionTimeUniform.set(gameTime);
        float intensity = ItemTwitchHelper.getTwitchIntensity(stack, mc.level != null ? mc.level.getGameTime() : 0);
        if (intensity < 0.005f) return; // outside burst — skip corruption entirely
        if (CosmicShaders.corruptionIntensityUniform != null)
            CosmicShaders.corruptionIntensityUniform.set(intensity);

        RenderType rt = lateRender ? lateCorruptionRenderType(ctx) : CosmicRenderType.CORRUPTION;
        TextureAtlasSprite maskSprite = atlas.getSprite(getCorruptionMask());
        VertexConsumer vc = buf.getBuffer(rt);
        mc.getItemRenderer().renderQuadList(ps, vc,
                CosmicRenderUtils.bakeItem(maskSprite), stack, light, overlay);

        if (!lateRender && buf instanceof MultiBufferSource.BufferSource bs)
            bs.endBatch(rt);
    }
}
