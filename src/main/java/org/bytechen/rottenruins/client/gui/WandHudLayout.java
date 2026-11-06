package org.bytechen.rottenruins.client.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.bytechen.rottenruins.HallMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-only HUD placement, stored independently of worlds and server settings. */
@OnlyIn(Dist.CLIENT)
public final class WandHudLayout {
    public static final int WIDTH = 96;
    public static final int HEIGHT = 96;
    public static final float MIN_SCALE = 0.70F;
    public static final float MAX_SCALE = 2.00F;
    private static final int EDGE = 8;
    private static final int BOTTOM = 16;
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("rotten_ruins/wand_hud.properties");
    private static final WandHudLayout INSTANCE = new WandHudLayout();

    private float xFraction = 1.0F;
    private float yFraction = 1.0F;
    private float widthScale = 0.85F;
    private float heightScale = 0.85F;
    private boolean hidden;

    private WandHudLayout() {
        load();
    }

    public static WandHudLayout get() { return INSTANCE; }

    public float widthScale(int screenWidth) {
        return Math.max(0.1F, Math.min(widthScale, (screenWidth - EDGE * 2.0F) / WIDTH));
    }

    public float heightScale(int screenHeight) {
        return Math.max(0.1F, Math.min(widthScale, (screenHeight - EDGE - BOTTOM) / (float) HEIGHT));
    }

    public int width(int screenWidth, int screenHeight) {
        return Math.round(WIDTH * Math.min(widthScale(screenWidth), heightScale(screenHeight)));
    }

    public int height(int screenWidth, int screenHeight) {
        return width(screenWidth, screenHeight);
    }

    public int x(int screenWidth, int screenHeight) {
        int travel = Math.max(0, screenWidth - EDGE * 2 - width(screenWidth, screenHeight));
        return EDGE + Math.round(travel * xFraction);
    }

    public int y(int screenWidth, int screenHeight) {
        int travel = Math.max(0, screenHeight - EDGE - BOTTOM - height(screenWidth, screenHeight));
        return EDGE + Math.round(travel * yFraction);
    }

    public void moveTo(int px, int py, int screenWidth, int screenHeight) {
        int travelX = Math.max(0, screenWidth - EDGE * 2 - width(screenWidth, screenHeight));
        int travelY = Math.max(0, screenHeight - EDGE - BOTTOM - height(screenWidth, screenHeight));
        xFraction = travelX == 0 ? 0 : clamp((px - EDGE) / (float) travelX, 0, 1);
        yFraction = travelY == 0 ? 0 : clamp((py - EDGE) / (float) travelY, 0, 1);
    }

    public void resize(float requestedWidth, float requestedHeight) {
        // Keep the normalized anchor stable, including at the right and bottom edges.
        widthScale = clamp((requestedWidth + requestedHeight) / 2, MIN_SCALE, MAX_SCALE);
        heightScale = widthScale;
    }

    public float requestedWidthScale() { return widthScale; }
    public float requestedHeightScale() { return heightScale; }
    public boolean hidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public void reset() {
        xFraction = 1.0F;
        yFraction = 1.0F;
        widthScale = 0.85F;
        heightScale = 0.85F;
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("x", Float.toString(xFraction));
        properties.setProperty("y", Float.toString(yFraction));
        properties.setProperty("widthScale", Float.toString(widthScale));
        properties.setProperty("heightScale", Float.toString(heightScale));
        properties.setProperty("hidden", Boolean.toString(hidden));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Rotten Ruins wand HUD layout");
            }
        } catch (IOException exception) {
            HallMod.LOGGER.warn("Could not save wand HUD layout", exception);
        }
    }

    private void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            xFraction = parse(properties, "x", 1.0F, 0, 1);
            yFraction = parse(properties, "y", 1.0F, 0, 1);
            // Migrate layouts saved before width and height could be adjusted separately.
            float oldScale = parse(properties, "scale", 0.85F, MIN_SCALE, MAX_SCALE);
            widthScale = parse(properties, "widthScale", oldScale, MIN_SCALE, MAX_SCALE);
            heightScale = parse(properties, "heightScale", oldScale, MIN_SCALE, MAX_SCALE);
            resize(widthScale, heightScale);
            hidden = Boolean.parseBoolean(properties.getProperty("hidden", "false"));
        } catch (IOException exception) {
            HallMod.LOGGER.warn("Could not load wand HUD layout", exception);
        }
    }

    private static float parse(Properties properties, String key, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
            return Float.isFinite(value) ? clamp(value, min, max) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
