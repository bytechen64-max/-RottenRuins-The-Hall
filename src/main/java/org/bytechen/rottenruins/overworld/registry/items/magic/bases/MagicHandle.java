package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.bytechen.rottenruins.api.magic.MagicCastEvent;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.WandEnchantments;
import org.bytechen.rottenruins.overworld.registry.items.magic.WandItem;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.capability.TwistedPoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法术释放的<b>总调度</b>：{@link StaffBase} 全部委托到这里，三种释放模式共用这一条时间轴。
 * 顺序：解析法杖 → 取槽位法术 → 流派匹配 → {@code canCast} → 冷却检查 →（需要目标就跑准星射线）
 * → 抛 {@link MagicCastEvent} → 校验扣蓝 → 写冷却 → {@code cast} 出效果 → Post 事件。
 * 被动法术（{@link SpellKind#PASSIVE}）不走这条路径，选中即以 {@code PASSIVE_SPELL} 结束。
 * 目标只在真正释放那一刻解析；{@code KEEP} 在开始维持时解析一次并锁定整段。
 * 冷却 / {@code canCast} / 法力在按下时预检（手感），但事件可改耗蓝，所以事件之后那次才是权威判定。
 * 所有方法只在<b>服务端</b>产生效果；客户端走进 {@link StaffBase#use} 只负责起手动画。
 */
public final class MagicHandle {

    /** 每个玩家的释放状态。只有服务端线程会写，用并发容器只是为了读取端安全。 */
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private MagicHandle() {
    }

    private static int discountedCost(ServerPlayer player, InteractionHand hand, int base) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof StaffBase) || base <= 0) return Math.max(0, base);
        int level = EnchantmentHelper.getItemEnchantmentLevel(WandEnchantments.EFFICIENCY.get(), held);
        return Math.max(0, (int) Math.ceil(base * (1.0d - 0.05d * Math.min(5, level))));
    }

    private static boolean canPay(ServerPlayer player, InteractionHand hand,
                                  IMagicBaseItem spell, int cost) {
        if (cost <= 0) return true;
        ItemStack held = player.getItemInHand(hand);
        TwistedPoint mana = getMana(player);
        if (held.getItem() instanceof WandItem wand && wand.usesReserve(spell)) {
            int shortfall = Math.max(0, cost - wand.mana(held));
            return shortfall == 0 || (mana != null && mana.getMana() >= shortfall);
        }
        return mana != null && mana.getMana() >= cost;
    }

    private static boolean pay(ServerPlayer player, InteractionHand hand,
                               IMagicBaseItem spell, int cost) {
        if (cost <= 0) return true;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof WandItem wand && wand.usesReserve(spell)) {
            int available = wand.mana(held);
            int shortfall = Math.max(0, cost - available);
            if (shortfall > 0) {
                TwistedPoint mana = getMana(player);
                if (mana == null || !mana.spend(shortfall)) return false;
            }
            int remaining = Math.max(0, available - cost);
            wand.setMana(held, remaining);
            if (remaining == 0) WandItem.empty(player, hand, held, RegisterItem.WAND.get());
            return true;
        }
        TwistedPoint mana = getMana(player);
        return mana != null && mana.spend(cost);
    }

    // 状态

    /** 取（必要时创建）该玩家的释放状态。 */
    @NotNull
    public static State getState(@NotNull net.minecraft.world.entity.player.Player player) {
        return STATES.computeIfAbsent(player.getUUID(), key -> new State());
    }

    /** 读状态但<b>不创建</b>，没有就返回 null：给 HUD / 别的模组这类"只想看看在不在施法"的调用方用。 */
    @Nullable
    public static State peekState(@NotNull net.minecraft.world.entity.player.Player player) {
        return STATES.get(player.getUUID());
    }

    public static void clearState(@NotNull net.minecraft.world.entity.player.Player player) {
        STATES.remove(player.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }

    @Nullable
    public static PlayerMagicPool getPool(@NotNull net.minecraft.world.entity.player.Player player) {
        return player.getCapability(CapabilityRegistry.MAGIC_POOL_CAP).orElse(null);
    }

    @Nullable
    public static TwistedPoint getMana(@NotNull net.minecraft.world.entity.player.Player player) {
        return player.getCapability(CapabilityRegistry.MANA_CAP).orElse(null);
    }

    /** 当前选中槽位里的法术堆，<b>双端可用</b>（客户端读到的是同步副本，见 {@code MagicSync}）。 */
    @NotNull
    public static ItemStack selectedSpell(@NotNull net.minecraft.world.entity.player.Player player) {
        PlayerMagicPool pool = getPool(player);
        if (pool == null) {
            return ItemStack.EMPTY;
        }
        int slot = pool.getSelectedSlot();
        return pool.isValidSlot(slot) ? pool.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** 开始释放的结果；{@code keepUsing} = 调用方是否应进入"使用中"状态（蓄力 / 维持需要，立刻释放不需要）。 */
    public record UseStartOutcome(@NotNull CastResult result, boolean keepUsing) {
    }

    /** 冷却中必须在起手之前就返回、<b>连"使用中"状态都不进</b>，否则客户端会举杖 + 压移动速度却什么都放不出来。 */
    public static boolean isSelectedSpellOnCooldown(@NotNull net.minecraft.world.entity.player.Player player) {
        PlayerMagicPool pool = getPool(player);
        if (pool == null) {
            return false;
        }
        int slot = pool.getSelectedSlot();
        if (!pool.isValidSlot(slot)) {
            return false;
        }
        ResourceLocation magicId = MagicStats.magicId(pool.getStackInSlot(slot));
        return magicId != null && pool.onCooldown(magicId);
    }

    /** 按下时调用（等价于旧名 {@link #clientOnServerUsing}）。 */
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

        int slot = pool.getSelectedSlot();
        if (!pool.isValidSlot(slot)) {
            return new UseStartOutcome(CastResult.INVALID_SLOT, false);
        }

        ItemStack spellStack = pool.getStackInSlot(slot);
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        if (spell == null) {
            return new UseStartOutcome(CastResult.NO_SPELL, false);
        }

        // 被动法术不走释放路径，否则会被它那个占位的 UsingType 当成主动法术放出去。
        if (spell.isPassive()) {
            return new UseStartOutcome(CastResult.PASSIVE_SPELL, false);
        }

        if (!staff.accepts(staffStack, spell)) {
            return new UseStartOutcome(CastResult.REJECTED, false);
        }

        // 冷却键是物品注册名：同名物品共享，不是按堆、也不是按物品实例。
        ResourceLocation magicId = MagicStats.magicId(spellStack);
        if (magicId != null && pool.onCooldown(magicId)) {
            return new UseStartOutcome(CastResult.ON_COOLDOWN, false);
        }

        if (!spell.canCast(player, spellStack)) {
            return new UseStartOutcome(CastResult.REJECTED, false);
        }

        // 法力预检：蓄力型也要立刻告诉玩家没蓝，而不是让他蓄两秒再失败。
        int previewCost = discountedCost(player, hand, MagicStats.manaCost(player, spellStack));
        if (!canPay(player, hand, spell, previewCost)) {
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
                    state.beginChanneling(spellStack, slot, hand, outcome.target());
                    yield new UseStartOutcome(CastResult.SUCCESS, true);
                }
                yield new UseStartOutcome(outcome.result(), false);
            }
        };
    }

    /** 旧名字，保留以免外部调用点失效：等价于 {@code handleUseStart(player, hand).result()}。 */
    @NotNull
    public static CastResult clientOnServerUsing(@NotNull ServerPlayer player,
                                                 @NotNull InteractionHand hand) {
        return handleUseStart(player, hand).result();
    }

    /**
     * 使用中每 tick 调用（服务端）。{@code CHARGING} 只累加 tick、不扣蓝；
     * {@code CHANNELING} 有两条<b>互相独立</b>的耗蓝线：按秒的 {@code baseKeepCostPerSecond}
     * 与按触发速度的 {@code baseKeepCostPerTrigger}（生效有间隔，扣费也有间隔）。
     */
    public static void handleUseTick(@NotNull ServerPlayer player, @NotNull InteractionHand hand,
                                     int heldTicks) {
        State state = peekState(player);
        if (state == null || state.isIdle()) {
            return;
        }
        // 用 state 记下的手判断，不用 player.getUsedItemHand()：后者依赖原版 useItem 字段的生命周期。
        if (state.getHand() != hand) {
            stopChannel(player, state);
            return;
        }

        ItemStack spellStack = state.getSpell();
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        ItemStack currentFocus = player.getItemInHand(hand);
        if (spell == null || !(currentFocus.getItem() instanceof StaffBase currentStaff)
                || !currentStaff.accepts(currentFocus, spell)) {
            stopChannel(player, state);
            return;
        }

        if (state.isCharging()) {
            state.setHeldTicks(heldTicks);
            if (spell != null) {
                spell.onChargeTick(player, spellStack, heldTicks);
            }
            return;
        }

        if (state.isChanneling()) {
            state.setHeldTicks(heldTicks);

            // 维持耗蓝按秒一次，与触发速度无关（另一条在下面跟着触发节奏走，两者刻意解耦）。
            if (state.tickKeepBilling()) {
                int perSecond = discountedCost(player, hand, MagicStats.keepCostPerSecond(player, spellStack));
                if (perSecond > 0) {
                    if (spell != null && !pay(player, hand, spell, perSecond)) {
                        stopChannel(player, state);
                        return;
                    }
                    ItemStack billedFocus = player.getItemInHand(hand);
                    if (!(billedFocus.getItem() instanceof StaffBase current)
                            || (spell != null && !current.accepts(billedFocus, spell))) {
                        stopChannel(player, state);
                        return;
                    }
                }
            }

            // 效果触发按法术声明的触发速度走（1 = 每 tick），不是每 tick 无条件。
            if (spell != null) {
                int interval = MagicStats.keepTriggerInterval(player, spellStack);
                if (state.tickKeepPulse(interval)) {
                    LivingEntity target = state.getLockedTarget();

                    // Pay before applying a pulse so an empty reserve cannot grant a free hit.
                    int perTrigger = discountedCost(player, hand,
                            MagicStats.keepCostPerTrigger(player, spellStack));
                    if (perTrigger > 0) {
                        if (!pay(player, hand, spell, perTrigger)) {
                            stopChannel(player, state);
                            return;
                        }
                    }

                    boolean keepGoing = spell.onKeepTick(player, spellStack, heldTicks, interval, target);

                    ItemStack afterPulse = player.getItemInHand(hand);
                    if (!keepGoing || !(afterPulse.getItem() instanceof StaffBase activeStaff)
                            || !activeStaff.accepts(afterPulse, spell)) {
                        stopChannel(player, state);
                    }
                }
            }
        }
    }

    /**
     * 松手时调用（服务端）。蓄力中蓄满才真正释放，没到门槛返回 {@code NOT_CHARGED}（不扣蓝、不进冷却）；
     * 维持中结束维持并回调 {@code onKeepStop}（法力与冷却在开始维持时已结算）。
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
            InteractionHand usedHand = state.getHand();
            IMagicBaseItem spell = MagicStats.spellOf(spellStack);
            state.reset();

            if (spell == null) {
                return CastResult.NO_SPELL;
            }

            // chargeRatio = 已按 tick / 蓄满所需 tick，钳在 [0,1]。
            float ratio = MagicStats.chargeRatio(player, spellStack, heldTicks);
            float minRatio = MagicStats.minChargeRatio(player, spellStack);

            // 连最低门槛都没到 → 本次作废，不扣蓝也不进冷却（否则"手抖点一下"就白扣）。
            if (ratio <= 0f || ratio < minRatio) {
                return CastResult.NOT_CHARGED;
            }

            // 威力由法术按 ctx.chargeRatio() 缩放（蓄满 = 1.0），耗蓝仍按全额收 —— 同原版弓。
            return castNow(player, usedHand, slot, spellStack, spell, ratio, heldTicks).result();
        }

        stopChannel(player, state);
        return CastResult.SUCCESS;
    }

    /**
     * 轮盘 CHARGE 的网络容差：客户端按"按住 R 的时长"算蓄力，服务端只能按自己的 tick 计数，
     * 差 1~2 tick 时 {@code minChargeRatio = 1.0} 的法术会莫名 {@code NOT_CHARGED}。
     * 结算时补这么多 tick（≤100ms），且不超过法术实际需要的蓄力 tick。
     */
    public static final int WHEEL_CHARGE_TOLERANCE_TICKS = 2;

    private record WheelTarget(@NotNull PlayerMagicPool pool, int poolSlot,
                               @NotNull ItemStack spellStack, @NotNull IMagicBaseItem spell,
                               @NotNull InteractionHand hand) {
    }

    private static CastResult lastWheelError = CastResult.NOT_ACTIVE;

    /**
     * 轮盘：开始蓄力 / 开始维持（{@code IMMEDIATELY} 走 {@link #handleWheelCast}）。
     * 不伪造原版"使用中"状态：状态记在 {@code State#isWheelSession()} 上，由 {@link #tickWheel} 推进。
     */
    @NotNull
    public static CastResult handleWheelBegin(@NotNull ServerPlayer player, int poolSlot) {
        WheelTarget target = validateWheelTarget(player, poolSlot);
        if (target == null) {
            return lastWheelError;
        }
        State state = getState(player);
        if (!state.isIdle() || player.isUsingItem()) {
            return CastResult.NOT_ACTIVE;
        }
        target.pool().setSelectedSlot(poolSlot);

        return switch (target.spell().getUsingType()) {
            case IMMEDIATELY -> CastResult.REJECTED;
            case CHARGE -> {
                state.beginCharging(target.spellStack(), poolSlot, target.hand());
                state.setWheelSession(true);
                yield CastResult.SUCCESS;
            }
            case KEEP -> {
                CastOutcome outcome = castNow(player, target.hand(), poolSlot,
                        target.spellStack(), target.spell(), 1.0f, 0);
                if (!outcome.result().isSuccess()) {
                    yield outcome.result();
                }
                state.beginChanneling(target.spellStack(), poolSlot, target.hand(),
                        outcome.target());
                state.setWheelSession(true);
                yield CastResult.SUCCESS;
            }
        };
    }

    /** 轮盘：松手瞬间释放 {@code IMMEDIATELY} 法术（目标 / 扣蓝 / 冷却 / 事件全复用 {@link #castNow}）。 */
    @NotNull
    public static CastResult handleWheelCast(@NotNull ServerPlayer player, int poolSlot) {
        WheelTarget target = validateWheelTarget(player, poolSlot);
        if (target == null) {
            return lastWheelError;
        }
        if (target.spell().getUsingType() != UsingType.IMMEDIATELY) {
            return CastResult.REJECTED;
        }
        State state = peekState(player);
        if (state != null && !state.isIdle()) {
            return CastResult.NOT_ACTIVE;
        }
        target.pool().setSelectedSlot(poolSlot);
        return castNow(player, target.hand(), poolSlot, target.spellStack(),
                target.spell(), 1.0f, 0).result();
    }

    /**
     * 轮盘：松手 / 结束维持。{@code CHARGE} 用服务端 heldTicks 加 {@link #WHEEL_CHARGE_TOLERANCE_TICKS} 结算；
     * 没有轮盘会话时返回 {@code NOT_ACTIVE}，不会误伤普通右键。
     */
    @NotNull
    public static CastResult handleWheelEnd(@NotNull ServerPlayer player) {
        State state = peekState(player);
        if (state == null || state.isIdle() || !state.isWheelSession()) {
            return CastResult.NOT_ACTIVE;
        }
        int held = state.getHeldTicks();
        int chargeTicks = MagicStats.chargeTicks(player, state.getSpell());
        if (chargeTicks > 0) {
            held = Math.min(chargeTicks, held + WHEEL_CHARGE_TOLERANCE_TICKS);
        }
        return handleUseRelease(player, state.getHand(), held);
    }

    /** 轮盘：取消。蓄力作废零代价；维持结束但<b>不退还</b>起手的蓝与冷却。 */
    @NotNull
    public static CastResult handleWheelCancel(@NotNull ServerPlayer player) {
        State state = peekState(player);
        if (state == null || state.isIdle() || !state.isWheelSession()) {
            return CastResult.NOT_ACTIVE;
        }
        if (state.isCharging()) {
            state.reset();
            return CastResult.CANCELLED;
        }
        handleUseRelease(player, state.getHand(), state.getHeldTicks());
        return CastResult.CANCELLED;
    }

    /** 服务端每 tick 推进轮盘会话（由 {@code MagicEventHandlers#onLivingTick} 的服务端分支调用）。 */
    public static void tickWheel(@NotNull ServerPlayer player) {
        State state = peekState(player);
        if (state == null || state.isIdle() || !state.isWheelSession()) {
            return;
        }
        // 会话安全：死了 / 法杖被换掉 → 走取消路径（维持会回调 onKeepStop）
        if (!player.isAlive() || player.isRemoved()
                || !(player.getItemInHand(state.getHand()).getItem() instanceof StaffBase)) {
            handleWheelCancel(player);
            return;
        }
        handleUseTick(player, state.getHand(), state.getHeldTicks() + 1);
    }

    /** 轮盘施法的统一前置校验：槽位合法、主动法术、主手法杖接受、冷却与蓝量预检；失败原因写进 {@link #lastWheelError}。 */
    @Nullable
    private static WheelTarget validateWheelTarget(@NotNull ServerPlayer player, int poolSlot) {
        lastWheelError = CastResult.NOT_ACTIVE;
        if (player.isSpectator()) {
            lastWheelError = CastResult.REJECTED;
            return null;
        }
        PlayerMagicPool pool = getPool(player);
        if (pool == null) {
            lastWheelError = CastResult.INVALID_SLOT;
            return null;
        }
        if (!pool.isValidSlot(poolSlot)) {
            lastWheelError = CastResult.INVALID_SLOT;
            return null;
        }
        ItemStack spellStack = pool.getStackInSlot(poolSlot);
        IMagicBaseItem spell = MagicStats.spellOf(spellStack);
        if (spell == null) {
            lastWheelError = CastResult.NO_SPELL;
            return null;
        }
        if (spell.isPassive()) {
            lastWheelError = CastResult.PASSIVE_SPELL;
            return null;
        }
        // 与右键一致：只认主手法杖
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack staffStack = player.getItemInHand(hand);
        if (!(staffStack.getItem() instanceof StaffBase staff)) {
            lastWheelError = CastResult.INVALID_STAFF;
            return null;
        }
        if (!staff.accepts(staffStack, spell)) {
            lastWheelError = CastResult.REJECTED;
            return null;
        }
        ResourceLocation magicId = MagicStats.magicId(spellStack);
        if (magicId != null && pool.onCooldown(magicId)) {
            lastWheelError = CastResult.ON_COOLDOWN;
            return null;
        }
        if (!spell.canCast(player, spellStack)) {
            lastWheelError = CastResult.REJECTED;
            return null;
        }
        int previewCost = discountedCost(player, hand, MagicStats.manaCost(player, spellStack));
        if (!canPay(player, hand, spell, previewCost)) {
            lastWheelError = CastResult.NOT_ENOUGH_MANA;
            return null;
        }
        return new WheelTarget(pool, poolSlot, spellStack, spell, hand);
    }

    // 目标解析

    /**
     * 沿施法者<b>准星</b>解算要锁定的目标：射线从眼睛出发、长度取 {@code MagicStats.targetRange}，
     * 会被方块阻挡（原版 {@code ProjectileUtil.getHitResultOnViewVector}，和弓箭同一套判定），
     * 且永远不含施法者自己。解不到时按 {@code allowSelfCast()} 返回自己或 null。
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

    private static boolean isTargetable(@NotNull ServerPlayer caster, @NotNull Entity entity) {
        if (entity == caster || entity.isSpectator() || !entity.isPickable()) {
            return false;
        }
        return entity instanceof LivingEntity living && living.isAlive();
    }

    /**
     * 沿准星解算"指定的那个<b>位置</b>"（给需要落点的法术用，与 {@link #resolveTarget} 的区别是永远有值）：
     * 先打生物，否则方块受击面，两者都没打到就取射线上 {@code range} 处 —— 所以"对着天放"落点在空中。
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

    // 内部

    /** 一次释放的结果：除结果码外还带出锁定的目标（{@code KEEP} 要把它记进 {@link State} 供整段维持使用）。 */
    private record CastOutcome(@NotNull CastResult result, @Nullable LivingEntity target) {
    }

    /**
     * 结束维持：<b>无条件</b>回调 {@code onKeepStop} 再清状态 —— 漏一次会永久留下残留（淡出中的光束、
     * 施法标记、循环音效），多一次最多是重复清理。
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

    /** 真正的一次释放：解目标 → 事件 → 扣蓝 → 进冷却 → 出效果 → Post 事件（走到这里说明冷却与 canCast 已过）。 */
    @NotNull
    private static CastOutcome castNow(@NotNull ServerPlayer player, @NotNull InteractionHand hand,
                                       int slot, @NotNull ItemStack spellStack,
                                       @NotNull IMagicBaseItem spell,
                                       float chargeRatio, int heldTicks) {
        // 目标解析放最前面：瞄空时连事件都不该抛、法力与冷却都不该动。
        LivingEntity target = null;
        if (spell.needsTarget()) {
            target = resolveTarget(player, spellStack);
            if (target == null) {
                return new CastOutcome(CastResult.NO_TARGET, null);
            }
        }

        int cost = discountedCost(player, hand, MagicStats.manaCost(player, spellStack));

        MagicCastEvent event = new MagicCastEvent(player, spellStack, spell.getUsingType(),
                hand, slot, chargeRatio, target, cost);
        if (MinecraftForge.EVENT_BUS.post(event) || event.isCanceled()) {
            return new CastOutcome(CastResult.CANCELLED, target);
        }
        cost = event.getManaCost();

        // Capture the focus before charging it: the last cast can turn it into an empty wand.
        boolean specialUse = MagicStats.isSpecialUse(player, player.getItemInHand(hand), spellStack);

        MagicCastContext context = new MagicCastContext(slot, hand, heldTicks, chargeRatio,
                target, specialUse);

        // 事件可能改过耗蓝，所以这里才是权威判定。
        if (!pay(player, hand, spell, cost)) {
            return new CastOutcome(CastResult.NOT_ENOUGH_MANA, target);
        }

        ResourceLocation magicId = MagicStats.magicId(spellStack);
        if (magicId != null) {
            int cooldown = MagicStats.cooldownTicks(player, spellStack);
            // 倍率钳到 >=0：返回负数没有意义，钳掉比让冷却变成负数安全。
            float multiplier = Math.max(0.0F, spell.cooldownMultiplier(context));
            if (multiplier != 1.0F) {
                cooldown = Math.max(0, Math.round(cooldown * multiplier));
            }
            if (cooldown > 0) {
                PlayerMagicPool pool = getPool(player);
                if (pool != null) {
                    pool.setCooldown(magicId, cooldown);
                }
            }
        }

        spell.cast(player, spellStack, context);
        ItemStack usedStaff = player.getItemInHand(hand);
        if (usedStaff.getItem() instanceof WandItem wand && wand.capacity() > 0) {
            usedStaff.hurtAndBreak(1, player, holder -> holder.broadcastBreakEvent(hand));
        }

        MinecraftForge.EVENT_BUS.post(new MagicCastEvent.Post(player, spellStack,
                spell.getUsingType(), cost, chargeRatio, target, CastResult.SUCCESS));

        return new CastOutcome(CastResult.SUCCESS, target);
    }
}
