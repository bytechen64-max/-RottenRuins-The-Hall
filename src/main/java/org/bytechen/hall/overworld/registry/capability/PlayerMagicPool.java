package org.bytechen.hall.overworld.registry.capability;

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
import org.bytechen.hall.api.magic.MagicStats;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteItemHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 玩家的魔法池：<b>10 格法术槽</b> + <b>同名共享冷却</b> + 当前选中格。
 *
 * <h3>三条规则，以及它们各自在哪实现</h3>
 * <table border="1">
 *   <tr><th>规则</th><th>实现位置</th></tr>
 *   <tr><td>一个栏只能放一个魔法</td>
 *       <td>{@link #getSlotLimit(int)} 恒为 1；{@link #insertItem} 第 3 步直接拒绝已有内容</td></tr>
 *   <tr><td>槽位不能存重复的魔法</td>
 *       <td>{@link #isItemValid} 与 {@link #containsSpell}：任何一格已经有了同一种法术，别格就拒绝</td></tr>
 *   <tr><td>同一玩家的同名物品共享冷却</td>
 *       <td>{@link #cooldowns} 的键是<b>物品注册名</b>（不是 ItemStack、也不是 Item 实例），
 *           所以同一件法术放在 1 号还是 3 号槽是同一个冷却</td></tr>
 * </table>
 *
 * <h3>相对早期版本修掉的问题</h3>
 * <ol>
 *   <li><b>冷却不再存在物品上。</b> 原来 {@code upDate()} 递减的是
 *       {@code CMagicBaseItem.coolcount} —— 那是<b>注册表单例</b>的字段，
 *       结果甲放完法术乙也不能放。现在冷却按玩家存在这里。</li>
 *   <li><b>{@code getSlots()} 不再越界。</b> 原来返回 {@code handler.getSlots() + addition}，
 *       底层却是固定 10 格；{@code addition > 0} 时任何遍历到 {@code getSlots()} 的代码
 *       都会读越界。现在底层数组按 {@link MagicStats#MAX_SLOT_COUNT} 分配，
 *       {@code getSlots()} 由 {@link MagicStats#slotCount} 决定并做钳制。</li>
 *   <li><b>{@code changeSlot} 不再能选出非法下标。</b> 原来的环绕算式会算出负数、
 *       或正好等于 {@code getSlots()}。现在统一走 {@link #setSelectedSlot}，环绕与钳制在里面。</li>
 *   <li><b>槽位数收缩不再吞物品。</b> 属性修饰器调低槽位数时，
 *       {@link #ensureSlotCount()} 会把越界格子里的法术塞回背包（塞不下就掉在地上）。</li>
 * </ol>
 *
 * <h3>关于槽位选择</h3>
 * <p>按需求，<b>选择本身先放着</b>：这里只有"选中第几格"的存储与几个安全的移动方法，
 * 没有按键绑定、没有选择界面、也没有把选中格同步到客户端。外部（命令、以后的 GUI）
 * 直接调 {@link #setSelectedSlot(int)} 即可。</p>
 */
public class PlayerMagicPool implements ByteItemHandle, ICapabilitySerializable<CompoundTag> {

    /** NBT：槽内法术（{@code ContainerHelper} 的 {@code Items} 列表）。 */
    public static final String NBT_SLOTS = "Items";
    /** NBT：当前选中格。 */
    public static final String NBT_SELECTED = "HallMagicSelected";
    /** NBT：冷却表（键是物品注册名）。 */
    public static final String NBT_COOLDOWNS = "HallMagicCooldowns";

    /**
     * 底层数组长度。按最大值分配，这样属性修饰器扩容时不需要重建数组；
     * 可见格数始终由 {@link MagicStats#slotCount(Player)} 决定。
     */
    private static final int BACKING_SIZE = MagicStats.MAX_SLOT_COUNT;

    private final Player player;
    private final NonNullList<ItemStack> slots;
    /** 冷却表：物品注册名 → 剩余 tick。 */
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();
    private int selected;

    private final LazyOptional<PlayerMagicPool> self = LazyOptional.of(() -> this);

    /** 给以后接客户端同步用的脏标记。 */
    private boolean dirty;

    public PlayerMagicPool(@NotNull Player player) {
        this.player = player;
        this.slots = NonNullList.withSize(BACKING_SIZE, ItemStack.EMPTY);
    }

    // ══════════════════════════════════════════════════════════════
    // IItemHandler
    // ══════════════════════════════════════════════════════════════

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
        // 一个栏只放一个魔法
        if (!slots.get(slot).isEmpty()) {
            return stack;
        }
        if (!simulate) {
            slots.set(slot, stack.copyWithCount(1));
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
            markDirty();
        }
        return taken;
    }

    /** 一个栏只能放一个魔法。 */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /**
     * 能否放进这一格：是法术 + 不在别格重复。
     * <p>注意 {@code simulate} 也走这里，所以"预览放置"同样遵守去重规则。</p>
     */
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

    // ══════════════════════════════════════════════════════════════
    // ByteItemHandle
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean canAdd(Item item) {
        return item instanceof org.bytechen.hall.overworld.registry.items.magic.bases.IMagicBaseItem;
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

    // ---- 槽位选择 ----

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

    // ---- 去重 ----

    @Override
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

    @Override
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

    // ---- 冷却 ----

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

        // 只有服务端才做"驱逐越界物品"与"标脏"。
        // 客户端那份是同步过来的镜像 —— 往客户端玩家的背包里塞东西是错的
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

    /**
     * 把"已经不在可用范围"的格子清空（槽位数被属性修饰器调小时会用到）。
     *
     * <p>刻意把法术<b>还给玩家</b>而不是直接丢掉 —— 玩家不会因为换了件装备
     * 就莫名其妙损失法术。背包塞不下才掉在脚下。</p>
     *
     * @return 是否有内容被挪走
     */
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

    // ══════════════════════════════════════════════════════════════
    // 同步钩子
    // ══════════════════════════════════════════════════════════════

    /**
     * 是否有未同步的改动。
     * <p>留给以后接客户端同步用（法术槽与法力都需要同步，但按需求这一轮只管服务端）。</p>
     */
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

    // ══════════════════════════════════════════════════════════════
    // 能力提供者 / 序列化
    // ══════════════════════════════════════════════════════════════

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

    /** 实体被移除时由 Forge 调用（经由 {@code invalidateCaps}）。 */
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
        dirty = false;
    }
}
