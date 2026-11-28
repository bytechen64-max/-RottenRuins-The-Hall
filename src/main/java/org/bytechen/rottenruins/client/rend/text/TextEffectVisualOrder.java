package org.bytechen.rottenruins.client.rend.text;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.client.rend.text.effect.EffectColor;
import org.bytechen.rottenruins.client.rend.text.effect.EffectMotion;
import org.bytechen.rottenruins.client.rend.text.effect.EffectPlan;
import org.bytechen.rottenruins.client.rend.text.effect.EffectSpan;
import org.bytechen.rottenruins.client.rend.text.effect.GlyphEffectSink;
import org.bytechen.rottenruins.client.rend.text.effect.GlyphEffectState;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectClock;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectDetector;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectParser;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectPlanCache;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectSettings;

import java.util.Optional;

/**
 * L1 钩子的实际逻辑：把 {@code ClientLanguage.getVisualOrder} 返回的原版
 * {@link FormattedCharSequence} <b>包一层</b>，让颜色/位移在 {@code accept}（真正的绘制时刻）
 * 重新求值 —— 这是"聊天行/告示牌等被缓存的 FCS 也能逐帧动画"的关键。
 *
 * <h3>为什么可以安全地包 wrapper（而不必 cancel 重造）</h3>
 * <ul>
 *   <li>字符顺序、bidi、Arabic shaping 仍由原版 FCS 决定，我们只<b>替换每个字的 Style</b>
 *       并设置 pending 位移；</li>
 *   <li>原版 FCS 在构建时（{@code SubStringSource}）已经走过 T1，标记已被消费成零宽 ——
 *       所以 wrapper 看到的第 k 个可视字符，正好对应解析计划里的第 k 个可视字符；</li>
 *   <li>{@code index} 原样透传：{@code StringSplitter.WidthLimitedCharSink} 用它做裁剪/命中，
 *       而 {@code Font$StringRenderOutput.accept} 完全忽略它。</li>
 * </ul>
 *
 * <h3>为什么含 RTL 字符时放弃</h3>
 * <p>bidi 重排后"第 k 个被画出来的字"不再等于逻辑顺序的第 k 个字，逐字颜色/位移会错位。
 * 这时不接管（原版已由 T1 在构建期上静态颜色），牺牲动画换取正确性。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectVisualOrder {

    private TextEffectVisualOrder() {}

    /**
     * 尝试接管一个视觉顺序。
     *
     * @return {@code null} = 不接管（调用方保留原版 FCS）
     */
    public static FormattedCharSequence wrap(FormattedText text, FormattedCharSequence original) {
        if (text == null || original == null || original == FormattedCharSequence.EMPTY) return null;
        // 总开关关闭时不需要逐帧 overlay：T1 已在视觉顺序构建阶段把标记剥离成零宽（纯文本降级），
        // 这里返回 null 让原版 FCS 原样使用即可。
        if (!TextEffectSettings.enabled()) return null;
        if (!TextEffectDetector.hasMarkers(text)) return null;

        String full = flatten(text);
        if (!TextEffectParser.hasMarkers(full, 0)) return null;

        EffectPlan plan = TextEffectPlanCache.get(full, 0);
        if (plan.isEmpty() || plan.hasRtl || plan.visibleCount == 0) return null;

        org.bytechen.rottenruins.client.rend.text.effect.TextEffectStats.WRAPPED_TEXTS.incrementAndGet();
        return sink -> acceptWithEffects(original, plan, sink);
    }

    private static boolean acceptWithEffects(FormattedCharSequence original, EffectPlan plan,
                                             FormattedCharSink sink) {
        float time = TextEffectClock.seconds();
        float speed = TextEffectSettings.speed();
        float intensity = TextEffectSettings.intensity();

        int[] ordinal = {0};
        return original.accept((index, style, codePoint) -> {
            int current = ordinal[0]++;
            if (current >= plan.visibleCount) {
                // 对不上（理论上不该发生）：原样透传，宁可没有效果也不吞字。
                return sink.accept(index, style, codePoint);
            }

            Style out = style;
            EffectSpan colorSpan = plan.colorSpanAt(current);
            if (colorSpan != null) {
                int rgb = EffectColor.colorFor(colorSpan, current, time, speed);
                if (rgb >= 0) out = style.withColor(TextColor.fromRgb(rgb));
            }

            GlyphEffectState state = null;
            EffectSpan motionSpan = plan.motionSpanAt(current);
            if (motionSpan != null) {
                EffectMotion.Offset offset = EffectMotion.offsetFor(motionSpan, current, time, speed);
                if (offset != null) {
                    state = new GlyphEffectState(offset.dx * intensity, offset.dy * intensity);
                }
            }

            GlyphEffectSink.setIfPossible(sink, state);
            try {
                return sink.accept(index, out, codePoint);
            } finally {
                GlyphEffectSink.clearIfPossible(sink);
            }
        });
    }

    /** 把 FormattedText 的各段原文按逻辑顺序拼起来（标记跨段的情况不存在，拼接只是为了统一下标）。 */
    private static String flatten(FormattedText text) {
        StringBuilder builder = new StringBuilder();
        text.visit((style, content) -> {
            if (content != null) builder.append(content);
            return Optional.empty();
        }, Style.EMPTY);
        return builder.toString();
    }
}
