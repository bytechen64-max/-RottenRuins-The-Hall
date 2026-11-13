package org.bytechen.rottenruins.api.text;

/**
 * 给 Java 作者用的 § 分节符文本构建器（纯 Java，双端安全：公共代码/服务端也能调用，真正渲染在客户端）。
 * 注意：产出的字符串带标记，{@code getString()} 与 {@code length()} 都含标记文本，只用于显示，勿做名字比较/铁砧/命令选择器。
 */
public final class TextEffectText {

    private TextEffectText() {}

    /** `#RRGGBB`（不带 alpha）。 */
    public static String hex(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static String open(String effectAndParams) {
        return "\u00a7[" + TextEffectIds.NAMESPACE + ":" + effectAndParams + "]";
    }

    public static String close() {
        return TextEffectIds.CLOSE_ALL;
    }

    public static String reset() {
        return TextEffectIds.RESET_ALL;
    }

    /** 渐变·腐化：沿文字从 {@code fromRgb} 到 {@code toRgb}（线性光插值，静止）。 */
    public static String gradient(String text, int fromRgb, int toRgb) {
        return gradient(text, fromRgb, toRgb, 0F);
    }

    /** 可流动渐变；speed=0 静止（与旧行为一致），0.3~1.0 有明显流动感，相位随时间沿文字推进、首尾闭环无缝。 */
    public static String gradient(String text, int fromRgb, int toRgb, float speed) {
        String params = TextEffectIds.GRAD + ":" + hex(fromRgb) + ":" + hex(toRgb);
        if (speed > 0F) {
            params += ":speed=" + fmt(speed);
        }
        return open(params) + text + close();
    }

    public static String solid(String text, int rgb) {
        return open(TextEffectIds.SOLID + ":" + hex(rgb)) + text + close();
    }

    public static String rainbow(String text) {
        return open(TextEffectIds.RAINBOW) + text + close();
    }

    public static String pulse(String text, int rgb, float amp, float speed) {
        return open(TextEffectIds.PULSE + ":" + hex(rgb) + ":amp=" + fmt(amp) + ":speed=" + fmt(speed))
                + text + close();
    }

    /** 抖动：只改绘制坐标，不改排版。 */
    public static String shake(String text, float amp, float freq) {
        return open(TextEffectIds.SHAKE + ":amp=" + fmt(amp) + ":freq=" + fmt(freq)) + text + close();
    }

    public static String wave(String text, float amp, float len, float speed) {
        return open(TextEffectIds.WAVE + ":amp=" + fmt(amp) + ":len=" + fmt(len) + ":speed=" + fmt(speed))
                + text + close();
    }

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
