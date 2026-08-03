package org.bytechen.hall.overworld.swarm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.bytechen.hall.HallMod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Hall 方块快照磁盘存储。
 * <p>
 * 使用自定义二进制格式按区块存储方块快照，支持高效读写。
 * 格式：调色板 + 压缩坐标索引。
 */
public class HallDiskStorage {

    private final Path storageDir;

    public HallDiskStorage(Path dimPath) {
        this.storageDir = dimPath.resolve("snapshots");
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            HallMod.LOGGER.error("[HallDisk] 无法创建存储目录: {}", storageDir, e);
        }
    }

    private File getFileForChunk(ChunkPos pos) {
        return storageDir.resolve(pos.x + "." + pos.z + ".hdat").toFile();
    }

    /**
     * 将整个区块的快照批量写入二进制文件。
     * <p>
     * 格式：
     * <pre>
     * [Int: 调色板大小] -> [String(UTF): 调色板项...] -> [Int: 数据量] ->
     *  循环 [Short: packedPos (x4|y12|z4), Short: paletteIndex]
     * </pre>
     */
    public void writeChunk(ChunkPos pos, Map<BlockPos, String> snapshots) {
        File file = getFileForChunk(pos);
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            // 构建调色板
            List<String> palette = new ArrayList<>(new HashSet<>(snapshots.values()));
            out.writeInt(palette.size());
            for (String s : palette) {
                out.writeUTF(s);
            }

            // 写入数据
            out.writeInt(snapshots.size());
            for (Map.Entry<BlockPos, String> entry : snapshots.entrySet()) {
                BlockPos p = entry.getKey();
                // 压缩坐标：x(4bits) | y(12bits) | z(4bits)
                short packedPos = (short) (((p.getX() & 15) << 12)
                        | ((p.getY() & 4095) << 4)
                        | (p.getZ() & 15));
                out.writeShort(packedPos);
                out.writeShort(palette.indexOf(entry.getValue()));
            }
        } catch (IOException e) {
            HallMod.LOGGER.error("[HallDisk] 写入区块 {} 失败", pos, e);
        }
    }

    /**
     * 从二进制文件读取区块快照。
     */
    public Map<BlockPos, String> readChunk(ChunkPos pos) {
        File file = getFileForChunk(pos);
        if (!file.exists()) return Collections.emptyMap();

        Map<BlockPos, String> result = new HashMap<>();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            int paletteSize = in.readInt();
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = in.readUTF();
            }

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                short packed = in.readShort();
                int paletteIdx = in.readShort();

                int x = (packed >> 12) & 15;
                int y = (packed >> 4) & 4095;
                int z = packed & 15;

                BlockPos worldPos = new BlockPos(
                        pos.getMinBlockX() + x,
                        y,
                        pos.getMinBlockZ() + z
                );

                if (paletteIdx >= 0 && paletteIdx < palette.length) {
                    result.put(worldPos, palette[paletteIdx]);
                }
            }
        } catch (IOException e) {
            HallMod.LOGGER.error("[HallDisk] 读取区块 {} 失败", pos, e);
        }
        return result;
    }

    /**
     * 删除指定区块的快照文件。
     */
    public void deleteChunk(ChunkPos pos) {
        File file = getFileForChunk(pos);
        if (file.exists()) {
            file.delete();
        }
    }
}
