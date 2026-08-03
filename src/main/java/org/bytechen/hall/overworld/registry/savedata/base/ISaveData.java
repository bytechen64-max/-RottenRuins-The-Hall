package org.bytechen.hall.overworld.registry.savedata.base;

import net.minecraft.nbt.CompoundTag;

public interface ISaveData {
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}
