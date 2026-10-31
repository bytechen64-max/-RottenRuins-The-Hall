package org.bytechen.rottenruins.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.text.TextEffectIds;
import org.bytechen.rottenruins.api.text.TextEffectText;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectSettings;

import java.util.List;
import java.util.Locale;

/**
 * § 文本特效预览屏 —— {@code /rrsfx screen} 打开。
 *
 * <h3>它解决什么</h3>
 * <p>文本特效整条链路是"看不见的"：标记字符串（{@link TextEffectText}）→ 解析成计划
 * （{@code TextEffectParser}/{@code EffectPlan}）→ 在 {@code StringDecomposer} / overlay /
 * 逐字渲染层接管上色与位移。任何一环没接上，表现都只是"字没动"，日志里往往什么都没有。
 * 这里把 8 种效果<b>并排画在同一个屏幕上、每帧重绘</b>，于是：
 * <ul>
 *   <li>8 行全不动 → 钩子没接管（查 {@code textEffectsFontHooksEnabled} / {@code /rrsfx stats}）；</li>
 *   <li>只有颜色类几行动、位移类不动 → 颜色层（T1/L1）通了、位移层（T2）没通；</li>
 *   <li>全都在动 → 链路通，剩下的只需进 tooltip / 聊天核对覆盖范围。</li>
 * </ul>
 *
 * <h3>为什么自绘而不是原版控件</h3>
 * <p>每行要显示的是"带标记的字符串本身"（而不是按钮/文本框），并且需要固定的左右两列、
 * 悬停高亮与逐帧重绘；仿 {@link DifficultySelectScreen} 一次性自绘最简单 —— 原版控件会把
 * 文本包进自己的渲染逻辑，反而要多绕一层。</p>
 *
 * <h3>布局（240 高的最小 GUI 分辨率也放得下）</h3>
 * <pre>
 *   y=10   标题（lang 值里自带渐变标记，本身就是第 0 号样本）
 *   y=21   engine / hooks / clock / glyphs
 *   y=31   调色板（顺带演示 TextEffectText.hex）
 *   y=45   8 行样本，行高 13（左列效果名，右列带标记的样本文本）
 *   底部    hint（lang）+ runtime 状态
 * </pre>
 * <p>不做滚动：8 行固定内容一屏放得下，滚动反而会与世界里的滚轮/输入抢事件。
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectPreviewScreen extends Screen {

    // ══════════════════════════════════════════════════════════════
    // 布局常量
    // ══════════════════════════════════════════════════════════════

    /** 标题基线。 */
    private static final int TITLE_Y = 10;

    /** 引擎开关 / 时钟 / 预算行（标题下方）。 */
    private static final int SETTINGS_Y = 21;

    /** 调色板行。 */
    private static final int PALETTE_Y = 31;

    /** 第一行样本的 y。 */
    private static final int ROWS_TOP = 45;

    /** 行高：9px 字高 + 4px 行距；8 行共 104px。 */
    private static final int ROW_HEIGHT = 13;

    /** 左列（效果名）到右列（样本）的水平距离。 */
    private static final int LABEL_WIDTH = 108;

    /** 底部提示离屏幕底边的距离。 */
    private static final int HINT_BOTTOM_GAP = 30;

    /** 运行时状态行离屏幕底边的距离。 */
    private static final int STATUS_BOTTOM_GAP = 18;

    /** 内容区左右留白（面板还要在这基础上再外扩 6px）。 */
    private static final int PANEL_MARGIN = 8;

    /** 内容区最大宽度：再宽只是留白变多，样本本身不需要那么长。 */
    private static final int CONTENT_MAX_WIDTH = 360;

    /** 面板比样本区每边多出的像素。位移效果可能让字形超出 1~2px，留出来才不会贴到边框。 */
    private static final int PANEL_PADDING = 6;

    // ══════════════════════════════════════════════════════════════
    // 样本
    // ══════════════════════════════════════════════════════════════

    /**
     * 各效果共用的长样本（18 个字）。
     * <p>为什么刻意选长句：渐变要够长才看得出"从腐化绿到王庭血红"的过渡；抖动/波浪要有
     * 足够多的字才能看出相邻字的相位差（只给两个字，抖起来更像"整段平移"）。
     * 18 字 × 9px ≈ 162px，加上左列 108px 后仍在 320 宽的最小 GUI 分辨率内。
     */
    private static final String SAMPLE = "腐朽的低语仍在蔓延，它们记得你的名字";

    /**
     * 渐变的起止色与纯色/呼吸的基色。
     * <p>直接取 {@link TextEffectIds} 里的默认调色板，不在这里另立一套 —— 否则以后换主题色时
     * 演示屏会和真实效果不一致，演示屏反而变成"错误样例"。
     */
    private static final int GRAD_FROM = TextEffectIds.DEFAULT_GRAD_FROM;
    private static final int GRAD_TO = TextEffectIds.DEFAULT_GRAD_TO;
    private static final int PALE = TextEffectIds.DEFAULT_PALE;

    /**
     * 一行样本：左列标签 + 右列带标记的原文。
     * <p>{@code label} 是自己拼的 {@code [rrs:xxx]} 外观串（见 {@link #label(String)}），
     * {@code sample} 是 {@link TextEffectText} 生成的真实标记文本。
     */
    private record Row(String label, String sample) {}

    // ══════════════════════════════════════════════════════════════
    // 状态
    // ══════════════════════════════════════════════════════════════

    /** 8 行样本：内容不随运行时配置变化，开屏时构建一次即可（不用每帧拼字符串）。 */
    private final List<Row> rows;

    // ══════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════

    public TextEffectPreviewScreen() {
        // lang 键由 datagen（LangDataCN / LangDataEN）生成；值里自带 §[rrs:grad:…] 标记，
        // 所以标题本身就是这一屏的第一个效果样本 —— 标题不动就说明钩子没生效。
        super(Component.translatable("rotten_ruins.gui.textfx.title"));
        this.rows = buildRows();
    }

    /**
     * 构建 8 行样本。
     * <p>最后一行演示"两种效果叠在同一段字上"：颜色类 span 与位移类 span 在解析计划里是
     * <b>两条独立通道</b>（各自取最内层），所以把 {@code shake} 包在 {@code gradient} 外面时，
     * 同一段字会同时拿到渐变颜色与抖动位移。这里只用公开构建器嵌套拼接，
     * 不依赖 {@code open()/close()} 的内部参数格式。
     */
    private static List<Row> buildRows() {
        String combined = TextEffectText.shake(
                TextEffectText.gradient(SAMPLE, GRAD_FROM, GRAD_TO), 1.0F, 2.0F);

        return List.of(
                new Row(label(TextEffectIds.GRAD), TextEffectText.gradient(SAMPLE, GRAD_FROM, GRAD_TO)),
                new Row(label(TextEffectIds.SOLID), TextEffectText.solid(SAMPLE, PALE)),
                new Row(label(TextEffectIds.RAINBOW), TextEffectText.rainbow(SAMPLE)),
                new Row(label(TextEffectIds.PULSE), TextEffectText.pulse(SAMPLE, PALE, 0.35F, 1.0F)),
                new Row(label(TextEffectIds.SHAKE), TextEffectText.shake(SAMPLE, 1.0F, 2.0F)),
                new Row(label(TextEffectIds.WAVE), TextEffectText.wave(SAMPLE, 1.2F, 12.0F, 1.0F)),
                new Row(label(TextEffectIds.GLITCH), TextEffectText.glitch(SAMPLE, 2.5F, 12.0F, 0.10F)),
                new Row(label("grad+shake"), combined));
    }

    /**
     * 左列标签的着色外观，例如 {@code §8[§7rrs§8:§fgrad§8]} → 「[rrs:grad]」。
     *
     * <h3>为什么不直接画 {@code §[rrs:grad]} 这串原文</h3>
     * <p>那本身就是一个<b>真的</b>效果标记：引擎会把整段吃成"一个没有内容的效果 span"，
     * 于是标签要么消失、要么把颜色漏给后面的字 —— 反而看不到"标签"。所以把 § 拆成原版的
     * §8/§7/§f 色码、用方括号拼出同款观感，既安全又能和真实语法对得上。
     */
    private static String label(String effectId) {
        return "§8[§7rrs§8:§f" + effectId + "§8]";
    }

    // ══════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 本屏 isPauseScreen()=false，背景世界还在跑；不先铺一层原版暗色遮罩的话，
        // 样本会直接压在世界画面上，深色字根本看不清（renderBackground 在有世界时就是渐变遮罩）。
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int leftX = contentLeft();
        int contentW = contentWidth();

        // ── 样本区底板 ──
        int panelTop = ROWS_TOP - PANEL_PADDING;
        int panelBottom = ROWS_TOP + this.rows.size() * ROW_HEIGHT + 4;
        guiGraphics.fill(leftX - PANEL_PADDING, panelTop,
                leftX + contentW + PANEL_PADDING, panelBottom, 0x66000000);
        guiGraphics.renderOutline(leftX - PANEL_PADDING, panelTop,
                contentW + PANEL_PADDING * 2, panelBottom - panelTop, 0x33FFFFFF);

        // ── 顶部三行 ──
        guiGraphics.drawCenteredString(this.font, this.title, centerX, TITLE_Y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal(engineLine()), centerX, SETTINGS_Y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal(paletteLine()), centerX, PALETTE_Y, 0xFFFFFF);

        // ── 8 行样本 ──
        int labelX = leftX + 2;
        int sampleX = leftX + LABEL_WIDTH;
        int hoveredRow = rowAt(mouseX, mouseY);

        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            int rowY = ROWS_TOP + i * ROW_HEIGHT;

            if (i == hoveredRow) {
                guiGraphics.fill(leftX - PANEL_PADDING, rowY - 1,
                        leftX + contentW + PANEL_PADDING, rowY + ROW_HEIGHT - 3, 0x22FFFFFF);
            }

            // 左列是我们的说明文字，不希望被动画干扰：关阴影（阴影会让"字在动"更难判断）。
            guiGraphics.drawString(this.font, Component.literal(row.label()), labelX, rowY, 0xFFFFFF, false);

            // 右列是带标记的样本文本，必须走 Component/String 的 Font 绘制路径才会进解析层。
            // 阴影保持开启：亮色字在深色底板上更容易看清（颜色类效果仍由 Style 决定）。
            guiGraphics.drawString(this.font, Component.literal(row.sample()), sampleX, rowY, 0xFFFFFF, true);
        }

        // ── 底部：提示 + 运行时状态 ──
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("rotten_ruins.gui.textfx.hint"),
                centerX, this.height - HINT_BOTTOM_GAP, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, Component.literal(runtimeLine()),
                centerX, this.height - STATUS_BOTTOM_GAP, 0xFFFFFF);
    }

    // ══════════════════════════════════════════════════════════════
    // 状态行
    // ══════════════════════════════════════════════════════════════

    /**
     * 引擎状态行：总开关 / 钩子旁路 / 时间源 / 每帧动画字形预算。
     * <p>每帧重建而不缓存：{@code /rrsfx on|off} 可以在开屏状态下改开关，缓存会让这一行
     * 一直显示开屏那一刻的旧值。三行短字符串的分配可以忽略（这是调试屏，不是 HUD 热路径）。
     */
    private static String engineLine() {
        return "§8engine " + onOff(TextEffectSettings.enabled())
                + "§8 · hooks " + onOff(TextEffectSettings.hooksEnabled())
                + "§8 · clock " + (TextEffectSettings.useGameTime() ? "§7GAME" : "§7WALL")
                + "§8 · glyphs §f" + TextEffectSettings.maxAnimatedGlyphs();
    }

    /** 调色板行：顺便演示 {@link TextEffectText#hex(int)} 的输出格式（{@code #RRGGBB}）。 */
    private static String paletteLine() {
        return "§8palette " + TextEffectText.hex(GRAD_FROM) + "§8→" + TextEffectText.hex(GRAD_TO)
                + "§8 · pale " + TextEffectText.hex(PALE);
    }

    /** 底部运行时状态：ON/OFF + 全局强度/速度倍率（与 {@code /rrsfx off} 对照时看这一行）。 */
    private static String runtimeLine() {
        return "§8runtime " + onOff(TextEffectSettings.runtimeEnabled())
                + "§8 · intensity §f" + oneDecimal(TextEffectSettings.intensity())
                + "§8 · speed §f" + oneDecimal(TextEffectSettings.speed());
    }

    /** 开关的统一配色：绿 = 开，红 = 关（与命令反馈里的 §a/§c 一致）。 */
    private static String onOff(boolean value) {
        return value ? "§aON" : "§cOFF";
    }

    /** 一位小数：强度/速度是 0.05~8 的倍率，1 位足够且不会把这一行撑到换行。 */
    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ══════════════════════════════════════════════════════════════
    // 布局计算
    // ══════════════════════════════════════════════════════════════

    /** 内容区宽度：屏幕越宽给到 {@link #CONTENT_MAX_WIDTH} 为止，再宽只增加左右留白。 */
    private int contentWidth() {
        return Math.min(this.width - (PANEL_MARGIN + PANEL_PADDING) * 2, CONTENT_MAX_WIDTH);
    }

    /** 内容区左边（整体居中）。 */
    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    /** 鼠标所在的样本行；不在样本区返回 -1（用于行高亮，顺带验证"画出来的坐标"和"看到的坐标"一致）。 */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseY < ROWS_TOP) return -1;
        int index = (int) ((mouseY - ROWS_TOP) / ROW_HEIGHT);
        if (index < 0 || index >= this.rows.size()) return -1;
        if (mouseX < contentLeft() - PANEL_PADDING
                || mouseX > contentLeft() + contentWidth() + PANEL_PADDING) {
            return -1;
        }
        return index;
    }

    // ══════════════════════════════════════════════════════════════
    // 界面属性
    // ══════════════════════════════════════════════════════════════

    /**
     * 不暂停背景世界。
     * <p>动画本身走墙钟（见 {@code TextEffectClock}），暂停与否不影响相位；不暂停还有额外好处：
     * 可以边开着预览屏边看背景世界里已经在跑的文字（聊天栏、血条名）是否同样在动，
     * 一条线索同时验证"本屏自绘"和"全局接管"两条路径。
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
