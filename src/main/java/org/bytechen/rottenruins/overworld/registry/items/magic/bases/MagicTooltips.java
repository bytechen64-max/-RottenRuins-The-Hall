package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.bytechen.rottenruins.HallMod;
import org.jetbrains.annotations.NotNull;

/**
 * 魔法物品的<b>介绍行</b>构造器 —— 法术与饰品共用。
 *
 * <p>目前只有三项：<b>冷却 / 消耗 / 类型</b>。需求里"不同类型的法术用不同的颜色"指的
 * 就是最后一项：每一项的<b>数值</b>用该流派的颜色（{@link MagicType#rgb()}），
 * 标签用深灰色 —— 只给数值上色，整行都染色反而看不清。</p>
 *
 * <p>为什么单独一个类而不是写在两个基类里：法术（{@code CMagicBaseItem}）和饰品
 * （{@code MagicCurioItem}）是两条继承线，但要显示的东西必须一致，
 * 否则同一个流派在两个物品上会显示成两种颜色。颜色和文案只在这里定义一次。</p>
 *
 * <h3>加一行新的介绍</h3>
 * <p>照着 {@link #cooldownLine} 写一个方法，键名统一 {@code tooltip.<modid>.magic.<项>}，
 * 再去两个语言文件里补译文即可。参数用 {@code %s}。</p>
 */
public final class MagicTooltips {

    /** 标签色：深灰，不抢数值的颜色。 */
    private static final ChatFormatting LABEL = ChatFormatting.DARK_GRAY;

    private MagicTooltips() {
    }

    private static String key(String suffix) {
        return "tooltip." + HallMod.MODID + ".magic." + suffix;
    }

    /**
     * 「类型：火焰」—— 数值部分是<b>该流派的颜色</b>。
     *
     * <p>译名键是 {@code magic_type.<modid>.<小写流派名>}（见 {@link MagicType#translationKey}）。</p>
     */
    @NotNull
    public static Component typeLine(@NotNull MagicType type) {
        MutableComponent value = Component.translatable(type.translationKey(HallMod.MODID))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("type"), value).withStyle(LABEL);
    }

    /**
     * 「冷却：1.5 秒」。
     *
     * @param ticks 冷却 tick；{@code <= 0} 时显示"无冷却"
     */
    @NotNull
    public static Component cooldownLine(int ticks, @NotNull MagicType type) {
        if (ticks <= 0) {
            return Component.translatable(key("cooldown.none")).withStyle(LABEL);
        }
        // 一位小数：0.5 秒和 2 秒的差别玩家看得出来，整数会把 1.5 显示成 1
        String seconds = String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0F);
        MutableComponent value = Component.literal(seconds)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("cooldown"), value).withStyle(LABEL);
    }

    /**
     * 「消耗：40 法力」。
     *
     * @param mana 启动耗蓝；{@code <= 0} 时显示"无消耗"
     */
    @NotNull
    public static Component manaLine(int mana, @NotNull MagicType type) {
        if (mana <= 0) {
            return Component.translatable(key("mana.none")).withStyle(LABEL);
        }
        MutableComponent value = Component.literal(Integer.toString(mana))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("mana"), value).withStyle(LABEL);
    }

    /**
     * 维持耗蓝那一行（{@code KEEP} 模式专用；其它模式由调用方决定不要加）。
     */
    @NotNull
    public static Component keepCostLine(int perSecond, @NotNull MagicType type) {
        MutableComponent value = Component.literal(Integer.toString(perSecond))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("keep_cost"), value).withStyle(LABEL);
    }
}
