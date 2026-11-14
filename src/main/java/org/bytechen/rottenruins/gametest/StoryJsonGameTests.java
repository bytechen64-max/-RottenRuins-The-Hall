package org.bytechen.rottenruins.gametest;

import org.bytechen.rottenruins.platform.minecraft.story.StoryPacketCodec;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.*;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.story.*;
import org.bytechen.rottenruins.platform.forge.story.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoryJsonGameTests {
    @GameTest(template = "potential_empty", batch = "story_json")
    public static void ritualSnapshotSurvivesSaveAndCatalogChange(GameTestHelper helper) {
        var definition = StoryCatalog.snapshot().stories().get(ResourceLocation.parse("rotten_ruins:fleshbed"));
        var snapshot = StoryPacketCodec.encode(definition, 1000, UUID.randomUUID());
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("Fed", 128);
        tag.putLong("StartTime", 1000);
        tag.putInt("RitualTicks", definition.durationTicks());
        tag.put("StorySnapshot", snapshot);
        var state = org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks.FLESHBED.get().defaultBlockState();
        var bed = new org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
        bed.load(tag);
        var saved = bed.saveWithFullMetadata();
        var restored = new org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
        restored.load(saved);
        helper.assertTrue(restored.endTime() == 1000 + definition.durationTicks(), "Saved ritual retains JSON duration");
        helper.assertTrue(restored.saveWithFullMetadata().getCompound("StorySnapshot").equals(snapshot), "Text/options/timestamps survive actual BE serialization");
        var original = StoryCatalog.snapshot();
        try {
            StoryCatalog.publish(new StoryCatalog.Snapshot(Map.of(), List.of()));
            helper.assertTrue(restored.endTime() == bed.endTime(), "Catalog replacement cannot retime running ritual");
            helper.assertTrue(restored.saveWithFullMetadata().getCompound("StorySnapshot").equals(snapshot), "Ritual playback does not depend on live catalog");
        } finally { StoryCatalog.publish(original); }
        saved.remove("RitualTicks");
        saved.remove("StorySnapshot");
        restored.load(saved);
        helper.assertTrue(restored.endTime() == 1800, "Pre-JSON saves retain legacy 800-tick ritual");
        helper.succeed();
    }

    private static final ResourceLocation ID = ResourceLocation.parse("test:nested/demo");
    private static final String STORY = """
            {"font":"wenkai","timing":"autoplay","shadow":false,"entry_speed":7,"placement":{"distance":2},
             "pages":[{"text":{"translate":"rotten_ruins.story.opening"},"hold_ticks":100},
                      {"text":{"text":"second"},"font":"mashan","exit_speed":12,"placement":{"angle":45}}]}
            """;
    private static final String RULE = """
            {"event":"test","delay_ticks":2,"cooldown_ticks":100,"once_key":"test:once",
             "conditions":{"required_flags":["test:ready"]},"choices":[{"story":"test:nested/demo"}]}
            """;

    @GameTest(template = "potential_empty", batch = "story_json")
    public static void overridesValidationAndAtomicReload(GameTestHelper helper) throws Exception {
        Path root = Files.createTempDirectory("rr-story-json-");
        var original = StoryCatalog.snapshot();
        try {
            Path low = root.resolve("low"), high = root.resolve("high");
            write(low, "stories/nested/demo.json", STORY.replace("\"hold_ticks\":100", "\"hold_ticks\":50"));
            write(high, "stories/nested/demo.json", STORY);
            write(high, "story_rules/demo.json", RULE);
            try (var resources = resources(low, high)) {
                reload(resources);
                var loaded = StoryCatalog.snapshot();
                helper.assertTrue(loaded.stories().get(ID).pages().get(0).holdTicks() == 100, "Higher pack wins exact nested path");
                var page = loaded.stories().get(ID).pages().get(1);
                helper.assertTrue(page.placement().distance() == 2 && page.placement().angle() == 45, "Page placement merges top-level defaults");
                helper.assertTrue(page.font().getPath().equals("mashan") && !page.shadow(), "Page options override inherited values");
                boolean immutable = false;
                try { loaded.stories().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                helper.assertTrue(immutable, "Published maps immutable");
            }
            for (String invalid : List.of("{broken", "{}", STORY.replace("\"entry_speed\":7", "\"entry_speed\":0"),
                    STORY.replace("\"shadow\":false", "\"shdaow\":false"), STORY.replace("\"hold_ticks\":100", "\"hold_ticks\":1.5"),
                    STORY.replace("\"autoplay\"", "\"clocked\""))) {
                write(high, "stories/nested/demo.json", invalid);
                var before = StoryCatalog.snapshot();
                boolean rejected = false;
                try (var resources = resources(low, high)) { reload(resources); }
                catch (Exception expected) {
                    rejected = true;
                    helper.assertTrue(expected.toString().contains("stories/nested/demo.json"), "Diagnostic includes source path");
                }
                helper.assertTrue(rejected && StoryCatalog.snapshot() == before, "Invalid reload preserves entire old snapshot");
            }
            write(high, "stories/nested/demo.json", STORY);
            write(high, "story_rules/demo.json", RULE.replace("test:nested/demo", "test:missing"));
            boolean missing = false;
            try (var resources = resources(low, high)) { StoryReloadListener.read(resources); }
            catch (IllegalArgumentException expected) { missing = true; }
            helper.assertTrue(missing, "Unknown cross-reference rejected");
            Files.delete(high.resolve("data/test/story_rules/demo.json"));
            write(high, "stories/nested/demo.json", "{\"enabled\":false}");
            try (var resources = resources(low, high)) {
                reload(resources);
                helper.assertTrue(StoryCatalog.snapshot().stories().isEmpty() && StoryCatalog.snapshot().rules().isEmpty(), "Disable/delete removes stale definitions and rules");
            }
            helper.succeed();
        } finally {
            StoryCatalog.publish(original);
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @GameTest(template = "potential_empty", batch = "story_json")
    public static void schedulingFlagsMemoryAndPacketOptions(GameTestHelper helper) {
        var original = StoryCatalog.snapshot();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "story-json"));
        new ServerGamePacketListenerImpl(player.server, new Connection(PacketFlow.SERVERBOUND), player);
        try {
            var story = StoryJsonLoader.story(ID, JsonParser.parseString(STORY).getAsJsonObject());
            var rule = StoryJsonLoader.rule(ResourceLocation.parse("test:rule"), JsonParser.parseString(RULE).getAsJsonObject());
            StoryCatalog.publish(new StoryCatalog.Snapshot(Map.of(ID, story), List.of(rule)));
            helper.assertTrue(!StoryDirector.trigger(player, "test", StoryDirector.context(player)), "Flags gate trigger");
            StoryDirector.flag(player, "test:ready", true);
            helper.assertTrue(StoryDirector.trigger(player, "test", StoryDirector.context(player)), "Enabled flag permits trigger");
            helper.assertTrue(!StoryDirector.trigger(player, "test", StoryDirector.context(player)), "Duplicate pending job blocked");
            StoryDirector.tick(player);
            helper.assertTrue(StoryDirector.pendingCount(player) == 1, "Delay counts player ticks");
            StoryCatalog.publish(new StoryCatalog.Snapshot(Map.of(), List.of()));
            StoryDirector.tick(player);
            helper.assertTrue(StoryDirector.previous(player, "test:rule").equals(ID.toString()), "Delayed job keeps definition across reload");
            StoryCatalog.publish(new StoryCatalog.Snapshot(Map.of(ID, story), List.of(rule)));
            helper.assertTrue(!StoryDirector.trigger(player, "test", StoryDirector.context(player)), "Consumed once key persists");
            var state = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            state.remove("test:once");
            helper.assertTrue(!StoryDirector.trigger(player, "test", StoryDirector.context(player)), "Cooldown still blocks after once flag cleared");
            var packet = StoryPacketCodec.encode(story, 100, UUID.randomUUID());
            var entries = packet.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
            helper.assertTrue(entries.size() == 2 && !entries.getCompound(0).getBoolean("shadow"), "Multi-page/options sent atomically");
            helper.assertTrue(entries.getCompound(0).getFloat("entry_speed") == 7 && entries.getCompound(1).getFloat("exit_speed") == 12, "Page speeds encoded");
            helper.assertTrue(entries.getCompound(0).getString("text").contains("\"translate\""), "Network preserves localization keys");
            var clocked = StoryJsonLoader.story(ID, JsonParser.parseString("""
                    {"timing":"clocked","pages":[{"text":"one","duration_ticks":80},{"text":"two","duration_ticks":120}]}
                    """).getAsJsonObject());
            var timeline = StoryPacketCodec.encode(clocked, 1000, UUID.randomUUID()).getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
            helper.assertTrue(clocked.durationTicks() == 200 && timeline.getCompound(1).getLong("startTime") == 1080,
                    "Unequal clocked page durations retain exact server timeline");
            var dispatcher = player.server.getCommands().getDispatcher();
            var source = player.createCommandSourceStack().withPermission(2);
            try {
                helper.assertTrue(dispatcher.execute("rotten_ruins story play @s test:nested/demo", source) == 1, "Play command accepts catalog ID");
                helper.assertTrue(dispatcher.execute("rotten_ruins story list", source) == 1, "List command reflects catalog");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) { throw new AssertionError(error); }
            helper.succeed();
        } finally { StoryDirector.cancel(player); StoryCatalog.publish(original); }
    }

    private static MultiPackResourceManager resources(Path low, Path high) {
        return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(new PathPackResources("low", low, false), new PathPackResources("high", high, false)));
    }
    private static void write(Path root, String path, String json) throws Exception {
        Path file = root.resolve("data/test/" + path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }
    private static void reload(ResourceManager resources) {
        new StoryReloadListener().reload(new PreparableReloadListener.PreparationBarrier() {
            public <T> CompletableFuture<T> wait(T prepared) { return CompletableFuture.completedFuture(prepared); }
        }, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
    }
}
