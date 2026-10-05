package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

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
 */
public record MagicCastContext(int slot, @NotNull InteractionHand hand,
                               int heldTicks, float chargeRatio) {

    /** 非蓄力释放的默认上下文。 */
    public static MagicCastContext instant(@NotNull InteractionHand hand, int slot) {
        return new MagicCastContext(slot, hand, 0, 1.0f);
    }

    /** 是否是从魔法槽里取出来的法术。 */
    public boolean fromSlot() {
        return slot >= 0;
    }
}
