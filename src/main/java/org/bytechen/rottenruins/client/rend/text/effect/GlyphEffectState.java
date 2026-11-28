package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 一个可视字符的<b>位移参数</b>（不可变）。
 *
 * <p>只在"解析层 → 绘制层"之间传递，不参与任何排版计算：绘制层会把 dx/dy 加到
 * {@code StringRenderOutput} 的 x/y 上，画完这一个字立刻减回去。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GlyphEffectState {

    public static final GlyphEffectState NONE = new GlyphEffectState(0F, 0F);

    public final float dx;
    public final float dy;

    public GlyphEffectState(float dx, float dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public boolean isZero() {
        return dx == 0F && dy == 0F;
    }
}
