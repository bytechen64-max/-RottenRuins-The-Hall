package org.bytechen.hall.overworld.swarm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/**
 * Hall 感染群系扩散数据持久化钩子。
 * <p>
 * 负责触发磁盘 IO 系统的生命周期管理，
 * 不使用 NBT 存储庞大的快照 Map（快照通过 {@link HallBiomeProcessor} 异步写入磁盘）。
 */
public class HallSpreadSaveData extends SavedData {

    private static final String DATA_ID = "hall_spread_data";

    public HallSpreadSaveData() {
    }

    /**
     * 获取或创建数据实例，同时初始化处理器的存储路径。
     */
    public static HallSpreadSaveData get(ServerLevel level) {
        HallSpreadSaveData data = level.getDataStorage().computeIfAbsent(
                HallSpreadSaveData::load,
                HallSpreadSaveData::new,
                DATA_ID
        );

        // 初始化处理器的存储路径和线程池
        HallBiomeProcessor.initStorage(level);

        return data;
    }

    /**
     * 存档加载逻辑。
     */
    public static HallSpreadSaveData load(CompoundTag nbt) {
        return new HallSpreadSaveData();
    }

    /**
     * 存档保存逻辑。
     * 不向 level.dat 写入庞大的数据，真正的快照已异步写入磁盘。
     */
    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag nbt) {
        return nbt;
    }

    /**
     * 显式标记为脏，用于元数据更新。
     */
    public void markDirty() {
        this.setDirty();
    }
}
