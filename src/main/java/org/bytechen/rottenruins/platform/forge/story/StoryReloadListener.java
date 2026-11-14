package org.bytechen.rottenruins.platform.forge.story;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.core.story.StoryRule;
import org.bytechen.rottenruins.gameplay.story.*;
import java.util.*;
import java.util.function.BiConsumer;

@Mod.EventBusSubscriber(modid = HallMod.MODID)
public final class StoryReloadListener extends SimplePreparableReloadListener<StoryCatalog.Snapshot> {
    @SubscribeEvent public static void register(AddReloadListenerEvent event) { event.addListener(new StoryReloadListener()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { StoryCatalog.clear(); StoryDirector.clearAll(); }
    @Override protected StoryCatalog.Snapshot prepare(ResourceManager resources, ProfilerFiller profiler) { return read(resources); }
    @Override protected void apply(StoryCatalog.Snapshot snapshot, ResourceManager resources, ProfilerFiller profiler) {
        StoryCatalog.publish(snapshot);
        HallMod.LOGGER.info("Story catalog reloaded: {} stories, {} rules", snapshot.stories().size(), snapshot.rules().size());
    }
    public static StoryCatalog.Snapshot read(ResourceManager resources) {
        Map<ResourceLocation, StoryDefinition> stories = new HashMap<>();
        List<StoryRule> rules = new ArrayList<>();
        read(resources, "stories", (id, json) -> {
            var story = StoryJsonLoader.story(id, json);
            if (story != null) stories.put(id, story);
        });
        read(resources, "story_rules", (id, json) -> {
            var rule = StoryJsonLoader.rule(id, json);
            if (rule != null) rules.add(rule);
        });
        for (var rule : rules) for (var choice : rule.choices()) {
            StoryDefinition target = stories.get(ResourceLocation.parse(choice.story()));
            String problem = target == null ? "choices references missing/disabled story " + choice.story()
                    : rule.event().equals("fleshbed_start") && (target.timing() != StoryDefinition.Timing.CLOCKED || rule.delayTicks() != 0)
                    ? "fleshbed_start requires a clocked story and delay_ticks=0" : null;
            if (problem != null) {
                var ruleId = ResourceLocation.parse(rule.id());
                var source = ResourceLocation.fromNamespaceAndPath(ruleId.getNamespace(), "story_rules/" + ruleId.getPath() + ".json");
                String pack = resources.getResource(source).map(resource -> resource.sourcePackId()).orElse("unknown");
                throw new IllegalArgumentException("Invalid story JSON: data/" + source.getNamespace() + "/" + source.getPath()
                        + " (pack " + pack + "): " + problem);
            }
        }
        return new StoryCatalog.Snapshot(stories, rules);
    }
    private static void read(ResourceManager resources, String folder, BiConsumer<ResourceLocation, com.google.gson.JsonObject> consume) {
        var files = resources.listResources(folder, id -> id.getPath().endsWith(".json"));
        for (var file : files.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).toList()) {
            String path = file.getKey().getPath();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getKey().getNamespace(), path.substring(folder.length() + 1, path.length() - 5));
            try (var reader = file.getValue().openAsReader()) {
                consume.accept(id, StoryJsonLoader.object(JsonParser.parseReader(reader), "document"));
            } catch (Exception error) {
                throw new IllegalArgumentException("Invalid story JSON: data/" + file.getKey().getNamespace() + "/" + path
                        + " (pack " + file.getValue().sourcePackId() + "): " + error.getMessage(), error);
            }
        }
    }
}
