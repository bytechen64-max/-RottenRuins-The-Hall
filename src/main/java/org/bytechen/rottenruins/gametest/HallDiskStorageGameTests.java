package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.swarm.HallDiskStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class HallDiskStorageGameTests {
    @GameTest(template = "potential_empty", batch = "hall_storage")
    public static void snapshotCoordinatesRoundTrip(GameTestHelper helper) throws IOException {
        Path dir = Files.createTempDirectory("hall-snapshot-test-");
        ChunkPos chunk = new ChunkPos(-3, 4);
        BlockPos low = new BlockPos(-47, -32, 65);
        BlockPos high = new BlockPos(-35, 320, 79);
        try {
            HallDiskStorage storage = new HallDiskStorage(dir);
            Map<BlockPos, String> expected = Map.of(
                    low, "minecraft:oak_stairs",
                    high, "minecraft:stone");
            storage.writeChunk(chunk, expected);
            Map<BlockPos, String> actual = new HallDiskStorage(dir).readChunk(chunk);
            helper.assertTrue(expected.equals(actual), "Snapshot must preserve local X/Z and signed Y");
            helper.succeed();
        } finally {
            Files.deleteIfExists(dir.resolve("snapshots").resolve("-3.4.hdat"));
            Files.deleteIfExists(dir.resolve("snapshots"));
            Files.deleteIfExists(dir);
        }
    }
}
