package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterFeature;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.HallDimensions;

/** 在真实维度生成区块，覆盖海盆填水、陆地保留及白天水生刷怪的回归。 */
@GameTestHolder("rotten_ruins_worldgen_tests")
@PrefixGameTestTemplate(false)
public final class HallOceanGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void oceanContainsWaterAndAllowsSquid(GameTestHelper helper) {
        ServerLevel level = hall(helper);
        BlockPos sample = findRegion(level, true);
        int seaLevel = level.getChunkSource().getGenerator().getSeaLevel();
        BlockPos water = new BlockPos(sample.getX(), seaLevel - 10, sample.getZ());
        helper.assertTrue(level.getFluidState(water).is(FluidTags.WATER), "Ocean basin must contain deep water");
        helper.assertTrue(level.getFluidState(water.atY(seaLevel - 1)).is(FluidTags.WATER), "Ocean must fill to sea level");
        helper.assertTrue(level.isEmptyBlock(water.atY(seaLevel)), "Water surface must open to the sky");
        BlockPos floor = water.atY(level.getHeight(Heightmap.Types.OCEAN_FLOOR,
                water.getX(), water.getZ()) - 1);
        helper.assertTrue(level.getBlockState(floor).is(RegisterBlock.HALL_ASH_SAND.get()),
                "Ocean floor must use Hall ash sand instead of vanilla gravel");
        helper.assertTrue(level.getBiome(water).unwrapKey().orElseThrow().location().getPath().equals("hall_ocean"),
                "Deep sea terrain must select the ocean biome");
        helper.assertTrue(level.getBiome(water).value().getMobSettings().getMobs(MobCategory.MONSTER)
                .unwrap().stream().anyMatch(spawn -> spawn.type == EntityTypeRegistry.ABNORMAL_SQUID.get()),
                "Loaded ocean biome must contain the squid spawn entry");
        Difficulty oldDifficulty = level.getDifficulty();
        // GameTest 默认世界为普通难度；自然生成规则应绕开恒正午的黑暗限制。
        helper.assertTrue(oldDifficulty != Difficulty.PEACEFUL, "Run aquatic spawning tests outside peaceful difficulty");
        helper.assertTrue(SpawnPlacements.getPlacementType(EntityTypeRegistry.ABNORMAL_SQUID.get())
                == SpawnPlacements.Type.IN_WATER, "Squid must use water placement");
        helper.assertTrue(SpawnPlacements.checkSpawnRules(EntityTypeRegistry.ABNORMAL_SQUID.get(), level,
                MobSpawnType.NATURAL, water, level.random), "Squid must pass daytime ocean spawn rules");
        helper.assertTrue(!SpawnPlacements.checkSpawnRules(EntityTypeRegistry.ABNORMAL_SQUID.get(), level,
                MobSpawnType.NATURAL, water.atY(seaLevel + 2), level.random), "Squid cannot spawn in air");
        var squid = EntityTypeRegistry.ABNORMAL_SQUID.get().create(level);
        helper.assertTrue(squid != null, "Squid entity must construct");
        squid.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5);
        helper.assertTrue(squid.checkSpawnObstruction(level), "Water must not fail the inherited land-mob obstruction check");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void inlandRemainsAboveSeaAndRejectsSquid(GameTestHelper helper) {
        ServerLevel level = hall(helper);
        BlockPos sample = findRegion(level, false);
        level.getChunkAt(sample);
        int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR, sample.getX(), sample.getZ());
        helper.assertTrue(surface > level.getChunkSource().getGenerator().getSeaLevel(), "Inland terrain must remain above the sea, got Y=" + surface);
        BlockPos pos = sample.atY(surface);
        helper.assertTrue(level.getBiome(pos).unwrapKey().orElseThrow().location().getPath().equals("flesh_marrow"),
                "Inland terrain must preserve the original biome");
        helper.assertTrue(!SpawnPlacements.checkSpawnRules(EntityTypeRegistry.ABNORMAL_SQUID.get(), level,
                MobSpawnType.NATURAL, pos, level.random), "Squid cannot naturally spawn on inland terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void treesRequireDrySupportAndLeaveNoPartialTrunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(3, 3, 3));
        // Keep all attempts at one cleared location; only the final valid attempt may mutate it.
        for (BlockPos pos : BlockPos.betweenClosed(root.offset(-6, -1, -6), root.offset(6, 12, 6))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        var tree = RegisterFeature.HALL_TREE.get();
        var config = NoneFeatureConfiguration.INSTANCE;
        var generator = level.getChunkSource().getGenerator();
        level.setBlock(root.below(), Blocks.WATER.defaultBlockState(), 2);
        helper.assertTrue(!tree.place(config, level, generator, RandomSource.create(1), root),
                "Water beneath an air block must not count as solid ground");
        helper.assertTrue(level.isEmptyBlock(root), "Rejected water-surface attempt must place no log");
        level.setBlock(root.below(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(!tree.place(config, level, generator, RandomSource.create(1), root),
                "Tree cannot float above air");
        level.setBlock(root.below(), RegisterBlock.HALL_GRASS_BLOCK.get().defaultBlockState(), 2);
        level.setBlock(root, Blocks.WATER.defaultBlockState(), 2);
        helper.assertTrue(!tree.place(config, level, generator, RandomSource.create(1), root),
                "Tree cannot start underwater");
        level.setBlock(root, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(root.above(3), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(!tree.place(config, level, generator, RandomSource.create(1), root),
                "Blocked trunk must reject the whole tree");
        helper.assertTrue(level.isEmptyBlock(root) && level.isEmptyBlock(root.above()),
                "Blocked attempt must not leave a partial trunk");
        level.setBlock(root.above(3), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(root.above(2), Blocks.WATER.defaultBlockState(), 2);
        helper.assertTrue(!tree.place(config, level, generator, RandomSource.create(1), root),
                "Tree cannot replace water further up its trunk");
        helper.assertTrue(level.isEmptyBlock(root), "Wet trunk attempt must place no log");
        level.setBlock(root.above(2), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(tree.place(config, level, generator, RandomSource.create(1), root),
                "Dry Hall ground must still generate a tree");
        helper.assertTrue(level.getBlockState(root).is(RegisterBlock.HALL_LOG.get()),
                "Valid dry-ground attempt must place the trunk");
        helper.succeed();
    }

    private static ServerLevel hall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(HallDimensions.HEALL_LEVEL);
        helper.assertTrue(level != null, "Bundled Hall dimension must load");
        return level;
    }

    private static BlockPos findRegion(ServerLevel level, boolean ocean) {
        var state = level.getChunkSource().randomState();
        var continents = state.router().continents();
        for (int x = -4096; x <= 4096; x += 128) {
            for (int z = -4096; z <= 4096; z += 128) {
                double c = continents.compute(new DensityFunction.SinglePointContext(x, 96, z));
                if (ocean ? c < -0.2 : c > 0.25) return new BlockPos(x, 96, z);
            }
        }
        throw new AssertionError("Could not find " + (ocean ? "ocean" : "inland") + " within 4096 blocks");
    }
}
