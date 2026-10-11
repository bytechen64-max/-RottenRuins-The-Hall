package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一次释放的上下文，传给 {@link IMagicBaseItem#cast}。
 *
 * <p>用 record 而不是可变对象：释放过程中这些量都不该被改，
 * 想让它们变化应该通过 {@code MagicStatProvider}（改的是"下次求值的结果"）
 * 或者事件（改的是"这一次"）。</p>
 *
 * @param slot        法术来自哪个魔法槽下标；不是从槽位释放时为 {@code -1}
 * @param hand        触发用的那只手（法杖所在的手）
 * @param heldTicks   从按下到现在的 tick 数（{@code IMMEDIATELY} 为 0）
 * @param chargeRatio 蓄力完成度 {@code 0.0~1.0}；非 {@code CHARGE} 模式恒为 {@code 1.0}
 * @param target      本次释放锁定的目标；法术不要求目标、或准星没指到任何东西时为 {@code null}
 */
public record MagicCastContext(int slot, @NotNull InteractionHand hand,
                               int heldTicks, float chargeRatio,
                               @Nullable LivingEntity target) {

    /**
     * 非蓄力释放的默认上下文（不带目标）。
     *
     * <p>保留这个三参数版本是因为它是最常用的“直接打出去”上下文，
     * 需要目标的法术请用 {@link #instant(InteractionHand, int, LivingEntity)}。</p>
     */
    public static MagicCastContext instant(@NotNull InteractionHand hand, int slot) {
        return new MagicCastContext(slot, hand, 0, 1.0f, null);
    }

    /** 非蓄力释放的默认上下文，带锁定目标。 */
    public static MagicCastContext instant(@NotNull InteractionHand hand, int slot,
                                           @Nullable LivingEntity target) {
        return new MagicCastContext(slot, hand, 0, 1.0f, target);
    }

    /**
     * 不带目标的三参数构造，兼容旧调用点。
     *
     * <p>record 的规范构造是五参数的，这里补一个委托构造，
     * 免得以前写 {@code new MagicCastContext(slot, hand, ticks, ratio)} 的地方编译不过。</p>
     */
    public MagicCastContext(int slot, @NotNull InteractionHand hand,
                            int heldTicks, float chargeRatio) {
        this(slot, hand, heldTicks, chargeRatio, null);
    }

    /** 是否是从魔法槽里取出来的法术。 */
    public boolean fromSlot() {
        return slot >= 0;
    }

    /** 本次释放是否锁定了目标。 */
    public boolean hasTarget() {
        return this.target != null;
    }

    /**
     * 换一个目标，其余字段照搬。
     *
     * <p>只给框架内部和事件用：目标是“释放那一刻”解析出来的，
     * 解析完再回填到上下文里，而不是让法术自己去 raycast。</p>
     */
    @NotNull
    public MagicCastContext withTarget(@Nullable LivingEntity newTarget) {
        return new MagicCastContext(this.slot, this.hand, this.heldTicks,
                this.chargeRatio, newTarget);
    }
}
