package org.bytechen.rottenruins.client.tooltip;

import java.util.List;

import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.content.magic.base.MagicTooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * 法术物品 tooltip 的<b>文字段</b>：简介、Shift 提示、折叠的细节行。
 *
 * <p>数值段（法力消耗 / 类型 / 冷却 / 持续消耗 / 蓄力时间）由服务端安全的
 * {@link CMagicBaseItem#appendHoverText} 追加，这里只补需要知道 Shift 状态的部分
 * —— {@code content} 层不允许引用客户端类，所以按 Shift 拆到客户端。</p>
 *
 * <p>{@link ItemTooltipEvent} 在 {@code appendHoverText} 之后触发，所以追加顺序
 * 天然就是"数值段 → 空行 → 简介 → 提示 → 细节"。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpellTooltipSections {

    private SpellTooltipSections() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof CMagicBaseItem spell)) {
            return;
        }
        ResourceLocation id = spell.getMagicId();
        if (id == null) {
            return;
        }
        List<Component> lines = event.getToolTip();
        String prefix = "item." + id.getNamespace() + "." + id.getPath();

        // 空行也保留：简介和数值段之间要断开，需求里明确写了
        Component desc = present(prefix + ".desc");
        if (desc != null) {
            lines.add(Component.empty());
            lines.add(desc);
        }
        lines.add(MagicTooltips.shiftHint());

        if (!Screen.hasShiftDown()) {
            return;
        }
        // 语言文件里的补充说明：item.<id>.detail.1、.2 …… 连续编号，缺号即止
        for (int i = 1; ; i++) {
            Component detail = present(prefix + ".detail." + i);
            if (detail == null) {
                break;
            }
            lines.add(MagicTooltips.detailText(detail));
        }
    }

    /** 语言文件里没有这个键就返回 {@code null}（不同法术的细节条数不一样）。 */
    private static Component present(String key) {
        if (!Language.getInstance().has(key)) {
            return null;
        }
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }
}
