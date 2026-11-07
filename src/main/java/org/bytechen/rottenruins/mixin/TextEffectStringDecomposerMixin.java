package org.bytechen.rottenruins.mixin;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.bytechen.rottenruins.client.rend.text.TextEffectDecomposer;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectDetector;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectSettings;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>T1 钩子</b>：拦截原版 {@code §} 码解析核心，把 {@code §[rrs:...]} 分节符标记消费掉，
 * 并把逐字颜色/位移信息交给绘制层。
 *
 * <h3>为什么挂在 {@code iterateFormatted(String, int, Style, Style, Sink)} 这个重载上</h3>
 * <p>它是所有解析路径最终汇合的地方：{@code Font.renderText(String)}、
 * {@code StringSplitter.stringWidth/splitLines}（测量与折行）、
 * {@code SubStringSource}（Component 的视觉顺序构建）、{@code Language} 的默认实现
 * 最后都会进到这里。只挂这一个点，宽度/折行就天然与"标记零宽"一致，不需要另写测量逻辑。</p>
 *
 * <h3>安全边界（这个类值得被反复怀疑）</h3>
 * <ul>
 *   <li>只有真的探测到<b>带闭合括号</b>的 {@code §[rrs:} 才尝试接管；否则原版逻辑原样跑。</li>
 *   <li>接管的实现是"混合委托"（标记之间的文本 gap 交回原版），不整段复刻状态机。</li>
 *   <li>任何 {@code Throwable} 都吞掉并退回原版 —— 最坏情况是特效不生效，而不是文字消失。</li>
 * </ul>
 *
 * <p>注意：{@code StringDecomposer} 是<b>双端类</b>，但本 mixin 只登记在 mixin 配置的
 * {@code client} 段，专用服务器不会加载它（单人游戏的集成服务端线程仍可能经过这里，
 * 所以解析器不依赖任何客户端单例）。</p>
 */
@Mixin(StringDecomposer.class)
public abstract class TextEffectStringDecomposerMixin {

    @Inject(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void rrs$iterateFormatted(String text, int start, Style first, Style second,
                                              FormattedCharSink sink,
                                              CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!TextEffectSettings.hooksEnabled()) return;
            if (!TextEffectDetector.hasMarkers(text, start)) return;

            Boolean result = TextEffectDecomposer.tryIterate(text, start, first, second, sink);
            if (result != null) {
                cir.setReturnValue(result);
            }
        } catch (Throwable throwable) {
            // 解析层失败 = 这次不接管，原版继续画；只报一次，绝不把文字画坏。
            TextEffectStats.failure(throwable);
        }
    }
}
