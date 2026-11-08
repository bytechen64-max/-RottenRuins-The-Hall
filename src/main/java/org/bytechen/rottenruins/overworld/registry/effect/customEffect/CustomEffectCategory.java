package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

/**
 * 自定义效果的<b>分类轴</b>，对应原版 {@code MobEffectCategory}。
 *
 * <p>它只描述"这是好 buff 还是坏 debuff"，用来决定配色、HUD 边框颜色、
 * 以及"是否该被免疫/净化"这类通用判断。具体行为永远由
 * {@link CustomEffectBase} 的子类实现。</p>
 */
public enum CustomEffectCategory {

    /** 增益：绿色边框。 */
    BENEFICIAL(0x22FF22),

    /** 减益：红色边框。 */
    HARMFUL(0xFF2222),

    /** 中性：蓝色边框（既不算好也不算坏，比如"被标记"）。 */
    NEUTRAL(0x5A82FF);

    private final int borderColor;

    CustomEffectCategory(int borderColor) {
        this.borderColor = borderColor;
    }

    /** 该分类在 HUD 上的边框色（ARGB 的 RGB 部分）。 */
    public int getBorderColor() {
        return borderColor;
    }

    public boolean isBeneficial() {
        return this == BENEFICIAL;
    }

    public boolean isHarmful() {
        return this == HARMFUL;
    }
}
