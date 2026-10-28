package org.bytechen.rottenruins.client.fx;

/**
 * 一个效果作用在哪个几何域上。
 *
 * <p>这是框架的第一根轴：现有两套半成品框架各自只会一个域 ——
 * {@code mask_layers}（宇宙星空/泛光）只会 {@link #SURFACE_UV}，
 * 描边管线只会 {@link #SILHOUETTE}。把它们并到一个栈里，效果才有地方放。</p>
 *
 * <p>决定这个域的，是"效果需要看到什么"：</p>
 * <ul>
 *   <li>{@link #SURFACE_UV} —— 需要物品自己的 quad 与 UV：析晶、裂纹、表面冒烟、
 *       本体变色。副作用是天然不盖别的东西（画在物品表面上）。</li>
 *   <li>{@link #SURFACE_WORLD} —— 同样需要物品几何，但按世界空间采样噪声
 *       （例如沿世界上方生长的火焰壳）。</li>
 *   <li>{@link #SILHOUETTE} —— 只需要剪影：火焰/烟雾在物品**外侧**燃烧、
 *       边缘扩散、外发光。必须自己保证不盖物品表面。</li>
 *   <li>{@link #SCREEN} —— 与物品几何无关的全屏后处理（屏幕结霜、镜头污渍）。</li>
 * </ul>
 */
public enum ItemFxDomain {
    SURFACE_UV,
    SURFACE_WORLD,
    SILHOUETTE,
    SCREEN
}
