package org.bytechen.hall.client.fx;

/**
 * 一张离屏「场」的声明。
 *
 * <p>效果**声明**自己要什么场，{@link ItemFxFieldPool} 负责真的把它开出来、跟着主 RT 改尺寸、
 * 以及在不需要时回收。效果自己不碰 FBO 生命周期 —— 今天描边里那两处
 * {@code ensureMaskTarget} / {@code ensureHaloTargets} 就是"效果自己管生命周期"的样子，
 * 加第二个效果时必然打架。</p>
 *
 * @param name      效果内唯一的场名
 * @param format    像素格式（见 {@link ItemFxFormat}）
 * @param scaleNum  尺寸分子（相对主 RT）
 * @param scaleDen  尺寸分母（相对主 RT）
 * @param minSize   最短边下限（避免小窗口下开出一堆 1px 的图）
 * @param persistent true = 跨帧保留内容（火焰/烟雾这类有状态的场）；
 *                   false = 每帧现用现清（剪影、模糊中间图）
 */
public record ItemFxFieldSpec(String name,
                              ItemFxFormat format,
                              int scaleNum,
                              int scaleDen,
                              int minSize,
                              boolean persistent) {

    public ItemFxFieldSpec {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("场必须有名字");
        if (scaleNum <= 0 || scaleDen <= 0) throw new IllegalArgumentException("缩放必须是正数");
        if (minSize < 1) minSize = 1;
    }

    /** 与主 RT 同尺寸、每帧重来：剪影遮罩这类。 */
    public static ItemFxFieldSpec full(String name, ItemFxFormat format) {
        return new ItemFxFieldSpec(name, format, 1, 1, 1, false);
    }

    /** 主 RT 的 1/N、每帧重来：模糊中间图这类。 */
    public static ItemFxFieldSpec scaled(String name, ItemFxFormat format, int den, int minSize) {
        return new ItemFxFieldSpec(name, format, 1, den, minSize, false);
    }

    /** 跨帧保留的场（火焰/烟雾/腐蚀）。S2 起才会真正用上。 */
    public static ItemFxFieldSpec state(String name, ItemFxFormat format, int den, int minSize) {
        return new ItemFxFieldSpec(name, format, 1, den, minSize, true);
    }

    /** 按主 RT 尺寸算出这张场该有多大。 */
    public int widthOf(int mainWidth) {
        return Math.max(minSize, mainWidth * scaleNum / scaleDen);
    }

    public int heightOf(int mainHeight) {
        return Math.max(minSize, mainHeight * scaleNum / scaleDen);
    }
}
