package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.bytechen.rottenruins.api.magic.MagicCastEvent;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.capability.TwistedPoint;
import org.bytechen.rottenruins.utils.ModUtils;
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
 *   往下走 = 【需要目标就跑一次准星射线，解不到就 CastResult.NO_TARGET（不扣费、不进 CD）】
 *          → 抛 MagicCastEvent（可取消/可改耗蓝）
 *          → 校验并扣除法力（不够就 CastResult.NOT_ENOUGH_MANA，不扣、不进冷却）
 *          → 写入冷却（按物品注册名）
 *          → 调 IMagicBaseItem#cast 出效果
 *          → 抛 MagicCastEvent.Post
 * </pre>
 *
 * <p><b>被动法术不在这条路径上</b>：{@link SpellKind#PASSIVE} 的法术放进魔法槽就生效，
 * 不消耗法力也不进冷却。{@code handleUseStart} 选中它时会以
 * {@code CastResult.PASSIVE_SPELL} 直接结束，不会调用 {@link IMagicBaseItem#cast}。
 * 被动效果的实际驱动（槽位 tick / 放入取出时的 equip / unequip）不在这里。</p>
 *
 * <h3>目标是怎么来的</h3>
 * <p>只有法术声明 {@code needsTarget()} 为 {@code true} 时才解析，时机是<b>真正释放的那一刻</b>
 * —— {@code IMMEDIATELY} / {@code KEEP} 是按下时，{@code CHARGE} 是松手时（所以蓄力期间瞄准有用）。
 * 解法见 {@link #resolveTarget}：从眼睛沿准星射线，<b>被方块挡住就打不到</b>，
 * 距离上限由法术声明并可被属性/附魔改。解不到目标时，{@code allowSelfCast()} 为
 * {@code true} 就退化成对自己释放，否则返回 {@code NO_TARGET}。</p>
 *
 * <p>{@code KEEP} 模式的目标在开始维持时解析一次并<b>锁定</b>整段维持
 * （见 {@link State#getLockedTarget()}），不会跟着准星换人。</p>
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

        // 槽位选择由客户端界面 MagicScreen（默认 M 键）写入；
        // 这里只读取当前选中格，不负责"怎么选"。
        int slot = pool.getSelectedSlot();
        if (!pool.isValidSlot(slot)) {
            return new UseStartOutcome(CastResult.INVALID_SLOT, false);
        }

        ItemStack spellStack = pool.getStackInSlot(slot);
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        if (spell == null) {
            return new UseStartOutcome(CastResult.NO_SPELL, false);
        }

        // 被动法术不走释放路径：放进槽里就持续生效，由后续的槽位框架驱动。
        // 没有这一条的话，被动法术会被它那个占位的 UsingType 当成主动法术放出去。
        if (spell.isPassive()) {
            return new UseStartOutcome(CastResult.PASSIVE_SPELL, false);
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
                    castNow(player, hand, slot, spellStack, spell, 1.0f, 0).result(), false);

            case CHARGE -> {
                state.beginCharging(spellStack, slot, hand);
                yield new UseStartOutcome(CastResult.SUCCESS, true);
            }

            case KEEP -> {
                CastOutcome outcome = castNow(player, hand, slot, spellStack, spell, 1.0f, 0);
                if (outcome.result().isSuccess()) {
                    // 需要目标的法术：把启动那一刻锁定的目标带进维持，整段维持都用它
                    state.beginChanneling(spellStack, slot, hand, outcome.target());
                    yield new UseStartOutcome(CastResult.SUCCESS, true);
                }
                yield new UseStartOutcome(outcome.result(), false);
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
     *   <li>{@code CHANNELING}：这里有<b>两套互相独立</b>的计时器与两条独立的耗蓝线 ——
     *       <b>按秒</b>结算 {@code baseKeepCostPerSecond()}（法力不足就结束维持），
     *       以及<b>按触发速度</b>回调 {@code onKeepTick} 并按次数结算
     *       {@code baseKeepCostPerTrigger()}（"生效有间隔，扣费也就有间隔"）。
     *       两条线各自独立，法术通常只用其中一条。</li>
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

            // 维持耗蓝：每秒一次。**与触发速度无关** —— 这一条只管"维持本身要付的代价"。
            // 另一条（每次生效扣一次）在下面跟着触发节奏走，两者刻意解耦。
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

            // 效果触发：**按法术声明的触发速度**走，不是每 tick。
            // 1 = 每 tick（默认）、2 = 每 2 tick…… 见 IMagicBaseItem#baseKeepTriggerInterval。
            if (spell != null) {
                int interval = MagicStats.keepTriggerInterval(player, spellStack);
                if (state.tickKeepPulse(interval)) {
                    // 这一段维持锁定的目标（不需要目标的法术为 null）。
                    // 可能在维持期间死了 —— 交给法术自己判断要不要收手。
                    LivingEntity target = state.getLockedTarget();

                    // 1) 先出效果。允许返回 false 主动收手。
                    boolean keepGoing = spell.onKeepTick(player, spellStack, heldTicks, interval, target);

                    // 2) 再扣"这一次生效"的法力 —— 生效有间隔，扣费也就有间隔。
                    int perTrigger = MagicStats.keepCostPerTrigger(player, spellStack);
                    if (perTrigger > 0) {
                        TwistedPoint mana = getMana(player);
                        if (mana != null && !mana.spend(perTrigger)) {
                            // 扣不动了 → 维持结束。这一发已经打出去了，从下一发起不再发生。
                            stopChannel(player, state);
                            return;
                        }
                    }

                    if (!keepGoing) {
                        stopChannel(player, state);
                    }
                }
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
            // 需要目标的法术在这里才解析目标（松手那一刻），所以蓄力期间可以瞄准。
            return castNow(player, usedHand, slot, spellStack, spell, ratio, heldTicks).result();
        }

        // CHANNELING
        stopChannel(player, state);
        return CastResult.SUCCESS;
    }

    // ══════════════════════════════════════════════════════════════
    // 目标解析
    // ══════════════════════════════════════════════════════════════

    /**
     * 沿施法者<b>准星</b>解算本次要锁定的目标；解不到时按法术的
     * {@code allowSelfCast()} 决定是返回施法者自己还是 {@code null}。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>射线从<b>眼睛</b>出发，长度 = {@code MagicStats.targetRange(...)}（法术声明，
     *       可被属性/附魔折叠，钳在 {@code [0, 128]}）。</li>
     *   <li>会被<b>方块阻挡</b>：瞄准墙后的生物锁不上（用的是原版
     *       {@code ProjectileUtil.getHitResultOnViewVector}，和弓箭的判定同一套）。</li>
     *   <li>只考虑<b>活着的、可被选中的、不是旁观者</b>的生物，且永远<b>不含施法者自己</b>
     *       —— 否则开启自我释放的法术会永远锁到自己身上，准星就没意义了。</li>
     *   <li>没锁到时：{@code allowSelfCast()} 为 {@code true} → 返回施法者；
     *       为 {@code false} → 返回 {@code null}（调用方应该以
     *       {@link CastResult#NO_TARGET} 结束，不扣蓝不进冷却）。</li>
     * </ul>
     *
     * <p><b>公开</b>这个方法是有意的：{@code KEEP} 模式的目标在开始维持时就锁定了，
     * 法术若想做"光束跟着准星走"，可以在每次 {@code onKeepTick} 里自己调一次它重新解，
     * 而不用重新实现一套射线。</p>
     *
     * @param spellStack 法术堆（提供射程与 {@code allowSelfCast} 策略）
     * @return 锁定到的目标；解不到且不允许自我释放时为 {@code null}
     */
    @Nullable
    public static LivingEntity resolveTarget(@NotNull ServerPlayer caster,
                                             @NotNull ItemStack spellStack) {
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        if (spell == null) {
            return null;
        }
        boolean selfCast = spell.allowSelfCast();

        double range = MagicStats.targetRange(caster, spellStack);
        if (range <= 0.0d) {
            // 射程为 0（或被修饰器减到 0）→ 射线长度为 0，必然什么都打不到
            return selfCast ? caster : null;
        }

        HitResult hit = ProjectileUtil.getHitResultOnViewVector(caster,
                entity -> isTargetable(caster, entity), range);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living.isAlive()) {
            return living;
        }
        return selfCast ? caster : null;
    }

    /** 某个实体能不能被锁定：活着的生物、可被选中、非旁观者、不是施法者本人。 */
    private static boolean isTargetable(@NotNull ServerPlayer caster, @NotNull Entity entity) {
        if (entity == caster || entity.isSpectator() || !entity.isPickable()) {
            return false;
        }
        return entity instanceof LivingEntity living && living.isAlive();
    }

    /**
     * 沿施法者<b>准星</b>解算"指定的那个<b>位置</b>" —— 给需要落点的法术用
     * （天降之物、地面 AoE、传送落点…）。
     *
     * <p>和 {@link #resolveTarget} 的区别只有一点：那个返回"锁定的<b>目标</b>"（没有就是
     * {@code null} 或自己），这个返回"准星射线打到的<b>坐标</b>"，永远有值。</p>
     *
     * <p>取值规则：</p>
     * <ul>
     *   <li>准星先打生物（活着的、非旁观、非施法者本人）→ 返回那个生物身上的命中点；</li>
     *   <li>否则走方块射线（会被墙挡住）→ 返回方块受击面；</li>
     *   <li>两者都没打到（对着天）→ 返回射线上距离 {@code range} 的那一点。
     *       所以"对着天上放"是有意义的：落点就在空中。</li>
     * </ul>
     *
     * <p>{@code range} 一般传 {@code MagicStats.targetRange(caster, spellStack)}
     * —— 那样射程就能被属性/附魔改；法术也可以在 {@code baseTargetRange()} 里给个更大的默认值。</p>
     *
     * @return 准星指定的位置；{@code range <= 0} 时返回施法者眼睛的位置
     */
    @NotNull
    public static Vec3 resolveAimPoint(@NotNull ServerPlayer caster, double range) {
        if (range <= 0.0d) {
            return caster.getEyePosition();
        }
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(caster,
                entity -> isTargetable(caster, entity), range);
        return hit.getLocation();
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    /**
     * 一次释放的结果：除了结果码，还要把<b>锁定的目标</b>带出来。
     *
     * <p>只为了 {@code KEEP} 模式需要 —— 它要把这次解析到的目标记进
     * {@link State} 供整段维持使用。其它模式只看 {@code result()}。</p>
     */
    private record CastOutcome(@NotNull CastResult result, @Nullable LivingEntity target) {
    }

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
     * 真正的一次释放：解目标 → 事件 → 扣蓝 → 进冷却 → 出效果 → Post 事件。
     *
     * <p>走到这里说明冷却、{@code canCast} 已经过了（蓄力型的"蓄满"也过了）。</p>
     */
    @NotNull
    private static CastOutcome castNow(@NotNull ServerPlayer player, @NotNull InteractionHand hand,
                                       int slot, @NotNull ItemStack spellStack,
                                       @NotNull IMagicBaseItem spell,
                                       float chargeRatio, int heldTicks) {
        // ---- 0. 目标解析（只对声明了 needsTarget 的法术做）----
        // 放在最前面：瞄空时连事件都不该抛、法力冷却都不该动。
        LivingEntity target = null;
        if (spell.needsTarget()) {
            target = resolveTarget(player, spellStack);
            if (target == null) {
                return new CastOutcome(CastResult.NO_TARGET, null);
            }
        }

        int cost = MagicStats.manaCost(player, spellStack);

        // ---- 1. 释放前事件：可取消、可改耗蓝。取消时法力与冷却都不动 ----
        MagicCastEvent event = new MagicCastEvent(player, spellStack, spell.getUsingType(),
                hand, slot, chargeRatio, target, cost);
        if (MinecraftForge.EVENT_BUS.post(event) || event.isCanceled()) {
            return new CastOutcome(CastResult.CANCELLED, target);
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
                return new CastOutcome(CastResult.NOT_ENOUGH_MANA, target);
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
        spell.cast(player, spellStack,
                new MagicCastContext(slot, hand, heldTicks, chargeRatio, target));

        // ---- 5. 释放后事件：不可取消，用来挂后续逻辑 ----
        MinecraftForge.EVENT_BUS.post(new MagicCastEvent.Post(player, spellStack,
                spell.getUsingType(), cost, chargeRatio, target, CastResult.SUCCESS));

        return new CastOutcome(CastResult.SUCCESS, target);
    }
}
