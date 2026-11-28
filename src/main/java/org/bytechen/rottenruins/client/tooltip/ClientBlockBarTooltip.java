package org.bytechen.rottenruins.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.overworld.registry.items.BlockBarTooltip;

/** 「格挡减伤条」的绘制端（绯红誓约 / 寂寒白日共用）：布局由这里定，文案与配色全随 {@link BlockBarTooltip} 数据过来。 */
@OnlyIn(Dist.CLIENT)
public final class ClientBlockBarTooltip implements ClientTooltipComponent {

    //  布局常量（HEIGHT 必须与 renderImage 里的走位严格一致）

    /** 最小宽度：即使中文字体量出来很窄，面板也不至于缩成一条。 */
    private static final int MIN_WIDTH = 132;

    private static final int MARGIN = 2;

    private static final int INNER_PAD = 2;

    private static final int TEXT_INSET = 4;

    private static final int LABEL_H = 10;

    private static final int LABEL_GAP = 2;

    private static final int BAR_H = 5;

    private static final int LABEL_VALUE_GAP = 24;

    /** 整块面板高度：外面 2 + 上边框 1 + 内边距 2 + 标题 10 + 间隙 2 + 条 5 + 内边距 2 + 下边框 1 + 外面 2。 */
    private static final int HEIGHT = MARGIN * 2 + 1 + INNER_PAD + LABEL_H + LABEL_GAP + BAR_H + INNER_PAD + 1;

    /** 自己那圈 1px 边框的透明度：底板已有彩色边框，压到 0x78 才不形成"框里套框"。 */
    private static final int INNER_FRAME_ALPHA = 0x78;

    //  数据

    private final Component label;
    private final Component value;

    /** 条子的填充比例 = 1 - 伤害倍率（0.25 → 填 75%）。 */
    private final float fill;

    private final int accentFrom;
    private final int accentTo;
    private final int highlight;
    private final int shadow;

    /** 由 Forge 工厂每帧构建 tooltip 时调用一次，实例短命，不需要缓存。 */
    public ClientBlockBarTooltip(BlockBarTooltip data) {
        float reduction = Mth.clamp(1.0F - data.blockMultiplier(), 0.0F, 1.0F);
        this.fill = reduction;
        this.label = Component.translatable(data.labelKey());
        this.value = Component.translatable(data.valueKey(), Math.round(reduction * 100.0F));
        this.accentFrom = data.accentFrom();
        this.accentTo = data.accentTo();
        this.highlight = data.highlight();
        this.shadow = data.shadow();
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int text = font.width(this.label) + LABEL_VALUE_GAP + font.width(this.value)
                + (TEXT_INSET + 1) * 2;
        return Math.max(MIN_WIDTH, text);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics gui) {
        // x,y 是屏幕绝对坐标（1.20.1 只给 pose 做了 z += 400），不要再叠加鼠标位置或 tooltip 原点。
        final int width = getWidth(font);
        final int frameLeft = x + 1;
        final int frameTop = y + MARGIN;
        final int frameRight = x + width - 1;
        final int frameBottom = y + HEIGHT - MARGIN;

        // 上边用亮端、其余用暗端；alpha 压低，视觉重心留给外面那圈底板边框。
        final int frameFrom = withAlpha(this.accentFrom, INNER_FRAME_ALPHA);
        final int frameTo = withAlpha(this.accentTo, INNER_FRAME_ALPHA);
        gui.fill(frameLeft, frameTop, frameRight, frameTop + 1, frameFrom);
        gui.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, frameTo);
        gui.fill(frameLeft, frameTop + 1, frameLeft + 1, frameBottom - 1, frameTo);
        gui.fill(frameRight - 1, frameTop + 1, frameRight, frameBottom - 1, frameTo);

        // drawString(..., false) 关阴影：tooltip 里的原版文字本身没有阴影，带阴影会比正文更亮。
        final int contentTop = frameTop + 1 + INNER_PAD;
        final int contentLeft = frameLeft + 1 + TEXT_INSET;
        final int contentRight = frameRight - 1 - TEXT_INSET;
        gui.drawString(font, this.label, contentLeft, contentTop, this.highlight, false);
        gui.drawString(font, this.value, contentRight - font.width(this.value), contentTop,
                this.accentFrom, false);

        // 轨道必须用不透明暗部色：底板是渐变，半透明轨道会被底色吃掉，"空着的部分"就看不出来了。
        final int barTop = contentTop + LABEL_H + LABEL_GAP;
        final int barBottom = barTop + BAR_H;
        gui.fill(contentLeft, barTop, contentRight, barBottom, this.shadow);

        final int filled = Math.round((contentRight - contentLeft) * this.fill);
        if (filled <= 0) return;

        gui.fillGradient(contentLeft, barTop, contentLeft + filled, barBottom,
                this.accentFrom, this.accentTo);
        gui.fill(contentLeft + filled - 1, barTop - 1, contentLeft + filled, barBottom + 1,
                this.highlight);
    }

    /** 只换 alpha、保留 RGB —— 色板是 ARGB（FF 前缀），直接用会把轨道画成实心。 */
    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
