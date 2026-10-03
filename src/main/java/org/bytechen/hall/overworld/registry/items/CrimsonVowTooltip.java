package org.bytechen.hall.overworld.registry.items;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 绯红誓约 tooltip 里那条自绘「誓约条」的<b>数据载体</b>（纯数据，不含任何绘制代码）。
 *
 * <p>与本模组 {@code ICosmicLayer} / {@code IFlowingName} 同一套思路的延伸：
 * 物品只负责<b>声明</b>"我这条 tooltip 要长这样"，不认识任何渲染类型。</p>
 *
 * <h3>为什么是一个只有基本类型的 record</h3>
 * <p>{@code Item#getTooltipImage} 是<b>公共代码</b>（Forge 补丁，见 {@code Item.java}），
 * 它既会被客户端的 {@code GuiGraphics.renderTooltip} 调到，也可能在服务端被调到 ——
 * 所以这个类里绝不能出现 client-only 类型（{@code ClientTooltipComponent}、
 * {@code GuiGraphics}、{@code Font} 全在客户端那一半）。真正画它的是
 * {@code org.bytechen.hall.client.tooltip.ClientCrimsonVowTooltip}，
 * 通过 {@code RegisterClientTooltipComponentFactoriesEvent} 按<b>这个类</b>注册过去
 * （Forge 的工厂表是按 {@code component.getClass()} 精确匹配的，所以这个类型必须
 * 是 final 的 record，不能有子类）。</p>
 *
 * <h3>为什么颜色也跟着数据一起过来</h3>
 * <p>面板用的四个颜色<b>就是这把剑自己的色板</b>（名字的粉紫渐变 + 描边的暗部/亮部）。
 * 让它们随数据一起传递，客户端渲染层就不必认识 {@code CrimsonVow}，
 * 也就不会出现"改了描边色、tooltip 还是老配色"这种两处漂移。</p>
 *
 * @param blockMultiplier 格挡成功时的伤害倍率（{@code 0.25} = 减伤 75%），
 *                        由 {@code IBlockingWeapon#blockDamageMultiplier()} 取值，
 *                        誓约条把它画成填充比例，<b>不写死</b>。
 * @param accentFrom      强调色（亮粉），同名字渐变起色
 * @param accentTo        强调色（紫），同名字渐变止色
 * @param highlight       高光色（雾粉紫），同描边亮部
 * @param shadow          暗部色（深紫黑），同描边暗部
 */
public record CrimsonVowTooltip(
        float blockMultiplier,
        int accentFrom,
        int accentTo,
        int highlight,
        int shadow) implements TooltipComponent {
}
