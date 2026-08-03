package org.bytechen.hall.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.infcore.core.evolution.EvolutionEntry;

import java.util.ArrayList;
import java.util.List;

public class EvolutionDataProvider extends org.bytechen.infcore.core.datagen.EvolutionDataProvider {
    public EvolutionDataProvider(PackOutput packOutput) {
        super(packOutput, HallMod.MODID, "default");
    }

    @Override
    public List<EvolutionEntry> buildEntries() {
        List<EvolutionEntry> entries = new ArrayList<>();

        entries.add(entry("inf", EntityType.PLAYER, false, false,
                List.of(target(EntityTypeRegistry.INF_PLAYER.get(), 100))));

        return entries;
    }
}
