package org.bytechen.rottenruins.platform.forge.infection;

import org.bytechen.rottenruins.utils.logging.InfectionLog;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.core.infection.blockspread.*;
import org.bytechen.rottenruins.core.infection.evolution.*;
import org.bytechen.rottenruins.core.infection.rules.InfectionRules;
import org.bytechen.rottenruins.core.infection.rules.RuleIndex;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** The Minecraft/Forge boundary: resource discovery, JSON validation, reload lifecycle. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectionRuleReloadListener extends SimplePreparableReloadListener<InfectionRules.Snapshot> {
    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new InfectionRuleReloadListener());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        InfectionRules.clear();
    }

    @Override
    protected InfectionRules.Snapshot prepare(ResourceManager resources, ProfilerFiller profiler) {
        return read(resources);
    }

    @Override
    protected void apply(InfectionRules.Snapshot rules, ResourceManager resources, ProfilerFiller profiler) {
        InfectionRules.publish(rules);
        InfectionRules.setReloadAction(InfectionRuleReloadListener::reloadCurrentServer);
        InfectionLog.LOGGER.info("Infection rules reloaded: {} evolution and {} block spread entries",
                rules.evolution().size(), rules.spread().size());
    }

    /** Builds an isolated snapshot. Failure never changes the active rules. */
    public static InfectionRules.Snapshot read(ResourceManager resources) {
        RuleIndex.Builder<EvolutionEntry> evolution = new RuleIndex.Builder<>();
        readDirectory(resources, "infcore_evolution", EvolutionData.class, (id, data) -> {
            require(data.entries() != null, "missing entries array");
            for (EvolutionEntry entry : data.entries()) {
                require(entry != null, "null evolution entry");
                String type = id(entry.type());
                String source = id(entry.source());
                require(entry.results() != null, "missing results for " + source);
                List<EvolutionTarget> targets = new ArrayList<>();
                long total = 0;
                for (EvolutionTarget target : entry.results()) {
                    require(target != null && target.weight() >= 0, "invalid target or negative weight for " + source);
                    targets.add(new EvolutionTarget(id(target.target()), target.weight()));
                    total += target.weight();
                }
                require(total <= Integer.MAX_VALUE, "weight total overflows for " + source);
                evolution.put(type, source, new EvolutionEntry(type, source,
                        entry.keepEquipment(), entry.keepNbt(), targets));
            }
        });

        RuleIndex.Builder<BlockSpreadEntry> spread = new RuleIndex.Builder<>();
        readDirectory(resources, "infcore_blockspread", BlockSpreadData.class, (id, data) -> {
            require(data.entries() != null, "missing entries array");
            for (BlockSpreadEntry entry : data.entries()) {
                require(entry != null, "null block spread entry");
                String type = id(entry.type());
                String source = id(entry.source());
                require(entry.results() != null, "missing results for " + source);
                List<BlockSpreadTarget> targets = new ArrayList<>();
                long total = 0;
                for (BlockSpreadTarget target : entry.results()) {
                    require(target != null && target.weight() >= 0, "invalid target or negative weight for " + source);
                    targets.add(new BlockSpreadTarget(id(target.target()), target.weight()));
                    total += target.weight();
                }
                require(total <= Integer.MAX_VALUE, "weight total overflows for " + source);
                spread.put(type, source, new BlockSpreadEntry(type, source, entry.dropResources(), targets));
            }
        });
        return new InfectionRules.Snapshot(evolution.build(), spread.build());
    }

    private static <T> void readDirectory(ResourceManager resources, String folder, Class<T> dataType,
                                           BiConsumer<ResourceLocation, T> consume) {
        // ResourceManager selects the highest-priority pack for each exact resource path.
        // Different paths are applied in lexical ID order, making duplicate rule keys deterministic.
        List<Map.Entry<ResourceLocation, Resource>> files = new ArrayList<>(resources
                .listResources(folder, path -> path.getPath().endsWith(".json")).entrySet());
        files.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (var file : files) {
            try (Reader reader = file.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader);
                require(json.isJsonObject() && json.getAsJsonObject().has("entries")
                        && json.getAsJsonObject().get("entries").isJsonArray(), "expected an entries array");
                T data = GSON.fromJson(json, dataType);
                require(data != null, "empty document");
                consume.accept(file.getKey(), data);
            } catch (Exception error) {
                String location = "data/" + file.getKey().getNamespace() + "/" + file.getKey().getPath();
                String message = "Invalid infection rules in " + location + " (pack "
                        + file.getValue().sourcePackId() + "): " + error.getMessage();
                InfectionLog.LOGGER.error(message);
                throw new IllegalArgumentException(message, error);
            }
        }
    }

    private static String id(String value) {
        ResourceLocation parsed = value == null ? null : ResourceLocation.tryParse(value);
        require(parsed != null, "invalid resource ID: " + value);
        return parsed.toString();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    /** Retained for the old managers' forceReload entry points. Call on the server thread. */
    public static void reloadCurrentServer() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Reload infection rules on the running server thread");
        }
        InfectionRules.publish(read(server.getResourceManager()));
    }
}
