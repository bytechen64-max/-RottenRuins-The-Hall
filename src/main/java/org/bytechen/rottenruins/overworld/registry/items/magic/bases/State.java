package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 单个玩家的「当前释放状态」；可变对象，由 {@code MagicHandle} 每 tick 驱动，不跨线程共享。
 */
public final class State {

    public enum UsingPhase {
        IDLE,
        CHARGING,
        CHANNELING
    }

    /** {@code KEEP} 每秒结算一次维持耗蓝，这就是那个"一秒"的 tick 数（20）。 */
    private static final int KEEP_BILLING_INTERVAL = 20;

    private UsingPhase phase = UsingPhase.IDLE;
    private ItemStack spell = ItemStack.EMPTY;
    private int slot = -1;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private int heldTicks;
    private int keepBillingTicks;
    private int keepPulseTicks;
    /** 是否"待触发第一次"（进入维持后的第一 tick 立刻触发一次）。 */
    private boolean keepPulsePending = true;
    /** 由轮盘发起：不走原版 useItem 生命周期，由 {@code MagicHandle#tickWheel} 每 tick 代为推进。 */
    private boolean wheelSession;
    /**
     * {@code KEEP} 维持期间锁定的目标：进入维持那一刻沿准星解析，整段维持不变（不需要目标时为 null）。
     * 调用方须自行检查 {@code isAlive()} / {@code isRemoved()}。
     */
    @Nullable
    private LivingEntity lockedTarget;

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

    @NotNull
    public ItemStack getSpell() {
        return spell;
    }

    /** 法术来自哪个槽位；非槽位释放为 {@code -1}。 */
    public int getSlot() {
        return slot;
    }

    @NotNull
    public InteractionHand getHand() {
        return hand;
    }

    public int getHeldTicks() {
        return heldTicks;
    }

    public boolean isWheelSession() {
        return wheelSession;
    }

    public void setWheelSession(boolean wheelSession) {
        this.wheelSession = wheelSession;
    }

    public void setHeldTicks(int heldTicks) {
        this.heldTicks = Math.max(0, heldTicks);
    }

    @Nullable
    public LivingEntity getLockedTarget() {
        return lockedTarget;
    }

    public void beginCharging(@NotNull ItemStack spell, int slot, @NotNull InteractionHand hand) {
        this.phase = UsingPhase.CHARGING;
        this.spell = spell;
        this.slot = slot;
        this.hand = hand;
        this.heldTicks = 0;
        this.keepBillingTicks = 0;
        this.keepPulseTicks = 0;
        this.keepPulsePending = true;
        // 蓄力的目标是在松手那一刻才解析的，所以蓄力期间没有"锁定目标"
        this.lockedTarget = null;
    }

    public void beginChanneling(@NotNull ItemStack spell, int slot, @NotNull InteractionHand hand) {
        beginChanneling(spell, slot, hand, null);
    }

    public void beginChanneling(@NotNull ItemStack spell, int slot, @NotNull InteractionHand hand,
                               @Nullable LivingEntity lockedTarget) {
        this.phase = UsingPhase.CHANNELING;
        this.spell = spell;
        this.slot = slot;
        this.hand = hand;
        this.heldTicks = 0;
        this.keepBillingTicks = 0;
        this.keepPulseTicks = 0;
        this.keepPulsePending = true;
        this.lockedTarget = lockedTarget;
    }

    public boolean tickKeepBilling() {
        if (++keepBillingTicks < KEEP_BILLING_INTERVAL) {
            return false;
        }
        keepBillingTicks = 0;
        return true;
    }

    /**
     * 推进 KEEP 的<b>效果触发</b>计时器，与按秒扣蓝的 {@link #tickKeepBilling()} 是两套独立计时器。
     * 相位：进入维持后第 1 tick 立刻返回 true，之后每 {@code interval} tick 一次
     * （触发速度 2 命中第 1、3、5… tick）；{@code interval <= 1} 表示每 tick 触发。
     */
    public boolean tickKeepPulse(int interval) {
        if (interval <= 1) {
            // 每 tick 触发：计时器没有意义，顺手清零免得下次换速度时相位错乱
            keepPulseTicks = 0;
            keepPulsePending = false;
            return true;
        }
        if (keepPulsePending) {
            keepPulsePending = false;
            keepPulseTicks = 0;
            return true;
        }
        if (++keepPulseTicks >= interval) {
            keepPulseTicks = 0;
            return true;
        }
        return false;
    }

    public void reset() {
        phase = UsingPhase.IDLE;
        spell = ItemStack.EMPTY;
        slot = -1;
        hand = InteractionHand.MAIN_HAND;
        heldTicks = 0;
        keepBillingTicks = 0;
        keepPulseTicks = 0;
        keepPulsePending = true;
        lockedTarget = null;
        wheelSession = false;
    }
}
