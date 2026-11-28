package org.bytechen.rottenruins.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

/**
 * 暴露 {@code GuiGraphics#renderTooltipInternal} 给 {@code SpellTooltipAnimator}。
 *
 * <p>1.20.1 里这个方法是 private，也没有"画完道具 tooltip"的公共事件
 * （{@code RenderTooltipEvent} 只有 GatherComponents / Pre / Color），
 * 所以开合动画走这条访问器：在 {@code Pre} 里取消原版绘制，自己套上姿态与裁剪再调一次原方法。
 * 重入靠 animator 里的 {@code selfRender} 标记放行，不会无限递归。</p>
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsTooltipInvoker {

    @Invoker("renderTooltipInternal")
    void rottenruins$renderTooltipInternal(Font font, List<ClientTooltipComponent> components,
                                           int mouseX, int mouseY, ClientTooltipPositioner positioner);
}
