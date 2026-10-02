package org.bytechen.hall.client.rend.text;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bytechen.hall.api.IFlowingName;

import java.util.List;

/**
 * 在 tooltip <b>构建</b>时把物品名字换成流动渐变色。
 *
 * <h3>为什么用 {@code ItemTooltipEvent}</h3>
 * <p>它是 Forge 提供的"tooltip 内容正在被收集"的钩子，直接给出
 * {@code List<Component>}，可以就地替换。相比去 {@code Font} 渲染层拦截，
 * 这条路每一步都是公开 API，<b>不可能把文字画坏</b>。</p>
 *
 * <h3>只认第一行</h3>
 * <p>原版 {@code ItemStack.getTooltipLines} 的第一个元素必定是物品名字，
 * 所以只替换 {@code index 0}，其余行（附魔、耐久、自定义 tooltip）一律不动 ——
 * 这样绝不会误伤别的文字。</p>
 */
public final class FlowingNameTooltipHook {

    private FlowingNameTooltipHook() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof IFlowingName flowing)) return;
        if (!flowing.flowingNameEnabled()) return;

        List<Component> lines = event.getToolTip();
        if (lines.isEmpty()) return;

        Component name = lines.get(0);
        if (name == null) return;

        lines.set(0, FlowingNameColors.flowing(name, flowing.flowingNameColorFrom(),
                flowing.flowingNameColorTo()));
    }
}
