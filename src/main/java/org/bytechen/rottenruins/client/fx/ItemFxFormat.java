package org.bytechen.rottenruins.client.fx;

/**
 * 离屏「场」的格式。
 *
 * <p>框架的第三根轴 —— 也是现有管线做不到有状态效果的原因：MC 的
 * {@code RenderTarget} 内部格式硬编码 {@code RGBA8}，而<b>多帧反馈的低值会被 8bit 量化截断</b>
 * （火焰羽流提前死亡、拖尾出现等值线状带状瑕疵）。凡是"跨帧累积"的场必须是浮点。</p>
 *
 * <p>{@link #RGBA8} 走 MC 现成的 {@code TextureTarget}；两个浮点格式需要自建 FBO
 * （{@code glTexImage2D(GL_RGBA16F)}）—— 它们在 GL 3.2 core 的<b>必支持可渲染格式</b>表里，
 * 不需要查扩展。注意 {@code RGB16F} <b>不在</b>那张表里，别用。</p>
 */
public enum ItemFxFormat {
    /** 8bit，够用且最省：剪影遮罩、纯显示的中间图。MC 的 TextureTarget 直接支持。 */
    RGBA8,
    /** 16F 双通道（能量 + 烟）。反馈场首选，带宽只有 RGBA16F 的一半。需要自建 FBO。 */
    RG16F,
    /** 16F 四通道：需要额外平流色调 / 多物理量时用。需要自建 FBO。 */
    RGBA16F
}
