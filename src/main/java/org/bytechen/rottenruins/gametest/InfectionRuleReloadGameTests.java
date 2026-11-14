package org.bytechen.rottenruins.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.infection.blockspread.BlockSpreadManager;
import org.bytechen.rottenruins.gameplay.infection.evolution.EvolutionManager;
import org.bytechen.rottenruins.core.infection.rules.InfectionRules;
import org.bytechen.rottenruins.platform.forge.infection.InfectionRuleReloadListener;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class InfectionRuleReloadGameTests {
    private static final ResourceLocation TEST_TYPE = ResourceLocation.parse("rotten_ruins:reload_test");

    @GameTest(template = "potential_empty", batch = "infection_resources")
    public static void packPriorityNestedPathsAndImmutableSnapshots(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("rr-rule-priority-");
        try {
            Path low = root.resolve("low");
            Path high = root.resolve("high");
            write(low, "data/test/infcore_evolution/default.json", evolution("minecraft:pig"));
            write(high, "data/test/infcore_evolution/default.json", evolution("minecraft:sheep"));
            write(high, "data/test/infcore_blockspread/nested/rule.json", spread("minecraft:gold_block"));
            try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(
                    new PathPackResources("low", low, false), new PathPackResources("high", high, false)))) {
                var snapshot = InfectionRuleReloadListener.read(resources);
                var entry = snapshot.evolution().get(TEST_TYPE.toString(), "minecraft:cow");
                helper.assertTrue("minecraft:sheep".equals(entry.results().get(0).target()),
                        "The higher-priority pack must replace the same resource path");
                helper.assertTrue(snapshot.spread().size() == 1, "Discover nested rule paths");
                var builder = snapshot.evolution().toBuilder();
                builder.put("test:new", "minecraft:cow", entry);
                helper.assertTrue(snapshot.evolution().size() == 1 && builder.build().size() == 2,
                        "Building changes must not mutate the active snapshot");
                boolean immutable = false;
                try { entry.results().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                helper.assertTrue(immutable, "Rule target lists must be immutable");
            }
            helper.succeed();
        } finally {
            deleteFixture(root, root);
        }
    }

    @GameTest(template = "potential_empty", batch = "infection_resources")
    public static void malformedFilesRetainBothActiveTables(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("rr-rule-errors-");
        var original = InfectionRules.current();
        try {
            write(root, "data/test/infcore_evolution/default.json", evolution("minecraft:pig"));
            List<String> invalidFiles = List.of("{broken", "{}",
                    "{\"entries\":[null]}",
                    spread("INVALID ID"),
                    spread("minecraft:stone").replace("\"weight\":1", "\"weight\":-1"),
                    "{\"entries\":[{\"type\":\"test:type\",\"source\":\"minecraft:stone\",\"results\":["
                            + "{\"target\":\"minecraft:stone\",\"weight\":2147483647},"
                            + "{\"target\":\"minecraft:stone\",\"weight\":1}]}]}");
            for (String invalid : invalidFiles) {
                write(root, "data/test/infcore_blockspread/bad.json", invalid);
                try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                        List.of(new PathPackResources("bad-test-pack", root, false)))) {
                    boolean failed = false;
                    try {
                        new InfectionRuleReloadListener().reload(new PreparableReloadListener.PreparationBarrier() {
                            @Override
                            public <T> CompletableFuture<T> wait(T prepared) {
                                return CompletableFuture.completedFuture(prepared);
                            }
                        }, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE,
                                Runnable::run, Runnable::run).join();
                    } catch (CompletionException expected) {
                        failed = expected.getCause().getMessage().contains("data/test/infcore_blockspread/bad.json");
                    }
                    helper.assertTrue(failed, "Reject invalid rules with the file's resource path");
                    helper.assertTrue(InfectionRules.current() == original,
                            "A bad spread file must not publish even the valid evolution table");
                }
            }
            helper.succeed();
        } finally {
            InfectionRules.publish(original);
            deleteFixture(root, root);
        }
    }

    @GameTest(template = "potential_empty", batch = "infection_resources", timeoutTicks = 1200)
    public static void worldPackReloadEditsRemovalsAndFailureRecovery(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        Path packs = server.getWorldPath(LevelResource.DATAPACK_DIR);
        Files.createDirectories(packs);
        Path fixture = Files.createTempDirectory(packs, "rr-rules-test-");
        List<String> originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        int evolutionCount = EvolutionManager.getEntryCount();
        int spreadCount = BlockSpreadManager.getEntryCount();
        var hallType = ResourceLocation.parse("rotten_ruins:hall_inf");
        var fallbacks = EvolutionManager.getFallbacks(hallType);
        // Preserve built-in entries while overriding their exact resource paths.
        JsonObject evolutionBase = readBuiltin(server.getResourceManager(), "infcore_evolution/default.json");
        JsonObject spreadBase = readBuiltin(server.getResourceManager(), "infcore_blockspread/default.json");
        try {
            write(fixture, "pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"Temporary rule reload test\"}}");
            writeOverride(fixture, "infcore_evolution/default.json", evolutionBase, evolution("minecraft:pig"));
            writeOverride(fixture, "infcore_blockspread/default.json", spreadBase, spread("minecraft:gold_block"));
            server.getPackRepository().reload();
            List<String> selected = new ArrayList<>(originalPacks);
            selected.add("file/" + fixture.getFileName());
            server.reloadResources(selected).join();
            assertTargets(helper, "minecraft:pig", "minecraft:gold_block");

            writeOverride(fixture, "infcore_evolution/default.json", evolutionBase, evolution("minecraft:sheep"));
            writeOverride(fixture, "infcore_blockspread/default.json", spreadBase, spread("minecraft:diamond_block"));
            server.reloadResources(selected).join();
            assertTargets(helper, "minecraft:sheep", "minecraft:diamond_block");
            helper.assertTrue(fallbacks.equals(EvolutionManager.getFallbacks(hallType)),
                    "Resource reload must preserve code-registered fallback tiers");

            var successful = InfectionRules.current();
            writeOverride(fixture, "infcore_evolution/default.json", evolutionBase, evolution("minecraft:rabbit"));
            write(fixture, "data/rotten_ruins/infcore_blockspread/default.json", "{broken");
            boolean failed = false;
            try { server.reloadResources(selected).join(); } catch (CompletionException expected) { failed = true; }
            helper.assertTrue(failed && successful == InfectionRules.current(),
                    "Failed live reload must retain both previously active tables");
            assertTargets(helper, "minecraft:sheep", "minecraft:diamond_block");

            Files.delete(fixture.resolve("data/rotten_ruins/infcore_evolution/default.json"));
            Files.delete(fixture.resolve("data/rotten_ruins/infcore_blockspread/default.json"));
            server.reloadResources(selected).join();
            helper.assertTrue(!EvolutionManager.hasEvolveRule(EntityType.COW, TEST_TYPE)
                            && !BlockSpreadManager.hasSpreadRule(Blocks.STONE, TEST_TYPE),
                    "Deleting rule files from an enabled pack must remove their cached entries");
            helper.assertTrue(evolutionCount == EvolutionManager.getEntryCount()
                            && spreadCount == BlockSpreadManager.getEntryCount(),
                    "Deleting overrides must reveal the original built-in files again");
        } finally {
            server.reloadResources(originalPacks).join();
            deleteFixture(fixture, packs);
            server.getPackRepository().reload();
        }
        helper.assertTrue(!EvolutionManager.hasEvolveRule(EntityType.COW, TEST_TYPE)
                        && !BlockSpreadManager.hasSpreadRule(Blocks.STONE, TEST_TYPE),
                "Disabling the pack must remove its rules, not leave stale cache entries");
        helper.assertTrue(evolutionCount == EvolutionManager.getEntryCount()
                        && spreadCount == BlockSpreadManager.getEntryCount(), "Restore all built-in rules");
        EvolutionManager.forceReload();
        helper.assertTrue(evolutionCount == EvolutionManager.getEntryCount(), "Keep the forceReload compatibility entry point");
        helper.succeed();
    }

    private static void assertTargets(GameTestHelper helper, String entity, String block) {
        helper.assertTrue(entity.equals(EvolutionManager.getEvolutionTargets(EntityType.COW, TEST_TYPE).get(0).target()),
                "Queries must see the new evolution rule after server reload");
        helper.assertTrue(block.equals(BlockSpreadManager.getSpreadTargets(Blocks.STONE, TEST_TYPE).get(0).target()),
                "Queries must see the new spread rule after server reload");
    }

    private static JsonObject readBuiltin(net.minecraft.server.packs.resources.ResourceManager resources,
                                           String path) throws IOException {
        try (Reader reader = resources.getResourceOrThrow(ResourceLocation.parse("rotten_ruins:" + path)).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void writeOverride(Path root, String path, JsonObject base, String addition) throws IOException {
        JsonObject copy = base.deepCopy();
        JsonArray entries = copy.getAsJsonArray("entries");
        entries.add(JsonParser.parseString(addition).getAsJsonObject().getAsJsonArray("entries").get(0));
        write(root, "data/rotten_ruins/" + path, copy.toString());
    }

    private static String evolution(String target) {
        return "{\"replace\":true,\"entries\":[{\"type\":\"" + TEST_TYPE
                + "\",\"source\":\"minecraft:cow\",\"keepNbt\":true,\"keepEquipment\":true,"
                + "\"results\":[{\"target\":\"" + target + "\",\"weight\":1}]}]}";
    }

    private static String spread(String target) {
        return "{\"entries\":[{\"type\":\"" + TEST_TYPE
                + "\",\"source\":\"minecraft:stone\",\"dropResources\":true,"
                + "\"results\":[{\"target\":\"" + target + "\",\"weight\":1}]}]}";
    }

    private static void write(Path root, String name, String text) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static void deleteFixture(Path fixture, Path allowedRoot) throws IOException {
        if (!fixture.toAbsolutePath().normalize().startsWith(allowedRoot.toAbsolutePath().normalize())) {
            throw new IOException("Fixture is outside the expected test directory");
        }
        try (var paths = Files.walk(fixture)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
