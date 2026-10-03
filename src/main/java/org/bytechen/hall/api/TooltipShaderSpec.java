package org.bytechen.hall.api;

/**
 * tooltip 底板着色器效果的参数（纯数据，双端安全）。
 *
 * <p>由 {@link ITooltipStyle#tooltipShader()} 提供给客户端；真正画它的是
 * {@code org.bytechen.hall.client.tooltip.TooltipShaders}。这个 record 里
 * <b>只有 String / float / int</b>，所以物品类（公共代码）可以放心构造它，
 * 不必认识任何客户端类型 —— 与 {@code CrimsonVowTooltip} 是同一条约束。</p>
 *
 * <h3>key 与未知 key 的处理</h3>
 * <p>{@link #key()} 选的是<b>图案</b>，当前只有一个：
 * {@link #THERMAL}（热力学流动）。常量定义在这里而不是客户端类里，是为了让
 * 公共代码能写出这个 key 而不引用客户端类。客户端遇到不认识的 key 一律
 * <b>什么都不画</b>，退回纯色底板 —— 拿不到效果总比画错好。</p>
 *
 * <h3>颜色</h3>
 * <p>三个颜色都是 ARGB。<b>只有 {@code baseColor} 的 alpha 有意义</b>
 * （底板透明度），{@code flowColor} / {@code hotColor} 只取 RGB：
 * 它们是被"混"进底色里的，参与 alpha 会把混合算成两层透明。</p>
 *
 * @param key       效果 key，见 {@link #THERMAL}
 * @param intensity 强度，{@code 0} = 只剩底色（等于关掉效果）
 * @param baseColor 冷底（ARGB，alpha 参与）
 * @param flowColor 热流主色（ARGB，只取 RGB）
 * @param hotColor  热核高光（ARGB，只取 RGB）
 */
public record TooltipShaderSpec(
        String key,
        float intensity,
        int baseColor,
        int flowColor,
        int hotColor) {

    /** 热力学流动：下方加热的板，热羽上升（实现见 {@code rendertype_tooltip_thermal.fsh}）。 */
    public static final String THERMAL = "thermal";

    /** 常用写法：thermal + 三个颜色，强度给默认的 0.9。 */
    public static TooltipShaderSpec thermal(int baseColor, int flowColor, int hotColor) {
        return new TooltipShaderSpec(THERMAL, 0.9F, baseColor, flowColor, hotColor);
    }

    /** 该 key 是否是当前客户端认识的效果。判断放在这里，免得两边各写一份 if（key 可能为 null）。 */
    public static boolean isKnown(String key) {
        return THERMAL.equals(key);
    }
}
