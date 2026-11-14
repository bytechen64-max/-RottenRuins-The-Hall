package org.bytechen.rottenruins.platform.forge.magic;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.bytechen.rottenruins.gameplay.magic.state.ManaAccount;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.platform.minecraft.magic.MagicStorage;
import org.jetbrains.annotations.Nullable;

/** Forge attachment/persistence only. The capability exposes the game account. */
public final class ManaProvider implements ICapabilitySerializable<CompoundTag> {
    private final ManaAccount account;
    private LazyOptional<ManaAccount> self;
    public ManaProvider(Player owner) {
        account = new ManaAccount(owner);
        self = LazyOptional.of(() -> account);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability != CapabilityRegistry.MANA_CAP) return LazyOptional.empty();
        if (!self.isPresent()) self = LazyOptional.of(() -> account);
        return self.cast();
    }
    public void invalidate() { self.invalidate(); }
    @Override public CompoundTag serializeNBT() { return MagicStorage.writeMana(account.storedState()); }
    @Override public void deserializeNBT(CompoundTag tag) {
        account.loadState(MagicStorage.readMana(tag, account.getMaxMana()));
    }
}
