package org.bytechen.hall.api;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Items implementing this interface gain automatic outline + GUI glint
 * rendering without any external registration.
 * <p>
 * The mixin system checks each rendered item — if the item implements
 * {@code ICustomOutline}, outline/glint parameters are read directly
 * from the item instance.  No calls to {@code GlintRenderManager} or
 * {@code HeldItemGlintHelper} are needed.
 * <p>
 * Minimal example:
 * <pre>{@code
 * public class MyItem extends Item implements ICustomOutline {
 *     @Override public int outlineColor() { return 0xFFFF6600; }
 * }
 * }</pre>
 * <p>
 * 渐变描边示例（主色↔副色 沿物品表面流动）：
 * <pre>{@code
 * public class GradientSword extends Item implements ICustomOutline {
 *     @Override public int outlineColor() { return 0xFFFF6600; }
 *     @Override public int outlineSecondaryColor() { return 0xFF23F0FF; }
 *     @Override public String outlineShaderKey() { return "gradient"; }
 *     @Override public float outlinePixelWidth() { return 2.0f; }
 * }
 * }</pre>
 *
 * <p>渲染实现是屏幕空间的「剪影遮罩 + 环形膨胀」：描边只出现在物品剪影<b>外侧</b>，
 * 因此它和贴在物品表面的宇宙星空层永不重叠。见
 * {@code org.bytechen.hall.client.rend.glint.ItemOutlinePipeline}。</p>
 */
public interface ICustomOutline {

    // ─── outline ─────────────────────────────────────────────

    /** Outline colour as ARGB int (e.g. {@code 0xFFFF6600}). Required. */
    int outlineColor();

    /** @return true to enable outline for the given context. Default: all world contexts. */
    default boolean outlineEnabled(ItemDisplayContext ctx) {
        return switch (ctx) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND,
                 GROUND, NONE, FIXED -> true;
            default -> false;
        };
    }

    /**
     * 描边向外扩展的宽度，单位是 <b>屏幕像素</b>。默认 2.5。
     *
     * <p>等宽就是靠它：与物品大小、与玩家距离都无关，GUI 图标 / 一手 / 掉落物
     * 在任何情况下描边一样粗。</p>
     *
     * <p>描边只向外长，不会侵占物品表面 —— 贴在表面的宇宙星空层因此不受影响。
     * 想要更细/更粗就改这里；也可以在配置文件里用全局倍率
     * {@code SplendidingConfig.outlineWidthScale} 整体缩放。</p>
     */
    default float outlinePixelWidth() { return 2.5f; }

    /** 描边整体不透明度。默认 1.0（完全不透明，对齐原版发光描边）。 */
    default float outlineOpacity() { return 1.0f; }

    /**
     * 剪影判定的纹理 alpha 阈值。物品纹理中 alpha 低于它的像素不算物品的一部分。
     * 默认 0.1。调大可以让半透明细节（如飘带、光边）也纳入描边外形。
     */
    default float outlineAlphaCutoff() { return 0.1f; }

    /**
     * 副色，只在 {@link #outlineShaderKey()} 为 {@code "gradient"} 时参与双色流动。
     * 返回 alpha 为 0 表示不启用（退回主色）。默认 0。
     */
    default int outlineSecondaryColor() { return 0; }

    /**
     * @deprecated 旧接口：模型空间缩放系数（默认 0.06 ≈ 放大 1.06×）。
     *     在旧的"放大壳"实现里它既是宽度也是内部染色的元凶。新管线按像素计算宽度，
     *     这个值不再生效，请改用 {@link #outlinePixelWidth()}。
     */
    @Deprecated
    default float outlineWidth() { return 0.06f; }

    /** Blend mode. Default {@link BlendMode#ADDITIVE}. */
    default BlendMode outlineBlend() { return BlendMode.ADDITIVE; }

    /**
     * 颜色模式选择器。它<b>不再</b>对应独立的着色器与 RenderType ——
     * 三条旧的"描边着色器"已经收敛成同一个几何算法，这个 key 只决定颜色怎么算：
     * <ul>
     *   <li>{@code null} / {@code "default"} — 纯色</li>
     *   <li>{@code "gradient"} — 主色↔副色 沿物品表面流动</li>
     *   <li>{@code "warp_fbm"} — 彩虹流动（旧噪声风格的动态观感）</li>
     * </ul>
     */
    default String outlineShaderKey() { return null; }

    /**
     * @deprecated 新管线在遮罩 pass 里直接算颜色，不再把 ShaderInstance 交出去配置。
     *     这个方法仍然会被调用（传的是剪影遮罩着色器），且所有 uniform 名都变了，
     *     所以旧实现里的 {@code ColorCount} / {@code FlowSpeed} 之类设置不会生效。
     *     请改用 {@link #outlineShaderKey()} 与 {@link #outlineSecondaryColor()}。
     */
    @Deprecated
    default void configureOutlineShader(ShaderInstance shader) {}

    /** Convenience: set a float uniform (no‑op if uniform missing). */
    default void setFloatUniform(ShaderInstance s, String name, float value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    /** Convenience: set an int uniform (no‑op if uniform missing). */
    default void setIntUniform(ShaderInstance s, String name, int value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    enum BlendMode {
        TRANSLUCENT,  // srcAlpha, 1-srcAlpha
        ADDITIVE,     // srcAlpha, one
    }

    // ─── glint ───────────────────────────────────────────────

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
