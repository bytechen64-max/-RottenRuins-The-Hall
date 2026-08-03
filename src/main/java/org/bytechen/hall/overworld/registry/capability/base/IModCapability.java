package org.bytechen.hall.overworld.registry.capability.base;

import net.minecraft.nbt.CompoundTag;

public interface IModCapability {
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}
