package org.bytechen.hall.client.cosmic;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

/**
 * Custom model loader for the {@code "splendiding:cosmic"} model type,
 * matching {@code mystery_buding.live.client.cosmic.GeometryLoaderCosmic}.
 *
 * <p>Item model JSON files specify:
 * <pre>{@code
 * { "loader": "splendiding:cosmic", "cosmic": { "mask": "splendiding:item/void_sword_mask" } }
 * }</pre>
 *
 * <p>The loader parses the JSON as a standard {@link BlockModel}, extracts the
 * mask texture resource location, and wraps the baked result in a
 * {@link BakedModelCosmic} instance.</p>
 */
public class GeometryLoaderCosmic implements IGeometryLoader<GeometryLoaderCosmic.CosmicGeometry> {

    @Override
    public CosmicGeometry read(JsonObject jsonObject, JsonDeserializationContext ctx) throws JsonParseException {
        jsonObject.remove("loader");
        BlockModel blockModel = BlockModel.fromString(jsonObject.toString());

        JsonObject cosmicObj = jsonObject.getAsJsonObject("cosmic");
        String maskTexture = cosmicObj.get("mask").getAsString();

        return new CosmicGeometry(blockModel, ResourceLocation.tryParse(maskTexture));
    }

    public static class CosmicGeometry implements IUnbakedGeometry<CosmicGeometry> {
        private final BlockModel blockModel;
        private final ResourceLocation maskTexture;

        public CosmicGeometry(BlockModel blockModel, ResourceLocation maskTexture) {
            this.blockModel = blockModel;
            this.maskTexture = maskTexture;
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                                   IGeometryBakingContext ctx) {
            this.blockModel.resolveParents(modelGetter);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext ctx, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides,
                               ResourceLocation modelLocation) {
            BakedModel baked = this.blockModel.bake(baker, this.blockModel, spriteGetter,
                    modelState, modelLocation, true);
            BakedModelCosmic bc = new BakedModelCosmic(baked, this.maskTexture);
            bc.setCorruptionEnabled(true); // JSON cosmic items also get corruption
            return bc;
        }
    }
}
