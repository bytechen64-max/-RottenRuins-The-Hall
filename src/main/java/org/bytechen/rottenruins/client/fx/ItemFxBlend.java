package org.bytechen.rottenruins.client.fx;

/**
 * 效果最后怎么合到画面上。
 *
 * <p>框架的第二根轴。现在的描边只有"加法 / 透明"两种，而未来那批效果各自需要不同的合成方式 ——
 * 这就是为什么它们塞不进现有管线：</p>
 *
 * <ul>
 *   <li>{@link #ADD} —— 自发光叠加（火焰、火星、辉光）。加法下"黑 = 加 0"，天然不需要 alpha 剪。</li>
 *   <li>{@link #ALPHA} —— 普通透明混合（烟雾的可见轮廓、彩色雾气）。</li>
 *   <li>{@link #MODULATE} —— 与已有画面相乘（结霜、曝光、被腐蚀处的变暗）。</li>
 *   <li>{@link #DARKEN} —— 只压暗不发光（浓烟、焦痕、裂纹处投下的阴影）。黑体火的外圈就需要它。</li>
 *   <li>{@link #REPLACE_BODY} —— 替换物品本体（腐蚀"吃掉"一块、裂纹改形、析晶覆盖表面）。
 *       这一档必须能拿到物品自己的绘制结果，所以它是 {@link ItemFxDomain#SURFACE_UV} 的常客。</li>
 * </ul>
 *
 * <p><b>实现状态</b>：S1 只有 {@link #ADD} / {@link #ALPHA} 真正接好（= 今天描边那两种）；
 * 其余三种是声明好的目标，等对应的效果落地时再实现，避免先写一堆没人用的代码。</p>
 */
public enum ItemFxBlend {
    ADD,
    ALPHA,
    MODULATE,
    DARKEN,
    REPLACE_BODY
}
