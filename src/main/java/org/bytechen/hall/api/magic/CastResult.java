package org.bytechen.hall.api.magic;

/**
 * 一次释放请求的结果。由 {@code MagicHandle} 返回，也用于
 * {@link MagicCastEvent.Post}。
 *
 * <p>数值化的原因：释放失败的具体理由需要能区分开 —— 冷却中要显示剩余秒数、
 * 法力不足要有提示音、蓄力不足应该静默，这些都不能靠一个 boolean 表达。</p>
 */
public enum CastResult {

    /** 释放成功（{@code KEEP} 模式下表示"已开始维持"）。 */
    SUCCESS,

    /** 手上没有法杖，或法杖类型不匹配。 */
    INVALID_STAFF,

    /** 当前选中的槽位没有法术。 */
    NO_SPELL,

    /** 该法术正在冷却中（同名物品共享这个冷却）。 */
    ON_COOLDOWN,

    /** 法力不足，已取消释放。 */
    NOT_ENOUGH_MANA,

    /** {@code CHARGE} 模式蓄力不足就松手了。 */
    NOT_CHARGED,

    /** 被 {@link MagicCastEvent} 的监听者取消。 */
    CANCELLED,

    /** 法术物品自身拒绝了这次释放（见 {@code CMagicBaseItem#canCast}）。 */
    REJECTED,

    /** 槽位下标非法（选中的槽位在可用范围之外）。 */
    INVALID_SLOT,

    /** 当前没有正在进行的释放（例如没在蓄力/维持时收到松手）。 */
    NOT_ACTIVE;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** 是否需要消耗冷却：只有真正打出去（或被法术自己拒绝以外）的情况才进冷却。 */
    public boolean consumesCooldown() {
        return this == SUCCESS;
    }
}
