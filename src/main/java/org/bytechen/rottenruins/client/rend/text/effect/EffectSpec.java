package org.bytechen.rottenruins.client.rend.text.effect;

import org.bytechen.rottenruins.api.text.TextEffectIds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 单个效果的<b>不可变参数块</b>（纯 Java，不引用任何 Minecraft 类 —— 解析/上色/位移
 * 三个环节共用它，也是单测唯一需要构造的"效果数据"）。
 *
 * <h3>为什么不用 Map&lt;String,Object&gt;</h3>
 * <p>效果参数要每帧被读几十次（逐字取色/取位移），Map 查找 + 装箱会进入热路径；
 * 而参数的合法集合是封闭的（amp/speed/len/…），用固定字段既快又能在解析时
 * 一次性 clamp，渲染时不需要再防御。</p>
 *
 * <h3>默认值从哪来</h3>
 * <p>全部来自 {@link TextEffectIds} —— 与提案 §4.3、预览 GIF 的公式保持同一来源，
 * 改默认值只需要改一个地方。</p>
 */
public final class EffectSpec {

    public enum Kind {
        GRAD, SOLID, RAINBOW, PULSE, SHAKE, WAVE, GLITCH
    }

    /** 渐变最多 4 个停靠色；其它颜色类效果只用第 0 个。 */
    public final Kind kind;
    public final int[] colors;

    public final float amp;
    public final float speed;
    public final float sat;
    public final float val;
    public final float len;
    public final float rate;
    public final float prob;

    private EffectSpec(Kind kind, int[] colors, float amp, float speed, float sat, float val,
                       float len, float rate, float prob) {
        this.kind = kind;
        this.colors = colors;
        this.amp = amp;
        this.speed = speed;
        this.sat = sat;
        this.val = val;
        this.len = len;
        this.rate = rate;
        this.prob = prob;
    }

    /** 颜色类效果（需要逐字覆盖 Style.color）返回 true；位移类返回 false。 */
    public boolean isColor() {
        switch (kind) {
            case GRAD:
            case SOLID:
            case RAINBOW:
            case PULSE:
                return true;
            default:
                return false;
        }
    }

    /**
     * 解析一个效果标记（名字 + 参数 token 串）。
     *
     * @param name  效果名（已小写，不含命名空间）
     * @param tokens 冒号分隔之后的参数 token（可能为空）
     * @return 解析成功的效果；未知效果名返回 {@code null}（调用方按"零宽无效标记"处理）
     */
    public static EffectSpec parse(String name, List<String> tokens) {
        Kind kind;
        switch (name) {
            case TextEffectIds.GRAD -> kind = Kind.GRAD;
            case TextEffectIds.SOLID -> kind = Kind.SOLID;
            case TextEffectIds.RAINBOW -> kind = Kind.RAINBOW;
            case TextEffectIds.PULSE -> kind = Kind.PULSE;
            case TextEffectIds.SHAKE -> kind = Kind.SHAKE;
            case TextEffectIds.WAVE -> kind = Kind.WAVE;
            case TextEffectIds.GLITCH -> kind = Kind.GLITCH;
            default -> {
                return null;
            }
        }

        Map<String, String> kv = new HashMap<>();
        List<String> positional = new ArrayList<>(tokens.size());
        for (String raw : tokens) {
            String token = raw == null ? "" : raw.trim();
            if (token.isEmpty()) continue;
            int eq = token.indexOf('=');
            if (eq > 0) {
                kv.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT),
                        token.substring(eq + 1).trim());
            } else {
                positional.add(token);
            }
        }

        switch (kind) {
            case GRAD: {
                List<Integer> list = new ArrayList<>(4);
                for (String token : positional) {
                    if (isColorToken(token)) list.add(parseColor(token, -1));
                }
                addColor(list, kv.get("from"), -1);
                addColor(list, kv.get("to"), -1);
                addColor(list, kv.get("color1"), -1);
                addColor(list, kv.get("color2"), -1);
                addColor(list, kv.get("color3"), -1);
                addColor(list, kv.get("color4"), -1);
                if (list.isEmpty()) {
                    list.add(TextEffectIds.DEFAULT_GRAD_FROM);
                    list.add(TextEffectIds.DEFAULT_GRAD_TO);
                } else if (list.size() == 1) {
                    list.add(TextEffectIds.DEFAULT_GRAD_TO);
                }
                int[] colors = new int[Math.min(4, list.size())];
                for (int i = 0; i < colors.length; i++) colors[i] = list.get(i);
                // speed > 0 时渐变会沿文字"流动"（见 EffectColor）；默认 0 = 静止渐变，
                // 保持旧内容/预览 GIF 的观感不变。
                float speed = clamp(kvFloat(kv, "speed", positional, -1, 0F), 0F, 8F);
                return new EffectSpec(kind, colors, 0F, speed, 0F, 0F, 0F, 0F, 0F);
            }
            case SOLID: {
                int color = firstColor(positional, kv.get("color"), TextEffectIds.DEFAULT_PALE);
                return new EffectSpec(kind, new int[]{color}, 0F, 1F, 0F, 0F, 0F, 0F, 0F);
            }
            case RAINBOW: {
                float speed = kvFloat(kv, "speed", positional, 0, TextEffectIds.DEFAULT_RAINBOW_SPEED);
                float sat = clamp(kvFloat(kv, "sat", positional, 1, TextEffectIds.DEFAULT_RAINBOW_SAT), 0F, 1F);
                float val = clamp(kvFloat(kv, "val", positional, 2, TextEffectIds.DEFAULT_RAINBOW_VAL), 0F, 1F);
                return new EffectSpec(kind, new int[]{0}, 0F, clampSpeed(speed), sat, val, 0F, 0F, 0F);
            }
            case PULSE: {
                int color = firstColor(positional, kv.get("color"), TextEffectIds.DEFAULT_PALE);
                float amp = clamp(kvFloat(kv, "amp", positional, colorIndexOffset(positional, kv),
                        TextEffectIds.DEFAULT_PULSE_AMP), 0F, 1F);
                float speed = clampSpeed(kvFloat(kv, "speed", positional,
                        colorIndexOffset(positional, kv) + 1, TextEffectIds.DEFAULT_PULSE_SPEED));
                return new EffectSpec(kind, new int[]{color}, amp, speed, 0F, 0F, 0F, 0F, 0F);
            }
            case SHAKE: {
                float amp = clamp(kvFloat(kv, "amp", positional, 0, TextEffectIds.DEFAULT_SHAKE_AMP), 0F, 8F);
                float freq = clampSpeed(kvFloat(kv, "freq", positional, 1, TextEffectIds.DEFAULT_SHAKE_FREQ));
                return new EffectSpec(kind, new int[]{0}, amp, freq, 0F, 0F, 0F, 0F, 0F);
            }
            case WAVE: {
                float amp = clamp(kvFloat(kv, "amp", positional, 0, TextEffectIds.DEFAULT_WAVE_AMP), 0F, 8F);
                float len = clamp(kvFloat(kv, "len", positional, 1, TextEffectIds.DEFAULT_WAVE_LEN), 1F, 128F);
                float speed = clampSpeed(kvFloat(kv, "speed", positional, 2, TextEffectIds.DEFAULT_WAVE_SPEED));
                return new EffectSpec(kind, new int[]{0}, amp, speed, 0F, 0F, len, 0F, 0F);
            }
            case GLITCH: {
                float amp = clamp(kvFloat(kv, "amp", positional, 0, TextEffectIds.DEFAULT_GLITCH_AMP), 0F, 12F);
                float rate = clamp(kvFloat(kv, "rate", positional, 1, TextEffectIds.DEFAULT_GLITCH_RATE), 0.5F, 60F);
                float prob = clamp(kvFloat(kv, "prob", positional, 2, TextEffectIds.DEFAULT_GLITCH_PROB), 0F, 1F);
                return new EffectSpec(kind, new int[]{0}, amp, rate, 0F, 0F, 0F, rate, prob);
            }
            default:
                return null;
        }
    }

    // ── 参数工具 ─────────────────────────────────────────────────

    private static int colorIndexOffset(List<String> positional, Map<String, String> kv) {
        // pulse 的颜色既可能写成位置参数，也可能写成 color=；位置参数里的颜色不算进"后续数字参数"的偏移。
        for (String token : positional) {
            if (isColorToken(token)) return 1;
        }
        return 0;
    }

    private static boolean isColorToken(String token) {
        return token != null && (token.startsWith("#") || token.equalsIgnoreCase("color"));
    }

    private static void addColor(List<Integer> list, String value, int fallback) {
        if (value == null) return;
        int parsed = parseColor(value, fallback);
        if (parsed >= 0) list.add(parsed);
    }

    private static int firstColor(List<String> positional, String kvColor, int fallback) {
        for (String token : positional) {
            if (isColorToken(token)) {
                int c = parseColor(token, -1);
                if (c >= 0) return c;
            }
        }
        if (kvColor != null) {
            int c = parseColor(kvColor, -1);
            if (c >= 0) return c;
        }
        return fallback;
    }

    private static float kvFloat(Map<String, String> kv, String key, List<String> positional,
                                 int position, float fallback) {
        String kvValue = kv.get(key);
        if (kvValue != null) {
            float f = parseFloat(kvValue, Float.NaN);
            if (!Float.isNaN(f)) return f;
        }
        if (position >= 0 && position < positional.size()) {
            float f = parseFloat(positional.get(position), Float.NaN);
            if (!Float.isNaN(f)) return f;
        }
        return fallback;
    }

    /** 颜色支持 {@code #RRGGBB} / {@code #RGB} / 纯十六进制。 */
    public static int parseColor(String token, int fallback) {
        if (token == null) return fallback;
        String s = token.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) return fallback;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String token, float fallback) {
        try {
            return Float.parseFloat(token);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static float clampSpeed(float value) {
        return clamp(value, 0.05F, 8F);
    }
}
