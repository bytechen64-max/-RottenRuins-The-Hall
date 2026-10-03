package org.bytechen.hall.overworld.registry.items;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 「格挡减伤条」的数据载体 —— 任何 {@code IBlockingWeapon} 都能用它。
 *
 * <p>原本叫 {@code CrimsonVowTooltip}，只服务绯红誓约；寂寒白日接进来的时候按项目惯例
 * （{@code WeaponBlock} 是从 {@code CrimsonVowBlock} 通用化来的）抽成了通用组件：
 * 图案逻辑一模一样，差别只在<b>文案键</b>和<b>四个颜色</b>，于是这两样跟着数据走。</p>
 *
 * <h3>为什么文案键也在这里</h3>
 * <p>条上的标题与数值是文字，各物品可以不同（"格挡减伤" / 别的说法）。让数据带上
 * 键名，客户端渲染层就不必认识任何具体物品 —— 这与颜色随数据传是同一条理由。
 * 键名是 {@code String}，公共代码可以安全携带。</p>
 *
 * <h3>为什么是一个只有基本类型的 record</h3>
 * <p>{@code Item#getTooltipImage} 是<b>公共代码</b>（Forge 补丁），两端都可能调到，
 * 所以这里绝不能出现 client-only 类型（{@code ClientTooltipComponent}、
 * {@code GuiGraphics}、{@code Font} 全在客户端那一半）。真正画它的是
 * {@code org.bytechen.hall.client.tooltip.ClientBlockBarTooltip}，通过
 * {@code RegisterClientTooltipComponentFactoriesEvent} 按<b>这个类</b>注册过去
 * （工厂表按 {@code component.getClass()} 精确匹配，所以必须是 final 的 record）。</p>
 *
 * @param blockMultiplier 格挡成功时的伤害倍率（{@code 0.25} = 减伤 75%），
 *                        由 {@code IBlockingWeapon#blockDamageMultiplier()} 取值，
 *                        条把它画成填充比例，<b>不写死</b>
 * @param labelKey        条左侧标题的翻译键
 * @param valueKey        条右侧数值的翻译键（带一个 {@code %s}：减伤百分比整数）
 * @param accentFrom      强调色（亮端）
 * @param accentTo        强调色（暗端）
 * @param highlight       高光色（标签与刻度线）
 * @param shadow          暗部色（未填充的轨道）
 */
public record BlockBarTooltip(
        float blockMultiplier,
        String labelKey,
        String valueKey,
        int accentFrom,
        int accentTo,
        int highlight,
        int shadow) implements TooltipComponent {
}
