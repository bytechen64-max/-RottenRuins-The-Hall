package org.bytechen.hall.overworld.registry.capability.base;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CapabilityProvider<T extends IModCapability> implements ICapabilitySerializable<CompoundTag> {
    private final Capability<T> capability;
    private final LazyOptional<T> instance;
    private final T impl;

    public CapabilityProvider(Capability<T> capability, T impl) {
        this.capability = capability;
        this.impl = impl;
        this.instance = LazyOptional.of(() -> impl);
    }

    @Nonnull @Override
    public <C> LazyOptional<C> getCapability(@Nonnull Capability<C> cap, @Nullable Direction side) {
        return cap == capability ? instance.cast() : LazyOptional.empty();
    }

    @Override public CompoundTag serializeNBT() { return impl.serializeNBT(); }
    @Override public void deserializeNBT(CompoundTag nbt) { impl.deserializeNBT(nbt); }
}
