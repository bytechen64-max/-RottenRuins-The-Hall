package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.hall.api.magic.CastResult;
import org.bytechen.hall.api.magic.MagicCastEvent;
import org.bytechen.hall.api.magic.MagicStats;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.hall.overworld.registry.capability.TwistedPoint;
import org.bytechen.hall.utils.ModUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法术释放的<b>总调度</b>。
 *
 * <p>{@link StaffBase} 把所有逻辑委托到这里，所以"按下 → 蓄力/维持 → 松手 → 扣蓝 → 进冷却 → 出效果"
 * 这条时间轴只有一份实现，三种释放模式共用它。</p>
 *
 * <h3>一次成功释放的完整顺序</h3>
 * <pre>
 *   解析法杖 → 取选中槽位的法术 → 法杖流派匹配 → 法术自身前置(canCast)
 *   → 冷却检查（同名物品共享）
 *   ├─ IMMEDIATELY：立刻往下走
 *   ├─ CHARGE     ：进入蓄力，等松手；松手时若已蓄满才往下走
 *   └─ KEEP       ：往下走，成功后进入"维持"
 *
 *   往下走 = 抛 MagicCastEvent（可取消/可改耗蓝）
 *          → 校验并扣除法力（不够就 CastResult.NOT_ENOUGH_MANA，不扣、不进冷却）
 *          → 写入冷却（按物品注册名）
 *          → 调 IMagicBaseItem#cast 出效果
 *          → 抛 MagicCastEvent.Post
 * </pre>
 *
 * <h3>为什么"检查"要在"扣费"之前分两处做</h3>
 * <p>冷却 / {@code canCast} / 法力余额都在<b>按下时</b>就先查一遍，
 * 目的是让玩家立刻知道自己能不能放（蓄力两秒之后才告诉他"没蓝"是很糟的手感）。
 * 但 {@code MagicCastEvent} 的监听者可以改耗蓝，所以事件之后必须<b>再查一次</b>法力余额
 * —— 那一次才是真正决定扣不扣的。</p>
 *
 * <h3>服务端权威</h3>
 * <p>本类的所有方法只在<b>服务端</b>产生效果。客户端虽然会走进 {@link StaffBase#use}，
 * 但那里只负责起手动画。</p>
 */
public final class MagicHandle {

    /** 每个玩家的释放状态。只有服务端线程会写，用并发容器只是为了读取端安全。 */
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** 法力 capability 缺失的告警只打一次，避免刷屏。 */
    private static volatile boolean warnedMissingManaCap;

    private MagicHandle() {
    }

    // ══════════════════════════════════════════════════════════════
    // 状态
    // ══════════════════════════════════════════════════════════════

    /** 取（必要时创建）该玩家的释放状态。 */
    @NotNull
    public static State getState(@NotNull net.minecraft.world.entity.player.Player player) {
        return STATES.computeIfAbsent(player.getUUID(), key -> new State());
    }

    /**
     * 读状态但<b>不创建</b>；没有就返回 {@code null}。
     * <p>给"只想看看在不在施法"的调用方用（例如 HUD、别的模组），避免顺手造出一堆空状态。</p>
     */
    @Nullable
    public static State peekState(@NotNull net.minecraft.world.entity.player.Player player) {
        return STATES.get(player.getUUID());
    }

    /** 玩家登出 / 换维度时清掉状态，避免这条 UUID 一直挂在表里。 */
    public static void clearState(@NotNull net.minecraft.world.entity.player.Player player) {
        STATES.remove(player.getUUID());
    }

    /** 服务器停止时全清。 */
    public static void clearAll() {
        STATES.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // 数据访问（给 HUD / 命令 / 别的模组用）
    // ══════════════════════════════════════════════════════════════

    /** 取玩家的魔法槽位容器；没挂载能力时返回 {@code null}。 */
    @Nullable
    public static PlayerMagicPool getPool(@NotNull net.minecraft.world.entity.player.Player player) {
        return player.getCapability(CapabilityRegistry.MAGIC_POOL_CAP).orElse(null);
    }

    /** 取玩家的法力池；没挂载能力时返回 {@code null}。 */
    @Nullable
    public static TwistedPoint getMana(@NotNull net.minecraft.world.entity.player.Player player) {
        return player.getCapability(CapabilityRegistry.MANA_CAP).orElse(null);
    }

    /**
     * 当前选中槽位里的法术堆。<b>双端可用</b>。
     *
     * <p>客户端读到的是同步过来的副本（见 {@code MagicSync}），所以客户端也能做
     * "按当前选中的法术显示不同准星/粒子"这类表现 —— 不需要自己再开一条同步通道。</p>
     *
     * <p>没挂载能力、或选中格为空时返回 {@link ItemStack#EMPTY}。</p>
     */
    @NotNull
    public static ItemStack selectedSpell(@NotNull net.minecraft.world.entity.player.Player player) {
        PlayerMagicPool pool = getPool(player);
        if (pool == null) {
            return ItemStack.EMPTY;
        }
        int slot = pool.getSelectedSlot();
        return pool.isValidSlot(slot) ? pool.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════
    // 对外入口（由 StaffBase 转发）
    // ══════════════════════════════════════════════════════════════

    /**
     * 开始释放的结果。
     *
     * @param result    这次开始的结果
     * @param keepUsing 调用方是否应该进入"使用中"状态（蓄力 / 维持需要；立刻释放不需要）
     */
    public record UseStartOutcome(@NotNull CastResult result, boolean keepUsing) {
    }

    /**
     * 按下时调用（等价于旧名 {@link #clientOnServerUsing}）。
     *
     * @see MagicHandle 类注释里那条完整时序
     */
    @NotNull
    public static UseStartOutcome handleUseStart(@NotNull ServerPlayer player,
                                                 @NotNull InteractionHand hand) {
        ItemStack staffStack = player.getItemInHand(hand);
        if (!(staffStack.getItem() instanceof StaffBase staff)) {
            return new UseStartOutcome(CastResult.INVALID_STAFF, false);
        }

        PlayerMagicPool pool = getPool(player);
        if (pool == null) {
            return new UseStartOutcome(CastResult.INVALID_SLOT, false);
        }

        // 槽位选择目前只有存储与取用，"怎么选"（按键 / GUI）按需求先放着
        int slot = pool.getSelectedSlot();
        if (!pool.isValidSlot(slot)) {
            return new UseStartOutcome(CastResult.INVALID_SLOT, false);
        }

        ItemStack spellStack = pool.getStackInSlot(slot);
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        if (spell == null) {
            return new UseStartOutcome(CastResult.NO_SPELL, false);
        }

        // 法杖流派是否接受这件法术
        if (!staff.accepts(staffStack, spell)) {
            return new UseStartOutcome(CastResult.REJECTED, false);
        }

        // 冷却：同名物品共享（键是物品注册名，不是堆、也不是物品实例）
        ResourceLocation magicId = MagicStats.magicId(spellStack);
        if (magicId != null && pool.onCooldown(magicId)) {
            return new UseStartOutcome(CastResult.ON_COOLDOWN, false);
        }

        // 法术自身的前置
        if (!spell.canCast(player, spellStack)) {
            return new UseStartOutcome(CastResult.REJECTED, false);
        }

        // 法力余额"预检"：蓄力型法术也要立刻告诉玩家没蓝，而不是让他蓄两秒再失败
        int previewCost = MagicStats.manaCost(player, spellStack);
        TwistedPoint mana = getMana(player);
        if (mana != null && previewCost > 0 && mana.getMana() < previewCost) {
            return new UseStartOutcome(CastResult.NOT_ENOUGH_MANA, false);
        }

        State state = getState(player);
        return switch (spell.getUsingType()) {
            case IMMEDIATELY -> new UseStartOutcome(
                    castNow(player, hand, slot, spellStack, spell, 1.0f, 0), false);

            case CHARGE -> {
                state.beginCharging(spellStack, slot, hand);
                yield new UseStartOutcome(CastResult.SUCCESS, true);
            }

            case KEEP -> {
                CastResult result = castNow(player, hand, slot, spellStack, spell, 1.0f, 0);
                if (result.isSuccess()) {
                    state.beginChanneling(spellStack, slot, hand);
                    yield new UseStartOutcome(CastResult.SUCCESS, true);
                }
                yield new UseStartOutcome(result, false);
            }
        };
    }

    /**
     * 旧名字，保留以免外部调用点失效。等价于
     * {@code handleUseStart(player, hand).result()}。
     */
    @NotNull
    public static CastResult clientOnServerUsing(@NotNull ServerPlayer player,
                                                 @NotNull InteractionHand hand) {
        return handleUseStart(player, hand).result();
    }

    /**
     * 使用中每 tick 调用（服务端）。
     *
     * <ul>
     *   <li>{@code CHARGING}：累加 tick 并回调法术的 {@code onChargeTick}；<b>不扣蓝</b>。</li>
     *   <li>{@code CHANNELING}：每秒结算一次维持耗蓝；法力不足或法术自己喊停就结束维持。</li>
     * </ul>
     */
    public static void handleUseTick(@NotNull ServerPlayer player, @NotNull InteractionHand hand,
                                     int heldTicks) {
        State state = peekState(player);
        if (state == null || state.isIdle()) {
            return;
        }
        // 手被换掉了（换手 / 切物品）→ 直接结束，避免状态挂着。
        // 用 state 里记下的那只手做判断，而不是 player.getUsedItemHand()：
        // 后者依赖原版 useItem 字段的生命周期（releaseUsing 期间还没清空、之后才清），
        // 而 state 是我们自己在按下那一刻记下来的，语义更硬。
        if (state.getHand() != hand) {
            stopChannel(player, state);
            return;
        }

        ItemStack spellStack = state.getSpell();
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);

        if (state.isCharging()) {
            state.setHeldTicks(heldTicks);
            if (spell != null) {
                spell.onChargeTick(player, spellStack, heldTicks);
            }
            return;
        }

        if (state.isChanneling()) {
            state.setHeldTicks(heldTicks);

            // 维持耗蓝：每秒一次
            if (state.tickKeepBilling()) {
                int perSecond = MagicStats.keepCostPerSecond(player, spellStack);
                if (perSecond > 0) {
                    TwistedPoint mana = getMana(player);
                    if (mana != null && !mana.spend(perSecond)) {
                        // 法力不够 → 维持到此为止（启动那笔已经扣过了，进冷却）
                        stopChannel(player, state);
                        return;
                    }
                }
            }

            if (spell != null && !spell.onKeepTick(player, spellStack, heldTicks)) {
                stopChannel(player, state);
            }
        }
    }

    /**
     * 松手时调用（服务端）。
     *
     * <ul>
     *   <li>蓄力中：蓄满才真正释放；没蓄满返回 {@link CastResult#NOT_CHARGED}，
     *       <b>不扣蓝、不进冷却</b>。</li>
     *   <li>维持中：结束维持并回调 {@code onKeepStop}（法力与冷却在开始维持时就已结算）。</li>
     * </ul>
     */
    @NotNull
    public static CastResult handleUseRelease(@NotNull ServerPlayer player,
                                              @NotNull InteractionHand hand,
                                              int heldTicks) {
        State state = peekState(player);
        if (state == null || state.isIdle()) {
            return CastResult.NOT_ACTIVE;
        }

        if (state.isCharging()) {
            ItemStack spellStack = state.getSpell();
            int slot = state.getSlot();
            // 同理：用 state 里记下的手，不依赖 getUsedItemHand() 在原版释放流程里的时序
            InteractionHand usedHand = state.getHand();
            IMagicBaseItem spell = MagicStats.spellOf(spellStack);
            state.reset();

            if (spell == null) {
                return CastResult.NO_SPELL;
            }

            // ── 「蓄力提前放效果减弱」就在这里 ──
            // chargeRatio = 已按 tick / 蓄满所需 tick，钳在 [0,1]。
            float ratio = MagicStats.chargeRatio(player, spellStack, heldTicks);
            float minRatio = MagicStats.minChargeRatio(player, spellStack);

            // 连最低门槛都没到 → 这次作废：不扣蓝、不进冷却（否则"手抖点一下"就白扣）
            if (ratio <= 0f || ratio < minRatio) {
                return CastResult.NOT_CHARGED;
            }

            // 到门槛了 → 照常放出去。威力由法术自己按 ctx.chargeRatio() 缩放：
            // 蓄满 = 1.0 全额；提前放 = 同比例减弱。耗蓝仍按全额收
            // —— 和原版弓一样（提前松手伤害低，但箭照样消耗）。
            return castNow(player, usedHand, slot, spellStack, spell, ratio, heldTicks);
        }

        // CHANNELING
        stopChannel(player, state);
        return CastResult.SUCCESS;
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    /**
     * 结束维持：<b>无条件</b>回调法术的 {@code onKeepStop}，再清状态。
     *
     * <p>刻意不做"该不该通知"的判断：无论是因为松手、法力耗尽、法术自己喊停，
     * 还是手被换掉，法术那边都可能挂着一个需要收尾的东西（例子：正在淡出的光束、
     * 一个"正在施法"的标记、一段循环音效）。漏一次回调就会永久留下残留，
     * 而多一次回调最多是重复清理。所以这里统一通知。</p>
     */
    private static void stopChannel(@NotNull ServerPlayer player, @NotNull State state) {
        ItemStack spellStack = state.getSpell();
        int held = state.getHeldTicks();
        state.reset();
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        if (spell != null) {
            spell.onKeepStop(player, spellStack, held);
        }
    }

    /**
     * 真正的一次释放：事件 → 扣蓝 → 进冷却 → 出效果 → Post 事件。
     *
     * <p>走到这里说明冷却、{@code canCast} 已经过了（蓄力型的"蓄满"也过了）。</p>
     */
    @NotNull
    private static CastResult castNow(@NotNull ServerPlayer player, @NotNull InteractionHand hand,
                                      int slot, @NotNull ItemStack spellStack,
                                      @NotNull IMagicBaseItem spell,
                                      float chargeRatio, int heldTicks) {
        int cost = MagicStats.manaCost(player, spellStack);

        // ---- 1. 释放前事件：可取消、可改耗蓝。取消时法力与冷却都不动 ----
        MagicCastEvent event = new MagicCastEvent(player, spellStack, spell.getUsingType(),
                hand, slot, chargeRatio, cost);
        if (MinecraftForge.EVENT_BUS.post(event) || event.isCanceled()) {
            return CastResult.CANCELLED;
        }
        cost = event.getManaCost();

        // ---- 2. 扣蓝（事件可能改过数值，所以这里才是权威判定）----
        TwistedPoint mana = getMana(player);
        if (cost > 0) {
            if (mana == null) {
                // 能力没挂上时**放行**而不是拒绝：宁可法术能放，也不要因为挂载出问题
                // 让整个系统静默失效。这里警告一次，方便排查。
                if (!warnedMissingManaCap) {
                    warnedMissingManaCap = true;
                    ModUtils.LOGGER.warn("[MagicHandle] 玩家缺少法力能力({}), 本次按 0 消耗放行；"
                            + "请检查 ForgeEventHelpers.handleAttachCapabilities 是否给 Player 挂了 MANA_CAP",
                            CapabilityRegistry.MANA_CAP.getName());
                }
            } else if (!mana.spend(cost)) {
                return CastResult.NOT_ENOUGH_MANA;
            }
        }

        // ---- 3. 冷却：键是物品注册名，所以同名物品天然共享 ----
        ResourceLocation magicId = MagicStats.magicId(spellStack);
        if (magicId != null) {
            int cooldown = MagicStats.cooldownTicks(player, spellStack);
            if (cooldown > 0) {
                PlayerMagicPool pool = getPool(player);
                if (pool != null) {
                    pool.setCooldown(magicId, cooldown);
                }
            }
        }

        // ---- 4. 出效果 ----
        spell.cast(player, spellStack, new MagicCastContext(slot, hand, heldTicks, chargeRatio));

        // ---- 5. 释放后事件：不可取消，用来挂后续逻辑 ----
        MinecraftForge.EVENT_BUS.post(new MagicCastEvent.Post(player, spellStack,
                spell.getUsingType(), cost, chargeRatio, CastResult.SUCCESS));

        return CastResult.SUCCESS;
    }
}
