package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraft.util.FormattedCharSink;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * "这个 sink 能接收逐字位移"的<b>鸭子接口</b>。
 *
 * <p>{@code Font$StringRenderOutput} 由 mixin 实现它（字段挂在 sink 实例上），解析层在调用
 * {@code sink.accept(...)} 前后设置/清除 pending 状态。相比全局 ThreadLocal：状态与"这一次
 * 调用"天然配对，不会在别的 mod 再包一层 sink、或嵌套绘制时串味。</p>
 *
 * <p>唯一覆盖不到的是 {@code drawInBatch8xOutline} 的 lambda 包装（sink 不是
 * StringRenderOutput）——那条路径由 {@code TextEffectDecomposer}/{@code TextEffectVisualOrder}
 * 里的 scoped ThreadLocal 兜底，见 mixin 的注释。</p>
 */
@OnlyIn(Dist.CLIENT)
public interface GlyphEffectSink {

    /**
     * ThreadLocal 兜底：{@code drawInBatch8xOutline} 用 lambda 包了一层 sink，真实 sink 拿不到；
     * 解析层在"发现 sink 不是本接口"时把状态放这里，真正的 StringRenderOutput 在 accept 里读。
     * 严格 set/remove 配对（try/finally），不做常驻状态。
     */
    ThreadLocal<GlyphEffectState> FALLBACK = new ThreadLocal<>();

    void rrs$setPending(GlyphEffectState state);

    void rrs$clearPending();

    /** 如果 sink 支持（是 StringRenderOutput），设置 pending 状态；否则进 ThreadLocal 兜底。 */
    static void setIfPossible(FormattedCharSink sink, GlyphEffectState state) {
        if (sink instanceof GlyphEffectSink glyphSink) {
            glyphSink.rrs$setPending(state);
        } else {
            FALLBACK.set(state);
        }
    }

    static void clearIfPossible(FormattedCharSink sink) {
        if (sink instanceof GlyphEffectSink glyphSink) {
            glyphSink.rrs$clearPending();
        } else {
            FALLBACK.remove();
        }
    }

    /** T2 mixin 用：实例状态优先，其次 ThreadLocal 兜底。 */
    static GlyphEffectState currentPending() {
        GlyphEffectState fallback = FALLBACK.get();
        return fallback;
    }
}
