package org.bytechen.hall.client.mask;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.util.List;
import java.util.function.Function;

/**
 * 模型 loader：{@code "loader": "hall:mask_layers"}。
 *
 * <pre>{@code
 * {
 *   "parent": "minecraft:item/handheld",
 *   "textures": { "layer0": "hall:item/my_sword" },
 *   "loader": "hall:mask_layers",
 *   "mask_layers": [
 *     { "effect": "glow", "mask": "hall:item/my_sword_mask", "color": "#FF66E0FF" }
 *   ]
 * }
 * }</pre>
 *
 * <p>物品本体按普通 {@link BlockModel} 解析与烘焙，唯一的区别是烘焙结果被包进
 * {@link BakedModelMaskLayers}，让物品渲染管线能从模型上取到层。</p>
 *
 * <p>要在<b>同一个 JSON</b> 里同时有星空和附加层时，不要用这个 loader，用
 * {@code "loader": "hall:cosmic"} 并在它的 JSON 里追加 {@code "mask_layers"} —— 
 * 一个模型只能有一个 loader。</p>
 */
public class GeometryLoaderMaskLayers
        implements IGeometryLoader<GeometryLoaderMaskLayers.MaskLayersGeometry> {

    /** 注册用的 loader id；模型 JSON 里写作 {@code "hall:mask_layers"}。 */
    public static final String LOADER_ID = "mask_layers";

    @Override
    public MaskLayersGeometry read(JsonObject jsonObject, JsonDeserializationContext ctx)
            throws JsonParseException {
        List<MaskLayerSpec> layers = MaskLayerJson.parse(jsonObject);

        // 先把本系统自己的键摘掉，再交给 BlockModel 解析。未知键 BlockModel 本来
        // 也会忽略，但显式摘掉能让人一眼看出「哪些键归这套系统管」。
        jsonObject.remove(MaskLayerJson.KEY);
        jsonObject.remove("loader");
        BlockModel blockModel = BlockModel.fromString(jsonObject.toString());

        if (layers.isEmpty()) {
            HallMod.LOGGER.warn("[MaskLayer] loader \"hall:{}\" 没有解析出任何层，"
                    + "这件物品会退化成普通模型（检查 mask_layers 的写法）", LOADER_ID);
        }
        return new MaskLayersGeometry(blockModel, layers);
    }

    public static class MaskLayersGeometry implements IUnbakedGeometry<MaskLayersGeometry> {

        private final BlockModel blockModel;
        private final List<MaskLayerSpec> maskLayers;

        public MaskLayersGeometry(BlockModel blockModel, List<MaskLayerSpec> maskLayers) {
            this.blockModel = blockModel;
            this.maskLayers = maskLayers;
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
            return new BakedModelMaskLayers(baked, this.maskLayers);
        }
    }
}
