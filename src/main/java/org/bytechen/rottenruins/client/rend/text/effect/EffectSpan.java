package org.bytechen.rottenruins.client.rend.text.effect;

/**
 * 一个效果 span 的"可视字符区间 + 参数"（纯 Java）。
 *
 * <p>区间用<b>可视字符序号</b>（不是 raw 下标）表示：解析时从左到右数"真正会被画出来的字"，
 * 原版 § 码与自定义标记都不计数 —— 于是 T1 路径（测量/绘制）与 L1 路径（逐帧 overlay）
 * 能用同一套序号对齐效果。</p>
 *
 * <p>{@link #visibleEnd} 在解析到关闭标记 / 段尾 / {@code §r} 时才确定；解析完成后
 * 该对象对外视为只读。</p>
 */
public final class EffectSpan {

    public final EffectSpec spec;
    public final int visibleStart;

    /** 开区间右端（不含）。解析结束前可写，结束后只读。 */
    public int visibleEnd;

    public EffectSpan(EffectSpec spec, int visibleStart) {
        this.spec = spec;
        this.visibleStart = visibleStart;
        this.visibleEnd = visibleStart;
    }

    public boolean isColor() {
        return spec.isColor();
    }

    /** span 覆盖的可视字符数（可能为 0，例如"开了立刻关"）。 */
    public int length() {
        return Math.max(0, visibleEnd - visibleStart);
    }
}
