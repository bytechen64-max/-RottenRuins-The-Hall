package org.bytechen.rottenruins.overworld.registry.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.overworld.crafting.PotentialCraftingRecipe;
import org.bytechen.rottenruins.overworld.inventory.PotentialCraftingMenu;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 势能合成台的方块实体：16 个环形材料槽（每格 1 个物品）+ 圆心产物槽。
 *
 * <p>进度按势能单位累计：每 tick {@code 15 + power}，需求 {@code 配方tick × 15}。
 * 因此势能 0 不工作、势能 15（满）正好 2 倍速 = 时间减半，中间线性。
 * 势能掉到 0 只是暂停，不清进度。</p>
 *
 * <p>材料在<b>完成时</b>才扣除（界面要用它们做旋转动画）；合成期间槽位锁死，
 * 中途拆机会先按已锁定的消耗量扣掉，剩下的才掉落。</p>
 */
public final class PotentialCraftingTableBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider {

    public static final int RING_SLOTS = 16;
    public static final int OUTPUT_SLOT = RING_SLOTS;
    public static final int SLOT_COUNT = RING_SLOTS + 1;

    /** 一个势能单位对应的 tick 数；决定"满势能减半"（15+15 = 2×）。 */
    public static final int POWER_UNITS = 15;

    public static final int DATA_COUNT = 4;

    private static final int[] RING_FACE_SLOTS = buildRingFaceSlots();
    private static final int[] OUTPUT_FACE_SLOTS = {OUTPUT_SLOT};
    private static final int[] NO_SLOTS = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int progress;
    private int requiredProgress;
    private int totalTicks;
    private final int[] consume = new int[RING_SLOTS];
    private ItemStack pendingOutput = ItemStack.EMPTY;

    /** 同步给界面的数据（注意：ContainerData 走 short，值必须 < 32767）。 */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> isCrafting() ? 1 : 0;
                case 1 -> currentPower();
                case 2 -> progressPermille();
                case 3 -> Math.min(30000, totalTicks);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public PotentialCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(RegisterBlockEntities.POTENTIAL_CRAFTING_TABLE.get(), pos, state);
    }

    // ── 状态 ──

    public boolean isCrafting() {
        return totalTicks > 0;
    }

    public int progressPermille() {
        if (requiredProgress <= 0) {
            return 0;
        }
        return Math.min(1000, (int) ((long) progress * 1000L / requiredProgress));
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int currentPower() {
        BlockState state = getBlockState();
        return state.hasProperty(SourceMachineBlock.POWER)
                ? state.getValue(SourceMachineBlock.POWER) : 0;
    }

    // ── Tick ──

    public static void tick(Level level, BlockPos pos, BlockState state,
                            PotentialCraftingTableBlockEntity station) {
        if (level.isClientSide) {
            return;
        }
        int power = station.currentPower();
        if (!station.isCrafting()) {
            if (power > 0) {
                station.tryStart();
            }
            return;
        }
        if (power <= 0) {
            return;   // 没势能：暂停
        }
        station.progress += POWER_UNITS + power;
        station.setChanged();
        if (station.progress >= station.requiredProgress) {
            station.finishCraft();
        }
    }

    private void tryStart() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        PotentialCraftingRecipe recipe = PotentialCraftingRecipes.findMatch(level, this);
        if (recipe == null) {
            return;
        }
        int[] consumption = recipe.matchConsumption(this);
        if (consumption == null) {
            return;
        }
        ItemStack output = recipe.getResultItem(level.registryAccess()).copy();
        if (output.isEmpty() || !canAcceptOutput(output)) {
            return;
        }
        System.arraycopy(consumption, 0, consume, 0, RING_SLOTS);
        pendingOutput = output;
        totalTicks = recipe.timeTicks();
        requiredProgress = recipe.timeTicks() * POWER_UNITS;
        progress = 0;
        setChanged();
    }

    private boolean canAcceptOutput(@NotNull ItemStack output) {
        ItemStack current = items.get(OUTPUT_SLOT);
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(current, output)
                && current.getCount() + output.getCount() <= current.getMaxStackSize();
    }

    private void finishCraft() {
        for (int slot = 0; slot < RING_SLOTS; slot++) {
            if (consume[slot] > 0) {
                items.get(slot).shrink(consume[slot]);
            }
        }
        ItemStack output = pendingOutput.copy();
        ItemStack current = items.get(OUTPUT_SLOT);
        if (current.isEmpty()) {
            items.set(OUTPUT_SLOT, output);
        } else if (ItemStack.isSameItemSameTags(current, output)
                && current.getCount() + output.getCount() <= current.getMaxStackSize()) {
            current.grow(output.getCount());
        } else if (level != null) {
            net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5d, worldPosition.getY() + 0.9d,
                    worldPosition.getZ() + 0.5d, output);
        }
        resetCraft();
    }

    private void resetCraft() {
        progress = 0;
        requiredProgress = 0;
        totalTicks = 0;
        pendingOutput = ItemStack.EMPTY;
        java.util.Arrays.fill(consume, 0);
        setChanged();
    }

    /** 合成中途被破坏：先扣掉已投入的材料，剩下的交给调用方掉落。 */
    public void dropRemainderAfterInterrupt() {
        if (!isCrafting()) {
            return;
        }
        for (int slot = 0; slot < RING_SLOTS; slot++) {
            if (consume[slot] > 0) {
                items.get(slot).shrink(consume[slot]);
            }
        }
        resetCraft();
    }

    // ── Container ──

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    @NotNull
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    @NotNull
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    @NotNull
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        // 环形槽每格只放 1 个
        int limit = slot < RING_SLOTS ? 1 : stack.getMaxStackSize();
        stack.setCount(Math.min(stack.getCount(), limit));
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return !isCrafting() && slot >= 0 && slot < RING_SLOTS
                && items.get(slot).isEmpty() && !stack.isEmpty();
    }

    /** 漏斗：上面/侧面送料，下面只取产物；合成期间一律不导通。 */
    @Override
    public int[] getSlotsForFace(@NotNull Direction side) {
        if (isCrafting()) {
            return NO_SLOTS;
        }
        return side == Direction.DOWN ? OUTPUT_FACE_SLOTS : RING_FACE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @NotNull ItemStack stack, @NotNull Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, @NotNull ItemStack stack, @NotNull Direction side) {
        return !isCrafting() && side == Direction.DOWN && slot == OUTPUT_SLOT;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5d, worldPosition.getY() + 0.5d,
                worldPosition.getZ() + 0.5d) <= 64.0d;
    }

    @Override
    public void clearContent() {
        items.clear();
        resetCraft();
    }

    // ── MenuProvider ──

    @Override
    @NotNull
    public Component getDisplayName() {
        return Component.translatable("block.rotten_ruins.potential_crafting_table");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new PotentialCraftingMenu(id, inventory, this);
    }

    // ── 存档 ──

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress", progress);
        tag.putInt("Required", requiredProgress);
        tag.putInt("TotalTicks", totalTicks);
        if (isCrafting()) {
            tag.putIntArray("Consume", consume.clone());
            tag.put("Pending", pendingOutput.save(new CompoundTag()));
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        progress = tag.getInt("Progress");
        requiredProgress = tag.getInt("Required");
        totalTicks = tag.getInt("TotalTicks");
        java.util.Arrays.fill(consume, 0);
        if (tag.contains("Consume")) {
            int[] stored = tag.getIntArray("Consume");
            System.arraycopy(stored, 0, consume, 0, Math.min(stored.length, RING_SLOTS));
        }
        pendingOutput = tag.contains("Pending")
                ? ItemStack.of(tag.getCompound("Pending")) : ItemStack.EMPTY;
        if (totalTicks <= 0 || pendingOutput.isEmpty()) {
            resetCraft();   // 存档状态不完整 → 回到空闲，别卡住
        }
    }

    private static int[] buildRingFaceSlots() {
        int[] slots = new int[RING_SLOTS];
        for (int i = 0; i < RING_SLOTS; i++) {
            slots[i] = i;
        }
        return slots;
    }
}
