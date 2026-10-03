package org.bytechen.hall.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.hall.overworld.registry.items.CrimsonVowTooltip;
import org.bytechen.hall.utils.TranslateUtils;

/**
 * 绯红誓约 tooltip 里那条「誓约条」的<b>绘制端</b>。
 *
 * <h3>它出现在哪一行</h3>
 * <p>Forge 在 {@code ForgeHooksClient.gatherTooltipComponents} 里做的是
 * {@code elements.add(1, Either.right(itemComponent))} —— 图像组件被插在
 * <b>index 1</b>，也就是紧跟物品名字、压在所有文本行之上。所以本模组的
 * tooltip 最终是「绯红誓约（流动彩字）→ 誓约条 → lore → 格挡/誓约说明 →
 * 原版属性块」。想改这个位置只能去动文本行的顺序，图像自己是插不了队的。</p>
 *
 * <h3>坐标系（这条最容易踩）</h3>
 * <p>{@code renderImage} 拿到的 {@code x, y} 是<b>屏幕绝对坐标</b>下的 tooltip
 * 内容区左上角（背景是画在 {@code x-3, y-3} 处的）。1.20.1 的
 * {@code GuiGraphics.renderTooltipInternal} 只对 pose 做了 {@code z += 400}，
 * 没有平移 x/y —— 所以这里的 {@code fill}/{@code drawString} 直接用传入的
 * {@code x, y} 即可，<b>不要再叠加鼠标位置或 tooltip 原点</b>。</p>
 *
 * <h3>高度必须自成一体</h3>
 * <p>{@code getHeight()} 没有 {@code Font} 参数，只能给固定值，所以整块面板的
 * 竖向节奏全部由下面的常量算出来（{@link #HEIGHT}），别在 {@code renderImage}
 * 里临时加减 —— 一旦和 {@code getHeight()} 对不上，症状是面板压到相邻文字上，
 * 而不是报错。</p>
 *
 * <h3>为什么只有这么几次绘制调用</h3>
 * <p>tooltip 是在 {@code drawManaged} 块<b>之外</b>渲染的（原版只把背景包进了
 * 批处理），因此每次 {@code fill} 都会立刻 flush 一次。所以这里刻意压到
 * 7 次 fill + 2 次 drawString，而不是用逐列画法去拼一条横向渐变 ——
 * 横向渐变要 100+ 次 flush，代价不成比例。{@code fillGradient} 的渐变方向是
 * <b>纵向</b>（{@code GuiGraphics:239} 的顶点色分配），所以誓约条用的是纵向渐变。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCrimsonVowTooltip implements ClientTooltipComponent {

    // ──────────────────────────────────────────────────────────────
    //  布局常量（HEIGHT 必须与 renderImage 里的走位严格一致）
    // ──────────────────────────────────────────────────────────────

    /** 最小宽度：即使 1.20 的中文字体量出来很窄，面板也不至于缩成一条。 */
    private static final int MIN_WIDTH = 132;

    /** 面板与上下相邻文字行之间的留白（文字行是按 10px 紧贴排布的）。 */
    private static final int MARGIN = 2;

    /** 边框内侧留白。 */
    private static final int INNER_PAD = 2;

    /** 左右内容（标题、数值、誓约条）相对面板内沿的缩进。 */
    private static final int TEXT_INSET = 4;

    /** 标题行占位高度：与原版一行文字（{@code ClientTextTooltip.getHeight() == 10}）对齐。 */
    private static final int LABEL_H = 10;

    /** 标题行与誓约条之间的间隙。 */
    private static final int LABEL_GAP = 2;

    /** 誓约条的厚度。 */
    private static final int BAR_H = 5;

    /** 标题与数值之间至少留出的空白，避免窄面板上两者贴到一起。 */
    private static final int LABEL_VALUE_GAP = 24;

    /** 整块面板的高度：外面 2 + 上边框 1 + 内边距 2 + 标题 10 + 间隙 2 + 条 5 + 内边距 2 + 下边框 1 + 外面 2。 */
    private static final int HEIGHT = MARGIN * 2 + 1 + INNER_PAD + LABEL_H + LABEL_GAP + BAR_H + INNER_PAD + 1;

    /**
     * 面板自己那圈 1px 边框的透明度。
     *
     * <p>底板（{@code ITooltipStyle}）现在已经是粉紫描边的了，这里再用实色画一圈
     * 就成了"框里套框"，两条同样亮的线互相抢眼。压到 {@code 0x78} 之后它退成一道
     * 内侧压边，视觉重心留给外面的底板边框和中间的誓约条。</p>
     */
    private static final int INNER_FRAME_ALPHA = 0x78;

    // ──────────────────────────────────────────────────────────────
    //  数据
    // ──────────────────────────────────────────────────────────────

    private final Component label;
    private final Component value;

    /** 誓约条的填充比例 = 1 - 伤害倍率（0.25 → 填 75%）。 */
    private final float fill;

    private final int accentFrom;
    private final int accentTo;
    private final int highlight;
    private final int shadow;

    /**
     * 由 Forge 的工厂在<b>每帧</b>构建 tooltip 时调用一次
     * （{@code ClientTooltipComponent.create} → 工厂 → 这里）。
     * 实例是短命的，所以这里直接把文案和百分比算好即可，不需要任何缓存。
     */
    public ClientCrimsonVowTooltip(CrimsonVowTooltip data) {
        float reduction = Mth.clamp(1.0F - data.blockMultiplier(), 0.0F, 1.0F);
        this.fill = reduction;
        this.label = Component.translatable(TranslateUtils.CRIMSON_VOW_TOOLTIP_BAR_LABEL);
        this.value = Component.translatable(TranslateUtils.CRIMSON_VOW_TOOLTIP_BAR_VALUE,
                Math.round(reduction * 100.0F));
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
        final int width = getWidth(font);
        final int frameLeft = x + 1;
        final int frameTop = y + MARGIN;
        final int frameRight = x + width - 1;
        final int frameBottom = y + HEIGHT - MARGIN;

        // 1px 边框：上边用亮粉、其余用紫，给一个"上亮下暗"的金属压边感。
        // alpha 压低（见 INNER_FRAME_ALPHA）—— 外面那圈底板边框才是主角。
        final int frameFrom = withAlpha(this.accentFrom, INNER_FRAME_ALPHA);
        final int frameTo = withAlpha(this.accentTo, INNER_FRAME_ALPHA);
        gui.fill(frameLeft, frameTop, frameRight, frameTop + 1, frameFrom);
        gui.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, frameTo);
        gui.fill(frameLeft, frameTop + 1, frameLeft + 1, frameBottom - 1, frameTo);
        gui.fill(frameRight - 1, frameTop + 1, frameRight, frameBottom - 1, frameTo);

        // 标题行：左侧写"格挡减伤"，右侧右对齐写百分比。用 drawString(..., false)
        // 关掉阴影 —— tooltip 里的原版文字本身就没有阴影，带阴影会显得比正文更亮。
        final int contentTop = frameTop + 1 + INNER_PAD;
        final int contentLeft = frameLeft + 1 + TEXT_INSET;
        final int contentRight = frameRight - 1 - TEXT_INSET;
        gui.drawString(font, this.label, contentLeft, contentTop, this.highlight, false);
        gui.drawString(font, this.value, contentRight - font.width(this.value), contentTop,
                this.accentFrom, false);

        // 誓约条本体：先铺轨道，再把减伤那一截用粉→紫的纵向渐变盖上。
        // 轨道用<b>不透明</b>的暗部色：底板现在是一块深紫红渐变，半透明轨道会被
        // 底色吃进去，"空着的那 25%"就看不出来了。
        final int barTop = contentTop + LABEL_H + LABEL_GAP;
        final int barBottom = barTop + BAR_H;
        gui.fill(contentLeft, barTop, contentRight, barBottom, this.shadow);

        final int filled = Math.round((contentRight - contentLeft) * this.fill);
        if (filled <= 0) return;

        gui.fillGradient(contentLeft, barTop, contentLeft + filled, barBottom,
                this.accentFrom, this.accentTo);
        // 当前刻度：一条比条子上下各高 1px 的亮线，让数字和条长能对得上。
        gui.fill(contentLeft + filled - 1, barTop - 1, contentLeft + filled, barBottom + 1,
                this.highlight);
    }

    /** 只换 alpha、保留 RGB —— 面板色板全是 ARGB 的 FF 前缀，直接用会把轨道画成实心。 */
    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
