package org.bytechen.rottenruins.gameplay.magic.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bytechen.rottenruins.core.magic.state.SpellCooldowns;
import org.bytechen.rottenruins.core.magic.state.SpellLoadoutState;
import org.bytechen.rottenruins.core.magic.rules.MagicMath;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/** Game inventory for unique spell stacks, shared cooldowns and an independent loadout state. */
public final class SpellInventory {

    /** 轮盘页数（I / II / III）。 */
    public static final int WHEEL_PAGES = SpellLoadoutState.WHEEL_PAGES;
    /** 每页固定 8 个方位。 */
    public static final int WHEEL_SLOTS_PER_PAGE = SpellLoadoutState.SLOTS_PER_PAGE;
    /** -1 = 该方位没有绑定法术。 */
    public static final int WHEEL_EMPTY = SpellLoadoutState.EMPTY;

    /**
     * 底层数组长度。按最大值分配，这样属性修饰器扩容时不需要重建数组；
     * 可见格数始终由 {@link MagicStats#slotCount(net.minecraft.world.entity.player.Player)} 决定。
     */
    private static final int BACKING_SIZE = MagicMath.MAX_SLOT_COUNT;

    private final IntSupplier slotCount;
    private final NonNullList<ItemStack> slots;
    /** 冷却表：物品注册名 → 剩余 tick。 */
    private final SpellCooldowns cooldowns = new SpellCooldowns();
    private final SpellLoadoutState loadout = new SpellLoadoutState();

    /** Slot writes, selection changes, cooldown starts and server-side expiry request replication. */
    private boolean dirty;
    private int replicatedCapacity = -1;
    private int lastSentCapacity = -1;

    public SpellInventory(IntSupplier slotCount) {
        this.slotCount = java.util.Objects.requireNonNull(slotCount);
        this.slots = NonNullList.withSize(BACKING_SIZE, ItemStack.EMPTY);
    }

    // ══════════════════════════════════════════════════════════════
    // 物品容器操作
    // ══════════════════════════════════════════════════════════════

    public int getSlots() {
        return replicatedCapacity > 0 ? replicatedCapacity : MagicMath.slotCount(slotCount.getAsInt());
    }

    @NotNull
    public ItemStack getStackInSlot(int slot) {
        return isValidSlot(slot) ? slots.get(slot) : ItemStack.EMPTY;
    }

    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isValidSlot(slot) || !isItemValid(slot, stack)) {
            return stack;
        }
        // 一个栏只放一个魔法
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
    public int getSlotLimit(int slot) {
        return 1;
    }

    /**
     * 能否放进这一格：是法术 + 不在别格重复。
     * <p>注意 {@code simulate} 也走这里，所以"预览放置"同样遵守去重规则。</p>
     */
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (stack.isEmpty() || !isValidSlot(slot)) {
            return false;
        }
        if (!canAdd(stack.getItem())) {
            return false;
        }
        return !containsSpellExcept(stack.getItem(), slot);
    }

    // ══════════════════════════════════════════════════════════════
    // 法术合法性
    // ══════════════════════════════════════════════════════════════

    public boolean canAdd(Item item) {
        return item instanceof org.bytechen.rottenruins.gameplay.magic.spell.IMagicBaseItem;
    }

    // ---- 槽位选择 ----

    public int getSelectedSlot() { return loadout.selected(getSlots()); }

    public void setSelectedSlot(int slot) {
        loadout.select(slot, getSlots());
        markDirty();
    }

    public void selectRelative(int delta) {
        loadout.selectRelative(delta, getSlots());
        markDirty();
    }
    public void selectNext() { selectRelative(1); }
    public void selectPrev() { selectRelative(-1); }
    public boolean isValidSlot(int slot) { return SpellLoadoutState.isValidSlot(slot, getSlots()); }

    // ---- 去重 ----

    public boolean containsSpell(Item item) {
        return containsSpellExcept(item, -1);
    }

    /** 除 {@code exceptSlot} 之外，还有没有哪一格放着这个物品。 */
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

    @Nullable
    public ResourceLocation magicIdAt(int slot) {
        return MagicStats.magicId(getStackInSlot(slot));
    }

    /**
     * 把一件法术放进第一个能放的空格。
     *
     * @return 实际放入的槽位下标；放不进去（满了 / 重复）返回 {@code -1}
     */
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

    /** Validate both ends first, then move once. No intermediate extraction or lossy rollback. */
    public boolean moveSpell(int from, int to) {
        if (!isValidSlot(from) || !isValidSlot(to) || from == to || !slots.get(to).isEmpty()) return false;
        ItemStack moving = slots.get(from);
        if (moving.isEmpty() || moving.getCount() != 1 || !canAdd(moving.getItem())
                || containsSpellExcept(moving.getItem(), from)) return false;
        slots.set(to, moving);
        slots.set(from, ItemStack.EMPTY);
        // Preserve the existing policy: wheel bindings to the old slot are cleared on move.
        sanitizeWheelSlots();
        markDirty();
        return true;
    }

    // ---- 冷却 ----

    public boolean onCooldown(@Nullable ResourceLocation magicId) {
        if (magicId == null) {
            return false;
        }
        return cooldowns.remaining(magicId.toString()) > 0;
    }

    public int getCooldownRemaining(@Nullable ResourceLocation magicId) {
        if (magicId == null) {
            return 0;
        }
        return cooldowns.remaining(magicId.toString());
    }

    public void setCooldown(@Nullable ResourceLocation magicId, int ticks) {
        if (magicId == null || ticks <= 0) {
            return;
        }
        // 取较大者：连续两次写入时不要让短的那个把长的压回去
        cooldowns.start(magicId.toString(), ticks);
        markDirty();
    }

    /** Both sides advance display clocks. Only the authoritative side requests expiry synchronization. */
    public void tickCooldowns(boolean authoritative) {
        boolean expired = cooldowns.tick();
        if (authoritative && expired) markDirty();
    }

    /** Returns displaced stacks without deciding how a particular player/world should receive them. */
    public List<ItemStack> drainOverflow() {
        List<ItemStack> overflow = new ArrayList<>();
        for (int i = getSlots(); i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty()) {
                overflow.add(stack);
                slots.set(i, ItemStack.EMPTY);
            }
        }
        boolean changed = loadout.reconcileSelection(getSlots());
        changed |= loadout.sanitize(getSlots(), eligibleWheelBindings());
        if (!overflow.isEmpty() || changed) markDirty();
        return overflow;
    }

    // ══════════════════════════════════════════════════════════════
    // 轮盘方位映射
    // ══════════════════════════════════════════════════════════════

    public static boolean isValidWheelPage(int page) {
        return SpellLoadoutState.isValidPage(page);
    }

    public static boolean isValidWheelIndex(int index) {
        return SpellLoadoutState.isValidIndex(index);
    }

    public static boolean isValidWheelPosition(int page, int index) {
        return SpellLoadoutState.isValidPosition(page, index);
    }

    /** 读某个方位的魔法池槽位；空位 / 非法返回 {@link #WHEEL_EMPTY}。 */
    public int getWheelSlot(int page, int index) {
        int slot = loadout.binding(page, index);
        return isValidWheelTarget(slot) ? slot : WHEEL_EMPTY;
    }

    /** 某方位当前绑定的法术（空位返回空堆）。 */
    @NotNull
    public ItemStack getWheelStack(int page, int index) {
        int poolSlot = getWheelSlot(page, index);
        return poolSlot < 0 ? ItemStack.EMPTY : getStackInSlot(poolSlot);
    }

    /** 整页映射的副本（-1 = 空位），给界面绘制 / 发包用。 */
    @NotNull
    public int[] copyWheelPage(int page) { return loadout.copyPage(page); }
    public int[] copyWheelSlots() { return loadout.copyWheel(); }

    public void setWheelPage(int page, int[] mapping) {
        if (!isValidWheelPage(page) || mapping == null) return;
        loadout.setPage(page, mapping, getSlots(), eligibleWheelBindings());
        markDirty();
    }

    /** 这个魔法池槽位能不能作为轮盘目标：合法、非空、主动法术。 */
    public boolean isValidWheelTarget(int poolSlot) {
        return isValidSlot(poolSlot) && isStoredWheelTarget(poolSlot);
    }

    private boolean isStoredWheelTarget(int poolSlot) {
        if (poolSlot < 0 || poolSlot >= slots.size()) return false;
        ItemStack stack = slots.get(poolSlot);
        return !stack.isEmpty()
                && MagicStats.isSpell(stack)
                && !MagicStats.isPassive(stack);
    }

    /** 把非法 / 重复的方位清空（物品被取走、槽位数缩水、法术变化时都会用到）。 */
    public void sanitizeWheelSlots() {
        if (loadout.sanitize(getSlots(), eligibleWheelBindings())) markDirty();
    }

    /** Game identity validation stays here; the core only receives slot eligibility. */
    private IntPredicate eligibleWheelBindings() {
        var usedSpells = new HashSet<ResourceLocation>();
        return slot -> isStoredWheelTarget(slot)
                && usedSpells.add(MagicStats.magicId(slots.get(slot)));
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


    // ══════════════════════════════════════════════════════════════
    // 同步钩子
    // ══════════════════════════════════════════════════════════════

    /** 是否有待 MagicSync 推送的槽位、选择或冷却变化。 */
    public boolean isDirty() {
        return dirty || lastSentCapacity != getSlots();
    }

    /** 同步完成后清除脏标记。 */
    public void clearDirty() {
        dirty = false;
        lastSentCapacity = getSlots();
    }

    private void markDirty() {
        dirty = true;
    }

    public SpellInventorySnapshot snapshot() {
        sanitizeWheelSlots();
        return new SpellInventorySnapshot(slots, cooldowns.snapshot(), loadout.snapshot());
    }

    /** Explicit authoritative capacity avoids races with attribute synchronization on the client. */
    public void applyServerSnapshot(SpellInventorySnapshot snapshot, int capacity) {
        replicatedCapacity = MagicMath.slotCount(capacity);
        applySnapshot(snapshot);
        loadout.reconcileSelection(getSlots());
        loadout.sanitize(getSlots(), eligibleWheelBindings());
        lastSentCapacity = replicatedCapacity;
    }

    public void applySnapshot(SpellInventorySnapshot snapshot) {
        var items = snapshot.items();
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
        }
        cooldowns.load(snapshot.cooldowns());
        // Attributes/equipment may not be ready while Forge restores the capability.
        // Preserve valid stored bindings first; the authoritative tick reconciles actual capacity.
        loadout.load(snapshot.loadout(), BACKING_SIZE, eligibleWheelBindings());
        dirty = false;
    }
}
