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
 * § 文本特效预览屏 —— {@code /rrsfx screen} 打开：把 8 种效果并排画在同一屏上、每帧重绘，
 * 用"哪几行在动"定位链路断在哪一环（全不动 = 钩子没接管；只有颜色类动 = 位移层没通）。
 * 自绘而非原版控件：每行要画的是带标记的字符串本身，且需要固定两列、悬停高亮与逐帧重绘。
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectPreviewScreen extends Screen {

    private static final int TITLE_Y = 10;

    private static final int SETTINGS_Y = 21;

    private static final int PALETTE_Y = 31;

    private static final int ROWS_TOP = 45;

    /** 行高：9px 字高 + 4px 行距；8 行共 104px。 */
    private static final int ROW_HEIGHT = 13;

    private static final int LABEL_WIDTH = 108;

    private static final int HINT_BOTTOM_GAP = 30;

    private static final int STATUS_BOTTOM_GAP = 18;

    private static final int PANEL_MARGIN = 8;

    /** 内容区最大宽度：再宽只是留白变多，样本本身不需要那么长。 */
    private static final int CONTENT_MAX_WIDTH = 360;

    /** 面板比样本区每边多出的像素：位移效果可能让字形超出 1~2px，留出来才不会贴到边框。 */
    private static final int PANEL_PADDING = 6;

    /** 刻意选长句：渐变要够长才看得出过渡，抖动/波浪要够多字才看得出相邻字的相位差。 */
    private static final String SAMPLE = "腐朽的低语仍在蔓延，它们记得你的名字";

    /** 直接取 TextEffectIds 里的默认调色板，不另立一套 —— 否则换主题色时演示屏会和真实效果不一致。 */
    private static final int GRAD_FROM = TextEffectIds.DEFAULT_GRAD_FROM;
    private static final int GRAD_TO = TextEffectIds.DEFAULT_GRAD_TO;
    private static final int PALE = TextEffectIds.DEFAULT_PALE;

    /** 一行样本：label 是自己拼的 [rrs:xxx] 外观串，sample 是 TextEffectText 生成的真实标记文本。 */
    private record Row(String label, String sample) {}

    /** 8 行样本：内容不随运行时配置变化，开屏时构建一次即可。 */
    private final List<Row> rows;

    public TextEffectPreviewScreen() {
        // 标题的 lang 值自带 §[rrs:grad:…] 标记，本身就是第 0 号样本 —— 标题不动就说明钩子没生效。
        super(Component.translatable("rotten_ruins.gui.textfx.title"));
        this.rows = buildRows();
    }

    /** 最后一行演示效果叠加：颜色类与位移类 span 在解析计划里是两条独立通道，shake 包 gradient 可同时生效。 */
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

    /** 不能直接画 §[rrs:xxx] 原文：那是真的效果标记，引擎会把整段吃成空 span，标签消失或颜色漏给后面的字。 */
    private static String label(String effectId) {
        return "§8[§7rrs§8:§f" + effectId + "§8]";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // isPauseScreen()=false，背景世界还在跑；不先铺原版暗色遮罩，深色样本压在世界画面上会看不清。
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int leftX = contentLeft();
        int contentW = contentWidth();

        int panelTop = ROWS_TOP - PANEL_PADDING;
        int panelBottom = ROWS_TOP + this.rows.size() * ROW_HEIGHT + 4;
        guiGraphics.fill(leftX - PANEL_PADDING, panelTop,
                leftX + contentW + PANEL_PADDING, panelBottom, 0x66000000);
        guiGraphics.renderOutline(leftX - PANEL_PADDING, panelTop,
                contentW + PANEL_PADDING * 2, panelBottom - panelTop, 0x33FFFFFF);

        guiGraphics.drawCenteredString(this.font, this.title, centerX, TITLE_Y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal(engineLine()), centerX, SETTINGS_Y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal(paletteLine()), centerX, PALETTE_Y, 0xFFFFFF);

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

            // 左列是说明文字，不希望被动画干扰：关阴影（阴影会让"字在动"更难判断）。
            guiGraphics.drawString(this.font, Component.literal(row.label()), labelX, rowY, 0xFFFFFF, false);

            // 右列必须走 Component/Font 绘制路径才会进解析层；阴影保持开启，亮色字在深色底板上更清晰。
            guiGraphics.drawString(this.font, Component.literal(row.sample()), sampleX, rowY, 0xFFFFFF, true);
        }

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("rotten_ruins.gui.textfx.hint"),
                centerX, this.height - HINT_BOTTOM_GAP, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, Component.literal(runtimeLine()),
                centerX, this.height - STATUS_BOTTOM_GAP, 0xFFFFFF);
    }

    /** 引擎状态行（总开关 / 钩子旁路 / 时间源 / 每帧动画字形预算）；每帧重建而不缓存，/rrsfx on|off 才能即时反映。 */
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

    private static String onOff(boolean value) {
        return value ? "§aON" : "§cOFF";
    }

    /** 一位小数：强度/速度是 0.05~8 的倍率，1 位足够且不会把这一行撑到换行。 */
    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 内容区宽度：屏幕越宽给到 CONTENT_MAX_WIDTH 为止，再宽只增加左右留白。 */
    private int contentWidth() {
        return Math.min(this.width - (PANEL_MARGIN + PANEL_PADDING) * 2, CONTENT_MAX_WIDTH);
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    /** 鼠标所在的样本行；不在样本区返回 -1（顺带验证"画出来的坐标"和"看到的坐标"一致）。 */
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

    /**
     * 不暂停背景世界：动画走墙钟，暂停与否不影响相位；还能边开着预览屏边看背景世界里已跑的文字是否同样在动，
     * 一条线索同时验证"本屏自绘"和"全局接管"两条路径。
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
