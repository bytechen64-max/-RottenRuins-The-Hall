package org.bytechen.hall.client.cosmic;

import org.bytechen.hall.api.CosmicStyle;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.bytechen.hall.client.mask.MaskLayerJson;
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

import java.util.List;
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
 *
 * <h3>Optional per-model keys</h3>
 * <ul>
 *   <li>{@code "style"} — a {@link CosmicStyle} shader value; picks the fragment
 *       path in {@code cosmic.fsh}. Omitted / unknown → {@link CosmicStyle#DEEP_SPACE}.
 *       {@code crimson_vow} uses {@code 17} ({@link CosmicStyle#CRIMSON_VOW}),
 *       {@code silent_daylight} uses {@code 18} ({@link CosmicStyle#SILENT_DAYLIGHT}).</li>
 *   <li>{@code "corruption"} — whether the RGB-split burst layer is enabled.
 *       Defaults to {@code true} to match historical behaviour.</li>
 * </ul>
 */
public class GeometryLoaderCosmic implements IGeometryLoader<GeometryLoaderCosmic.CosmicGeometry> {

    @Override
    public CosmicGeometry read(JsonObject jsonObject, JsonDeserializationContext ctx) throws JsonParseException {
        // 叠加的效果层先解析，然后把键从 JSON 里摘掉再交给 BlockModel —— 这两步
        // 必须在 fromString 之前，否则 BlockModel 会看到它不认识的 mask_layers。
        List<MaskLayerSpec> maskLayers = MaskLayerJson.parse(jsonObject);
        jsonObject.remove(MaskLayerJson.KEY);

        jsonObject.remove("loader");
        BlockModel blockModel = BlockModel.fromString(jsonObject.toString());

        JsonObject cosmicObj = jsonObject.getAsJsonObject("cosmic");
        String maskTexture = cosmicObj.get("mask").getAsString();

        CosmicStyle style = CosmicStyle.DEEP_SPACE;
        if (cosmicObj.has("style") && cosmicObj.get("style").isJsonPrimitive()) {
            style = CosmicStyle.fromShaderValue(cosmicObj.get("style").getAsInt());
        }

        boolean corruption = true;
        if (cosmicObj.has("corruption") && cosmicObj.get("corruption").isJsonPrimitive()) {
            corruption = cosmicObj.get("corruption").getAsBoolean();
        }

        boolean twitch = true;
        if (cosmicObj.has("twitch") && cosmicObj.get("twitch").isJsonPrimitive()) {
            twitch = cosmicObj.get("twitch").getAsBoolean();
        }

        return new CosmicGeometry(blockModel, ResourceLocation.tryParse(maskTexture),
                style, corruption, twitch, maskLayers);
    }

    public static class CosmicGeometry implements IUnbakedGeometry<CosmicGeometry> {
        private final BlockModel blockModel;
        private final ResourceLocation maskTexture;
        private final CosmicStyle style;
        private final boolean corruption;
        private final boolean twitch;
        /** 同一 JSON 里声明的附加 mask 效果层（可为空）。 */
        private final List<MaskLayerSpec> maskLayers;

        public CosmicGeometry(BlockModel blockModel, ResourceLocation maskTexture,
                              CosmicStyle style, boolean corruption, boolean twitch,
                              List<MaskLayerSpec> maskLayers) {
            this.blockModel = blockModel;
            this.maskTexture = maskTexture;
            this.style = style;
            this.corruption = corruption;
            this.twitch = twitch;
            this.maskLayers = maskLayers == null ? List.of() : maskLayers;
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
            bc.setStyle(this.style);
            bc.setCorruptionEnabled(this.corruption); // JSON cosmic items also get corruption
            bc.setTwitchEnabled(this.twitch);
            // 附加的效果层挂到同一个模型上，和星空层叠加而不是互斥。
            bc.setMaskLayers(this.maskLayers);
            return bc;
        }
    }
}
