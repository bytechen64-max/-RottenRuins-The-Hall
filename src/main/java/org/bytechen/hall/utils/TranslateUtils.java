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
}
