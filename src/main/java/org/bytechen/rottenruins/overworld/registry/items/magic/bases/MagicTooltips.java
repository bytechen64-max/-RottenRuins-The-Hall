package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.bytechen.rottenruins.HallMod;
import org.jetbrains.annotations.NotNull;

/** 魔法物品的「介绍行」构造器（冷却/消耗/类型，法术与饰品共用）；只给数值上流派色、标签深灰，颜色与文案只在此定义一次。 */
public final class MagicTooltips {

    /** 标签色：深灰，不抢数值的颜色。 */
    private static final ChatFormatting LABEL = ChatFormatting.DARK_GRAY;

    private MagicTooltips() {
    }

    private static String key(String suffix) {
        return "tooltip." + HallMod.MODID + ".magic." + suffix;
    }

    /** 「类型：火焰」，数值用该流派颜色；译名键为 magic_type.<modid>.<小写流派名>（见 {@link MagicType#translationKey}）。 */
    @NotNull
    public static Component typeLine(@NotNull MagicType type) {
        MutableComponent value = Component.translatable(type.translationKey(HallMod.MODID))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("type"), value).withStyle(LABEL);
    }

    /** 「冷却：1.5 秒」；ticks <= 0 时显示"无冷却"。 */
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

    /** 「消耗：40 法力」；mana <= 0 时显示"无消耗"。 */
    @NotNull
    public static Component manaLine(int mana, @NotNull MagicType type) {
        if (mana <= 0) {
            return Component.translatable(key("mana.none")).withStyle(LABEL);
        }
        MutableComponent value = Component.literal(Integer.toString(mana))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("mana"), value).withStyle(LABEL);
    }

    /** 维持耗蓝那一行（KEEP 模式专用；其它模式由调用方决定不要加）。 */
    @NotNull
    public static Component keepCostLine(int perSecond, @NotNull MagicType type) {
        MutableComponent value = Component.literal(Integer.toString(perSecond))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.rgb())));
        return Component.translatable(key("keep_cost"), value).withStyle(LABEL);
    }
}
