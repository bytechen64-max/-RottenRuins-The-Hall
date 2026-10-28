package org.bytechen.hall.api;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品侧声明"我这件东西要挂哪些效果"。
 *
 * <p>与 {@link ICustomOutline} 的分工：{@code ICustomOutline} 是**描边**这一个效果的
 * 专用接口（颜色/宽度/混合，历史包袱都在那儿）；本接口是<b>框架级</b>的入口，
 * 效果 id 由调用方给，物品不需要认识任何一个具体效果。</p>
 *
 * <p>示例（绯红誓约）：</p>
 * <pre>{@code
 * @Override public List<ItemFxBinding> itemFxLayers(ItemDisplayContext ctx) {
 *     if (ctx == ItemDisplayContext.HEAD) return List.of();
 *     return List.of(ItemFxBinding.of(FlameFxEffect.ID, ItemFxParams.of("strength", 1.0f)));
 * }
 * }</pre>
 */
public interface ItemFxProvider {

    /** 返回这件物品在给定上下文下要挂的效果层；返回空列表表示没有。 */
    default List<ItemFxBinding> itemFxLayers(ItemDisplayContext ctx) {
        return List.of();
    }

    /**
     * 实例级版本（管线优先用它）：默认退回 {@link #itemFxLayers(ItemDisplayContext)}。
     *
     * <p>和 {@code ICustomOutline} 那几个 {@code *For(ItemStack)} 是同一个理由：
     * 不带 {@code stack} 的重载表达不了"改名/NBT 之后才挂这个效果"。</p>
     */
    default List<ItemFxBinding> itemFxLayers(ItemStack stack, ItemDisplayContext ctx) {
        return itemFxLayers(ctx);
    }

    /** 便捷重载：不区分上下文（内部自己判断）。 */
    default List<ItemFxBinding> itemFxLayers() {
        return itemFxLayers(ItemDisplayContext.GUI);
    }

    /** 一个效果绑定：效果 id + 该效果的参数块。 */
    record ItemFxBinding(String effectId, ItemFxParams params) {

        public ItemFxBinding {
            if (effectId == null || effectId.isBlank()) {
                throw new IllegalArgumentException("效果绑定必须指定 effect id");
            }
            if (params == null) params = ItemFxParams.EMPTY;
        }

        public static ItemFxBinding of(String effectId) {
            return new ItemFxBinding(effectId, ItemFxParams.EMPTY);
        }

        public static ItemFxBinding of(String effectId, ItemFxParams params) {
            return new ItemFxBinding(effectId, params);
        }
    }
}
