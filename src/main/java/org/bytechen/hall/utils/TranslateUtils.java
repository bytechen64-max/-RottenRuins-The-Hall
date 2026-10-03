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

    // ==================== tooltip 通用文案（几把格挡武器共用） ====================
    //
    // 机制说明是同一件事，文案就该只有一份：改了格挡规则只要改这里。
    // 各物品**自己的风味**（誓词、湍流、标签）仍走 item.hall.<id>.tooltip.* ——
    // "誓约""白日"这种词是身份，不该共用。
    //
    // "标签 + 正文"的行拆成两个键：两者在 Java 侧拼成一行、各自一对渐变颜色
    // （见 client.rend.text.TooltipLines —— 渐变的逐字上色会把整行拍平，
    // 合成一条 "%s …" 模板就分不出两种颜色了）。

    /** 通用 · 「格挡」标签 */
    public static final String TOOLTIP_LABEL_BLOCK = "hall.tooltip.label.block";
    /** 通用 · 格挡行正文 */
    public static final String TOOLTIP_BLOCK = "hall.tooltip.block";
    /** 通用 · 三把传说剑共有的特性正文（不可损坏 / 防火 / 合成归还） */
    public static final String TOOLTIP_TRAIT = "hall.tooltip.trait";
    /** 通用 · 减伤条标题 */
    public static final String TOOLTIP_BAR_LABEL = "hall.tooltip.bar.label";
    /** 通用 · 减伤条数值（%s = 减伤百分比整数） */
    public static final String TOOLTIP_BAR_VALUE = "hall.tooltip.bar.value";

    // ==================== 绯红誓约 tooltip（只留自己的风味与调试行） ====================

    /** 绯红誓约 · 誓词（斜体那一行，自带一对渐变） */
    public static final String CRIMSON_VOW_TOOLTIP_LORE = "item.hall.crimson_vow.tooltip.lore";
    /** 绯红誓约 · 特性行的标签（「誓约」，正文用 {@link #TOOLTIP_TRAIT}） */
    public static final String CRIMSON_VOW_TOOLTIP_LABEL_TRAIT = "item.hall.crimson_vow.tooltip.label.trait";
    /** 绯红誓约 · F3+H 高级 tooltip 的调试行（%s ×4） */
    public static final String CRIMSON_VOW_TOOLTIP_DEBUG = "item.hall.crimson_vow.tooltip.debug";

    // ==================== 寂寒白日 tooltip ====================

    /** 寂寒白日 · 誓词（斜体那一行） */
    public static final String SILENT_DAYLIGHT_TOOLTIP_LORE = "item.hall.silent_daylight.tooltip.lore";
    /** 寂寒白日 · 「湍流」标签（它独有的一行，讲剑刃上的水光） */
    public static final String SILENT_DAYLIGHT_TOOLTIP_LABEL_TIDE = "item.hall.silent_daylight.tooltip.label.tide";
    /** 寂寒白日 · 湍流行的正文 */
    public static final String SILENT_DAYLIGHT_TOOLTIP_TIDE = "item.hall.silent_daylight.tooltip.tide";
    /** 寂寒白日 · 特性行的标签（「白日」，正文用 {@link #TOOLTIP_TRAIT}） */
    public static final String SILENT_DAYLIGHT_TOOLTIP_LABEL_TRAIT = "item.hall.silent_daylight.tooltip.label.trait";
    /** 寂寒白日 · 调试行（%s ×3；它没有绯红誓约那个攻击距离加成，所以少一个参数） */
    public static final String SILENT_DAYLIGHT_TOOLTIP_DEBUG = "item.hall.silent_daylight.tooltip.debug";
}
