package org.bytechen.rottenruins.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.bytechen.rottenruins.client.rend.text.TextEffectVisualOrder;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectSettings;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>L1 钩子</b>：在 {@code ClientLanguage.getVisualOrder} 的返回值上包一层逐帧求值的
 * {@link FormattedCharSequence}。
 *
 * <h3>为什么必须在"返回处"包，而不是更早接管</h3>
 * <p>Component 的视觉顺序（以及它的 bidi 重排）在这里一次性构建、随后被缓存
 * （聊天行、告示牌、bossbar 都会跨帧复用）。如果只在构建时改颜色，动画会永远停在第一帧。
 * 包一层 wrapper 后，原版 FCS 仍是字符顺序/bidi 的唯一来源，而颜色与位移在每次
 * {@code accept}（真正的绘制时刻）重新计算 —— 缓存不再妨碍动画。</p>
 *
 * <h3>为什么不在 HEAD cancel、自己造 FCS</h3>
 * <p>那样就要自己处理 bidi 重排与 Arabic shaping，风险高一个数量级；wrapper 只替换
 * 每个字的 Style 并设置 pending 位移状态，index 原样透传，点击/hover/宽度都不受影响。</p>
 *
 * <p>含 RTL 字符的文本直接放弃接管（重排后逐字序号会错位），由 T1 在构建期上的静态颜色
 * 兜底 —— 牺牲动画换正确性。</p>
 */
@Mixin(ClientLanguage.class)
public abstract class TextEffectClientLanguageMixin {

    @Inject(
            method = "getVisualOrder(Lnet/minecraft/network/chat/FormattedText;)Lnet/minecraft/util/FormattedCharSequence;",
            at = @At("RETURN"),
            // 非 void 方法的 RETURN 注入若要改返回值，Mixin 要求标 cancellable：
            // 否则 setReturnValue 会抛 CancellationException（实机第一轮就踩到了）。
            cancellable = true
    )
    private void rrs$wrapVisualOrder(FormattedText text, CallbackInfoReturnable<FormattedCharSequence> cir) {
        try {
            if (!TextEffectSettings.hooksEnabled()) return;
            ClientLanguage self = (ClientLanguage) (Object) this;
            if (self.isDefaultRightToLeft()) return;

            FormattedCharSequence wrapped = TextEffectVisualOrder.wrap(text, cir.getReturnValue());
            if (wrapped != null) {
                cir.setReturnValue(wrapped);
                cir.cancel();
            }
        } catch (Throwable throwable) {
            TextEffectStats.failure(throwable);
        }
    }
}
