package org.bytechen.rottenruins.client.rend.text.effect;

/**
 * 颜色类效果的逐字取色（纯 Java）。
 *
 * <h3>为什么渐变在"线性光"里插值</h3>
 * <p>直接在 sRGB 上线性插值，绿→红的中段会发灰发脏；先转到线性光、插值、再转回 sRGB，
 * 中段会走暗橄榄/黄褐 —— 这正是"腐化绿 → 王庭血红"想要的过渡。转换是幂等的数值函数，
 * 没有状态，因此可以在每次绘制时重算（动画色相也走这里）。</p>
 *
 * <h3>ordinal 是"可视字符序号"</h3>
 * <p>与 {@link TextEffectParser} 的计数口径一致；span 内的相对位置用它减去
 * {@link EffectSpan#visibleStart} 得到，长度用 span 的区间。这样同一套公式在 T1（测量/绘制）
 * 与 L1（逐帧 overlay）里结果完全一致。</p>
 */
public final class EffectColor {

    private EffectColor() {}

    /**
     * 取某个可视字符的颜色。
     *
     * @return {@code 0xRRGGBB}；不是颜色类效果返回 -1（调用方保持原 Style）
     */
    public static int colorFor(EffectSpan span, int ordinal, float time, float speedMultiplier) {
        if (span == null) return -1;
        EffectSpec spec = span.spec;
        int local = ordinal - span.visibleStart;
        int length = Math.max(1, span.visibleEnd - span.visibleStart);

        switch (spec.kind) {
            case GRAD: {
                float t = length <= 1 ? 0F : clamp01(local / (float) (length - 1));
                if (spec.speed > 0F) {
                    // 流动模式：相位随时间推进，用"首色补到尾部"的循环取色，
                    // 这样颜色从文字末端流回开头时没有硬接缝（2 色时就是 A→B→A 的往复）。
                    // speed=0.5 时约 2 秒走完一圈，肉眼能明确看出"在流"。
                    float phase = t + time * spec.speed * speedMultiplier;
                    phase -= (float) Math.floor(phase);
                    return cyclicMultiStop(spec.colors, phase);
                }
                return multiStop(spec.colors, t);
            }
            case SOLID:
                return spec.colors[0];
            case RAINBOW: {
                float hue = (local * 0.055F + time * 0.12F * spec.speed * speedMultiplier) % 1F;
                if (hue < 0F) hue += 1F;
                return hsvToRgb(hue, spec.sat, spec.val);
            }
            case PULSE: {
                double phase = time * 0.8D * spec.speed * speedMultiplier + local * 0.03D;
                float k = 1F - spec.amp * (0.5F + 0.5F * (float) Math.sin(2D * Math.PI * phase));
                return scale(spec.colors[0], k);
            }
            default:
                return -1;
        }
    }

    // ── 颜色运算 ─────────────────────────────────────────────────

    private static int multiStop(int[] colors, float t) {
        if (colors.length == 0) return 0xFFFFFF;
        if (colors.length == 1) return colors[0];
        int last = colors.length - 1;
        float position = t * last;
        int index = (int) Math.floor(position);
        if (index >= last) return colors[last];
        if (index < 0) index = 0;
        return lerpLinear(colors[index], colors[index + 1], position - index);
    }

    /** 循环取色：把首色接在末尾，t 在 [0,1) 上完整走一圈，用于"流动渐变"避免接缝。 */
    private static int cyclicMultiStop(int[] colors, float t) {
        int count = colors.length;
        if (count <= 1) return colors.length == 0 ? 0xFFFFFF : colors[0];
        float position = clamp01(t) * count;
        int index = (int) Math.floor(position);
        if (index >= count) index = count - 1;
        float frac = position - index;
        return lerpLinear(colors[index], colors[(index + 1) % count], frac);
    }

    /** 线性光空间的两色插值。 */
    public static int lerpLinear(int from, int to, float t) {
        float r = lerp(linear(from >> 16 & 0xFF), linear(to >> 16 & 0xFF), t);
        float g = lerp(linear(from >> 8 & 0xFF), linear(to >> 8 & 0xFF), t);
        float b = lerp(linear(from & 0xFF), linear(to & 0xFF), t);
        return (toSrgb(r) << 16) | (toSrgb(g) << 8) | toSrgb(b);
    }

    private static float linear(int channel) {
        float c = channel / 255F;
        return c <= 0.04045F ? c / 12.92F : (float) Math.pow((c + 0.055F) / 1.055F, 2.4F);
    }

    private static int toSrgb(float linear) {
        float c = clamp01(linear);
        float s = c <= 0.0031308F ? 12.92F * c
                : 1.055F * (float) Math.pow(c, 1F / 2.4F) - 0.055F;
        return Math.max(0, Math.min(255, Math.round(s * 255F)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int scale(int rgb, float k) {
        int r = Math.max(0, Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k)));
        int g = Math.max(0, Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k)));
        int b = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * k)));
        return (r << 16) | (g << 8) | b;
    }

    /** HSV → RGB（0..1 输入，0xRRGGBB 输出）。 */
    public static int hsvToRgb(float h, float s, float v) {
        float sector = (h - (float) Math.floor(h)) * 6F;
        int i = (int) Math.floor(sector);
        float f = sector - i;
        float p = v * (1F - s);
        float q = v * (1F - s * f);
        float t = v * (1F - s * (1F - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(clamp01(r) * 255F) << 16)
                | (Math.round(clamp01(g) * 255F) << 8)
                | Math.round(clamp01(b) * 255F);
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) return 0F;
        return Math.max(0F, Math.min(1F, v));
    }
}
