package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.bytechen.hall.api.magic.CastResult;
import org.jetbrains.annotations.NotNull;

/**
 * 法杖基类 —— <b>纯转发层</b>，所有逻辑都在 {@code MagicHandle} 里。
 *
 * <p>法杖自己的职责只有三件事：</p>
 * <ol>
 *   <li>声明<b>流派</b>（{@link MagicType}），并通过 {@link #accepts} 决定"这根杖能不能放这个法术"；</li>
 *   <li>把原版物品使用生命周期（{@code use} / {@code onUseTick} / {@code releaseUsing}）
 *       转发给 {@code MagicHandle}；</li>
 *   <li>提供"使用中"的持续时间与姿势，让蓄力/维持有动画可看。</li>
 * </ol>
 *
 * <p>法术的三种释放模式（立刻 / 蓄力 / 按住）<b>不由法杖决定</b>，而是由槽里那件法术自己
 * 声明（{@code CMagicBaseItem#getUsingType}）。所以同一根杖换一个槽位就是换一种手感。</p>
 *
 * <h3>原版使用生命周期的三个坑（本类已按框架既有做法处理）</h3>
 * <ul>
 *   <li>{@link #useOnRelease} <b>必须</b>返回 {@code true}。返回 {@code false} 时，
 *       "按满时长"会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}，
 *       于是蓄力到顶后松手什么都不放。</li>
 *   <li>{@link #getUseDuration} 给一个很大的值，这样"按住"不会自己到点结束。</li>
 *   <li>客户端也要进入使用态（否则蓄力动画/进度条不显示、和服务端不同步），
 *       但<b>客户端不做任何判定</b> —— 判定一律在 {@code MagicHandle} 的服务端分支里做。</li>
 * </ul>
 *
 * <h3>写一根法杖</h3>
 * <pre>{@code
 * public class EmberStaff extends StaffBase {
 *     public EmberStaff() {
 *         super(new Properties().stacksTo(1).durability(512), MagicType.FIRE);
 *     }
 *     // 想把法杖限制成只能放火系：
 *     // @Override public boolean accepts(ItemStack staff, IMagicBaseItem spell) {
 *     //     return spell.getMagicType() == MagicType.FIRE;
 *     // }
 * }
 * }</pre>
 */
public abstract class StaffBase extends Item {

    /**
     * "使用中"的时长上限。
     * <p>取一个很大的值（和 {@code IBlockingWeapon} 用同一个思路），
     * 这样按住维持不会被原版自己结束掉；真正的结束条件是松手、法力耗尽、
     * 或法术自己从 {@code onKeepTick} 返回 false。</p>
     */
    public static final int MAGIC_USE_DURATION = 72000;

    private final MagicType magicType;

    protected StaffBase(@NotNull Properties properties, @NotNull MagicType magicType) {
        super(properties);
        this.magicType = magicType;
    }

    /** 法杖的流派。 */
    @NotNull
    public MagicType getMagicType() {
        return magicType;
    }

    /**
     * 这根杖能不能释放该法术。<b>默认不限制</b>，子类可覆写来实现流派绑定。
     *
     * <p>返回 {@code false} 时 {@code MagicHandle} 以 {@link CastResult#REJECTED} 结束，
     * 不扣蓝、不进冷却。</p>
     *
     * @param staff 手上的法杖堆
     * @param spell 槽里选中的法术
     */
    public boolean accepts(@NotNull ItemStack staff, @NotNull IMagicBaseItem spell) {
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // 对外入口：委托给 MagicHandle
    // ══════════════════════════════════════════════════════════════

    /**
     * 开始一次释放。这是本类唯一的对外逻辑入口，实现<b>完全委托</b>给
     * {@code MagicHandle.handleUseStart}。
     *
     * <p>返回 {@link MagicHandle.UseStartOutcome}：既给出结果，也告诉调用方
     * "要不要进入使用态"（蓄力与维持需要，立刻释放不需要）。</p>
     */
    @NotNull
    public MagicHandle.UseStartOutcome useMagic(@NotNull ServerPlayer player, @NotNull InteractionHand hand) {
        return MagicHandle.handleUseStart(player, hand);
    }

    // ══════════════════════════════════════════════════════════════
    // 原版生命周期 → 转发
    // ══════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                  @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 客户端：只起手，让蓄力/维持有动画与进度条；判定全在服务端
        if (level.isClientSide()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        MagicHandle.UseStartOutcome outcome = useMagic(serverPlayer, hand);

        if (outcome.keepUsing()) {
            // 蓄力 / 维持：进入使用态，后续靠 onUseTick 与 releaseUsing 推进
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        // 立刻释放型：已经打完了（或失败），不需要维持使用态
        return outcome.result().isSuccess()
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }

    /** 必须返回 true —— 否则蓄力到顶后松手不会触发 {@code releaseUsing}。 */
    @Override
    public boolean useOnRelease(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return MAGIC_USE_DURATION;
    }

    /** 举杖姿势。 */
    @Override
    @NotNull
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity living,
                          @NotNull ItemStack stack, int remainingUseDuration) {
        if (!(living instanceof Player player)) {
            return;
        }
        int heldTicks = MAGIC_USE_DURATION - remainingUseDuration;

        // 客户端：只做表现（粒子 / 音效 / 蓄力进度），不做任何判定。
        // 判定必须在服务端 —— 客户端的槽位与法力都只是同步过来的副本。
        if (level.isClientSide()) {
            onClientUseTick(player, stack, heldTicks);
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MagicHandle.handleUseTick(serverPlayer, player.getUsedItemHand(), heldTicks);
    }

    /**
     * <b>客户端</b>每 tick 的蓄力 / 维持表现钩子。默认什么都不做。
     *
     * <p>为什么给这个钩子：蓄力与维持的反馈（脚下的环、光尘、蓄力进度条、
     * 每档音调）都是纯表现，放在服务端做会变成"发一堆发包让客户端放粒子"。
     * 这里由客户端自己算即可 —— 需要的数据都已经同步过来了：</p>
     *
     * <pre>{@code
     * @Override
     * protected void onClientUseTick(Player player, ItemStack staff, int heldTicks) {
     *     ItemStack spell = selectedSpell(player);
     *     IMagicBaseItem item = MagicStats.spellOf(spell);
     *     if (item == null) return;
     *
     *     switch (item.getUsingType()) {
     *         case CHARGE -> {
     *             // 蓄力完成度：0 → 1。可以用来缩放粒子半径、音调、进度条
     *             float ratio = chargeRatioOf(player, spell, heldTicks);
     *             spawnChargeRing(player, ratio);
     *         }
     *         case KEEP -> spawnChannelAura(player, heldTicks);
     *         case IMMEDIATELY -> { }   // 按下的那一瞬间就生效了，没有持续期
     *     }
     * }
     * }</pre>
     *
     * @param player   客户端玩家（{@code LocalPlayer}）
     * @param staffStack 手上的法杖
     * @param heldTicks  从按下到现在的 tick 数
     */
    protected void onClientUseTick(@NotNull Player player, @NotNull ItemStack staffStack, int heldTicks) {
    }

    /**
     * 当前选中槽位里的法术堆。<b>双端可用</b>，客户端读到的是同步过来的副本。
     * 客户端拿它做"按选中法术显示不同表现"很方便。
     */
    @NotNull
    protected static ItemStack selectedSpell(@NotNull Player player) {
        return MagicHandle.selectedSpell(player);
    }

    /** 蓄力完成度 {@code 0.0~1.0}；需要的 tick 为 0 时返回 {@code 1.0}。 */
    protected static float chargeRatioOf(@NotNull Player player, @NotNull ItemStack spell, int heldTicks) {
        return org.bytechen.hall.api.magic.MagicStats.chargeRatio(player, spell, heldTicks);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level,
                             @NotNull LivingEntity living, int remainingUseDuration) {
        if (level.isClientSide() || !(living instanceof ServerPlayer player)) {
            return;
        }
        int heldTicks = MAGIC_USE_DURATION - remainingUseDuration;
        MagicHandle.handleUseRelease(player, player.getUsedItemHand(), heldTicks);
    }
}
