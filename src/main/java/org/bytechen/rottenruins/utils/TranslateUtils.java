package org.bytechen.rottenruins.utils;

/** 翻译键常量；语言文件在 {@code assets/hall/lang/}。tooltip 机制文案只留一份，标签与正文分两个键。 */
public final class TranslateUtils {

    private TranslateUtils() {}

    public static final String DIFFICULTY_EASY = "rotten_ruins.difficulty.easy";
    public static final String DIFFICULTY_NORMAL = "rotten_ruins.difficulty.normal";
    public static final String DIFFICULTY_HARD = "rotten_ruins.difficulty.hard";
    public static final String DIFFICULTY_INCOMPREHENSIBLE = "rotten_ruins.difficulty.incomprehensible";

    public static final String DIFFICULTY_EASY_TOOLTIP = "rotten_ruins.difficulty.easy.tooltip";
    public static final String DIFFICULTY_NORMAL_TOOLTIP = "rotten_ruins.difficulty.normal.tooltip";
    public static final String DIFFICULTY_HARD_TOOLTIP = "rotten_ruins.difficulty.hard.tooltip";
    public static final String DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP = "rotten_ruins.difficulty.incomprehensible.tooltip";

    public static final String GUI_DIFFICULTY_SELECT_TITLE = "rotten_ruins.gui.difficulty_select.title";
    public static final String GUI_DIFFICULTY_SELECT_HINT = "rotten_ruins.gui.difficulty_select.hint";

    public static final String TOOLTIP_LABEL_BLOCK = "rotten_ruins.tooltip.label.block";
    public static final String TOOLTIP_BLOCK = "rotten_ruins.tooltip.block";
    public static final String TOOLTIP_TRAIT = "rotten_ruins.tooltip.trait";
    public static final String TOOLTIP_BAR_LABEL = "rotten_ruins.tooltip.bar.label";
    /** %s = 减伤百分比整数。 */
    public static final String TOOLTIP_BAR_VALUE = "rotten_ruins.tooltip.bar.value";

    public static final String CRIMSON_VOW_TOOLTIP_LORE = "item.rotten_ruins.crimson_vow.tooltip.lore";
    public static final String CRIMSON_VOW_TOOLTIP_LABEL_TRAIT = "item.rotten_ruins.crimson_vow.tooltip.label.trait";
    /** F3+H 高级 tooltip 的调试行：%s ×4。 */
    public static final String CRIMSON_VOW_TOOLTIP_DEBUG = "item.rotten_ruins.crimson_vow.tooltip.debug";
    public static final String CRIMSON_VOW_TOOLTIP_LABEL_SIGIL = "item.rotten_ruins.crimson_vow.tooltip.label.sigil";
    public static final String CRIMSON_VOW_TOOLTIP_SIGIL = "item.rotten_ruins.crimson_vow.tooltip.sigil";
    public static final String CRIMSON_VOW_COOLDOWN = "item.rotten_ruins.crimson_vow.cooldown";

    public static final String SILENT_DAYLIGHT_TOOLTIP_LORE = "item.rotten_ruins.silent_daylight.tooltip.lore";
    public static final String SILENT_DAYLIGHT_TOOLTIP_LABEL_TIDE = "item.rotten_ruins.silent_daylight.tooltip.label.tide";
    public static final String SILENT_DAYLIGHT_TOOLTIP_TIDE = "item.rotten_ruins.silent_daylight.tooltip.tide";
    public static final String SILENT_DAYLIGHT_TOOLTIP_LABEL_TRAIT = "item.rotten_ruins.silent_daylight.tooltip.label.trait";
    public static final String SILENT_DAYLIGHT_TOOLTIP_LABEL_SLASH = "item.rotten_ruins.silent_daylight.tooltip.label.slash";
    /** 三连斩正文：%s ×3（次数 / 冰伤 / 百分比）。 */
    public static final String SILENT_DAYLIGHT_TOOLTIP_SLASH = "item.rotten_ruins.silent_daylight.tooltip.slash";
    public static final String SILENT_DAYLIGHT_TOOLTIP_LABEL_WARD = "item.rotten_ruins.silent_daylight.tooltip.label.ward";
    public static final String SILENT_DAYLIGHT_TOOLTIP_WARD = "item.rotten_ruins.silent_daylight.tooltip.ward";
    /** 调试行：%s ×3（本剑没有攻击距离加成，比绯红誓约少一个参数）。 */
    public static final String SILENT_DAYLIGHT_TOOLTIP_DEBUG = "item.rotten_ruins.silent_daylight.tooltip.debug";
}
