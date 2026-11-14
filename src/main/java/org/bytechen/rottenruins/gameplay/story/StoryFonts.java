package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.HallMod;

import java.util.List;

/** Shared command presets; every custom font falls back to Minecraft for missing glyphs. */
public final class StoryFonts {
    public record Preset(String key, String displayName, ResourceLocation resource) {}

    public static final List<Preset> PRESETS = List.of(
            preset("default", "原版剧情字体", "story"),
            preset("wenkai", "霞鹜文楷 Lite", "wenkai"),
            preset("xiaowei", "站酷小薇", "xiaowei"),
            preset("mashan", "马善政毛笔体", "mashan"));

    private StoryFonts() {}

    public static Preset find(String key) {
        return PRESETS.stream().filter(font -> font.key().equals(key)).findFirst().orElse(null);
    }

    private static Preset preset(String key, String name, String resource) {
        return new Preset(key, name, ResourceLocation.fromNamespaceAndPath(HallMod.MODID, resource));
    }
}
