package org.bytechen.hall.overworld.registry.capability.interfaces;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家的魔法槽位容器。
 *
 * <p>继承 {@link IItemHandler}，所以能用 Forge 的通用物品栏机制（GUI、漏斗、比较器）来读写；
 * 额外的方法都是"魔法"这个概念特有的：当前选中哪一格、冷却、去重。</p>
 *
 * <p><b>命名说明</b>：{@code getSlotnow()} / {@code changeSlot(int)} / {@code upDate()}
 * 是早期版本就有的名字，语义偏含糊但已被调用点使用，所以保留成"别名"并在下面写清含义，
 * 同时提供语义明确的新名字。新代码请用新名字。</p>
 */
public interface ByteItemHandle extends IItemHandler {

    /** 这个物品能不能放进魔法槽（目前要求实现 {@code IMagicBaseItem}）。 */
    boolean canAdd(Item item);

    /** 容器所属玩家。 */
    Player getOwner();

    /**
     * 相对移动选中槽位（会环绕）。
     * <p>等价于 {@link #selectRelative(int)}。用于"滚轮上下切法术"这类操作。
     */
    void changeSlot(int i);

    /** 当前选中的槽位下标。等价于 {@link #getSelectedSlot()}。 */
    int getSlotnow();

    /** 每 tick 推进冷却。等价于 {@link #tickCooldowns()}。 */
    void upDate();

    // ══════════════════════════════════════════════════════════════
    // 槽位选择
    // ══════════════════════════════════════════════════════════════
    // 只有"存取"，没有"怎么选"——按键绑定与选择 GUI 按需求先放着。
    // 外部（命令 / 以后的选择界面）直接调 setSelectedSlot 即可。

    /** 当前选中的槽位（释放法术时用的就是它）。 */
    int getSelectedSlot();

    /** 直接指定选中的槽位；越界会被环绕到合法范围。 */
    void setSelectedSlot(int slot);

    /** 相对移动选中槽位，环绕。 */
    void selectRelative(int delta);

    /** 下一格。 */
    void selectNext();

    /** 上一格。 */
    void selectPrev();

    /** 当前可用范围内的合法下标。 */
    boolean isValidSlot(int slot);

    // ══════════════════════════════════════════════════════════════
    // 去重
    // ══════════════════════════════════════════════════════════════

    /** 某个物品是否已经在任意一格（用于"槽位不能存重复魔法"）。 */
    boolean containsSpell(Item item);

    /** 某格的冷却键（物品注册名）；空格返回 {@code null}。 */
    @Nullable
    ResourceLocation magicIdAt(int slot);

    // ══════════════════════════════════════════════════════════════
    // 冷却（按玩家 + 物品注册名，所以同名物品共享）
    // ══════════════════════════════════════════════════════════════

    /** 该法术是否在冷却中。 */
    boolean onCooldown(@Nullable ResourceLocation magicId);

    /** 剩余冷却 tick；不在冷却返回 0。 */
    int getCooldownRemaining(@Nullable ResourceLocation magicId);

    /** 写入冷却；同名的已有冷却会被覆盖成较大者。 */
    void setCooldown(@Nullable ResourceLocation magicId, int ticks);

    /** 每 tick 递减所有冷却。服务端每 tick 调一次。 */
    void tickCooldowns();
}
