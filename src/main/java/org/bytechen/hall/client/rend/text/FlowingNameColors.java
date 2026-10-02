package org.bytechen.hall.client.rend.text;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * 物品名字的<b>粉→紫流动彩字</b> —— 纯文本级实现。
 *
 * <h3>为什么最终选了"改文本颜色"这条最朴素的路</h3>
 * <p>先前尝试过在 {@code Font} 层逐字上色（接管 {@code renderChar}），
 * 实机结果是<b>整段文字消失</b>。那属于"侵入绘制最底层"的做法：
 * 一旦对原生渲染流程的还原有任何偏差，症状就是文字没了，而且很难从日志看出来。
 * 既然需求只是"名字有粉紫流动感"，那么<b>用原版的文本颜色机制</b>就够了 ——
 * 这条路的每一步都是公开 API，不可能把文字画坏。</p>
 *
 * <h3>实现方式</h3>
 * <p>把名字拆成"逐字一个 {@link Component}，各自带自己的颜色"，再用
 * {@link MutableComponent#append} 串起来。颜色按
 * <b>（字序号 + 时间）</b>取色，于是整条渐变会沿文字流动。</p>
 *
 * <h3>刷新时机</h3>
 * <p>由 {@code ItemTooltipEvent} 在<b>每次构建 tooltip 时</b>调用
 * （见 {@code FlowingNameTooltipHook}）。那个事件是在 tooltip 内容被收集时触发的，
 * 拿到的 {@code List<Component>} 可以就地替换，所以名字每次都会用当时的相位重新上色。</p>
 */
public final class FlowingNameColors {

    /** 一个完整循环跨越多少个字。 */
    private static final float SPAN = 8.0F;

    /** 流动速度（字/秒）。 */
    private static final float FLOW = 6.0F;

    private FlowingNameColors() {}

    /**
     * 生成<b>固定的</b>逐字渐变组件：从 from 到 to 沿文字单向铺开。
     *
     * <p>用于手持物品的名字（快捷栏下方那一条）。它与 tooltip 的区别在于：
     * 手持那条路径没有"每帧重建"的机会，颜色在创建时定死，所以给一个
     * <b>沿文字方向的稳定渐变</b>，而不是随时间流动。</p>
     *
     * <p>实现只用 {@code Component.literal} + {@code withStyle} 这类公开 API，
     * 不会影响任何渲染流程。</p>
     */
    public static MutableComponent gradient(Component name, int from, int to) {
        if (name == null) return Component.empty();

        String text = name.getString();
        if (text.isEmpty()) return name.copy();

        Style base = name.getStyle().withColor((net.minecraft.network.chat.TextColor) null);
        int[] points = text.codePoints().toArray();
        int count = Math.max(1, points.length - 1);

        MutableComponent out = Component.empty();
        for (int i = 0; i < points.length; i++) {
            float t = i / (float) count;                 // 0 → 1 沿文字铺开
            out.append(Component.literal(new String(Character.toChars(points[i])))
                    .withStyle(base.withColor(lerpRgb(from, to, t))));
        }
        return out;
    }

    /**
     * 把名字重建成"逐字带色"的流动渐变色组件。
     *
     * @param name   原名字组件（保留它原有的样式，例如粗体/斜体/自定义字体）
     * @param from   渐变起色（RGB，ARGB 的 alpha 会被忽略）
     * @param to     渐变止色
     * @return 带流动渐变色的名字；{@code name} 为空时原样返回
     */
    public static MutableComponent flowing(Component name, int from, int to) {
        if (name == null) return Component.empty();

        String text = name.getString();
        if (text.isEmpty()) return name.copy();

        // 继承原名字的样式（去掉颜色 —— 颜色由我们逐字给）
        Style base = name.getStyle().withColor((net.minecraft.network.chat.TextColor) null);

        float time = timeSeconds();
        MutableComponent out = Component.empty();

        int i = 0;
        // 用 codePoints 遍历，避免把代理对（emoji / 生僻字）从中间劈开
        for (int cp : text.codePoints().toArray()) {
            float phase = i / SPAN + time * (FLOW / SPAN);
            // 三角波 0..1..0：让粉紫之间平滑往复，而不是锯齿式跳色
            float t = phase - (float) Math.floor(phase);
            t = t < 0.5F ? t * 2.0F : (1.0F - t) * 2.0F;

            int rgb = lerpRgb(from, to, t);
            out.append(Component.literal(new String(Character.toChars(cp)))
                    .withStyle(base.withColor(rgb)));
            i++;
        }
        return out;
    }

    /** 秒级时间：游戏 tick + 帧插值，保证逐帧连续推进（而不是每 tick 跳一格）。 */
    private static float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0F;
        return (mc.level.getGameTime() + mc.getFrameTime()) / 20.0F;
    }

    /**
     * 两色 RGB 线性插值（只算 RGB，返回 0xRRGGBB —— 原版 TextColor 要的就是这个）。
     */
    private static int lerpRgb(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF;
        int r = Mth.clamp((int) (ar + (br - ar) * t), 0, 255);
        int g = Mth.clamp((int) (ag + (bg - ag) * t), 0, 255);
        int b = Mth.clamp((int) (ab + (bb - ab) * t), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
