package org.bytechen.rottenruins.client.tooltip;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.armor.ModArmorItem;
import org.bytechen.rottenruins.content.armor.ModArmorMaterial;
import org.bytechen.rottenruins.content.magic.base.MagicTooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * 两套盔甲的 tooltip：常显「套装简介」，按住 Shift 再展开特殊效果细节。
 *
 * <p>每件自带的属性加成不在这里画 —— 原版 {@code ArmorItem} 会按 attribute modifier 自动列出，
 * 重复列一遍只会更乱。细节行用 {@code *关键字*} 标记，渲染成金色，其余灰色；
 * 如梦套的简介走模组自带的 {@code §[rrs:grad:…]} 渐变，粗制套是普通紫色。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ArmorTooltipSections {

    /** 细节里的关键词：金色。 */
    private static final ChatFormatting KEYWORD = ChatFormatting.GOLD;
    /** 其余文字：紫色。 */
    private static final ChatFormatting TEXT = ChatFormatting.LIGHT_PURPLE;
    /** 细节正文：灰色，和法术的折叠段一致。 */
    private static final ChatFormatting DETAIL = ChatFormatting.GRAY;

    private static final String MARK = "*";

    /** 如梦套简介的渐变端点：蓝 → 紫。 */
    private static final String GRADIENT_FROM = "#4C6BFF";
    private static final String GRADIENT_TO = "#B36BFF";

    /** 与 {@code ArmorSetHandlers} 保持一致：每点幸运 3%，上限 50%。 */
    private static final double DODGE_PER_LUCK = 0.03D;
    private static final double MAX_DODGE = 0.50D;

    private ArmorTooltipSections() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ModArmorItem armor)) {
            return;
        }
        String set = "item." + HallMod.MODID + ".armor." + armor.getMaterial().getName();

        event.getToolTip().add(Component.empty());
        Component desc = description(armor, set + ".desc");
        if (desc != null) {
            event.getToolTip().add(desc);
        }
        event.getToolTip().add(MagicTooltips.shiftHint());

        if (!Screen.hasShiftDown()) {
            return;
        }
        // 特殊效果细节：item.rotten_ruins.armor.<材质>.detail.1、.2 …… 连续编号，缺号即止
        for (int i = 1; ; i++) {
            String raw = raw(set + ".detail." + i);
            if (raw == null) {
                break;
            }
            event.getToolTip().add(MagicTooltips.detail(keyword(raw)));
        }
        // 如梦套的闪避率是实时算的，没法写死在语言文件里
        if (armor.getMaterial() == ModArmorMaterial.DREAMLIKE) {
            event.getToolTip().add(MagicTooltips.detail(dodgeLine(set + ".dodge")));
        }
    }

    /** 当前闪避率：{@code 3% × 幸运}，上限 50%；拿不到玩家时按 0 显示。 */
    private static Component dodgeLine(String key) {
        Player player = Minecraft.getInstance().player;
        double luck = player == null ? 0.0D
                : player.getAttributeValue(Attributes.LUCK);
        double chance = Math.min(MAX_DODGE, Math.max(0.0D, DODGE_PER_LUCK * luck));
        return Component.translatable(key, Component.literal(percent(chance)).withStyle(KEYWORD))
                .withStyle(DETAIL);
    }

    /** {@code 0.075 → "7.5%"}，整数不拖小数点（{@code 0.3 → "30%"}）。 */
    private static String percent(double ratio) {
        double value = ratio * 100.0D;
        if (Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /** 简介：如梦套走蓝紫渐变，其余紫色。 */
    private static Component description(ModArmorItem armor, String key) {
        String text = raw(key);
        if (text == null) {
            return null;
        }
        if (armor.getMaterial() == ModArmorMaterial.DREAMLIKE) {
            return Component.literal("§[rrs:grad:" + GRADIENT_FROM + ":" + GRADIENT_TO + "]"
                    + text + "§[/]");
        }
        return Component.literal(text).withStyle(TEXT);
    }

    /**
     * 把 {@code *关键字*} 解析成金色片段，其余保持灰色。
     * 星号不成对时按普通字符处理，不会把后半句吞掉。
     */
    private static Component keyword(String text) {
        MutableComponent line = Component.empty();
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf(MARK, index);
            int close = open < 0 ? -1 : text.indexOf(MARK, open + 1);
            if (open < 0 || close < 0) {
                line.append(Component.literal(text.substring(index)).withStyle(DETAIL));
                break;
            }
            if (open > index) {
                line.append(Component.literal(text.substring(index, open)).withStyle(DETAIL));
            }
            line.append(Component.literal(text.substring(open + 1, close)).withStyle(KEYWORD));
            index = close + 1;
        }
        return line;
    }

    /** 取原始文案（不做样式），缺失返回 {@code null}。 */
    private static String raw(String key) {
        return Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : null;
    }
}
