package org.bytechen.rottenruins.api.text;

/**
 * 给 Java 作者用的 § 分节符文本<b>构建器</b>（纯 Java，双端安全 —— 公共代码/服务端也能调用，
 * 只是字符串里会带标记，真正渲染发生在客户端）。
 *
 * <h3>为什么要有这一层</h3>
 * <p>标记语法是给 lang / datapack 作者手写的；Java 里如果到处手拼
 * {@code "§[rrs:grad:#ff0000:#00ff00]"}，一是容易拼错，二是把"语法细节"扩散到
 * 物品/命令/官方代码里，将来改语法要全仓库搜。这里每个效果一个方法，调用点只依赖
 * 方法名与语义。</p>
 *
 * <h3>怎么用</h3>
 * <pre>{@code
 *   Component.literal(TextEffectText.gradient("腐朽之疫", 0x6FBF4A, 0x8B1E2D));
 *   Component.literal("警示：" + TextEffectText.shake("不可直视", 1.0F, 2.0F));
 * }</pre>
 *
 * <p>注意：构建出来的字符串<b>带标记</b>，它的 {@code getString()} 与 {@code length()}
 * 都会包含标记文本 —— 只用于显示，不要拿去做名字比较 / 铁砧 / 命令选择器。</p>
 */
public final class TextEffectText {

    private TextEffectText() {}

    /** `#RRGGBB`（不带 alpha）。 */
    public static String hex(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** 打开一个效果 span（效果名 + 已拼好的参数串，例如 {@code open("grad:#ff0000:#00ff00")}）。 */
    public static String open(String effectAndParams) {
        return "\u00a7[" + TextEffectIds.NAMESPACE + ":" + effectAndParams + "]";
    }

    /** 关闭最内层 span。 */
    public static String close() {
        return TextEffectIds.CLOSE_ALL;
    }

    /** 清空全部自定义效果。 */
    public static String reset() {
        return TextEffectIds.RESET_ALL;
    }

    /** 渐变·腐化：沿文字从 {@code fromRgb} 到 {@code toRgb}（线性光插值，静止）。 */
    public static String gradient(String text, int fromRgb, int toRgb) {
        return gradient(text, fromRgb, toRgb, 0F);
    }

    /**
     * 渐变·腐化（可流动）。
     *
     * @param speed 流动速度；{@code 0} = 静止渐变（与旧行为一致），{@code 0.3~1.0} 有明显流动感。
     *              流动是"相位随时间沿文字推进"，首尾闭环无缝。
     */
    public static String gradient(String text, int fromRgb, int toRgb, float speed) {
        String params = TextEffectIds.GRAD + ":" + hex(fromRgb) + ":" + hex(toRgb);
        if (speed > 0F) {
            params += ":speed=" + fmt(speed);
        }
        return open(params) + text + close();
    }

    /** 任意纯色。 */
    public static String solid(String text, int rgb) {
        return open(TextEffectIds.SOLID + ":" + hex(rgb)) + text + close();
    }

    /** 彩虹流彩。 */
    public static String rainbow(String text) {
        return open(TextEffectIds.RAINBOW) + text + close();
    }

    /** 呼吸（亮度脉动）。 */
    public static String pulse(String text, int rgb, float amp, float speed) {
        return open(TextEffectIds.PULSE + ":" + hex(rgb) + ":amp=" + fmt(amp) + ":speed=" + fmt(speed))
                + text + close();
    }

    /** 抖动（只改绘制坐标，不改排版）。 */
    public static String shake(String text, float amp, float freq) {
        return open(TextEffectIds.SHAKE + ":amp=" + fmt(amp) + ":freq=" + fmt(freq)) + text + close();
    }

    /** 波浪（纵向正弦位移）。 */
    public static String wave(String text, float amp, float len, float speed) {
        return open(TextEffectIds.WAVE + ":amp=" + fmt(amp) + ":len=" + fmt(len) + ":speed=" + fmt(speed))
                + text + close();
    }

    /** 故障（随机瞬移的崩坏文本）。 */
    public static String glitch(String text, float amp, float rate, float prob) {
        return open(TextEffectIds.GLITCH + ":amp=" + fmt(amp) + ":rate=" + fmt(rate) + ":prob=" + fmt(prob))
                + text + close();
    }

    /** 浮点格式化：去掉多余小数位，保持标记短。 */
    private static String fmt(float v) {
        if (v == Math.round(v)) {
            return Integer.toString((int) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
