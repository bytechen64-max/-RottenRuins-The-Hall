package org.bytechen.rottenruins.overworld.registry.spawning;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.HallDimensions;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated.AbnormalSquidEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.util.RandomSource;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;

/**
 * 自然生成放置规则。
 *
 * <p>腐化生物需要在腐化群系（{@code rotten_ruins:corruption_wasteland}）里自然生成，
 * 这里给全部腐化生物注册 {@code ON_GROUND + MOTION_BLOCKING_NO_LEAVES} 的放置规则；
 * 生成表本身写在 {@code CorruptionBiomeData} 的 biome spawners 里。放置规则也核对群系，
 * 防止其它刷新表或运行时注入让它们在自然群系里自然生成。</p>
 */
public class SplendidingSpawnPlacements {

    private static final ResourceKey<Biome> CORRUPTION_WASTELAND = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "corruption_wasteland"));

    private static final ResourceKey<Biome> HALL_OCEAN = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_ocean"));

    public static void register() {
        SpawnPlacements.register(EntityTypeRegistry.ABNORMAL_SQUID.get(), SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SplendidingSpawnPlacements::checkAbnormalSquidSpawn);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_ZOMBIE);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_COW);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_PIG);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_SHEEP);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_CHICKEN);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_SKELETON);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_WOLF);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_BAT);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_VILLAGER);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_BONE_RUINS);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_ZOMBIE);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_COW);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_PIG);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_VILLAGER);
        registerCorruption(CorruptionEntityTypes.GROVELER);
        registerCorruption(CorruptionEntityTypes.ATTACKER);
        registerCorruption(CorruptionEntityTypes.BEARER);
        registerCorruption(CorruptionEntityTypes.LONG_ARM_BEAST);
        registerCorruption(CorruptionEntityTypes.SHOCKER);
        registerCorruption(CorruptionEntityTypes.HUMUS_COLUMN);
        registerCorruption(CorruptionEntityTypes.SUMMONING_PILLAR);
    }

    private static boolean checkAbnormalSquidSpawn(EntityType<AbnormalSquidEntity> type,
            ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        // 恒正午的王庭海洋允许水生敌怪生成，不使用陆生 Monster 的黑暗/脚下方块检查。
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if ((reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION)
                && (!level.getLevel().dimension().equals(HallDimensions.HEALL_LEVEL)
                || !level.getBiome(pos).is(HALL_OCEAN))) return false;
        return level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.above()).is(FluidTags.WATER)
                && level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static <T extends Monster> void registerCorruption(RegistryObject<EntityType<T>> type) {
        SpawnPlacements.register(type.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SplendidingSpawnPlacements::checkCorruptionSpawn);
    }

    private static <T extends Monster> boolean checkCorruptionSpawn(EntityType<T> type,
            ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if ((reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION)
                && !level.getBiome(pos).is(CORRUPTION_WASTELAND)) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }
}
