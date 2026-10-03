package org.bytechen.hall.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bytechen.hall.api.ITooltipStyle;
import org.bytechen.hall.api.TooltipShaderSpec;

import java.util.List;

/**
 * 把 {@link ITooltipStyle} 的颜色（和可选着色器效果）写进原版 tooltip 底板。
 *
 * <h3>为什么是 {@code RenderTooltipEvent.Color}</h3>
 * <p>它是 Forge 为"这块底板该是什么颜色"专门留的钩子，触发点就在
 * {@code GuiGraphics.renderTooltipInternal} 里画底板之前，改完的四个值会被原样
 * 传给 {@code TooltipRenderUtil.renderTooltipBackground}。所以这条路
 * <b>不混入、不自绘、不接管渲染流程</b> —— 和 {@code FlowingNameTooltipHook}
 * 改文字颜色是同一种做法，改错了顶多颜色难看，不会把 tooltip 画没。</p>
 *
 * <h3>为什么挂在 Forge 总线</h3>
 * <p>{@code RenderTooltipEvent} 是游戏事件（不是 mod 生命周期事件），
 * 所以注册在 {@code MinecraftForge.EVENT_BUS} 上 —— 与
 * {@code ClientPacketHandlers.init()} 里那几条 {@code addListener} 一样。</p>
 *
 * <h3>它在这个时序里的位置（决定了效果能画在哪）</h3>
 * <pre>
 *   drawManaged → flush()                      ← 批处理清空，此刻立即绘制是安全的
 *     RenderTooltipEvent.Color                 ← 我们在这里：画效果层 + 改四个颜色
 *     TooltipRenderUtil.renderTooltipBackground ← 铺底色（alpha 0 = 让位）+ 压 1px 内框
 *   （drawManaged 结束）→ flush()
 *   文字 → 图像组件（誓约条）                    ← 都压在上面
 * </pre>
 *
 * <p>Forge 在 47.4.20 <b>没有</b> PostBackground 之类的钩子（{@code RenderTooltipEvent}
 * 只有 {@code Pre} / {@code Color} / {@code GatherComponents}），所以效果只能画在
 * 铺底色之前：让原版底色透明、自己画一层，再让原版的 1px 内框压在效果之上。
 * 顺序上没什么可挑的，但结果正好是我们想要的层次。</p>
 *
 * <h3>只认自己的物品</h3>
 * <p>这个事件对<b>所有</b> tooltip 都会发。这里用 {@code instanceof ITooltipStyle}
 * 过滤，所以没实现接口的物品一个像素都不会变。另外
 * {@code event.getItemStack()} 取的是 {@code GuiGraphics.tooltipStack}：
 * 有些绘制路径（例如聊天栏的物品悬浮）不带物品栈，那里拿到的是
 * {@code ItemStack.EMPTY}，过滤会直接跳过 —— 这正是我们要的默认行为。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TooltipStyleHook {

    /**
     * 内容区到"原版实际涂到的范围"的外扩量。
     *
     * <p>{@code TooltipRenderUtil} 的写法是：底色从内容区外扩 3 起，
     * 外面再加一圈 1px 的描边线（{@code renderHorizontalLine(gui, i, j - 1, ...)}、
     * {@code renderVerticalLineGradient(gui, i - 1, ...)}）——
     * 合起来就是外扩 4。效果层的四边形按这个值铺，才能与底板轮廓逐像素对齐；
     * 差 1px 的症状是边缘露出一条没被效果盖住的缝。</p>
     */
    private static final int PADDING = 4;

    private TooltipStyleHook() {}

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ITooltipStyle style)) return;
        if (!style.tooltipStyleEnabled()) return;

        // 边框始终用物品自己的配色：原版会在效果层之上再画这 1px 内框，
        // 它是画面里最利落的一条线，也是"效果没加载成功"时唯一还在的装饰。
        event.setBorderStart(style.tooltipBorderTop());
        event.setBorderEnd(style.tooltipBorderBottom());

        // 先试着画效果层，再决定底色要不要让位 ——
        // 顺序不能反：先让位、后画失败的话，tooltip 会变成一块透明玻璃。
        TooltipShaderSpec spec = style.tooltipShader();
        if (spec != null && TooltipShaders.isReady(spec.key()) && drawShaderFloor(event, spec)) {
            event.setBackgroundStart(0);   // alpha 0：原版底色让位给效果层
            event.setBackgroundEnd(0);
            return;
        }

        // 没有效果 / 效果不可用（着色器没起来）：退回纯色底板
        event.setBackgroundStart(style.tooltipBackgroundTop());
        event.setBackgroundEnd(style.tooltipBackgroundBottom());
    }

    /**
     * 在"原版底色本该占的那块矩形"上画效果层。
     *
     * <p>宽高必须自己算 —— 事件没给。算法与
     * {@code GuiGraphics.renderTooltipInternal} 里那段逐字一致：
     * 宽度取所有组件里最宽的一行，高度是各组件高度之和（只有一个组件时还要 -2，
     * 因为第一行后面那 2px 间隔只在多组件时才有意义）。</p>
     *
     * @return 是否真的画上了。{@code false} 时调用方要保留纯色底板。
     */
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

    /**
     * 秒级时间：游戏 tick + 帧插值。
     *
     * <p>与 {@code FlowingNameColors.timeSeconds()} 同一个时间源 ——
     * 暂停时热流也停住，而不是"暂停了背景还在动"。
     * 血条那边用的是 {@code System.currentTimeMillis()}（暂停时星尘继续流动），
     * 两者取向不同：tooltip 是界面的附属物，跟着世界一起停更自然。</p>
     */
    private static float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0F;
        return (mc.level.getGameTime() + mc.getFrameTime()) / 20.0F;
    }
}
