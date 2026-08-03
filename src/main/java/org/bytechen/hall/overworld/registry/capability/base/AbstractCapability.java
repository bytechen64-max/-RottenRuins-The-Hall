package org.bytechen.hall.overworld.registry.capability.base;

import net.minecraft.nbt.CompoundTag;

public abstract class AbstractCapability<T extends AbstractCapability<T>> {
    public abstract CompoundTag serializeNBT();
    public abstract void deserializeNBT(CompoundTag nbt);
}
