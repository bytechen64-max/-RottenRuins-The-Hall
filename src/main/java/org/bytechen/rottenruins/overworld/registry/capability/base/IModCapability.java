package org.bytechen.rottenruins.overworld.registry.capability.base;

import net.minecraft.nbt.CompoundTag;

public interface IModCapability {
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}
