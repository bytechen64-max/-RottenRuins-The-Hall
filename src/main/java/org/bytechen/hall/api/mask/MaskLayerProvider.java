package org.bytechen.hall.api.mask;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品自描述的 mask 层来源。
 *
 * <p>实现它的物品不需要任何模型 JSON 改动、也不需要任何注册调用，
 * 就能在宇宙星空层之上再挂任意多个 mask 效果层：</p>
 *
 * <pre>{@code
 * public class MySword extends SwordItem implements MaskLayerProvider {
 *
 *     private static final ResourceLocation MASK =
 *             ResourceLocation.fromNamespaceAndPath("hall", "item/my_sword_mask");
 *
 *     @Override
 *     public List<MaskLayerSpec> maskLayers(ItemStack stack) {
 *         return List.of(
 *                 MaskLayerSpec.glow(MASK).color(0xFF3CF0FF).width(2.5f).build());
 *     }
 * }
 * }</pre>
 *
 * <h3>为什么按 ItemStack 取而不是按 Item</h3>
 * <p>层是可以跟着物品状态走的 —— 附魔、充能、NBT 等级都可能改变颜色或强度，
 * 而 {@code Item} 实例上拿不到这些。传 {@code ItemStack} 让这条路一开始就
 * 走得通，不用等到有人需要时再改签名。</p>
 *
 * <h3>注意</h3>
 * <p>本方法在<b>渲染热路径</b>上每帧会被调用（每个物品、每个渲染上下文各一次）。
 * 不要在里面分配大对象或做重量级查询；返回常量列表、或按 NBT 开关在
 * {@link List#of()} 与 {@link List#of(Object)} 之间二选一即可。</p>
 *
 * @see MaskLayerSpec
 * @see org.bytechen.hall.client.mask.MaskLayerRegistry
 */
public interface MaskLayerProvider {

    /**
     * 该物品携带的额外 mask 效果层。
     *
     * @param stack 正在渲染的物品栈，可用于按 NBT 状态动态决定层
     * @return 层列表，按 {@link MaskLayerSpec#order()} 升序绘制；没有则返回空列表
     */
    List<MaskLayerSpec> maskLayers(ItemStack stack);
}
