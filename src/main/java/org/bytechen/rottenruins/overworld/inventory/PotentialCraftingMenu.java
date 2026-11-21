package org.bytechen.rottenruins.overworld.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.SourceMenus;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 势能合成台界面：16 个环形材料槽（每格 1 个物品）+ 圆心产物槽。
 *
 * <p>槽位坐标由客户端界面的动画改写（见 {@code PotentialCraftingScreen}）；
 * 合成期间所有容器槽位锁死。</p>
 */
public final class PotentialCraftingMenu extends AbstractContainerMenu {

    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 256;

    /** 圆心（= 产物槽位置）。 */
    public static final int RING_CENTER_X = 96;
    public static final int RING_CENTER_Y = 84;

    /** 环形槽静止半径。 */
    public static final int RING_RADIUS = 48;

    /** 「开始合成」按钮（界面绘制与点击判定共用同一组常量）。 */
    public static final int BUTTON_START = 0;
    public static final int BUTTON_X = 58;
    public static final int BUTTON_Y = 146;
    public static final int BUTTON_WIDTH = 60;
    public static final int BUTTON_HEIGHT = 18;

    private static final int INVENTORY_Y = 182;
    private static final int HOTBAR_Y = 236;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    /** 客户端构造。 */
    public PotentialCraftingMenu(int id, @NotNull Inventory playerInventory, @NotNull FriendlyByteBuf buffer) {
        this(id, playerInventory, buffer.readBlockPos(),
                new SimpleContainer(PotentialCraftingTableBlockEntity.SLOT_COUNT),
                new SimpleContainerData(PotentialCraftingTableBlockEntity.DATA_COUNT));
    }

    /** 服务端构造。 */
    public PotentialCraftingMenu(int id, @NotNull Inventory playerInventory,
                                 @NotNull PotentialCraftingTableBlockEntity station) {
        this(id, playerInventory, station.getBlockPos(), station, station.data);
    }

    private PotentialCraftingMenu(int id, @NotNull Inventory playerInventory, @NotNull BlockPos pos,
                                  @NotNull Container container, @NotNull ContainerData data) {
        super(SourceMenus.POTENTIAL_CRAFTING.get(), id);
        this.pos = pos;
        this.container = container;
        this.data = data;
        checkContainerSize(container, PotentialCraftingTableBlockEntity.SLOT_COUNT);
        addDataSlots(data);

        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            double angle = Math.toRadians(ringAngleDegrees(index));
            addSlot(new RingSlot(container, index,
                    RING_CENTER_X + Mth.floor(Math.cos(angle) * RING_RADIUS) - 8,
                    RING_CENTER_Y + Mth.floor(Math.sin(angle) * RING_RADIUS) - 8));
        }

        addSlot(new Slot(container, PotentialCraftingTableBlockEntity.OUTPUT_SLOT,
                RING_CENTER_X - 8, RING_CENTER_Y - 8) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(@NotNull Player player) {
                return !isCrafting();
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    /** 第 index 个环形槽的静止角度（度，0° = 正右，顺时针；从正上方起排）。 */
    public static int ringAngleDegrees(int index) {
        return -90 + Math.round(index * (360.0f / PotentialCraftingTableBlockEntity.RING_SLOTS));
    }

    // ── 界面读的状态 ──

    public BlockPos pos() {
        return pos;
    }

    public boolean isCrafting() {
        return data.get(0) == 1;
    }

    public int power() {
        return data.get(1);
    }

    public int progressPermille() {
        return data.get(2);
    }

    public int totalTicks() {
        return data.get(3);
    }

    public float progressFraction() {
        return Mth.clamp(progressPermille() / 1000.0f, 0.0f, 1.0f);
    }

    @NotNull
    public BlockPos blockPos() {
        return pos;
    }

    public boolean ringEmpty() {
        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            if (slots.get(index).hasItem()) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    public Container container() {
        return container;
    }

    /**
     * 按钮：客户端点「开始合成」→ {@code handleInventoryButtonClick} → 服务端走到这里。
     * 材料校验与开始都发生在服务端，客户端只是发起请求。
     */
    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id != BUTTON_START) {
            return false;
        }
        return container instanceof PotentialCraftingTableBlockEntity station && station.requestStart();
    }

    // ── 交互 ──

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        // 合成期间吞掉容器内的所有点击（含快速移动 / 数字键交换）
        if (isCrafting() && slotId >= 0 && slotId < PotentialCraftingTableBlockEntity.SLOT_COUNT) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int ringEnd = PotentialCraftingTableBlockEntity.RING_SLOTS;
        int containerEnd = PotentialCraftingTableBlockEntity.SLOT_COUNT;

        if (index < containerEnd) {
            if (isCrafting()) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, containerEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, ringEnd, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return container.stillValid(player);
    }

    /** 环形材料槽：每格 1 个，合成期间不可拿不可放。 */    private final class RingSlot extends Slot {
        private RingSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return !isCrafting() && container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return !isCrafting();
        }
    }
}
