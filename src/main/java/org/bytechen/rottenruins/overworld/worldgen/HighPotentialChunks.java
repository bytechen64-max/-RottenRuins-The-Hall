package org.bytechen.rottenruins.overworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.config.data.SplendidingConfig;

/**
 * 高势区：由世界种子、维度和区块坐标决定的稀疏区块标记。
 *
 * <p>由配置 highPotentialChunkRarity 控制密度，默认平均每
 * {@value #CHUNKS_PER_HIGH_POTENTIAL} 个区块有一个高势区。
 * 判定不读取或生成区块，也不需要额外的存档数据；种子、维度和密度不变时结果一致。
 * 修改密度会同时影响已有区块和新生成区块。
 * 将来增加高势区机制时，在服务端用 {@link #isHighPotentialChunk(ServerLevel, BlockPos)}
 * 或区块坐标重载方法查询即可。</p>
 */
public final class HighPotentialChunks {
    /** 默认稀有度；实际判定使用配置值。 */
    public static final int CHUNKS_PER_HIGH_POTENTIAL = SplendidingConfig.DEFAULT_HIGH_POTENTIAL_CHUNK_RARITY;

    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Z_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long DIMENSION_SALT = 0x165667B19E3779F9L;

    private HighPotentialChunks() {}

    public static boolean isHighPotentialChunk(ServerLevel level, BlockPos pos) {
        return isHighPotentialChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean isHighPotentialChunk(ServerLevel level, ChunkPos pos) {
        return isHighPotentialChunk(level, pos.x, pos.z);
    }

    public static boolean isHighPotentialChunk(ServerLevel level, int chunkX, int chunkZ) {
        return isHighPotentialChunk(level.getSeed(), level.dimension(), chunkX, chunkZ);
    }

    /** 使用当前配置查询分布，不加载世界或区块。 */
    public static boolean isHighPotentialChunk(long worldSeed, ResourceKey<Level> dimension,
                                               int chunkX, int chunkZ) {
        return isHighPotentialChunk(worldSeed, dimension, chunkX, chunkZ, getRarity());
    }

    /** 显式指定稀有度的纯计算入口：0 关闭，1 全部，N 表示约 1/N 的区块。 */
    public static boolean isHighPotentialChunk(long worldSeed, ResourceKey<Level> dimension,
                                               int chunkX, int chunkZ, int rarity) {
        if (rarity < 0) throw new IllegalArgumentException("High potential chunk rarity must be non-negative");
        if (rarity == 0) return false;
        long dimensionHash = mix64(dimension.location().toString().hashCode() * DIMENSION_SALT);
        long hash = mix64(worldSeed ^ dimensionHash
                ^ ((long) chunkX * X_SALT) ^ ((long) chunkZ * Z_SALT));
        return Long.remainderUnsigned(hash, rarity) == 0;
    }

    private static int getRarity() {
        if (ConfigHelper.configHolder == null) return CHUNKS_PER_HIGH_POTENTIAL;
        SplendidingConfig config = ConfigHelper.configHolder.get();
        if (config == null || config.highPotentialChunkRarity < 0) return CHUNKS_PER_HIGH_POTENTIAL;
        return config.highPotentialChunkRarity;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
