package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 法杖基类 —— 纯转发层：声明流派、把原版使用生命周期（use / onUseTick / releaseUsing）
 * 转发给 MagicHandle、提供"使用中"时长与姿势。释放模式由槽里那件法术自己声明
 * （CMagicBaseItem#getUsingType），不由法杖决定。
 */
public abstract class StaffBase extends Item {

    /** "使用中"的时长上限：取极大值，按住维持不会自己到点结束；真正的结束是松手 / 法力耗尽 / 法术返回 false。 */
    public static final int MAGIC_USE_DURATION = 72000;

    private final MagicType magicType;
    private final StaffGrade grade;

    protected StaffBase(@NotNull Properties properties, @NotNull MagicType magicType) {
        this(properties, magicType, StaffGrade.COMMON);
    }

    protected StaffBase(@NotNull Properties properties, @NotNull MagicType magicType,
                        @NotNull StaffGrade grade) {
        super(properties);
        this.magicType = magicType;
        this.grade = grade;
    }

    /** 法杖的流派。 */
    @NotNull
    public MagicType getMagicType() {
        return magicType;
    }

    /** 法杖等级（决定对同流派法术的固定加成）。 */
    @NotNull
    public StaffGrade getGrade() {
        return grade;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(getGrade().getTranslationKey())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (getGrade().spellBonus() > 0f) {
            tooltip.add(Component.translatable("tooltip.rotten_ruins.staff_grade_bonus",
                    (int) getGrade().spellBonus()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 这根杖能不能放该法术：默认不限制，子类可覆写做流派绑定；返回 false 时 MagicHandle 以 CastResult.REJECTED 结束，不扣蓝不进冷却。 */
    public boolean accepts(@NotNull ItemStack staff, @NotNull IMagicBaseItem spell) {
        return true;
    }

    /** 开始一次释放，完全委托 MagicHandle.handleUseStart；返回的 UseStartOutcome 同时说明"要不要进入使用态"。 */
    @NotNull
    public MagicHandle.UseStartOutcome useMagic(@NotNull ServerPlayer player, @NotNull InteractionHand hand) {
        return MagicHandle.handleUseStart(player, hand);
    }

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                  @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 选中法术还在冷却就直接返回，连"使用中"状态都不进：客户端原本无条件 startUsingItem，
        // 会摆出举杖姿势并压移动速度却什么都没放出来。冷却表两端一致（PlayerMagicPool 同步 + 各自倒计时）。
        if (MagicHandle.isSelectedSpellOnCooldown(player)) {
            return InteractionResultHolder.fail(stack);
        }

        // 客户端：只起手，让蓄力/维持有动画与进度条；判定全在服务端
        if (level.isClientSide()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        MagicHandle.UseStartOutcome outcome = useMagic(serverPlayer, hand);
        // MagicHandle may replace a depleted wand with the empty wand.
        ItemStack current = player.getItemInHand(hand);

        if (outcome.keepUsing()) {
            // 蓄力 / 维持：进入使用态，后续靠 onUseTick 与 releaseUsing 推进
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(current);
        }

        // 立刻释放型：已经打完了（或失败），不需要维持使用态
        return outcome.result().isSuccess()
                ? InteractionResultHolder.consume(current)
                : InteractionResultHolder.fail(current);
    }

    /** 必须返回 true —— 否则蓄力到顶后松手不会触发 releaseUsing。 */
    @Override
    public boolean useOnRelease(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return MAGIC_USE_DURATION;
    }

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

        // 客户端只做表现（粒子 / 音效 / 蓄力进度），判定必须留在服务端 —— 客户端的槽位与法力只是同步副本。
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
     * 客户端每 tick 的蓄力 / 维持表现钩子，默认什么都不做 —— 表现为纯表现，客户端自己算即可，
     * 需要的数据（选中法术、已持 tick 数）都已同步过来，见 selectedSpell / chargeRatioOf。
     */
    protected void onClientUseTick(@NotNull Player player, @NotNull ItemStack staffStack, int heldTicks) {
    }

    /** 当前选中槽位里的法术堆；双端可用，客户端读到的是同步过来的副本。 */
    @NotNull
    protected static ItemStack selectedSpell(@NotNull Player player) {
        return MagicHandle.selectedSpell(player);
    }

    /** 蓄力完成度 0.0~1.0；需要的 tick 为 0 时返回 1.0。 */
    protected static float chargeRatioOf(@NotNull Player player, @NotNull ItemStack spell, int heldTicks) {
        return org.bytechen.rottenruins.api.magic.MagicStats.chargeRatio(player, spell, heldTicks);
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
