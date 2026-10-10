package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 单个玩家的「当前释放状态」。
 *
 * <p>早期版本的这个类有几处不能工作的地方，已一并修正：</p>
 * <ol>
 *   <li>{@code phase} 字段<b>从来没有被赋过值</b>（一直是 {@code null}），
 *       于是 {@code if (state.phase != UsingPhase.IDLE) return;} 这句
 *       在 {@code null != IDLE} 下恒为真 —— 整个派发入口永久早退，法术一次也放不出来。
 *       现在默认值就是 {@code IDLE}。</li>
 *   <li>原来存的是 {@code CMagicBaseItem item}（物品<b>单例</b>）。法术要连 NBT 一起用，
 *       所以改存 {@link ItemStack}。</li>
 * </ol>
 *
 * <p>对象是可变的，由 {@code MagicHandle} 每 tick 驱动；一帧内不共享给别的线程。</p>
 */
public final class State {

    /** 释放阶段。 */
    public enum UsingPhase {
        /** 什么都没在做。 */
        IDLE,
        /** {@code CHARGE}：正在蓄力，等松手。 */
        CHARGING,
        /** {@code KEEP}：已经开始生效，正在维持。 */
        CHANNELING
    }

    /** {@code KEEP} 每秒结算一次维持耗蓝，这就是那个"一秒"的 tick 数。 */
    private static final int KEEP_BILLING_INTERVAL = 20;

    private UsingPhase phase = UsingPhase.IDLE;
    private ItemStack spell = ItemStack.EMPTY;
    private int slot = -1;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private int heldTicks;
    private int keepBillingTicks;

    @NotNull
    public UsingPhase getPhase() {
        return phase;
    }

    public boolean isIdle() {
        return phase == UsingPhase.IDLE;
    }

    public boolean isCharging() {
        return phase == UsingPhase.CHARGING;
    }

    public boolean isChanneling() {
        return phase == UsingPhase.CHANNELING;
    }

    /** 本次释放使用的法术堆（IDLE 时为空堆）。 */
    @NotNull
    public ItemStack getSpell() {
        return spell;
    }

    /** 法术来自哪个槽位；非槽位释放为 {@code -1}。 */
    public int getSlot() {
        return slot;
    }

    /** 触发用的那只手。 */
    @NotNull
    public InteractionHand getHand() {
        return hand;
    }

    /** 从按下到现在多少 tick。 */
    public int getHeldTicks() {
        return heldTicks;
    }

    public void setHeldTicks(int heldTicks) {
        this.heldTicks = Math.max(0, heldTicks);
    }

    /** 进入蓄力。 */
    public void beginCharging(@NotNull ItemStack spell, int slot, @NotNull InteractionHand hand) {
        this.phase = UsingPhase.CHARGING;
        this.spell = spell;
        this.slot = slot;
        this.hand = hand;
        this.heldTicks = 0;
        this.keepBillingTicks = 0;
    }

    /** 进入维持。 */
    public void beginChanneling(@NotNull ItemStack spell, int slot, @NotNull InteractionHand hand) {
        this.phase = UsingPhase.CHANNELING;
        this.spell = spell;
        this.slot = slot;
        this.hand = hand;
        this.heldTicks = 0;
        this.keepBillingTicks = 0;
    }

    /**
     * 推进维持计费计时器。
     *
     * @return 是否到了该结算这一秒的维持耗蓝
     */
    public boolean tickKeepBilling() {
        if (++keepBillingTicks < KEEP_BILLING_INTERVAL) {
            return false;
        }
        keepBillingTicks = 0;
        return true;
    }

    /** 回到 IDLE 并清掉所有引用。 */
    public void reset() {
        phase = UsingPhase.IDLE;
        spell = ItemStack.EMPTY;
        slot = -1;
        hand = InteractionHand.MAIN_HAND;
        heldTicks = 0;
        keepBillingTicks = 0;
    }
}
