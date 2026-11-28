package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.GroundingStakeBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceWireBlock;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

/** 用 runGameTestServer 验证真实世界中的支撑、台阶、通断及种子分区。 */
@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class PotentialGameTests {
    @GameTest(template = "potential_empty")
    public static void stakeSupports(GameTestHelper helper) {
        var level = helper.getLevel();
        var stake = RegisterBlock.GROUNDING_STAKE.get();
        int i = 0;
        for (Direction facing : Direction.values()) {
            BlockPos pos = helper.absolutePos(new BlockPos(3 + i++ * 4, 3, 4));
            BlockPos support = pos.relative(facing.getOpposite());
            var state = stake.defaultBlockState().setValue(GroundingStakeBlock.FACING, facing);
            helper.assertTrue(!state.canSurvive(level, pos), "Unsupported stake must be rejected: " + facing);
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            helper.assertTrue(state.canSurvive(level, pos), "Supported stake must survive: " + facing);
            level.setBlockAndUpdate(pos, state);
            BlockPos stacked = pos.relative(facing);
            helper.assertTrue(!state.canSurvive(level, stacked), "Stakes must not support stacked stakes");
            level.removeBlock(support, false);
            helper.assertTrue(level.getBlockState(pos).isAir(), "Removing support must remove stake: " + facing);
        }
        helper.succeed();
    }

    @GameTest(template = "potential_empty")
    public static void wireSteps(GameTestHelper helper) {
        var level = helper.getLevel();
        var wire = RegisterBlock.SOURCE_WIRE.get();
        BlockPos low = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos high = low.east().above();
        level.setBlockAndUpdate(low.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(high.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(low, wire.defaultBlockState());
        level.setBlockAndUpdate(high, wire.defaultBlockState());
        helper.assertTrue(high.equals(wire.wireNeighbor(level, low, Direction.EAST)), "Wire must climb one block");
        helper.assertTrue(low.equals(wire.wireNeighbor(level, high, Direction.WEST)), "Step connection must be reciprocal");
        level.setBlockAndUpdate(low.above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(wire.wireNeighbor(level, low, Direction.EAST) == null, "Ceiling must block climbing wire");
        level.removeBlock(low.below(), false);
        helper.assertTrue(level.getBlockState(low).isAir(), "Wire must drop when support disappears");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", timeoutTicks = 60)
    public static void highLowCircuit(GameTestHelper helper) {
        var level = helper.getLevel();
        var config = ConfigHelper.configHolder.get();
        int originalRarity = config.highPotentialChunkRarity;
        BlockPos left = null;
        int testRarity = 0;
        // 选择测试场地内跨越高/低势区的相邻区块，不依赖测试世界的种子。
        for (int rarity = 2; rarity <= 64 && left == null; rarity++) {
            for (int x = 4; x < 27; x++) {
                BlockPos candidate = helper.absolutePos(new BlockPos(x, 2, 16));
                BlockPos end = candidate.east(4);
                if (HighPotentialChunks.isHighPotentialChunk(level.getSeed(), level.dimension(), candidate.getX() >> 4, candidate.getZ() >> 4, rarity)
                        != HighPotentialChunks.isHighPotentialChunk(level.getSeed(), level.dimension(), end.getX() >> 4, end.getZ() >> 4, rarity)) {
                    left = candidate;
                    testRarity = rarity;
                    break;
                }
            }
        }
        helper.assertTrue(left != null, "Test arena must contain a high/low boundary");
        config.highPotentialChunkRarity = testRarity;
        BlockPos start = left;
        var wire = RegisterBlock.SOURCE_WIRE.get();
        var stake = RegisterBlock.GROUNDING_STAKE.get().defaultBlockState().setValue(GroundingStakeBlock.FACING, Direction.UP);
        try {
            for (int i = 0; i <= 4; i++) {
                BlockPos pos = start.east(i);
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos, i == 0 || i == 4 ? stake : wire.defaultBlockState());
            }
        } catch (RuntimeException error) {
            config.highPotentialChunkRarity = originalRarity;
            throw error;
        }
        helper.runAfterDelay(5, () -> {
            try {
                for (int i = 1; i <= 3; i++) helper.assertTrue(
                        level.getBlockState(start.east(i)).getValue(SourceWireBlock.POWER) > 0, "Connected high/low circuit must activate");
                level.removeBlock(start.east(2), false);
            } catch (RuntimeException error) {
                config.highPotentialChunkRarity = originalRarity;
                throw error;
            }
        });
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(level.getBlockState(start.east()).getValue(SourceWireBlock.POWER) == 0, "Disconnected left half must turn off");
                helper.assertTrue(level.getBlockState(start.east(3)).getValue(SourceWireBlock.POWER) == 0, "Disconnected right half must turn off");
            } finally {
                config.highPotentialChunkRarity = originalRarity;
            }
            // succeed 会立即启动下一批测试，必须先恢复共享配置。
            helper.succeed();
        });
    }
}
