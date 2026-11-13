package org.bytechen.rottenruins.overworld.registry.capability;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.capability.interfaces.ByteItemHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 玩家的魔法池：5 格法术槽（属性 magic_slots 可扩容）+ 同名共享冷却 + 当前选中格 + 轮盘方位映射。
 * 规则：一格只放一个魔法且不能重复；同名法术共享冷却（键是物品注册名，不是 ItemStack / Item 实例，
 * 所以同一件法术放在 1 号还是 3 号槽是同一个冷却）。选中格由客户端 MagicScreen 经
 * MagicPoolActionPacket 写入、服务端只校验与存储；命令等外部入口可直接调 setSelectedSlot。
 */
public class PlayerMagicPool implements ByteItemHandle, ICapabilitySerializable<CompoundTag> {

    /** NBT：槽内法术（ContainerHelper 的 Items 列表）。 */
    public static final String NBT_SLOTS = "Items";
    /** NBT：当前选中格。 */
    public static final String NBT_SELECTED = "HallMagicSelected";
    /** NBT：冷却表（键是物品注册名）。 */
    public static final String NBT_COOLDOWNS = "HallMagicCooldowns";
    /** NBT：轮盘施法界面的"方位 → 魔法池槽位"映射（3 页 × 8 位，-1 = 空位）。 */
    public static final String NBT_WHEEL_SLOTS = "HallMagicWheelSlots";

    /** 轮盘页数（I / II / III）。 */
    public static final int WHEEL_PAGES = 3;
    /** 每页固定 8 个方位。 */
    public static final int WHEEL_SLOTS_PER_PAGE = 8;
    /** -1 = 该方位没有绑定法术。 */
    public static final int WHEEL_EMPTY = -1;

    /** 底层数组按最大值分配，扩容时不必重建；可见格数始终由 MagicStats.slotCount 决定并钳制。 */
    private static final int BACKING_SIZE = MagicStats.MAX_SLOT_COUNT;

    private final Player player;
    private final NonNullList<ItemStack> slots;
    /** 冷却表：物品注册名 → 剩余 tick。 */
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();
    /** 轮盘方位映射：wheelSlots[page * 8 + index] = 魔法池槽位下标，-1 = 空位；法术本体仍在池里。 */
    private final int[] wheelSlots = new int[WHEEL_PAGES * WHEEL_SLOTS_PER_PAGE];
    private int selected;

    private final LazyOptional<PlayerMagicPool> self = LazyOptional.of(() -> this);

    /** 给以后接客户端同步用的脏标记。 */
    private boolean dirty;

    public PlayerMagicPool(@NotNull Player player) {
        this.player = player;
        this.slots = NonNullList.withSize(BACKING_SIZE, ItemStack.EMPTY);
        java.util.Arrays.fill(wheelSlots, WHEEL_EMPTY);
    }

    @Override
    public int getSlots() {
        return Math.max(1, Math.min(MagicStats.slotCount(player), BACKING_SIZE));
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        return isValidSlot(slot) ? slots.get(slot) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isValidSlot(slot) || !isItemValid(slot, stack)) {
            return stack;
        }
        if (!slots.get(slot).isEmpty()) {
            return stack;
        }
        if (!simulate) {
            slots.set(slot, stack.copyWithCount(1));
            sanitizeWheelSlots();
            markDirty();
        }
        if (stack.getCount() <= 1) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        return remainder;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = slots.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = existing.copyWithCount(Math.min(amount, existing.getCount()));
        if (!simulate) {
            slots.set(slot, ItemStack.EMPTY);
            sanitizeWheelSlots();
            markDirty();
        }
        return taken;
    }

    /** 一个栏只能放一个魔法。 */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** 能否放入：是法术且没在别格重复；simulate 预览也走这里，所以预览同样遵守去重。 */
    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (stack.isEmpty() || !isValidSlot(slot)) {
            return false;
        }
        if (!canAdd(stack.getItem())) {
            return false;
        }
        return !containsSpellExcept(stack.getItem(), slot);
    }

    @Override
    public boolean canAdd(Item item) {
        return item instanceof org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
    }

    @Override
    @NotNull
    public Player getOwner() {
        return player;
    }

    /** 别名：见 {@link #selectRelative(int)}。 */
    @Override
    public void changeSlot(int i) {
        selectRelative(i);
    }

    /** 别名：见 {@link #getSelectedSlot()}。 */
    @Override
    public int getSlotnow() {
        return getSelectedSlot();
    }

    /** 别名：见 {@link #tickCooldowns()}。 */
    @Override
    public void upDate() {
        tickCooldowns();
    }

    @Override
    public int getSelectedSlot() {
        // 槽位数可能被属性修饰器改小，所以读的时候顺手夹一次
        return isValidSlot(selected) ? selected : 0;
    }

    @Override
    public void setSelectedSlot(int slot) {
        int count = getSlots();
        if (count <= 0) {
            selected = 0;
            return;
        }
        // 环绕到合法范围（负数也能正确落回去）
        selected = Math.floorMod(slot, count);
        markDirty();
    }

    @Override
    public void selectRelative(int delta) {
        setSelectedSlot(getSelectedSlot() + delta);
    }

    @Override
    public void selectNext() {
        selectRelative(1);
    }

    @Override
    public void selectPrev() {
        selectRelative(-1);
    }

    @Override
    public boolean isValidSlot(int slot) {
        return slot >= 0 && slot < getSlots();
    }

    @Override
    public boolean containsSpell(Item item) {
        return containsSpellExcept(item, -1);
    }

    private boolean containsSpellExcept(Item item, int exceptSlot) {
        int count = getSlots();
        for (int i = 0; i < count; i++) {
            if (i == exceptSlot) {
                continue;
            }
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public ResourceLocation magicIdAt(int slot) {
        return MagicStats.magicId(getStackInSlot(slot));
    }

    /** 放进第一个能放的空格；返回实际放入的槽位，放不进去（满了 / 重复）返回 -1。 */
    public int addSpell(@NotNull ItemStack spell) {
        if (spell.isEmpty()) {
            return -1;
        }
        int count = getSlots();
        for (int i = 0; i < count; i++) {
            if (slots.get(i).isEmpty() && isItemValid(i, spell)) {
                slots.set(i, spell.copyWithCount(1));
                markDirty();
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onCooldown(@Nullable ResourceLocation magicId) {
        if (magicId == null) {
            return false;
        }
        Integer left = cooldowns.get(magicId);
        return left != null && left > 0;
    }

    @Override
    public int getCooldownRemaining(@Nullable ResourceLocation magicId) {
        if (magicId == null) {
            return 0;
        }
        Integer left = cooldowns.get(magicId);
        return left == null ? 0 : Math.max(0, left);
    }

    @Override
    public void setCooldown(@Nullable ResourceLocation magicId, int ticks) {
        if (magicId == null || ticks <= 0) {
            return;
        }
        // 取较大者：连续两次写入时不要让短的那个把长的压回去
        cooldowns.merge(magicId, ticks, Math::max);
        markDirty();
    }

    @Override
    public void tickCooldowns() {
        // 冷却倒计时两端都跑：客户端那份用来显示，这样就不必每 tick 发包。
        // 注意"递减"不算脏 —— 客户端能自己算出来，重发等于每秒几十个无用包。
        boolean expired = false;
        if (!cooldowns.isEmpty()) {
            Iterator<Map.Entry<ResourceLocation, Integer>> it = cooldowns.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ResourceLocation, Integer> entry = it.next();
                int left = entry.getValue() - 1;
                if (left <= 0) {
                    it.remove();
                    expired = true;
                } else {
                    entry.setValue(left);
                }
            }
        }

        // 只有服务端才做"驱逐越界物品"与"标脏"：客户端那份是同步镜像，往客户端背包塞东西是错的
        // （而且会因为槽位数变化而在客户端凭空造出物品）。
        if (player.level().isClientSide()) {
            return;
        }
        boolean changed = expired;
        if (ensureSlotCount()) {
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    /** 槽位数缩小时把越界格子里的法术还给玩家（背包塞不下才掉在脚下）而非丢弃；@return 是否有内容被挪走。 */
    private boolean ensureSlotCount() {
        boolean moved = false;
        int usable = getSlots();
        for (int i = usable; i < slots.size(); i++) {
            ItemStack overflow = slots.get(i);
            if (overflow.isEmpty()) {
                continue;
            }
            slots.set(i, ItemStack.EMPTY);
            moved = true;
            if (!player.getInventory().add(overflow)) {
                player.drop(overflow, false);
            }
        }
        if (!isValidSlot(selected)) {
            selected = 0;
            moved = true;
        }
        return moved;
    }

    public static boolean isValidWheelPage(int page) {
        return page >= 0 && page < WHEEL_PAGES;
    }

    public static boolean isValidWheelIndex(int index) {
        return index >= 0 && index < WHEEL_SLOTS_PER_PAGE;
    }

    public static boolean isValidWheelPosition(int page, int index) {
        return isValidWheelPage(page) && isValidWheelIndex(index);
    }

    /** 读某个方位的魔法池槽位；空位 / 非法返回 WHEEL_EMPTY。 */
    public int getWheelSlot(int page, int index) {
        if (!isValidWheelPosition(page, index)) {
            return WHEEL_EMPTY;
        }
        int poolSlot = wheelSlots[page * WHEEL_SLOTS_PER_PAGE + index];
        return isValidWheelTarget(poolSlot) ? poolSlot : WHEEL_EMPTY;
    }

    /** 某方位当前绑定的法术（空位返回空堆）。 */
    @NotNull
    public ItemStack getWheelStack(int page, int index) {
        int poolSlot = getWheelSlot(page, index);
        return poolSlot < 0 ? ItemStack.EMPTY : getStackInSlot(poolSlot);
    }

    /** 整页映射的副本（-1 = 空位），给界面绘制 / 发包用。 */
    @NotNull
    public int[] copyWheelPage(int page) {
        int[] out = new int[WHEEL_SLOTS_PER_PAGE];
        if (!isValidWheelPage(page)) {
            java.util.Arrays.fill(out, WHEEL_EMPTY);
            return out;
        }
        System.arraycopy(wheelSlots, page * WHEEL_SLOTS_PER_PAGE, out, 0, WHEEL_SLOTS_PER_PAGE);
        return out;
    }

    /** 全部页的映射副本（长度 WHEEL_PAGES * 8）。 */
    @NotNull
    public int[] copyWheelSlots() {
        return wheelSlots.clone();
    }

    /** 服务端调用：写入一整页映射（这里做兜底清洗）。 */
    public void setWheelPage(int page, int @NotNull [] mapping) {
        if (!isValidWheelPage(page) || mapping == null) {
            return;
        }
        int base = page * WHEEL_SLOTS_PER_PAGE;
        for (int i = 0; i < WHEEL_SLOTS_PER_PAGE; i++) {
            wheelSlots[base + i] = i < mapping.length ? mapping[i] : WHEEL_EMPTY;
        }
        sanitizeWheelSlots();
        markDirty();
    }

    /** 这个魔法池槽位能不能作为轮盘目标：合法、非空、主动法术。 */
    public boolean isValidWheelTarget(int poolSlot) {
        if (!isValidSlot(poolSlot)) {
            return false;
        }
        ItemStack stack = slots.get(poolSlot);
        return !stack.isEmpty()
                && MagicStats.isSpell(stack)
                && !MagicStats.isPassive(stack);
    }

    /** 把非法 / 重复的方位清空（物品被取走、槽位数缩水、法术变化时都会用到）。 */
    public void sanitizeWheelSlots() {
        boolean[] used = new boolean[Math.max(1, getSlots())];
        for (int i = 0; i < wheelSlots.length; i++) {
            int poolSlot = wheelSlots[i];
            if (poolSlot == WHEEL_EMPTY) {
                continue;
            }
            if (!isValidWheelTarget(poolSlot) || used[poolSlot]) {
                wheelSlots[i] = WHEEL_EMPTY;
                continue;
            }
            used[poolSlot] = true;
        }
    }

    /** 某个魔法池槽位当前绑定在哪些方位（跨页）。 */
    @NotNull
    public java.util.List<int[]> wheelPositionsOf(int poolSlot) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int page = 0; page < WHEEL_PAGES; page++) {
            for (int index = 0; index < WHEEL_SLOTS_PER_PAGE; index++) {
                if (getWheelSlot(page, index) == poolSlot) {
                    out.add(new int[]{page, index});
                }
            }
        }
        return out;
    }

    /** 把魔法池里的主动法术按顺序填进第 I 页（只在第一次生成轮盘数据时调用）。 */
    private void autoAssignWheelPage0() {
        java.util.Arrays.fill(wheelSlots, 0, WHEEL_SLOTS_PER_PAGE, WHEEL_EMPTY);
        int nextIndex = 0;
        int count = getSlots();
        for (int poolSlot = 0; poolSlot < count && nextIndex < WHEEL_SLOTS_PER_PAGE; poolSlot++) {
            if (isValidWheelTarget(poolSlot)) {
                wheelSlots[nextIndex++] = poolSlot;
            }
        }
    }

    /** 是否有未同步的改动（留给以后接客户端同步用）。 */
    public boolean isDirty() {
        return dirty;
    }

    /** 同步完成后清除脏标记。 */
    public void clearDirty() {
        dirty = false;
    }

    private void markDirty() {
        dirty = true;
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                             @Nullable Direction side) {
        // 同一份实现同时响应"带类型的 MAGIC_POOL_CAP"与"旧的 ITEM_HANDLE_CAP"
        if (capability == CapabilityRegistry.MAGIC_POOL_CAP
                || capability == CapabilityRegistry.ITEM_HANDLE_CAP) {
            return self.cast();
        }
        return LazyOptional.empty();
    }

    /** 实体被移除时由 Forge 调用（经由 invalidateCaps）。 */
    public void invalidate() {
        self.invalidate();
    }

    @Override
    @NotNull
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, slots);

        CompoundTag cooldownTag = new CompoundTag();
        cooldowns.forEach((id, ticks) -> {
            if (ticks > 0) {
                cooldownTag.putInt(id.toString(), ticks);
            }
        });
        tag.put(NBT_COOLDOWNS, cooldownTag);
        tag.putInt(NBT_SELECTED, selected);
        sanitizeWheelSlots();
        tag.putIntArray(NBT_WHEEL_SLOTS, wheelSlots);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull CompoundTag tag) {
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, slots);

        cooldowns.clear();
        CompoundTag cooldownTag = tag.getCompound(NBT_COOLDOWNS);
        for (String key : cooldownTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            int ticks = cooldownTag.getInt(key);
            if (id != null && ticks > 0) {
                cooldowns.put(id, ticks);
            }
        }

        selected = tag.getInt(NBT_SELECTED);
        if (!isValidSlot(selected)) {
            selected = 0;
        }

        java.util.Arrays.fill(wheelSlots, WHEEL_EMPTY);
        boolean hadWheelData = tag.contains(NBT_WHEEL_SLOTS);
        if (hadWheelData) {
            int[] saved = tag.getIntArray(NBT_WHEEL_SLOTS);
            int copy = Math.min(saved.length, wheelSlots.length);
            System.arraycopy(saved, 0, wheelSlots, 0, copy);
        } else {
            // 老存档 / 新玩家：第一次拿到轮盘数据时把主动法术填进第 I 页，否则"按 R 没反应"会被当成 bug；
            // 之后玩家在装载界面里怎么改就怎么存。
            autoAssignWheelPage0();
        }
        sanitizeWheelSlots();
        dirty = false;
    }
}
