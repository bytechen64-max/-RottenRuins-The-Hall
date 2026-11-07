package org.bytechen.rottenruins.datagen.gen.corruption;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;

/**
 * 腐化群系（{@code rotten_ruins:corruption_wasteland}）。
 *
 * <p>由腐化方块扩散驱动：任一腐化扩散方块被放下时，对应方块柱的群系会被改成该群系
 * （逻辑与王庭 {@code hall_wasteland} 完全一致）。群系本身带腐化生物的自然生成表，
 * 召唤柱权重最低（相对较少）。</p>
 */
public class CorruptionBiomeData {

    public static final ResourceKey<Biome> CORRUPTION_WASTELAND = ResourceKey.create(
            Registries.BIOME, new ResourceLocation(HallMod.MODID, "corruption_wasteland"));

    public static void bootstrap(BootstapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> configuredCarvers = context.lookup(Registries.CONFIGURED_CARVER);
        context.register(CORRUPTION_WASTELAND, createBiome(placedFeatures, configuredCarvers));
    }

    public static Biome createBiome(HolderGetter<PlacedFeature> features,
                                    HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        MobSpawnSettings.Builder spawn = new MobSpawnSettings.Builder();

        // 同化种（基础尸群）
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_ZOMBIE.get(), 20, 1, 3));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_COW.get(), 15, 1, 2));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_PIG.get(), 15, 1, 2));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_SHEEP.get(), 15, 1, 3));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_CHICKEN.get(), 15, 2, 4));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_SKELETON.get(), 10, 1, 2));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_WOLF.get(), 8, 1, 2));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_BAT.get(), 8, 1, 3));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_VILLAGER.get(), 6, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.CORRUPTION_BONE_RUINS.get(), 3, 1, 1));

        // 腐化种
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.DEGENERATE_ZOMBIE.get(), 5, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.DEGENERATE_COW.get(), 5, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.DEGENERATE_PIG.get(), 5, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.DEGENERATE_VILLAGER.get(), 5, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.GROVELER.get(), 3, 1, 1));

        // 重构种
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.ATTACKER.get(), 2, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.BEARER.get(), 1, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.LONG_ARM_BEAST.get(), 1, 1, 1));
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.SHOCKER.get(), 1, 1, 1));

        // 联结种（功能性单位）
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.HUMUS_COLUMN.get(), 3, 1, 1));
        // 一阶召唤柱：权重最低（相对较少）
        spawn.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                CorruptionEntityTypes.SUMMONING_PILLAR.get(), 1, 1, 1));

        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(features, carvers);

        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .waterColor(0x5A1414)
                .waterFogColor(0x3A0A0A)
                .fogColor(0x2A0707)
                .skyColor(0x1A0404)
                .foliageColorOverride(0x5A1E1E)
                .grassColorOverride(0x6A2A2A)
                .ambientParticle(new AmbientParticleSettings(ParticleTypes.ASH, 0.01f))
                .build();

        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8F)
                .downfall(0.0F)
                .specialEffects(effects)
                .mobSpawnSettings(spawn.build())
                .generationSettings(generation.build())
                .build();
    }
}
