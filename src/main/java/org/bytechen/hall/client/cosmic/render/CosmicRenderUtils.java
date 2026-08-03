package org.bytechen.hall.client.cosmic.render;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.SimpleModelState;
import org.bytechen.hall.HallMod;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Bakes item quads from {@link TextureAtlasSprite} instances for use with
 * the cosmic shader layer, matching
 * {@code mystery_buding.live.render.CosmicRenderUtils} exactly.
 *
 * <p>The {@link ItemModelGenerator} decomposes a layered 2D texture into
 * {@link BlockElement} faces; the {@link FaceBakery} bakes those faces
 * into {@link BakedQuad} instances that can be rendered by
 * {@code ItemRenderer.renderQuadList()}.</p>
 */
public class CosmicRenderUtils {
    public static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    public static final FaceBakery FACE_BAKERY = new FaceBakery();

    public static List<BakedQuad> bakeItem(TextureAtlasSprite... sprites) {
        return bakeItem(Transformation.identity(), sprites);
    }

    public static List<BakedQuad> bakeItem(Transformation state, TextureAtlasSprite... sprites) {
        List<BakedQuad> quads = new LinkedList<>();
        ResourceLocation dummyLoc = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "cosmic_bakery");

        for (int i = 0; i < sprites.length; i++) {
            TextureAtlasSprite sprite = sprites[i];
            List<BlockElement> unbaked = ITEM_MODEL_GENERATOR.processFrames(i, "layer" + i, sprite.contents());

            for (BlockElement element : unbaked) {
                for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                    quads.add(FACE_BAKERY.bakeQuad(
                            element.from, element.to, entry.getValue(), sprite,
                            entry.getKey(), new SimpleModelState(state),
                            element.rotation, element.shade, dummyLoc));
                }
            }
        }

        return quads;
    }
}
