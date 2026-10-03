package org.bytechen.hall.overworld.registry.capability;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.hall.overworld.registry.items.MagicBaseItem;
import org.jetbrains.annotations.NotNull;

public class PlayerMagicPool implements ByteItemHandle {
    public final Player player ;
    public final ItemStackHandler handler;
    public int addition = 0;

    public PlayerMagicPool(Player player) {
        this.player = player;
        this.handler = new ItemStackHandler(10);
    }
    public void plusAddition(int i){
        addition = i;
    }
    @Override
    public boolean canAdd(Item item) {
        return item instanceof MagicBaseItem;
    }

    @Override
    public Player getOwner() {
        return player;
    }

    @Override
    public int getSlots() {
        return handler.getSlots()+addition;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int i) {
        return handler.getStackInSlot(i);
    }

    @Override
    public @NotNull ItemStack insertItem(int i, @NotNull ItemStack itemStack, boolean b) {
        return null;
    }

    @Override
    public @NotNull ItemStack extractItem(int i, int i1, boolean b) {
        return null;
    }

    @Override
    public int getSlotLimit(int i) {
        return 0;
    }

    @Override
    public boolean isItemValid(int i, @NotNull ItemStack itemStack) {
        return false;
    }
}
