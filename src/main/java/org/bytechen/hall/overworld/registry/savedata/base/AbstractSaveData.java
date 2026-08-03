package org.bytechen.hall.overworld.registry.savedata.base;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public abstract class AbstractSaveData extends SavedData implements ISaveData {
    @Override public CompoundTag save(CompoundTag nbt) { return serializeNBT(); }
    public void markDirty() { super.setDirty(); }
}
