package org.bytechen.rottenruins.overworld.swarm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.bytechen.rottenruins.HallMod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.*;

/**
 * Hall 方块快照磁盘存储。
 * <p>
 * 使用自定义二进制格式按区块存储方块快照，支持高效读写。
 * 格式：调色板 + 压缩坐标索引。
 */
public class HallDiskStorage {

    private static final int FORMAT_V2 = 0x48445332; // HDS2

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
     * [Int: HDS2] [Int: 调色板大小] [UTF: 调色板项...] [Int: 数据量]
     * 循环 [Byte: localX] [Int: y] [Byte: localZ] [Int: paletteIndex]。
     * 旧格式把 20 位坐标压进 short，读取时仅作兼容；新写入不再丢失 X 或负 Y。
     * </pre>
     */
    public void writeChunk(ChunkPos pos, Map<BlockPos, String> snapshots) {
        File file = getFileForChunk(pos);
        Path target = file.toPath();
        Path temporary = target.resolveSibling(file.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temporary.toFile())))) {

            List<String> palette = new ArrayList<>(new LinkedHashSet<>(snapshots.values()));
            Map<String, Integer> paletteIndex = new HashMap<>();
            for (int i = 0; i < palette.size(); i++) paletteIndex.put(palette.get(i), i);
            out.writeInt(FORMAT_V2);
            out.writeInt(palette.size());
            for (String s : palette) {
                out.writeUTF(s);
            }

            // 写入数据
            out.writeInt(snapshots.size());
            for (Map.Entry<BlockPos, String> entry : snapshots.entrySet()) {
                BlockPos p = entry.getKey();
                out.writeByte(p.getX() & 15);
                out.writeInt(p.getY());
                out.writeByte(p.getZ() & 15);
                out.writeInt(paletteIndex.get(entry.getValue()));
            }
        } catch (IOException e) {
            HallMod.LOGGER.error("[HallDisk] 写入区块 {} 失败", pos, e);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            return;
        }
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            HallMod.LOGGER.error("[HallDisk] 替换区块 {} 快照失败", pos, e);
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

            int header = in.readInt();
            boolean v2 = header == FORMAT_V2;
            int paletteSize = v2 ? in.readInt() : header;
            if (paletteSize < 0 || paletteSize > 65536) throw new IOException("无效的调色板大小: " + paletteSize);
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = in.readUTF();
            }

            int count = in.readInt();
            if (count < 0 || count > 1_000_000) throw new IOException("无效的快照数量: " + count);
            for (int i = 0; i < count; i++) {
                int x, y, z, paletteIdx;
                if (v2) {
                    x = in.readUnsignedByte();
                    y = in.readInt();
                    z = in.readUnsignedByte();
                    paletteIdx = in.readInt();
                } else {
                    int packed = in.readUnsignedShort();
                    x = (packed >> 12) & 15;
                    y = (packed >> 4) & 4095;
                    z = packed & 15;
                    paletteIdx = in.readShort();
                }

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
