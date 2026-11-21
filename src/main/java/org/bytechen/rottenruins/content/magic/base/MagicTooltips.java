package org.bytechen.rottenruins.content.magic.base;

import java.util.Locale;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.MagicTypes;
import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 魔法物品的介绍行构造器 —— 法术与饰品共用，颜色与文案只在这里定义一次。
 *
 * <h3>面板分两段</h3>
 * <ul>
 *   <li><b>常显段</b>：法力消耗（紫）→ 法术类型（金标签 + 流派色数值）→ 冷却 / 持续消耗 / 蓄力时间（青）；</li>
 *   <li><b>折叠段</b>：每个字号以 {@code --- } 开头的细节行（蓝紫），由客户端按住 Shift 才追加。</li>
 * </ul>
 * <p>简介放在客户端侧追加（见 {@code SpellTooltipSections}）：它需要读 Shift 键状态，
 * 而 {@code content} 层不允许引用客户端类。</p>
 *
 * <h3>加一行</h3>
 * <p>照着 {@link #cooldownLine} 写方法，键名统一 {@code tooltip.<modid>.magic.<项>}，参数用 {@code %s}，
 * 再去两个语言文件补译文。</p>
 */
public final class MagicTooltips {

    /** 法力消耗：紫色，整行同色。 */
    private static final ChatFormatting MANA = ChatFormatting.LIGHT_PURPLE;

    /** 类型标签：金色；数值由流派颜色决定。 */
    private static final ChatFormatting LABEL = ChatFormatting.GOLD;

    /** 持续消耗 / 蓄力时间：青色，和法力消耗区分开。 */
    private static final ChatFormatting BLUE = ChatFormatting.AQUA;

    /** 折叠段（{@code --- } 开头，只在按住 Shift 时出现）整体用灰色。 */
    private static final ChatFormatting DETAIL = ChatFormatting.GRAY;

    private MagicTooltips() {
    }

    private static String key(String suffix) {
        return "tooltip." + HallMod.MODID + ".magic." + suffix;
    }

    private static String seconds(int ticks) {
        // 一位小数：0.5 秒和 2 秒的差别看得出来，取整会把 1.5 显示成 1
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    // ── 常显段 ────────────────────────────────────────────────────

    /** 流派名的渐变：死亡流派固定深红，其余从流派色渐变到提亮 45% 的同色。 */
    private static Component schoolName(MagicType type) {
        int from = type == MagicType.DEATH ? 0x7A0A0A : type.rgb();
        int to = type == MagicType.DEATH ? 0xC62B2B : lighten(type.rgb(), 0.45F);
        String name = net.minecraft.locale.Language.getInstance()
                .getOrDefault(type.translationKey(HallMod.MODID));
        return Component.literal("§[rrs:grad:" + hex(from) + ":" + hex(to) + "]" + name + "§[/]");
    }

    /**
     * 底板用的流派色：死亡流派用深红，和流派名的渐变保持一致，其余用流派色本身。
     *
     * <p>直接取 {@code type.rgb()} 会让死亡流派的紫色名字配紫色底板，看不出"深红"这条要求。</p>
     */
    public static int accentRgb(MagicType type) {
        return type == MagicType.DEATH ? 0x9A1414 : type.rgb();
    }

    /** 往白色方向提亮：tooltip 底板的高光色与流派彩字共用。 */
    public static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    /** 往黑色方向压暗：底板底色用流派色的暗调，才不至于抢文字。 */
    public static int dim(int rgb, float factor) {
        int r = Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** 「法术类型：火焰」—— 标签金色，数值是着色器渐变彩字。 */
    @NotNull
    public static Component typeLine(@NotNull MagicType type) {
        return Component.translatable(key("type"), schoolName(type)).withStyle(LABEL);
    }

    /** 多流派：每个流派各自渐变，用灰斜杠连接。 */
    @NotNull
    public static Component typeLine(@NotNull MagicTypes types) {
        MutableComponent value = Component.empty();
        boolean first = true;
        for (MagicType type : types.values()) {
            if (!first) value.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY));
            value.append(schoolName(type));
            first = false;
        }
        return Component.translatable(key("type"), value).withStyle(LABEL);
    }

    /**
     * 「法力消耗：40」—— 紫。
     *
     * @param mana 启动耗蓝；{@code <= 0} 时显示"无消耗"
     */
    @NotNull
    public static Component manaLine(int mana) {
        if (mana <= 0) {
            return Component.translatable(key("mana.none")).withStyle(MANA);
        }
        return Component.translatable(key("mana"), Component.literal(Integer.toString(mana)).withStyle(MANA))
                .withStyle(MANA);
    }

    /** 「持续消耗：20 法力/秒」—— 青（{@code KEEP} 模式专用）。 */
    @NotNull
    public static Component keepCostLine(int perSecond) {
        return Component.translatable(key("keep_cost"),
                Component.literal(Integer.toString(perSecond)).withStyle(BLUE)).withStyle(BLUE);
    }

    /** 「蓄力时间：2.0 秒」—— 青（{@code CHARGE} 模式专用）。 */
    @NotNull
    public static Component chargeLine(int ticks) {
        return Component.translatable(key("charge"),
                Component.literal(seconds(ticks)).withStyle(BLUE)).withStyle(BLUE);
    }

    /** 「冷却：2.0 秒」—— 青，常显段。{@code <= 0} 时显示"冷却：无"。 */
    @NotNull
    public static Component cooldownLine(int ticks) {
        if (ticks <= 0) {
            return Component.translatable(key("cooldown.none")).withStyle(BLUE);
        }
        return Component.translatable(key("cooldown"), Component.literal(seconds(ticks)).withStyle(BLUE))
                .withStyle(BLUE);
    }

    // ── 折叠段 ────────────────────────────────────────────────────

    /** 给一行细节套上 {@code --- } 前缀并染成灰色。 */
    @NotNull
    public static Component detail(@NotNull Component line) {
        return Component.literal("--- ").withStyle(DETAIL).append(line.copy().withStyle(DETAIL));
    }

    /** 语言文件里的自定义细节行（{@code item.<id>.detail.<n>}）。 */
    @NotNull
    public static Component detailText(@NotNull Component text) {
        return detail(text);
    }

    /** 「按住 Shift 获取更多信息」—— 整行紫色，只有 Shift 是金色。 */
    @NotNull
    public static Component shiftHint() {
        return Component.translatable(key("shift_hint"),
                Component.translatable(key("shift_key")).withStyle(ChatFormatting.GOLD)).withStyle(MANA);
    }
}
