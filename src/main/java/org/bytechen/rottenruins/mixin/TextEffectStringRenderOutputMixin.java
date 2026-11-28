package org.bytechen.rottenruins.mixin;

import net.minecraft.network.chat.Style;
import org.bytechen.rottenruins.client.rend.text.effect.GlyphEffectSink;
import org.bytechen.rottenruins.client.rend.text.effect.GlyphEffectState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>T2 钩子</b>：逐字位移的唯一落点 —— 在 {@code Font$StringRenderOutput.accept} 的
 * HEAD 把 pending 偏移加到 {@code x/y} 上，RETURN 再减回去。
 *
 * <h3>为什么不改 {@code Font.renderChar}、不发顶点</h3>
 * <p>{@code renderChar} 是唯一发字形顶点的地方；项目历史上"接管 renderChar"曾导致整段文字
 * 消失（见 {@code FlowingNameColors} 的类注释）。这里<b>一个顶点都不自己发</b>、也不改
 * {@code renderChar} 的任何字节：只是给原版即将使用的绘制坐标加一个临时偏移，画完立刻还原。
 * 偏移不影响 {@code accept} 末尾的 advance 累加，所以宽度/折行完全不变。</p>
 *
 * <h3>为什么状态挂在 sink 实例上，而不是 ThreadLocal</h3>
 * <p>解析层在调用 {@code sink.accept(...)} 前后 set/clear；状态与"这一次调用"天然配对，
 * 不会被别的 mod 包的 sink 或嵌套绘制串味。outline 那条 lambda 包装路径拿不到本实例时，
 * 由解析层的 scoped ThreadLocal 兜底（见 {@code TextEffectDecomposer}）。</p>
 *
 * <h3>异常/提前返回的兜底</h3>
 * <p>{@code accept} 正常路径只有末尾一个 return（HEAD/RETURN 都能到）；即便别的 mod 在中间
 * cancel，{@code StringRenderOutput} 也是每次 {@code renderText} 新建的实例，残留偏移不会
 * 跨绘制调用。{@code finish()} 前再兜底还原一次，避免"背景效果条"用到被偏移的 x。</p>
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class TextEffectStringRenderOutputMixin implements GlyphEffectSink {

    @Shadow
    float x;

    @Shadow
    float y;

    @Unique
    private GlyphEffectState rrs$pending;

    @Unique
    private float rrs$savedX;

    @Unique
    private float rrs$savedY;

    @Unique
    private boolean rrs$shifted;

    @Override
    public void rrs$setPending(GlyphEffectState state) {
        this.rrs$pending = state;
    }

    @Override
    public void rrs$clearPending() {
        this.rrs$pending = null;
    }

    @Inject(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"))
    private void rrs$shiftByPending(int index, Style style, int codePoint,
                                    CallbackInfoReturnable<Boolean> cir) {
        GlyphEffectState state = this.rrs$pending;
        if (state == null) {
            // outline 的 lambda 包装路径：解析层把状态放在 ThreadLocal 兜底里。
            state = GlyphEffectSink.currentPending();
        }
        if (state == null || state.isZero()) return;

        this.rrs$savedX = this.x;
        this.rrs$savedY = this.y;
        this.rrs$shifted = true;
        this.x += state.dx;
        this.y += state.dy;
    }

    @Inject(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("RETURN"))
    private void rrs$restoreAfterAccept(int index, Style style, int codePoint,
                                        CallbackInfoReturnable<Boolean> cir) {
        rrs$restore();
        this.rrs$pending = null;
    }

    @Inject(method = "finish(IF)F", at = @At("HEAD"))
    private void rrs$restoreBeforeFinish(int backgroundColor, float startX,
                                         CallbackInfoReturnable<Float> cir) {
        rrs$restore();
        this.rrs$pending = null;
    }

    @Unique
    private void rrs$restore() {
        if (!this.rrs$shifted) return;
        this.x = this.rrs$savedX;
        this.y = this.rrs$savedY;
        this.rrs$shifted = false;
    }
}
