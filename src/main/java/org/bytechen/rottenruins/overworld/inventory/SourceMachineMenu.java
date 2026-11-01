package org.bytechen.rottenruins.overworld.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.SourceMenus;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.advancement.RotAdvancementTriggers;
import org.bytechen.rottenruins.advancement.SourceMilestoneTrigger;
import org.bytechen.rottenruins.overworld.energy.ElementBand;

public final class SourceMachineMenu extends AbstractContainerMenu {
    public final boolean collector;
    public final BlockPos pos;
    private final Container inventory;
    private final ContainerData data;

    public SourceMachineMenu(int id,Inventory playerInventory,FriendlyByteBuf buffer) {
        this(id,playerInventory,buffer.readBlockPos());
    }

    private SourceMachineMenu(int id,Inventory playerInventory,BlockPos pos) {
        this(id,playerInventory,pos,playerInventory.player.level().getBlockState(pos).is(RegisterBlock.SOURCE_COLLECTOR.get()),
                new SimpleContainer(2),new SimpleContainerData(SourceMachineBlockEntity.DATA_COUNT));
    }

    public SourceMachineMenu(int id,Inventory playerInventory,SourceMachineBlockEntity machine) {
        this(id,playerInventory,machine.getBlockPos(),machine.collector,machine,machine.data);
    }

    private SourceMachineMenu(int id,Inventory playerInventory,BlockPos pos,boolean collector,Container inventory,ContainerData data) {
        super(SourceMenus.SOURCE_MACHINE.get(),id);
        this.pos=pos; this.collector=collector; this.inventory=inventory; this.data=data;
        addDataSlots(data);
        if (collector) {
            addSlot(new Slot(inventory,0,55,52) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(RegisterItem.MANA_BOTTLE.get()); }
            });
            addSlot(new Slot(inventory,1,149,52) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
            for (int row=0;row<3;row++) for (int column=0;column<9;column++) {
                addSlot(new Slot(playerInventory,column+row*9+9,29+column*18,112+row*18));
            }
            for (int column=0;column<9;column++) addSlot(new Slot(playerInventory,column,29+column*18,170));
        }
    }

    public int value(int index) { return data.get(index); }
    public void configure(Player player,int fc,int width,int gain,boolean negative) {
        if (!collector && stillValid(player) && inventory instanceof SourceMachineBlockEntity machine) {
            machine.configure(fc,width,gain,negative);
            if(player instanceof ServerPlayer server && machine.element()!=null) {
                RotAdvancementTriggers.triggerSourceMilestone(server,SourceMilestoneTrigger.Milestone.TUNED_FILTER);
                if(machine.element()==ElementBand.VOID) RotAdvancementTriggers.triggerSourceMilestone(server,SourceMilestoneTrigger.Milestone.VOID_TUNING);
            }
        }
    }

    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player,int index) {
        if (!collector || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem();
        ItemStack original=stack.copy();
        if (index<2) {
            if (!moveItemStackTo(stack,2,38,true)) return ItemStack.EMPTY;
        } else if (stack.is(RegisterItem.MANA_BOTTLE.get())) {
            if (!moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY;
        } else if (index<29) {
            if (!moveItemStackTo(stack,29,38,false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack,2,29,false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack);
        return original;
    }
}
