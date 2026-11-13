package org.bytechen.rottenruins.api;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 实现本接口的物品自动获得描边 + GUI glint，无需外部注册（mixin 直接读物品实例上的参数）。
 * 渲染是屏幕空间的「剪影遮罩 + 环形膨胀」，描边只出现在剪影外侧，故不影响物品表面的星空层。
 */
public interface ICustomOutline {

    /** Outline colour as ARGB int (e.g. {@code 0xFFFF6600}). Required. */
    int outlineColor();

    /** 实例级覆写：默认退回无参版本；outlineColor() 拿不到 ItemStack，要按实例（改名/附魔/NBT）变化就覆写带 stack 的这几个。 */
    default int outlineColorFor(ItemStack stack) {
        return outlineColor();
    }

    default int outlineSecondaryColorFor(ItemStack stack) {
        return outlineSecondaryColor();
    }

    default String outlineShaderKeyFor(ItemStack stack) {
        return outlineShaderKey();
    }

    default BlendMode outlineBlendFor(ItemStack stack) {
        return outlineBlend();
    }

    /** @return true to enable outline for the given context. Default: all world contexts. */
    default boolean outlineEnabled(ItemDisplayContext ctx) {
        return switch (ctx) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND,
                 GROUND, NONE, FIXED -> true;
            default -> false;
        };
    }

    /** 描边向外扩展的宽度（屏幕像素，默认 2.5），同时是宽度上限：默认 DISTANCE 模式下实际宽取 min(本值, 物品表观尺寸 × outlineWidthMaxRatio) 再夹在 outlineMinPixelWidth 之上。 */
    default float outlinePixelWidth() { return 2.5f; }

    /** 描边宽度怎么算：SCREEN = 恒定屏幕像素宽（旧行为）；DISTANCE = 按物品屏幕占比收细（默认）。 */
    enum OutlineWidthMode {
        SCREEN,
        DISTANCE
    }

    /** @return 宽度计算方式。默认 {@link OutlineWidthMode#DISTANCE}。 */
    default OutlineWidthMode outlineWidthMode() { return OutlineWidthMode.DISTANCE; }

    /** 描边整体不透明度。默认 1.0（完全不透明，对齐原版发光描边）。 */
    default float outlineOpacity() { return 1.0f; }

    /** 剪影判定的纹理 alpha 阈值（默认 0.1）：低于它的像素不算物品的一部分；调大让半透明细节（飘带、光边）也纳入外形。 */
    default float outlineAlphaCutoff() { return 0.1f; }

    /** 副色：只在 outlineShaderKey() 为 "gradient" 时参与双色流动；返回 alpha 0 表示不启用。默认 0。 */
    default int outlineSecondaryColor() { return 0; }

    /** @deprecated 旧的模型空间缩放系数，新管线按像素计算宽度，此值不生效，请改用 outlinePixelWidth()。 */
    @Deprecated
    default float outlineWidth() { return 0.06f; }

    default BlendMode outlineBlend() { return BlendMode.ADDITIVE; }

    /** 颜色模式 key：null / "default" 纯色、"gradient" 主色↔副色流动、"warp_fbm" 彩虹流动、"fire" 噪声湍流 + 白热核、
     * "fire_2" 世界空间体积火焰（0.9×1.0×0.9 格盒内 ray marching，有视差、会被墙遮挡；GUI 不画）。 */
    default String outlineShaderKey() { return null; }

    /** @deprecated 新管线在遮罩 pass 里直接算颜色，不再交出 ShaderInstance；uniform 名已变，旧的 ColorCount / FlowSpeed 设置不生效，请用 outlineShaderKey()。 */
    @Deprecated
    default void configureOutlineShader(ShaderInstance shader) {}

    default void setFloatUniform(ShaderInstance s, String name, float value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    default void setIntUniform(ShaderInstance s, String name, int value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    enum BlendMode {
        TRANSLUCENT,  // srcAlpha, 1-srcAlpha
        ADDITIVE,     // srcAlpha, one
    }

    /** Return non‑null to enable automatic GUI glint. Default null (no glint). */
    default GlintSettings glintSettings() { return null; }

    record GlintSettings(
        int color,
        int secondaryColor,
        ColorMode colorMode,
        float speed,
        float intensity,
        float bloomStrength,
        float bloomRadius,
        boolean showInGui,
        boolean showWhenHeld,
        boolean showInWorld
    ) {
        public static final int MAX_PALETTE_COLORS = 8;

        public enum ColorMode {
            SINGLE(0.0f), DUAL_SCROLL(1.0f), RAINBOW(2.0f), AUTO_SAMPLE_SCROLL(3.0f);
            public final float shaderValue;
            ColorMode(float v) { this.shaderValue = v; }
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int color = 0xCC23F0FF, secondary = 0xCCFF40C7;
            private ColorMode mode = ColorMode.SINGLE;
            private float speed = 1f, intensity = 0.8f, bloomStr, bloomR = 1f;
            private boolean inGui = true, held = true, inWorld = true;

            public Builder color(int c) { this.color = c; return this; }
            public Builder secondaryColor(int c) { this.secondary = c; return this; }
            public Builder colorMode(ColorMode m) { this.mode = m; return this; }
            public Builder speed(float s) { this.speed = s; return this; }
            public Builder intensity(float i) { this.intensity = i; return this; }
            public Builder bloomStrength(float b) { this.bloomStr = b; return this; }
            public Builder bloomRadius(float r) { this.bloomR = r; return this; }
            public Builder showInGui(boolean v) { this.inGui = v; return this; }
            public Builder showWhenHeld(boolean v) { this.held = v; return this; }
            public Builder showInWorld(boolean v) { this.inWorld = v; return this; }

            public GlintSettings build() {
                return new GlintSettings(color, secondary, mode, speed, intensity,
                        bloomStr, bloomR, inGui, held, inWorld);
            }
        }
    }
}
