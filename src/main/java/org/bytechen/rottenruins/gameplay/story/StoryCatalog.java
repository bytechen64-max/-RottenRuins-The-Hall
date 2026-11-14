package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.core.story.StoryRule;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** One atomic snapshot for stories and rules; readers never observe a partially reloaded catalog. */
public final class StoryCatalog {
    public record Snapshot(Map<ResourceLocation, StoryDefinition> stories, List<StoryRule> rules) {
        public Snapshot {
            stories = Map.copyOf(stories);
            rules = rules.stream().sorted(Comparator.comparingInt(StoryRule::priority).reversed()
                    .thenComparing(StoryRule::id)).toList();
        }
    }
    private static volatile Snapshot snapshot = new Snapshot(Map.of(), List.of());
    private StoryCatalog() {}
    public static Snapshot snapshot() { return snapshot; }
    public static void publish(Snapshot next) { snapshot = next; }
    public static void clear() { snapshot = new Snapshot(Map.of(), List.of()); }
}
