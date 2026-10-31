package org.bytechen.rottenruins.client.rend.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.client.rend.text.effect.EffectColor;
import org.bytechen.rottenruins.client.rend.text.effect.EffectMotion;
import org.bytechen.rottenruins.client.rend.text.effect.EffectPlan;
import org.bytechen.rottenruins.client.rend.text.effect.EffectSpan;
import org.bytechen.rottenruins.client.rend.text.effect.GlyphEffectSink;
import org.bytechen.rottenruins.client.rend.text.effect.GlyphEffectState;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectClock;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectPlanCache;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectSettings;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectStats;

/**
 * T1 钩子的实际解析/绘制逻辑：把 {@code StringDecomposer.iterateFormatted} 的调用
 * "接管"成"标记由我们消费、其余 gap 交回原版"。
 *
 * <h3>混合委托：为什么不是整段复刻原版状态机</h3>
 * <p>标记之间的文本 gap 直接调用原版 {@link StringDecomposer#iterateFormatted(String, Style, FormattedCharSink)}
 * 处理 —— 原版的 § 码、代理对、孤立字符逻辑一个字都不改；我们只在 gap 外面套一个
 * sink 包装，负责三件事：转发原串绝对下标、逐字覆盖颜色、给绘制 sink 设置位移状态。
 * 这样"语义风险"被压到最低，也避免与其它文本 mod 争夺 {@code StringDecomposer} 的实现权。</p>
 *
 * <h3>为什么 index 必须加 gap 偏移</h3>
 * <p>{@code StringSplitter.splitLines} 用 accept 的 index 做 substring/裁剪（见提案 C2），
 * 所以转发时必须还原成"原串里的绝对下标"，否则 tooltip 折行会切错位置。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectDecomposer {

    private TextEffectDecomposer() {}

    /**
     * 尝试接管一次 {@code iterateFormatted}。
     *
     * @return {@code null} = 没有需要处理的内容（调用方让原版继续执行）；
     *         否则为原版方法的返回值（sink 是否消费完）
     */
    public static Boolean tryIterate(String text, int start, Style first, Style second,
                                     FormattedCharSink sink) {
        if (text == null || text.isEmpty()) return null;

        EffectPlan plan = TextEffectPlanCache.get(text, start);
        if (plan.parts.isEmpty()) {
            // 整段只有标记、没有正文字符：标记必须被消费掉（否则原版会把 body 当正文画出来），
            // 直接返回"消费完、什么都没画"。
            return Boolean.TRUE;
        }

        float time = TextEffectClock.seconds();
        float speed = TextEffectSettings.speed();
        float intensity = TextEffectSettings.intensity();
        // 总开关关闭时仍要走完这个循环：标记必须被消费成零宽（否则原版会把 body 当正文画出来），
        // 只是不再上色/位移 —— 这正是配置注释里承诺的"降级为普通文本"。
        boolean applyEffects = TextEffectSettings.enabled();

        Style style = first;
        for (EffectPlan.Part part : plan.parts) {
            if (part.marker) continue;
            if (part.rawEnd <= part.rawStart) continue;

            String gap = text.substring(part.rawStart, part.rawEnd);
            GapSink gapSink = new GapSink(sink, plan, part.rawStart, part.ordinalStart,
                    time, speed, intensity, applyEffects);
            if (!StringDecomposer.iterateFormatted(gap, 0, style, second, gapSink)) {
                return Boolean.FALSE;
            }
            // 下一个 gap 的起始样式 = 本 gap 里原版 § 码作用后的样式（含末尾的格式码）。
            style = deriveStyle(gap, style, second);
        }
        return Boolean.TRUE;
    }

    /**
     * 扫描一段文本里的原版 § 码，推导出"这段结束后"的样式。
     *
     * <p>只处理 {@code §}+一个字符（与原版同样的跳过规则）；{@code §r} 回到 {@code second}
     * —— 不能假设 first==second（{@code StringSplitter.splitLines} 直接调 core 时两者不同）。</p>
     */
    static Style deriveStyle(String gap, Style start, Style second) {
        Style style = start;
        int length = gap.length();
        for (int i = 0; i + 1 < length; i++) {
            if (gap.charAt(i) != '\u00a7') continue;
            ChatFormatting formatting = ChatFormatting.getByCode(gap.charAt(i + 1));
            if (formatting != null) {
                style = formatting == ChatFormatting.RESET ? second : style.applyLegacyFormat(formatting);
            }
            i++; // 无论码是否合法，原版都会吞掉后一个字符
        }
        return style;
    }

    /** 文本 gap 的 sink 包装：转发下标 + 逐字颜色 + 位移状态。 */
    private static final class GapSink implements FormattedCharSink {

        private final FormattedCharSink real;
        private final EffectPlan plan;
        private final int rawOffset;
        private final int ordinalBase;
        private final float time;
        private final float speed;
        private final float intensity;
        /** 总开关；false = 只做零宽剥离，不上色/不位移。 */
        private final boolean applyEffects;
        private int count;

        private GapSink(FormattedCharSink real, EffectPlan plan, int rawOffset, int ordinalBase,
                        float time, float speed, float intensity, boolean applyEffects) {
            this.real = real;
            this.plan = plan;
            this.rawOffset = rawOffset;
            this.ordinalBase = ordinalBase;
            this.time = time;
            this.speed = speed;
            this.intensity = intensity;
            this.applyEffects = applyEffects;
        }

        @Override
        public boolean accept(int index, Style style, int codePoint) {
            int ordinal = ordinalBase + count;
            count++;

            Style out = applyColor(style, ordinal);
            GlyphEffectState state = motionState(ordinal);
            GlyphEffectSink.setIfPossible(real, state);
            try {
                // ★ 下标必须还原成原串里的绝对下标（提案 C2）：splitLines/裁剪依赖它。
                return real.accept(rawOffset + index, out, codePoint);
            } finally {
                GlyphEffectSink.clearIfPossible(real);
            }
        }

        private Style applyColor(Style style, int ordinal) {
            if (!applyEffects) return style;
            EffectSpan span = plan.colorSpanAt(ordinal);
            if (span == null) return style;
            int rgb = EffectColor.colorFor(span, ordinal, time, speed);
            return rgb < 0 ? style : style.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
        }

        private GlyphEffectState motionState(int ordinal) {
            if (!applyEffects) return null;
            EffectSpan span = plan.motionSpanAt(ordinal);
            if (span == null) return null;
            EffectMotion.Offset offset = EffectMotion.offsetFor(span, ordinal, time, speed);
            if (offset == null) return null;
            return new GlyphEffectState(offset.dx * intensity, offset.dy * intensity);
        }
    }
}
