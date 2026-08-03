package org.bytechen.hall.client.cosmic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Abstract base for custom baked models that intercept the entire item render
 * pipeline, matching {@code mystery_buding.live.client.cosmic.BakedModelRendererBase}.
 *
 * <p>Subclasses override {@link #render} to perform custom rendering (e.g. a
 * cosmic shader layer on top of the standard model).  All standard
 * {@link BakedModel} methods delegate to the wrapped {@code inner} model.</p>
 */
public abstract class BakedModelRendererBase implements BakedModel {

    protected final BakedModel inner;

    public BakedModelRendererBase(BakedModel inner) {
        this.inner = inner;
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
        return inner.getQuads(state, direction, random);
    }

    @Override public boolean useAmbientOcclusion()  { return inner.useAmbientOcclusion(); }
    @Override public boolean isGui3d()               { return inner.isGui3d(); }
    @Override public boolean usesBlockLight()        { return inner.usesBlockLight(); }
    @Override public boolean isCustomRenderer()      { return inner.isCustomRenderer(); }

    @SuppressWarnings("deprecation")
    @Override public TextureAtlasSprite getParticleIcon() { return inner.getParticleIcon(); }

    @SuppressWarnings("deprecation")
    @Override public ItemTransforms getTransforms()  { return inner.getTransforms(); }
    @Override public ItemOverrides getOverrides()    { return inner.getOverrides(); }

    /**
     * Custom render entry-point.  Called by {@link MixinItemRendererCosmic}
     * instead of the vanilla {@code ItemRenderer.render()} when the model
     * is an instance of this class.
     */
    public abstract void render(
            ItemStack stack,
            ItemDisplayContext context,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource multiBufferSource,
            int packedLight,
            int packedOverlay,
            BakedModel model);
}
