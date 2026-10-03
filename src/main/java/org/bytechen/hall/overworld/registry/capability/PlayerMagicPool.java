package org.bytechen.hall.overworld.registry.capability;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.hall.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.hall.overworld.registry.items.magic.bases.IMagicBaseItem;
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

    /**
     * 每 tick 推进一次槽内魔法物品的冷却。
     *
     * <p><b>从 main 摘入时修掉的两处</b>：</p>
     * <ol>
     *   <li>原来是无条件 {@code (CMagicBaseItem) stack.getItem()} —— 只要槽里放了
     *       任何非魔法物品就是 {@code ClassCastException}。改成先判类型。</li>
     *   <li>原来的条件是 {@code if (item.cooldown < 0) item.cooldown--;} ——
     *       冷却本来就是负的才递减，只会越走越负、永远回不到可用。
     *       按 {@code canUse()} 的语义（{@code cooldown <= 0} 即可用），
     *       正确做法是<b>正数才递减</b>。</li>
     * </ol>
     */
    public void upDate(){
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof CMagicBaseItem item)) {
                continue;
            }
            if (item.cooldown > 0) {
                item.cooldown--;
            }
        }
    }
    @Override
    public boolean canAdd(Item item) {
        return item instanceof IMagicBaseItem;
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
            return handler.insertItem(i,itemStack,b);
    }

    @Override
    public @NotNull ItemStack extractItem(int i, int i1, boolean b) {
        return handler.extractItem(i,i1,b);
    }

    @Override
    public int getSlotLimit(int i) {
        return 1;
    }

    @Override
    public boolean isItemValid(int i, @NotNull ItemStack itemStack) {
        return canAdd(itemStack.getItem());
    }
}
