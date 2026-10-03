package org.bytechen.hall.utils;

/**
 * 翻译键常量。
 * <p>
 * 所有翻译键统一在此定义，语言文件位于
 * {@code assets/hall/lang/}。
 */
public final class TranslateUtils {

    private TranslateUtils() {}

    // ==================== 难度名称 ====================

    /** 简单 */
    public static final String DIFFICULTY_EASY = "hall.difficulty.easy";
    /** 普通 */
    public static final String DIFFICULTY_NORMAL = "hall.difficulty.normal";
    /** 困难 */
    public static final String DIFFICULTY_HARD = "hall.difficulty.hard";
    /** 无法理解 */
    public static final String DIFFICULTY_INCOMPREHENSIBLE = "hall.difficulty.incomprehensible";

    /** 简单 - 悬浮提示 */
    public static final String DIFFICULTY_EASY_TOOLTIP = "hall.difficulty.easy.tooltip";
    /** 普通 - 悬浮提示 */
    public static final String DIFFICULTY_NORMAL_TOOLTIP = "hall.difficulty.normal.tooltip";
    /** 困难 - 悬浮提示 */
    public static final String DIFFICULTY_HARD_TOOLTIP = "hall.difficulty.hard.tooltip";
    /** 无法理解 - 悬浮提示 */
    public static final String DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP = "hall.difficulty.incomprehensible.tooltip";

    // ==================== 难度选择 GUI ====================

    /** 难度选择界面标题 */
    public static final String GUI_DIFFICULTY_SELECT_TITLE = "hall.gui.difficulty_select.title";
    /** 难度选择界面提示 */
    public static final String GUI_DIFFICULTY_SELECT_HINT = "hall.gui.difficulty_select.hint";

    // ==================== 绯红誓约 tooltip ====================
    //
    // 分两处消费：文本行在 CrimsonVow#appendHoverText，
    // 誓约条上的标题/数值在 client.tooltip.ClientCrimsonVowTooltip。
    // 带 %s 的那几条都只有一个参数（标签组件本身），百分比那条用 %% 转义。

    /** 绯红誓约 · 誓词（斜体深灰那一行） */
    public static final String CRIMSON_VOW_TOOLTIP_LORE = "item.hall.crimson_vow.tooltip.lore";
    /** 绯红誓约 · 格挡行（%s = 标签） */
    public static final String CRIMSON_VOW_TOOLTIP_BLOCK = "item.hall.crimson_vow.tooltip.block";
    /** 绯红誓约 · 誓约行（%s = 标签） */
    public static final String CRIMSON_VOW_TOOLTIP_VOW = "item.hall.crimson_vow.tooltip.vow";
    /** 绯红誓约 · 「格挡」标签（粉紫流动） */
    public static final String CRIMSON_VOW_TOOLTIP_LABEL_BLOCK = "item.hall.crimson_vow.tooltip.label.block";
    /** 绯红誓约 · 「誓约」标签（粉紫流动） */
    public static final String CRIMSON_VOW_TOOLTIP_LABEL_VOW = "item.hall.crimson_vow.tooltip.label.vow";
    /** 绯红誓约 · 誓约条标题（自绘面板左上角） */
    public static final String CRIMSON_VOW_TOOLTIP_BAR_LABEL = "item.hall.crimson_vow.tooltip.bar.label";
    /** 绯红誓约 · 誓约条数值（%s = 减伤百分比整数） */
    public static final String CRIMSON_VOW_TOOLTIP_BAR_VALUE = "item.hall.crimson_vow.tooltip.bar.value";
    /** 绯红誓约 · F3+H 高级 tooltip 的调试行（%s ×4） */
    public static final String CRIMSON_VOW_TOOLTIP_DEBUG = "item.hall.crimson_vow.tooltip.debug";
}
