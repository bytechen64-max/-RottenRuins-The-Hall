package org.bytechen.hall.overworld.difficulty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界级持久化数据：记录玩家是否已完成首次难度选择。
 * <p>
 * 存储在 overworld 的 SavedData 中，世界重启后依然有效。
 * 确保难度选择界面仅在全新世界首次进入时弹出一次。
 */
public class HallWorldData extends SavedData {

    private static final String DATA_NAME = "hall_difficulty_selected";
    private static final String KEY_SELECTED = "DifficultySelected";

    private boolean difficultySelected;

    private HallWorldData() {
        this.difficultySelected = false;
    }

    // ==================== 工厂 ====================

    /**
     * 从 NBT 加载或创建新实例。
     * 由 {@link net.minecraft.world.level.storage.DimensionDataStorage#computeIfAbsent} 调用。
     */
    public static HallWorldData load(CompoundTag tag) {
        HallWorldData data = new HallWorldData();
        data.difficultySelected = tag.getBoolean(KEY_SELECTED);
        return data;
    }

    public static HallWorldData create() {
        return new HallWorldData();
    }

    // ==================== 存取 ====================

    public boolean isDifficultySelected() {
        return difficultySelected;
    }

    public void markDifficultySelected() {
        if (!this.difficultySelected) {
            this.difficultySelected = true;
            setDirty();
        }
    }

    // ==================== NBT ====================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(KEY_SELECTED, difficultySelected);
        return tag;
    }

    /** SavedData 标识名，用于 DimensionDataStorage 查找 */
    public static String dataName() {
        return DATA_NAME;
    }
}
