package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Validated, immutable gameplay declaration; text remains serialized Component data until playback. */
public record StoryDefinition(ResourceLocation id, Timing timing, Conflict conflict, int priority, List<Page> pages) {
    public StoryDefinition { pages = List.copyOf(pages); }
    public enum Timing { READABLE, AUTOPLAY, CLOCKED }
    public enum Conflict { REPLACE, QUEUE, IGNORE }
    public record Placement(float angle, double distance, double height) {}
    public record Page(String textJson, ResourceLocation font, int holdTicks, int durationTicks,
                       Placement placement, Boolean shadow, Float entrySpeed, Float exitSpeed, boolean recaptureAnchor) {}
    public int durationTicks() { return timing == Timing.CLOCKED ? pages.stream().mapToInt(Page::durationTicks).sum() : 0; }
}
