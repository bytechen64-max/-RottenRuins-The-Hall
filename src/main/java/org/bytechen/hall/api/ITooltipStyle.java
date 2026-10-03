package org.bytechen.hall.api;

/**
 * 物品 tooltip <b>底板配色</b>的标记接口 —— 换掉原版那块深灰底 + 蓝紫边。
 *
 * <p>与本模组 {@code ICustomOutline} / {@code IFlowingName} / {@code IBlockingWeapon}
 * 同一套设计：接口即能力，客户端只做 {@code instanceof} 判断，<b>不需要注册</b>。</p>
 *
 * <h3>最小用法</h3>
 * <pre>{@code
 * public class MySword extends SwordItem implements ITooltipStyle {
 *     // 四个色都覆写才有意义；只实现接口不覆写 = 什么都没改（默认值就是原版色）
 * }
 * }</pre>
 *
 * <h3>挂载点：为什么是事件而不是"自己画一块底板"</h3>
 * <p>原版底板由 {@code TooltipRenderUtil.renderTooltipBackground} 绘制，
 * 它自己是个私有方法，颜色来自 {@code GuiGraphics.renderTooltipInternal} 里
 * 那一次 {@code RenderTooltipEvent.Color} 的四个 getter：</p>
 *
 * <pre>
 *   RenderTooltipEvent.Color e = ForgeHooksClient.onRenderTooltipColor(stack, ...);
 *   TooltipRenderUtil.renderTooltipBackground(this, x, y, w, h, 400,
 *           e.getBackgroundStart(), e.getBackgroundEnd(),    // 底：纵向渐变
 *           e.getBorderStart(),     e.getBorderEnd());       // 边：1px 内框，纵向渐变
 * </pre>
 *
 * <p>所以"改底板"这件事 Forge 已经开好了口子：在 Forge 总线上改这四个值即可，
 * <b>不需要混入、不需要自己画底板</b>，也就不会和后处理泛光、光影包、
 * 其它 tooltip mod 打架。真正干活的是
 * {@code org.bytechen.hall.client.tooltip.TooltipStyleHook}。</p>
 *
 * <h3>默认值 = 原版配色</h3>
 * <p>四个默认值逐字对应原版常量，所以"实现了接口但一个色都不覆写"的语义是
 * <b>保持原样</b>，而不是变成透明或黑色：</p>
 *
 * <table border="1">
 *   <tr><th>方法</th><th>原版常量</th><th>字面量</th></tr>
 *   <tr><td>{@link #tooltipBackgroundTop()} / {@link #tooltipBackgroundBottom()}</td>
 *       <td>{@code TooltipRenderUtil.BACKGROUND_COLOR}</td><td>{@code 0xF0100010}</td></tr>
 *   <tr><td>{@link #tooltipBorderTop()}</td>
 *       <td>{@code BORDER_COLOR_TOP}</td><td>{@code 0x505000FF}</td></tr>
 *   <tr><td>{@link #tooltipBorderBottom()}</td>
 *       <td>{@code BORDER_COLOR_BOTTOM}</td><td>{@code 0x5028007F}</td></tr>
 * </table>
 *
 * <p>底色那两个的 alpha 是 {@code 0xF0}（半透明），边框是 {@code 0x50}（很淡）——
 * 想让自己的配色更"实"，把边框 alpha 抬到 {@code 0x80} 上下即可。</p>
 *
 * <h3>作用范围</h3>
 * <p>只管<b>原版物品 tooltip 的底板</b>。JEI 这类自绘 tooltip 的 mod 走的是自己的
 * 绘制路径，不受这里影响（它们连 {@code RenderTooltipEvent.Color} 都不发）。</p>
 */
public interface ITooltipStyle {

    /**
     * 是否启用自定义底板。默认 {@code true}。
     * 想按状态（例如某个模式下回到原版观感）关掉时覆写它。
     */
    default boolean tooltipStyleEnabled() {
        return true;
    }

    /** 底板渐变的上端色（ARGB）。默认原版 {@code 0xF0100010}。 */
    default int tooltipBackgroundTop() {
        return 0xF0100010;
    }

    /** 底板渐变的下端色（ARGB）。默认原版 {@code 0xF0100010}（与原版一样是纯色底）。 */
    default int tooltipBackgroundBottom() {
        return 0xF0100010;
    }

    /** 边框 1px 内框的上端色（ARGB，带 alpha）。默认原版 {@code 0x505000FF}。 */
    default int tooltipBorderTop() {
        return 0x505000FF;
    }

    /** 边框 1px 内框的下端色（ARGB，带 alpha）。默认原版 {@code 0x5028007F}。 */
    default int tooltipBorderBottom() {
        return 0x5028007F;
    }

    /**
     * 底板上的着色器<b>效果</b>（例如粉色的热力学流动）。默认 {@code null} = 只用纯色底板。
     *
     * <h3>它是怎么被画上去的</h3>
     * <p>原版底板是"先铺底色、再压 1px 内框"，而 Forge 只在<b>铺底色之前</b>
     * 给了钩子（{@code RenderTooltipEvent.Color}，那一层没有 PostBackground）。
     * 所以接上效果之后的顺序是：</p>
     *
     * <ol>
     *   <li>把原版<b>底色</b>的 alpha 设成 0（让位），<b>边框保留</b>；</li>
     *   <li>在底色本该占的那块矩形上画一层着色器四边形
     *       （矩形 = {@code (x-4, y-4, w+8, h+8)}，与 {@code TooltipRenderUtil}
     *       实际涂到的范围逐像素对齐）；</li>
     *   <li>原版接着把 1px 内框画在效果<b>之上</b>，文字再压在两者之上。</li>
     * </ol>
     *
     * <p>着色器没加载成功（驱动/资源问题）时，客户端会<b>自动退回</b>到
     * {@link #tooltipBackgroundTop()} / {@link #tooltipBackgroundBottom()}
     * 的纯色底板 —— 也就是"效果没了，但 tooltip 还是一个正常的 tooltip"。</p>
     *
     * @return null（默认）表示不要效果；非 null 时 {@code key} 决定图案，
     *         三个颜色决定配色。未知 key 会被客户端忽略并退回纯色底板。
     */
    default TooltipShaderSpec tooltipShader() {
        return null;
    }
}
