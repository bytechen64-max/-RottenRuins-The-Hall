package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    /** KEEP 效果触发计时器（与按秒扣蓝的 {@link #keepBillingTicks} 是两套）。 */
    private int keepPulseTicks;
    /** 是否"待触发第一次"（进入维持后的第一 tick 立刻触发一次）。 */
    private boolean keepPulsePending = true;
    /**
     * {@code KEEP} 维持期间<b>锁定</b>的目标。
     *
     * <p>在开始维持的那一刻（也就是启动扣蓝、进冷却的那一次）沿准星解析出来，
     * 之后整段维持都用它 —— 这就是"锁定"的含义：按住期间目标不会因为准星
     * 划过去而换成别人。没声明需要目标的法术恒为 {@code null}。</p>
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

    /**
     * 本次维持锁定的目标；不需要目标的法术、或不在维持中时返回 {@code null}。
     *
     * <p>调用方要注意它<b>可能已经死了或已卸载</b>（玩家一直按着不放的时候会发生）——
     * 用之前请自己看一眼 {@code isAlive()} / {@code isRemoved()}。</p>
     */
    @Nullable
    public LivingEntity getLockedTarget() {
        return lockedTarget;
    }

    /** 进入蓄力。 */
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

    /** 进入维持（不带锁定目标）。 */
    public void beginChanneling(@NotNull ItemStack spell, int slot, @NotNull InteractionHand hand) {
        beginChanneling(spell, slot, hand, null);
    }

    /**
     * 进入维持，并记下这一段维持锁定的目标。
     *
     * @param lockedTarget 需要目标的法术在启动那一刻解析出的目标；不需要时为 {@code null}
     */
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

    /**
     * 推进 KEEP 的<b>效果触发</b>计时器。
     *
     * <p>与 {@link #tickKeepBilling()}（按秒扣蓝）是两套独立的计时器。框架里因此有两条
     * 互不影响的耗蓝线：一条按<b>秒</b>扣（跟触发快慢无关），一条按<b>触发次数</b>扣
     * （这个计时器每返回一次 {@code true} 就扣一次）。法术只声明其中一条时，另一条为 0。</p>
     *
     * <p><b>相位</b>：进入维持后的第 1 tick 就返回 {@code true}（立刻触发一次），
     * 之后每 {@code interval} tick 一次。也就是"触发速度 2"命中的是第 1、3、5…… tick，
     * <b>不是</b> 1、2、3 —— 第一次立刻触发，但不会因此把间隔缩短成 1。</p>
     *
     * @param interval 触发速度（tick）；{@code <= 1} 表示每 tick 触发
     * @return 本 tick 是否应该触发一次效果
     */
    public boolean tickKeepPulse(int interval) {
        if (interval <= 1) {
            // 每 tick 触发：计时器没有意义，顺手清零免得下次换速度时相位错乱
            keepPulseTicks = 0;
            keepPulsePending = false;
            return true;
        }
        // 进入维持后的第一 tick：立刻来一次
        if (keepPulsePending) {
            keepPulsePending = false;
            keepPulseTicks = 0;
            return true;
        }
        // 之后每隔 interval 个 tick 一次
        if (++keepPulseTicks >= interval) {
            keepPulseTicks = 0;
            return true;
        }
        return false;
    }

    /** 回到 IDLE 并清掉所有引用。 */
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
    }
}
