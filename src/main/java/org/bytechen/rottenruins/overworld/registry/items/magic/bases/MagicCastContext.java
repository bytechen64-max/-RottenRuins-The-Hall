package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一次释放的上下文，传给 {@link IMagicBaseItem#cast}；用 record 而不是可变对象：释放过程中这些量都不该被改，想让它们变化应该通过 {@code MagicStatProvider} 或事件。
 * slot：法术来自哪个魔法槽下标，不是从槽位释放时为 {@code -1}；heldTicks：从按下到现在的 tick 数（{@code IMMEDIATELY} 为 0）；
 * chargeRatio：蓄力完成度 {@code 0.0~1.0}（非 {@code CHARGE} 模式恒为 {@code 1.0}）；target：本次释放锁定的目标，不要求目标或准星没指到东西时为 {@code null}；
 * specialUse：本次释放算不算「法术流派 == 法杖流派」的特殊使用，由框架在释放那一刻算好（见 {@link IMagicBaseItem#isSpecialUse}），法杖不在手上时为 {@code false}。
 */
public record MagicCastContext(int slot, @NotNull InteractionHand hand,
                               int heldTicks, float chargeRatio,
                               @Nullable LivingEntity target, boolean specialUse) {

    /** 非蓄力释放的默认上下文（不带目标）；需要目标的法术用 {@link #instant(InteractionHand, int, LivingEntity)}。 */
    public static MagicCastContext instant(@NotNull InteractionHand hand, int slot) {
        return new MagicCastContext(slot, hand, 0, 1.0f, null, false);
    }

    /** 非蓄力释放的默认上下文，带锁定目标。 */
    public static MagicCastContext instant(@NotNull InteractionHand hand, int slot,
                                           @Nullable LivingEntity target) {
        return new MagicCastContext(slot, hand, 0, 1.0f, target, false);
    }

    /** 四参数构造（不带目标、不算特殊使用），兼容旧调用点。 */
    public MagicCastContext(int slot, @NotNull InteractionHand hand,
                            int heldTicks, float chargeRatio) {
        this(slot, hand, heldTicks, chargeRatio, null, false);
    }

    public MagicCastContext(int slot, @NotNull InteractionHand hand,
                            int heldTicks, float chargeRatio,
                            @Nullable LivingEntity target) {
        this(slot, hand, heldTicks, chargeRatio, target, false);
    }

    /** 是否是从魔法槽里取出来的法术。 */
    public boolean fromSlot() {
        return slot >= 0;
    }

    public boolean hasTarget() {
        return this.target != null;
    }

    /** 换一个目标，其余字段照搬；目标在释放那一刻由框架解析后回填，而不是让法术自己去 raycast。 */
    @NotNull
    public MagicCastContext withTarget(@Nullable LivingEntity newTarget) {
        return new MagicCastContext(this.slot, this.hand, this.heldTicks,
                this.chargeRatio, newTarget, this.specialUse);
    }

    public MagicCastContext withSpecialUse(boolean isSpecialUse) {
        return new MagicCastContext(this.slot, this.hand, this.heldTicks,
                this.chargeRatio, this.target, isSpecialUse);
    }

    /** 施法者手上那根法杖；法术想在 {@code cast} 里自己重判一次特殊使用时可配 {@link #specialUse} 使用。 */
    @NotNull
    public ItemStack staffOf(@NotNull LivingEntity caster) {
        return caster.getItemInHand(this.hand);
    }
}
