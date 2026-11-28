package org.bytechen.rottenruins.gameplay.infection.biome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * 群系扩散快捷工具。
 * <p>
 * 提供群系写入和客户端同步的核心方法，
 * 各模组的群系处理器可复用此工具避免重复实现。
 * <p>
 * 典型用法：
 * <pre>{@code
 * // 设置单个方块柱的群系
 * if (BiomeSpreadHelper.setColumnQuartBiome(level, pos, targetBiome)) {
 *     BiomeSpreadHelper.syncChunk(level, new ChunkPos(pos));
 * }
 * }</pre>
 */
public final class BiomeSpreadHelper {

    private BiomeSpreadHelper() {}

    /**
     * 将指定方块柱的所有 Y 层群系设置为目标群系。
     * <p>
     * 使用 QuartPos 粒度（4×4×4 方块）修改 {@link PalettedContainer} 中的群系数据。
     * 遍历区块的所有 Section，对每个 Section 的 4 个 Y 层设置群系。
     *
     * @param level      服务器世界
     * @param pos        任意方块坐标（会自动转为 quart 坐标）
     * @param biomeHolder 目标群系 Holder
     * @return 是否实际修改了群系数据
     */
    public static boolean setColumnQuartBiome(ServerLevel level, BlockPos pos, Holder<Biome> biomeHolder) {
        LevelChunk chunk = level.getChunkAt(pos);
        int qX = QuartPos.fromBlock(pos.getX() & 15);
        int qZ = QuartPos.fromBlock(pos.getZ() & 15);

        boolean changed = false;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.getBiomes() instanceof PalettedContainer<?> container) {
                @SuppressWarnings("unchecked")
                PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) container;
                for (int qY = 0; qY < 4; qY++) {
                    if (!biomes.get(qX, qY, qZ).equals(biomeHolder)) {
                        biomes.set(qX, qY, qZ, biomeHolder);
                        changed = true;
                    }
                }
            }
        }

        if (changed) chunk.setUnsaved(true);
        return changed;
    }

    /**
     * 向追踪该区块的所有玩家发送区块更新包，使客户端群系变化即时可见。
     *
     * @param level 服务器世界
     * @param cp    区块坐标
     */
    public static void syncChunk(ServerLevel level, ChunkPos cp) {
        LevelChunk chunk = level.getChunk(cp.x, cp.z);
        if (chunk == null) return;
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunk, level.getLightEngine(), null, null);
        level.getChunkSource().chunkMap
                .getPlayers(cp, false)
                .forEach(player -> player.connection.send(packet));
    }
}
