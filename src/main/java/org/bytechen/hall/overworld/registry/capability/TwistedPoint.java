package org.bytechen.hall.overworld.registry.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteNumberAbility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TwistedPoint implements ByteNumberAbility, ICapabilityProvider , INBTSerializable<CompoundTag> {
    private int point ;
    private final LazyOptional<ByteNumberAbility> optional = LazyOptional.of(() -> this);
    public TwistedPoint(int org){
        point = org;
    }
    @Override
    public int getNumber() {
        return point;
    }

    @Override
    public void setNumber(int i) {
         point = i;
    }

    @Override
    public void plus(int i) {
        point += i;
    }

    @Override
    public void minus(int i) {
        point -= i;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
        if (capability == CapabilityRegistry.TWIST){
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
       CompoundTag tag = new CompoundTag();
       tag.putInt("hell:twisted",point);
       return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
         point = tag.getInt("hell:twisted");
    }
}
