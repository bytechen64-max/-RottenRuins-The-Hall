package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.items.magic.WandItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 法术物品的行为接口：只声明基值（耗蓝 / 冷却 / 蓄力 / 伤害）与法术效果本身；冷却与法力的存取归 MagicHandle / PlayerMagicPool / TwistedPoint —— 那些必须按玩家存，放在 Item 单例上会让所有玩家共享同一份状态。 */
public interface IMagicBaseItem {

    // 以下基值都是「未被修饰的原始值」，业务代码请通过 MagicStats 取，那样才会经过属性 / 附魔折叠。

    @NotNull
    MagicType getMagicType(); // 法术流派。

    @NotNull
    UsingType getUsingType(); // 释放模式（立刻 / 蓄力 / 按住）；只对主动法术有意义，被动法术不读这个值。

    /** 分类轴：主动 / 被动（默认 ACTIVE），与流派、释放模式正交；被动法术不经过释放流程，框架不会调 cast。 */
    @NotNull
    default SpellKind spellKind() {
        return SpellKind.ACTIVE;
    }

    default boolean isPassive() { // 是不是被动法术，等价于 spellKind() == SpellKind.PASSIVE。
        return spellKind() == SpellKind.PASSIVE;
    }

    int baseManaCost(); // 单次释放的启动耗蓝；KEEP 模式下就是"启动消耗"。

    int baseKeepCostPerSecond(); // KEEP 维持期间每秒的额外耗蓝；其它模式忽略。

    int baseCooldownTicks(); // 释放后的冷却 tick；按「玩家 + 物品注册名」记，同名物品共享。

    int baseChargeTicks(); // CHARGE 模式需要蓄力的 tick 数；其它模式忽略。

    float baseDamage(); // 伤害基值；实际伤害请用 MagicStats.spellDamage(...)。

    /** 蓄力释放的最低蓄力比例（0.0~1.0）：松手时低于它本次释放作废（NOT_CHARGED，不扣蓝不进冷却），不低于则照放并由 chargeRatio() 缩放威力；非 CHARGE 忽略。 */
    default float baseMinChargeRatio() {
        return CMagicBaseItem.DEFAULT_MIN_CHARGE_RATIO;
    }

    /** 法术自身的额外释放前置条件（冷却与法力框架已查）；返回 false 会让框架以 REJECTED 结束，不扣蓝不进冷却。 */
    default boolean canCast(@NotNull ServerPlayer caster, @NotNull ItemStack stack) {
        return true;
    }

    /** 执行法术效果：走到这里冷却与法力必定已扣，所以不要再判"能不能放"（那是 canCast 的事）；返回 false 只是"空放"，照样扣蓝进冷却。 */
    boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack, @NotNull MagicCastContext ctx);

    /** KEEP 模式每多少 tick 触发一次效果（1 = 每 tick，20 = 每秒）：按下后第 1 tick 立刻触发一次再按 N 间隔，与每秒耗蓝无关；非 KEEP 忽略，<1 钳到 1。 */
    default int baseKeepTriggerInterval() {
        return 1;
    }

    /** 是否必须锁定目标才能释放：为 true 时框架在松手释放那一刻沿准星射线解算（蓄力期间瞄准也有效），结果放进 ctx.target()。 */
    default boolean needsTarget() {
        return false;
    }

    /** 解不到目标时是否允许打自己；默认 false 是刻意的 —— 若默认允许，伤害法术瞄空会反手打死自己，增益类法术请显式声明 true。 */
    default boolean allowSelfCast() {
        return false;
    }

    default double baseTargetRange() { // 锁定目标的最大距离（格）；射线会被方块阻挡，实际取它与方块命中点的较小者。
        return 32.0d;
    }

    /** KEEP 模式每次触发效果时扣的蓝：与每秒耗蓝互相独立（同时非 0 都会扣，不建议，容易算不清）；扣费在触发之后，扣不出来就立刻结束维持。 */
    default int baseKeepCostPerTrigger() {
        return 0;
    }

    /** 这一次释放的冷却倍率：冷却 = MagicStats.cooldownTicks(...) × 本值，返回 0 则这一下不进冷却，负数钳到 0。 */
    default float cooldownMultiplier(@NotNull MagicCastContext context) {
        return 1.0F;
    }

    /** 流派相同（且法杖可用）即算特殊使用；外部想判定请走 MagicStats.isSpecialUse，别自己抄这段比较逻辑。 */
    default boolean isSpecialUse(@NotNull ItemStack staffStack, @NotNull ItemStack spellStack) {
        return staffStack.getItem() instanceof StaffBase staff
                && (!(staff instanceof WandItem wand) || wand.capacity() > 0)
                && staff.getMagicType() == getMagicType();
    }

    /** 特殊使用的完整判定（框架实际调用的就是它）：法杖流派相同，或装备了同流派饰品；覆写必须先调 super 再追加条件，否则会把这两条判掉。 */
    default boolean isSpecialUse(@Nullable ServerPlayer caster,
                                 @NotNull ItemStack staffStack,
                                 @NotNull ItemStack spellStack) {
        if (isSpecialUse(staffStack, spellStack)) {
            return true;
        }
        return caster != null
                && org.bytechen.rottenruins.overworld.registry.items.magic.curios.ICuriosBase
                        .isSchoolEquipped(caster, getMagicType());
    }

    /** CHARGE 模式蓄力过程中每 tick 调用（服务端）；heldTicks = 已经按住多少 tick。 */
    default void onChargeTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
    }

    /** KEEP 维持中的效果回调（框架调用的是这个五参版本）：按触发速度调用而非每 tick；target 在开始维持时解析一次、整段维持不跟准星换人且可能已死，返回 false = 主动停止维持并进冷却。 */
    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                               int heldTicks, int triggerInterval,
                               @Nullable LivingEntity target) {
        return onKeepTick(caster, stack, heldTicks, triggerInterval);
    }

    /** 四参兼容版本：由五参版本默认转发到此，拿不到目标但行为完全不变；需要目标时请覆写五参版本。 */
    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                               int heldTicks, int triggerInterval) {
        return onKeepTick(caster, stack, heldTicks);
    }

    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) { // 三参兼容版本：转发链最底层，覆写它会终结整条转发。
        return true;
    }

    /** KEEP 模式结束时的回调（自然结束、法力耗尽、松手都会走到）。 */
    default void onKeepStop(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
    }
}
