package org.bytechen.hall.overworld.registry.capability.interfaces;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.items.IItemHandler;

public interface ByteItemHandle extends IItemHandler {
    boolean canAdd(Item item);
    Player getOwner();
}
