package org.bytechen.hall.client.mask;

import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.bytechen.hall.client.cosmic.BakedModelRendererBase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 只携带 mask 效果层、不要星空时的 BakedModel 包装。
 *
 * <p>对应模型 JSON 里的 {@code "loader": "hall:mask_layers"}。与
 * {@code BakedModelCosmic} 的关系是兄弟而不是父子：两者都只是「把层带到物品渲染
 * 管线」的载体，真正的绘制都由 {@code MaskLayerRenderer} 统一完成。</p>
 *
 * <p>之所以要有它，是因为 {@code BakedModelCosmic} 那个 loader 要求 JSON 里有
 * {@code "cosmic"} 块（它要拿 mask 去画星空）。一件只想要泛光、不想要星空的
 * 物品不该被迫先声明一个用不到的星空层。</p>
 */
public class BakedModelMaskLayers extends BakedModelRendererBase implements IMaskLayerCarrier {

    private final List<MaskLayerSpec> maskLayers;

    public BakedModelMaskLayers(BakedModel inner, List<MaskLayerSpec> maskLayers) {
        super(inner);
        this.maskLayers = List.copyOf(maskLayers);
    }

    @Override
    public List<MaskLayerSpec> maskLayers() {
        return maskLayers;
    }

    /** 本体仍由原版渲染（getQuads 委托给 inner），本类不接管绘制。 */
    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    /** 空实现：物品本体照常走原版路径，本类只负责「携带层」。 */
    @Override
    public void render(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay, BakedModel model) {
    }
}
