package org.bytechen.rottenruins.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bytechen.rottenruins.api.ITooltipStyle;
import org.bytechen.rottenruins.api.TooltipShaderSpec;

import java.util.List;

/** 把 {@link ITooltipStyle} 的颜色与着色器效果写进原版 tooltip 底板；只改 RenderTooltipEvent.Color，不自绘、不接管渲染流程。 */
@OnlyIn(Dist.CLIENT)
public final class TooltipStyleHook {

    /** 内容区到原版实际涂层的外扩量：底色外扩 3 + 1px 描边 = 4；差 1px 边缘就会露缝。 */
    private static final int PADDING = 4;

    private TooltipStyleHook() {}

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ITooltipStyle style)) return;
        if (!style.tooltipStyleEnabled()) return;

        // 边框始终用物品自己的配色：原版这 1px 内框压在效果层之上，也是效果没加载成功时唯一还在的装饰。
        event.setBorderStart(style.tooltipBorderTop());
        event.setBorderEnd(style.tooltipBorderBottom());

        // 先画效果层再决定底色是否让位，顺序不能反：先让位、后画失败会让 tooltip 变成透明玻璃。
        TooltipShaderSpec spec = style.tooltipShader();
        if (spec != null && TooltipShaders.isReady(spec.key()) && drawShaderFloor(event, spec)) {
            event.setBackgroundStart(0);
            event.setBackgroundEnd(0);
            return;
        }

        event.setBackgroundStart(style.tooltipBackgroundTop());
        event.setBackgroundEnd(style.tooltipBackgroundBottom());
    }

    /**
     * 现代 UI（ModernUI）会在 {@code RenderTooltipEvent.Pre} 里接管 tooltip 并取消原版绘制；
     * 它一接管，本类挂在 Color 上的底板就永远等不到调用，物品看起来就是"tooltip 没了"。
     * 这里抢在它前面把开关按下去，让带自定义底板的物品继续走原版管线；
     * 帧末由 {@code SpellTooltipAnimator} 的收尾统一恢复开关。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ITooltipStyle style)) return;
        if (!style.tooltipStyleEnabled()) return;
        ModernUiTooltipCompat.silence();
    }

    /** 宽高事件没给，算法同 renderTooltipInternal（最宽组件宽、高度求和，单组件 -2）；返回 false 时调用方须保留纯色底板。 */
    private static boolean drawShaderFloor(RenderTooltipEvent.Color event, TooltipShaderSpec spec) {
        List<ClientTooltipComponent> components = event.getComponents();
        if (components.isEmpty()) return false;

        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        if (width <= 0 || height <= 0) return false;

        TooltipShaders.draw(spec.key(), event.getGraphics(),
                event.getX() - PADDING, event.getY() - PADDING,
                width + PADDING * 2, height + PADDING * 2,
                spec, timeSeconds());
        return true;
    }

    /** 秒级时间：游戏 tick + 帧插值 —— 暂停时热流也停住（与 FlowingNameColors 同一时间源）。 */
    private static float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0F;
        return (mc.level.getGameTime() + mc.getFrameTime()) / 20.0F;
    }
}
