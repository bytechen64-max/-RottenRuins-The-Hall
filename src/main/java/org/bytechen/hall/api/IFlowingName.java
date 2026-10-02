package org.bytechen.hall.api;

/**
 * 物品名字使用<b>流动彩字</b>（粉 → 紫往复渐变）的标记接口。
 *
 * <p>与本模组 {@code ICosmicLayer} / {@code ICustomOutline} / {@code IBlockingWeapon}
 * 同一套设计：接口即能力，渲染层只做 {@code instanceof} 判断，<b>不需要注册</b>。</p>
 *
 * <h3>最小用法</h3>
 * <pre>{@code
 * public class MySword extends SwordItem implements IFlowingName {
 *     // 用默认的粉→紫即可
 * }
 * }</pre>
 *
 * <h3>实现原理（为什么必须靠渲染层而不是改文字）</h3>
 * <p>两条"直觉做法"都行不通：</p>
 * <ul>
 *   <li>tooltip 的组件列表会被 {@code Screen} 缓存，只有内容变化才重建 ——
 *       按帧重建逐字颜色的 {@code Component} 不会动；</li>
 *   <li>在 {@code FormattedCharSink} 层改 {@code Style} 的颜色也没用 ——
 *       原版用的是<b>字形索引</b>去查预计算颜色表，颜色不读 Style。</li>
 * </ul>
 * <p>所以颜色在<b>绘制那一刻逐字给</b>：实现见 {@code FontMixin} 与
 * {@code FlowingNameColors}。这个接口只负责"哪些物品要这样显示"。</p>
 */
public interface IFlowingName {

    /**
     * 是否启用流动名字。默认 {@code true}。
     * 想按状态（比如耐久耗尽、被禁用）关掉时覆写它。
     */
    default boolean flowingNameEnabled() {
        return true;
    }

    /**
     * 渐变起色（ARGB）。默认亮粉 {@code 0xFFFF4FB8}。
     */
    default int flowingNameColorFrom() {
        return 0xFFFF4FB8;
    }

    /**
     * 渐变止色（ARGB）。默认紫 {@code 0xFF9B4DFF}。
     */
    default int flowingNameColorTo() {
        return 0xFF9B4DFF;
    }
}
