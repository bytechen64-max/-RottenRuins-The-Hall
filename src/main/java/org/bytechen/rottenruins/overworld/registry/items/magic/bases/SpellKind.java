package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

/**
 * 法术的<b>分类轴</b>：主动 / 被动。
 *
 * <p>它和另外两个枚举<b>正交</b>，回答的是三个不同的问题：</p>
 * <table border="1">
 *   <tr><th>维度</th><th>枚举</th><th>回答</th></tr>
 *   <tr><td>流派</td><td>{@link MagicType}</td><td>火 / 死亡 / 虚空……（数值与表现）</td></tr>
 *   <tr><td><b>分类</b></td><td><b>{@link SpellKind}</b></td>
 *       <td><b>要不要靠法杖主动释放</b></td></tr>
 *   <tr><td>使用方式</td><td>{@link UsingType}</td>
 *       <td>主动法术按下后怎么走时间轴（立刻 / 蓄力 / 按住）</td></tr>
 * </table>
 *
 * <h3>{@link #ACTIVE} 主动</h3>
 * <p>现有三件法术（火球术 / 审判之矛 / 抑制场）全部属于这类。放进魔法槽、由法杖右键
 * 触发 {@code MagicHandle} 的释放流程：校验冷却与法力 → 抛事件 → 扣蓝 → 进冷却 →
 * 调 {@code cast()}。{@link UsingType} 只对这类法术有意义。</p>
 *
 * <h3>{@link #PASSIVE} 被动</h3>
 * <p>不释放。放进魔法槽后<b>持续生效</b>，没有耗蓝 / 冷却 / 蓄力语义，
 * 也不会被 {@code MagicHandle.handleUseStart} 放出去 —— 选中它时直接以
 * {@code CastResult.PASSIVE_SPELL} 结束，不调 {@code cast()}。被动效果的驱动
 * （槽位 tick、放入/取出的 equip/unequip）是后续框架的事，本枚举只负责"分类"。
 * 它声明的 {@link UsingType} 只是一个不会被读取的占位值。</p>
 *
 * <h3>为什么单独一个枚举，而不是往 {@link UsingType} 里加 {@code PASSIVE}</h3>
 * <p>那样会造出"被动 + 蓄力"这种自相矛盾的声明，而且所有
 * {@code switch (usingType)} 都得为它多写一个"其实不是释放模式"的分支。
 * 拆成两个维度后，被动法术从一开始就不会混进释放时间轴。</p>
 *
 * <h3>怎么声明</h3>
 * <p>覆写 {@code IMagicBaseItem#spellKind()} 即可，默认是 {@link #ACTIVE}，
 * 所以现有法术与外部实现都不用改动：</p>
 * <pre>{@code
 * public class ManaWellSpell extends CMagicBaseItem {
 *     public ManaWellSpell() {
 *         super(defaultProperties(), MagicType.WATER, UsingType.IMMEDIATELY,
 *                 0, 0, 0, 0, 0f);   // 被动法术这些数值都是占位
 *     }
 *     @Override public SpellKind spellKind() { return SpellKind.PASSIVE; }
 * }
 * }</pre>
 */
public enum SpellKind {

    /** 主动：需要放进魔法槽、由法杖释放。 */
    ACTIVE,

    /** 被动：放进魔法槽就生效，不能被释放。 */
    PASSIVE;

    /** 是不是主动法术。 */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /** 是不是被动法术。 */
    public boolean isPassive() {
        return this == PASSIVE;
    }
}
