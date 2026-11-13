package org.bytechen.rottenruins.api;

/** 物品 tooltip 底板配色的标记接口：客户端只做 instanceof 判断、无需注册；四个默认值逐字对应原版配色（底 0xF0100010、边框 0x505000FF → 0x5028007F，均 ARGB），实现了却不覆写即保持原样。 */
public interface ITooltipStyle {

    /** 是否启用自定义底板，默认 true；想按状态回到原版观感时覆写它。 */
    default boolean tooltipStyleEnabled() {
        return true;
    }

    default int tooltipBackgroundTop() {
        return 0xF0100010;
    }

    default int tooltipBackgroundBottom() {
        return 0xF0100010;
    }

    default int tooltipBorderTop() {
        return 0x505000FF;
    }

    default int tooltipBorderBottom() {
        return 0x5028007F;
    }

    /** 底板上的着色器效果，null（默认）= 只用纯色底板：key 决定图案、三个颜色决定配色；未知 key 或着色器加载失败时客户端自动退回纯色底板。 */
    default TooltipShaderSpec tooltipShader() {
        return null;
    }
}
