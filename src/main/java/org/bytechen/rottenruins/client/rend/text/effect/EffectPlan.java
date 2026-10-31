package org.bytechen.rottenruins.client.rend.text.effect;

import java.util.List;

/**
 * 一段文本的<b>不可变解析结果</b>（纯 Java）：把"原文"编译成"绘制时只需要顺序消费的数组"。
 *
 * <h3>两种消费者，一份计划</h3>
 * <ul>
 *   <li><b>T1（StringDecomposer）</b>：按 {@link Part} 顺序走 —— 文本 gap 交回原版处理，
 *       标记只是分界；逐字序号用于查 {@link #colorSpanAt(int)} / {@link #motionSpanAt(int)}。</li>
 *   <li><b>L1（ClientLanguage overlay）</b>：原版 FormattedCharSequence 会按可视顺序吐字，
 *       同样只需一个递增的 ordinal 查上面两个方法。</li>
 * </ul>
 *
 * <p>序号是"可视字符序号"：原版 § 码与我们的标记都不占号；代理对算 1 个。
 * 这一点和 {@code StringDecomposer} 的计数口径一致，是 T1/L1 能共享计划的关键。</p>
 */
public final class EffectPlan {

    /** 解析后的一段原文：要么是交给原版处理的文本 gap，要么是我们消费掉的标记。 */
    public static final class Part {
        public final boolean marker;
        /** 原文中的 [rawStart, rawEnd)。 */
        public final int rawStart;
        public final int rawEnd;
        /** 本 gap 起始处已经数过的可视字符数（文本 gap 才有意义）。 */
        public final int ordinalStart;

        private Part(boolean marker, int rawStart, int rawEnd, int ordinalStart) {
            this.marker = marker;
            this.rawStart = rawStart;
            this.rawEnd = rawEnd;
            this.ordinalStart = ordinalStart;
        }

        static Part text(int rawStart, int rawEnd, int ordinalStart) {
            return new Part(false, rawStart, rawEnd, ordinalStart);
        }

        static Part marker(int rawStart, int rawEnd) {
            return new Part(true, rawStart, rawEnd, -1);
        }
    }

    public final List<Part> parts;
    public final List<EffectSpan> spans;
    public final int visibleCount;

    /** 可视字符序号 → 最内层颜色 span 在 {@link #spans} 中的下标；-1 表示无。 */
    private final int[] colorSpanOf;
    /** 可视字符序号 → 最内层位移 span 下标；-1 表示无。 */
    private final int[] motionSpanOf;

    /**
     * 文本是否含 RTL 字符。含 RTL 时 L1 的 overlay 会放弃（bidi 重排后 ordinal 对不上），
     * 由原版路径静态渲染，避免错色/错位。
     */
    public final boolean hasRtl;

    /** 无效标记数量（未知效果名/超参），只用于诊断计数。 */
    public final int invalidMarkers;

    EffectPlan(List<Part> parts, List<EffectSpan> spans, int visibleCount,
               boolean hasRtl, int invalidMarkers) {
        this.parts = parts;
        this.spans = spans;
        this.visibleCount = visibleCount;
        this.hasRtl = hasRtl;
        this.invalidMarkers = invalidMarkers;

        this.colorSpanOf = new int[visibleCount];
        this.motionSpanOf = new int[visibleCount];
        java.util.Arrays.fill(colorSpanOf, -1);
        java.util.Arrays.fill(motionSpanOf, -1);

        // 按创建顺序填充：外层先、内层后 → 内层覆盖外层，正好是"取最内层"的语义。
        for (int i = 0; i < spans.size(); i++) {
            EffectSpan span = spans.get(i);
            int start = Math.max(0, span.visibleStart);
            int end = Math.min(visibleCount, span.visibleEnd);
            if (end <= start) continue;
            int[] target = span.isColor() ? colorSpanOf : motionSpanOf;
            for (int ordinal = start; ordinal < end; ordinal++) {
                target[ordinal] = i;
            }
        }
    }

    public EffectSpan colorSpanAt(int ordinal) {
        if (ordinal < 0 || ordinal >= colorSpanOf.length) return null;
        int index = colorSpanOf[ordinal];
        return index < 0 ? null : spans.get(index);
    }

    public EffectSpan motionSpanAt(int ordinal) {
        if (ordinal < 0 || ordinal >= motionSpanOf.length) return null;
        int index = motionSpanOf[ordinal];
        return index < 0 ? null : spans.get(index);
    }

    public boolean isEmpty() {
        return spans.isEmpty();
    }
}
