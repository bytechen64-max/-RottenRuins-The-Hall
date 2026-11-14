package org.bytechen.rottenruins.platform.forge.magic;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventory;
import org.bytechen.rottenruins.gameplay.magic.inventory.PlayerSpellInventories;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.platform.minecraft.magic.MagicStorage;
import org.jetbrains.annotations.Nullable;

/** Forge attachment/persistence only. Callers receive the game inventory, not this provider. */
public final class SpellInventoryProvider implements ICapabilitySerializable<CompoundTag> {
    private final SpellInventory inventory;
    private LazyOptional<SpellInventory> capability;

    public SpellInventoryProvider(Player owner) {
        inventory = PlayerSpellInventories.create(owner);
        capability = LazyOptional.of(() -> inventory);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> requested, @Nullable Direction side) {
        if (requested != CapabilityRegistry.MAGIC_POOL_CAP) return LazyOptional.empty();
        // The owning Entity gates invalid capabilities. After reviveCaps (player cloning),
        // recreate the view while retaining the same inventory for copying.
        if (!capability.isPresent()) capability = LazyOptional.of(() -> inventory);
        return capability.cast();
    }

    public void invalidate() { capability.invalidate(); }
    @Override public CompoundTag serializeNBT() { return MagicStorage.writePool(inventory.snapshot()); }
    @Override public void deserializeNBT(CompoundTag tag) { inventory.applySnapshot(MagicStorage.readPool(tag)); }
}
