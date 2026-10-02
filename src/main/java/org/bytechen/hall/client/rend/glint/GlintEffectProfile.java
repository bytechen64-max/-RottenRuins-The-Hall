package org.bytechen.hall.client.rend.glint;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration for an item glint effect.
 * Use the builder or static factory methods to create profiles,
 * then register via {@link GlintRenderManager#register}.
 */
public class GlintEffectProfile {

    public static final int MAX_PALETTE_COLORS = 8;

    public enum ColorMode {
        SINGLE(0.0f),
        DUAL_SCROLL(1.0f),
        RAINBOW(2.0f),
        AUTO_SAMPLE_SCROLL(3.0f);

        public final float shaderValue;

        ColorMode(float shaderValue) {
            this.shaderValue = shaderValue;
        }
    }

    // Primary color
    float red = 0.23f, green = 0.95f, blue = 1.0f, alpha = 1.0f;
    // Secondary color (for DUAL_SCROLL mode)
    float secondaryRed = 1.0f, secondaryGreen = 0.25f, secondaryBlue = 0.78f, secondaryAlpha = 1.0f;
    ColorMode colorMode = ColorMode.SINGLE;
    float speed = 1.0f;
    float intensity = 0.8f;
    // Bloom
    float bloomStrength = 0.0f;
    float bloomRadius = 1.0f;
    // Palette (for AUTO_SAMPLE_SCROLL)
    float[][] paletteColors = new float[0][];

    // World-space outline (third-person / ground)
    boolean worldOutlineEnabled = true;
    /** @deprecated 旧的模型空间缩放系数，新管线不再使用。见 {@link #worldOutlinePixelWidth}。 */
    @Deprecated
    float worldOutlineWidth = 0.06f;
    /** 描边宽度，单位屏幕像素。等宽靠它，与距离无关。 */
    float worldOutlinePixelWidth = 2.5f;
    /** 描边整体不透明度。 */
    float worldOutlineOpacity = 1.0f;
    String outlineShaderKey = null; // null = default

    // Gradient shader palette (for "gradient" outlineShaderKey)
    int gradientColorCount = 2;
    int[] gradientColors = new int[8];
    float gradientFlowSpeed = 1.0f;
    float gradientSpan = 1.0f;

    // Display context flags
    boolean showInGui = true;
    boolean showWhenHeld = true;
    boolean showInWorld = true;

    // -- builder --

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final GlintEffectProfile profile = new GlintEffectProfile();

        public Builder color(float r, float g, float b) {
            profile.red = r; profile.green = g; profile.blue = b;
            return this;
        }

        public Builder color(int rgb) {
            profile.red = ((rgb >> 16) & 0xFF) / 255.0f;
            profile.green = ((rgb >> 8) & 0xFF) / 255.0f;
            profile.blue = (rgb & 0xFF) / 255.0f;
            return this;
        }

        public Builder alpha(float a) { profile.alpha = a; return this; }

        public Builder secondaryColor(float r, float g, float b) {
            profile.secondaryRed = r; profile.secondaryGreen = g; profile.secondaryBlue = b;
            return this;
        }

        public Builder secondaryColor(int rgb) {
            profile.secondaryRed = ((rgb >> 16) & 0xFF) / 255.0f;
            profile.secondaryGreen = ((rgb >> 8) & 0xFF) / 255.0f;
            profile.secondaryBlue = (rgb & 0xFF) / 255.0f;
            return this;
        }

        public Builder secondaryAlpha(float a) { profile.secondaryAlpha = a; return this; }

        public Builder colorMode(ColorMode mode) { profile.colorMode = mode; return this; }
        public Builder speed(float s) { profile.speed = s; return this; }
        public Builder intensity(float i) { profile.intensity = i; return this; }
        public Builder bloomStrength(float s) { profile.bloomStrength = s; return this; }
        public Builder bloomRadius(float r) { profile.bloomRadius = r; return this; }

        public Builder palette(float[][] colors) {
            profile.paletteColors = colors;
            return this;
        }

        public Builder showInGui(boolean v) { profile.showInGui = v; return this; }
        public Builder showWhenHeld(boolean v) { profile.showWhenHeld = v; return this; }
        public Builder showInWorld(boolean v) { profile.showInWorld = v; return this; }

        public Builder worldOutline(boolean v) { profile.worldOutlineEnabled = v; return this; }
        /** @deprecated 改用 {@link #worldOutlinePixelWidth(float)}。 */
        @Deprecated
        public Builder worldOutlineWidth(float w) { profile.worldOutlineWidth = w; return this; }
        public Builder worldOutlinePixelWidth(float px) { profile.worldOutlinePixelWidth = px; return this; }
        public Builder worldOutlineOpacity(float o) { profile.worldOutlineOpacity = o; return this; }
        public Builder outlineShaderKey(String k) { profile.outlineShaderKey = k; return this; }

        /** Set gradient shader palette. 2-colour by default. */
        public Builder gradientColors(int... argb) {
            profile.gradientColorCount = Math.min(argb.length, 8);
            for (int i = 0; i < profile.gradientColorCount; i++) profile.gradientColors[i] = argb[i];
            for (int i = profile.gradientColorCount; i < 8; i++) profile.gradientColors[i] = 0;
            return this;
        }
        public Builder gradientFlowSpeed(float f) { profile.gradientFlowSpeed = f; return this; }
        public Builder gradientSpan(float f) { profile.gradientSpan = f; return this; }

        public GlintEffectProfile build() {
            return profile;
        }
    }

    // -- factory methods --

    public static GlintEffectProfile singleColor(float r, float g, float b) {
        return builder().color(r, g, b).colorMode(ColorMode.SINGLE).build();
    }

    public static GlintEffectProfile singleColor(int rgb) {
        return builder().color(rgb).colorMode(ColorMode.SINGLE).build();
    }

    public static GlintEffectProfile dualColor(int rgb1, int rgb2) {
        return builder().color(rgb1).secondaryColor(rgb2).colorMode(ColorMode.DUAL_SCROLL).build();
    }

    public static GlintEffectProfile rainbow() {
        return builder().colorMode(ColorMode.RAINBOW).speed(0.8f).intensity(0.9f).build();
    }

    public static GlintEffectProfile goldenGlint() {
        return builder()
                .color(0xFFD700)
                .secondaryColor(0xFFA500)
                .colorMode(ColorMode.DUAL_SCROLL)
                .speed(0.6f)
                .intensity(0.85f)
                .build();
    }

    /**
     * Create a profile with auto-sampled colors from the item's texture,
     * with bloom enabled for a star-like glow effect.
     */
    public static GlintEffectProfile autoSampleWithBloom(Item item) {
        float[][] palette = autoSampleColors(item);
        return builder()
                .colorMode(ColorMode.AUTO_SAMPLE_SCROLL)
                .palette(palette)
                .speed(0.9f)
                .intensity(0.85f)
                .bloomStrength(0.6f)
                .bloomRadius(1.2f)
                .build();
    }

    // -- auto color sampling --

    /**
     * Sample dominant colors from an item's texture sprite.
     * Returns up to MAX_PALETTE_COLORS RGBA arrays.
     */
    public static float[][] autoSampleColors(Item item) {
        if (item == null) return defaultPalette();

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return defaultPalette();

            BakedModel model = mc.getItemRenderer().getModel(
                    new ItemStack(item), mc.level, null, 0);
            TextureAtlasSprite sprite = model.getParticleIcon();
            if (sprite == null) return defaultPalette();

            return sampleSprite(sprite, 2, 24, 4);
        } catch (Exception e) {
            return defaultPalette();
        }
    }

    /**
     * Sample dominant colors from a texture atlas sprite.
     * @param sprite     the sprite to sample
     * @param sampleStep pixel step size (1 = every pixel, 2 = every other, etc.)
     * @param quantize   color channel quantization step (0-255, higher = fewer colors)
     * @param maxColors  maximum palette size
     */
    public static float[][] sampleSprite(TextureAtlasSprite sprite, int sampleStep,
                                         int quantize, int maxColors) {
        try {
            NativeImage image = getSpriteNativeImage(sprite);
            int w = sprite.contents().width();
            int h = sprite.contents().height();

            if (image == null) return defaultPalette();

            if (w <= 0 || h <= 0) return defaultPalette();

            Map<Integer, Integer> colorCounts = new LinkedHashMap<>();

            for (int y = 0; y < h; y += sampleStep) {
                for (int x = 0; x < w; x += sampleStep) {
                    int pixel = image.getPixelRGBA(x, y);
                    int a = (pixel >> 24) & 0xFF;
                    if (a < 32) continue; // skip transparent pixels

                    int r = ((pixel >> 16) & 0xFF) / quantize * quantize;
                    int g = ((pixel >> 8) & 0xFF) / quantize * quantize;
                    int b = (pixel & 0xFF) / quantize * quantize;
                    int quantized = (a << 24) | (r << 16) | (g << 8) | b;
                    colorCounts.merge(quantized, 1, Integer::sum);
                }
            }

            if (colorCounts.isEmpty()) return defaultPalette();

            List<Map.Entry<Integer, Integer>> sorted = colorCounts.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .limit(maxColors)
                    .collect(Collectors.toList());

            // Sort by hue for smooth palette
            sorted.sort(Comparator.comparingDouble(e -> {
                int rgb = e.getKey();
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                float max = Math.max(r, Math.max(g, b));
                float min = Math.min(r, Math.min(g, b));
                float hue = 0f;
                if (max - min > 0.001f) {
                    if (max == r) hue = ((g - b) / (max - min)) % 6f;
                    else if (max == g) hue = (b - r) / (max - min) + 2f;
                    else hue = (r - g) / (max - min) + 4f;
                    hue /= 6f;
                    if (hue < 0) hue += 1f;
                }
                return hue;
            }));

            float[][] palette = new float[Math.min(sorted.size(), MAX_PALETTE_COLORS)][4];
            for (int i = 0; i < palette.length; i++) {
                int rgba = sorted.get(i).getKey();
                palette[i][0] = ((rgba >> 16) & 0xFF) / 255.0f;
                palette[i][1] = ((rgba >> 8) & 0xFF) / 255.0f;
                palette[i][2] = (rgba & 0xFF) / 255.0f;
                palette[i][3] = ((rgba >> 24) & 0xFF) / 255.0f;
            }

            return palette;
        } catch (Exception e) {
            return defaultPalette();
        }
    }

    /**
     * Extracts the NativeImage from a sprite's contents, trying common
     * method/field names to work across different MCP mappings.
     */
    private static NativeImage getSpriteNativeImage(TextureAtlasSprite sprite) {
        Object contents = sprite.contents();
        // Try common getter names
        for (String methodName : new String[]{"originalImage", "getOriginalImage", "image", "getImage"}) {
            try {
                java.lang.reflect.Method m = contents.getClass().getMethod(methodName);
                Object result = m.invoke(contents);
                if (result instanceof NativeImage) return (NativeImage) result;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                break;
            }
        }
        // Try field access
        for (String fieldName : new String[]{"originalImage", "image", "mipmapImage"}) {
            try {
                java.lang.reflect.Field f = contents.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object result = f.get(contents);
                if (result instanceof NativeImage) return (NativeImage) result;
            } catch (NoSuchFieldException ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static float[][] defaultPalette() {
        return new float[][] {
                {1.0f, 0.3f, 0.1f, 1.0f},
                {1.0f, 0.6f, 0.15f, 1.0f},
                {1.0f, 0.75f, 0.2f, 1.0f},
                {0.9f, 0.5f, 0.15f, 1.0f},
        };
    }

    // -- display context check --

    public boolean shouldRender(ItemDisplayContext context) {
        return switch (context) {
            case GUI, FIXED -> showInGui;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> showWhenHeld;
            case GROUND, NONE -> showInWorld;
            default -> false;
        };
    }

    // -- getters --

    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public float getAlpha() { return alpha; }
    public float getSecondaryRed() { return secondaryRed; }
    public float getSecondaryGreen() { return secondaryGreen; }
    public float getSecondaryBlue() { return secondaryBlue; }
    public float getSecondaryAlpha() { return secondaryAlpha; }
    public ColorMode getColorMode() { return colorMode; }
    public float getSpeed() { return speed; }
    public float getIntensity() { return intensity; }
    public float getBloomStrength() { return bloomStrength; }
    public float getBloomRadius() { return bloomRadius; }
    public boolean isWorldOutlineEnabled() { return worldOutlineEnabled; }
    /** @deprecated 新管线不使用模型空间缩放系数。 */
    @Deprecated
    public float getWorldOutlineWidth() { return worldOutlineWidth; }
    public float getWorldOutlinePixelWidth() { return worldOutlinePixelWidth; }
    public float getWorldOutlineOpacity() { return worldOutlineOpacity; }
    public String getOutlineShaderKey() { return outlineShaderKey; }
    public int getGradientColorCount() { return gradientColorCount; }
    public int getGradientColor(int i) { return i < gradientColors.length ? gradientColors[i] : 0; }
    public float getGradientFlowSpeed() { return gradientFlowSpeed; }
    public float getGradientSpan() { return gradientSpan; }
    public int getPaletteSize() { return paletteColors.length; }

    public float getPaletteR(int index) {
        return index < paletteColors.length ? paletteColors[index][0] : 1.0f;
    }

    public float getPaletteG(int index) {
        return index < paletteColors.length ? paletteColors[index][1] : 1.0f;
    }

    public float getPaletteB(int index) {
        return index < paletteColors.length ? paletteColors[index][2] : 1.0f;
    }

    public float getPaletteA(int index) {
        return index < paletteColors.length ? paletteColors[index][3] : 1.0f;
    }
}
