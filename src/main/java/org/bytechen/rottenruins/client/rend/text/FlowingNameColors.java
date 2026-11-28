package org.bytechen.rottenruins.client.rend.text;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * 物品名字的粉→紫流动彩字，纯文本级实现：逐字一个带色 {@link Component} 再 append 串起来，
 * 颜色按（字序号 + 时间）取样。不走 Font 层逐字上色 —— 那条路曾让整段文字实机消失。
 */
public final class FlowingNameColors {

    private static final float SPAN = 8.0F;

    private static final float FLOW = 6.0F;

    private FlowingNameColors() {}

    public static MutableComponent gradient(Component name, int from, int to) {
        if (name == null) return Component.empty();

        String text = name.getString();
        if (text.isEmpty()) return name.copy();

        Style base = name.getStyle().withColor((net.minecraft.network.chat.TextColor) null);
        int[] points = text.codePoints().toArray();
        int count = Math.max(1, points.length - 1);

        MutableComponent out = Component.empty();
        for (int i = 0; i < points.length; i++) {
            float t = i / (float) count;
            out.append(Component.literal(new String(Character.toChars(points[i])))
                    .withStyle(base.withColor(lerpRgb(from, to, t))));
        }
        return out;
    }

    public static MutableComponent flowing(Component name, int from, int to) {
        if (name == null) return Component.empty();

        String text = name.getString();
        if (text.isEmpty()) return name.copy();

        Style base = name.getStyle().withColor((net.minecraft.network.chat.TextColor) null);

        float time = timeSeconds();
        MutableComponent out = Component.empty();

        int i = 0;
        // 用 codePoints 遍历，避免把代理对（emoji / 生僻字）从中间劈开
        for (int cp : text.codePoints().toArray()) {
            float phase = i / SPAN + time * (FLOW / SPAN);
            float t = phase - (float) Math.floor(phase);
            t = t < 0.5F ? t * 2.0F : (1.0F - t) * 2.0F;

            int rgb = lerpRgb(from, to, t);
            out.append(Component.literal(new String(Character.toChars(cp)))
                    .withStyle(base.withColor(rgb)));
            i++;
        }
        return out;
    }

    /**
     * 给 tooltip 任意一行上渐变：客户端走 {@link #flowing}（内部要 Minecraft.getInstance()，服务端不能调），
     * 否则走 {@link #gradient}；判断收在一处，免得各物品类里漏写一半。
     */
    public static MutableComponent line(Component text, int from, int to,
                                       net.minecraft.world.level.Level level) {
        return level != null && level.isClientSide
                ? flowing(text, from, to)
                : gradient(text, from, to);
    }

    /** 秒级时间：tick + 帧插值，保证逐帧连续推进而不是每 tick 跳一格。 */
    private static float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0F;
        return (mc.level.getGameTime() + mc.getFrameTime()) / 20.0F;
    }

    private static int lerpRgb(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF;
        int r = Mth.clamp((int) (ar + (br - ar) * t), 0, 255);
        int g = Mth.clamp((int) (ag + (bg - ag) * t), 0, 255);
        int b = Mth.clamp((int) (ab + (bb - ab) * t), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
