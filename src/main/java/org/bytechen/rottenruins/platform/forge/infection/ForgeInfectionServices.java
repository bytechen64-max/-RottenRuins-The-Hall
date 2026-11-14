package org.bytechen.rottenruins.platform.forge.infection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.api.infection.event.BlockSpreadEvent;
import org.bytechen.rottenruins.api.infection.event.EntityEvolveEvent;
import org.bytechen.rottenruins.gameplay.infection.InfectionRuntime;
import org.bytechen.rottenruins.platform.forge.infection.difficulty.GlobalDifficultyData;

/** Registry resolution, event delivery and persistent difficulty for the Forge runtime. */
public final class ForgeInfectionServices implements InfectionRuntime.Services {
    private static final String DATA_NAME = "infcore_global_difficulty";
    @Override public ResourceLocation entityId(EntityType<?> type) { return ForgeRegistries.ENTITY_TYPES.getKey(type); }
    @Override public EntityType<?> entity(ResourceLocation id) { return ForgeRegistries.ENTITY_TYPES.getValue(id); }
    @Override public ResourceLocation blockId(Block block) { return ForgeRegistries.BLOCKS.getKey(block); }
    @Override public Block block(ResourceLocation id) { return ForgeRegistries.BLOCKS.getValue(id); }
    @Override public boolean beforeSpread(BlockSpreadEvent.Pre event) { return MinecraftForge.EVENT_BUS.post(event); }
    @Override public void afterSpread(BlockSpreadEvent.Post event) { MinecraftForge.EVENT_BUS.post(event); }
    @Override public void afterEvolution(EntityEvolveEvent event) { MinecraftForge.EVENT_BUS.post(event); }
    private static GlobalDifficultyData data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                GlobalDifficultyData::load, GlobalDifficultyData::new, DATA_NAME);
    }
    @Override public ResourceLocation difficulty(ServerLevel level) { return data(level).getCurrentDifficulty(); }
    @Override public boolean changeDifficulty(ServerLevel level, ResourceLocation difficulty) {
        return data(level).setDifficulty(level.getServer().overworld(), difficulty);
    }
}
