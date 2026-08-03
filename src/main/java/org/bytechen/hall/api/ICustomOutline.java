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
 * Gradient outline example:
 * <pre>{@code
 * public class GradientSword extends Item implements ICustomOutline {
 *     public int outlineColor() { return 0xFFFF6600; }
 *     public String outlineShaderKey() { return "gradient"; }
 *     public void configureOutlineShader(ShaderInstance s) {
 *         // 3-colour gradient: orange → gold → white, scrolling slowly
 *         setIntUniform(s, "ColorCount", 3);
 *         setIntUniform(s, "Color0", 0xFFFF6600);
 *         setIntUniform(s, "Color1", 0xFFFFD700);
 *         setIntUniform(s, "Color2", 0xFFFFFFFF);
 *         setFloatUniform(s, "FlowSpeed", 0.4f);
 *         setFloatUniform(s, "GradientSpan", 1.5f);
 *     }
 * }
 * }</pre>
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

    /** Outline scale factor. 0 = same size. Default 1.06×. */
    default float outlineWidth() { return 0.06f; }

    /** Blend mode. Default {@link BlendMode#ADDITIVE}. */
    default BlendMode outlineBlend() { return BlendMode.ADDITIVE; }

    /**
     * Shader key — maps to registered shader in {@code SplendidingShaders}.
     * <ul>
     *   <li>{@code null} or {@code "default"} — solid‑colour outline</li>
     *   <li>{@code "gradient"} — flowing multi‑colour gradient</li>
     * </ul>
     * Register custom keys via {@code SplendidingShaders.registerOutlineShader(key, shader, renderType)}.
     */
    default String outlineShaderKey() { return null; }

    /**
     * Called <b>before</b> outline quads are submitted.
     * Use {@link #setFloatUniform} / {@link #setIntUniform} helpers.
     */
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
