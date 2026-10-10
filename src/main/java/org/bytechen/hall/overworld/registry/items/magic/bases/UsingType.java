package org.bytechen.hall.overworld.registry.items.magic.bases;

/**
 * 法术的<b>使用方式</b>。每个法术<b>必须</b>声明一种 —— 它决定这次释放的整条时间轴。
 *
 * <p>三种模式的时间轴由 {@code MagicHandle} 统一驱动，法术自己不用数 tick：</p>
 *
 * <h3>{@link #IMMEDIATELY} 立刻释放</h3>
 * <pre>
 *   右键按下 ──► 校验(冷却/法力/前置) ──► MagicCastEvent ──► 扣蓝 ──► 进冷却 ──► cast()
 * </pre>
 * <ul>
 *   <li>按下那一瞬间就生效，<b>不进"使用中"状态</b>，没有蓄力期也没有维持期；</li>
 *   <li>{@code onChargeTick} / {@code onKeepTick} / {@code onKeepStop} <b>都不会被调用</b>；</li>
 *   <li>{@code MagicCastContext#chargeRatio()} 恒为 {@code 1.0}；</li>
 *   <li>因为服务端不进使用态，所以也没有"松手"这一步 —— 服务端的 {@code releaseUsing}
 *       不会触发（客户端那份会在松手时自己结束）。</li>
 * </ul>
 *
 * <h3>{@link #CHARGE} 蓄力释放</h3>
 * <pre>
 *   按下 ──► 只做校验（预检法力），进入蓄力；不扣蓝、不进冷却
 *   按住 ──► 每 tick 回调 onChargeTick(caster, stack, heldTicks)
 *   松手 ──► chargeRatio = 已按tick / 蓄满所需tick   (钳 0~1)
 *            ├─ chargeRatio &lt; minChargeRatio ──► 作废（NOT_CHARGED），不扣蓝不进冷却
 *            └─ 否则 ──► MagicCastEvent ──► 扣蓝 ──► 进冷却 ──► cast(..., ctx.chargeRatio())
 * </pre>
 * <ul>
 *   <li><b>蓄满 = 全额；提前放 = 同比例减弱</b>（"提前放效果减弱"）。
 *       减弱这件事是<b>法术自己</b>做的：拿 {@code ctx.chargeRatio()} 去缩放伤害、半径、
 *       持续时间、粒子数量都行，框架不替它决定；</li>
 *   <li>{@code minChargeRatio} 默认 {@code 0.25}：低于它直接作废（防止"手抖点一下"
 *       白扣蓝），达到它就放出去；</li>
 *   <li>耗蓝<b>不随</b>蓄力比例打折 —— 和原版弓一样，提前松手伤害低但箭照样消耗。
 *       想改成按比例打折，改 {@code MagicHandle#castNow} 里取 cost 的那一处即可；</li>
 *   <li>蓄力期间法力<b>不会</b>被预留，所以蓄到一半被别人抽干法力，
 *       松手时会得到 {@code CastResult.NOT_ENOUGH_MANA}。</li>
 * </ul>
 *
 * <h3>{@link #KEEP} 按住持续</h3>
 * <pre>
 *   按下 ──► MagicCastEvent ──► 扣【启动】法力 ──► 进冷却 ──► cast()（做一次"起手"）
 *            └─ 成功则进入维持
 *   按住 ──► 每 tick 回调 onKeepTick(caster, stack, heldTicks)，返回 false 可主动停
 *            同时每 1 秒扣一次【维持】耗蓝；扣不动了立刻结束
 *   松手/耗尽/喊停 ──► onKeepStop(caster, stack, heldTicks)
 * </pre>
 * <ul>
 *   <li>持续逻辑写在 {@code onKeepTick} 里 —— 只要按住右键就会一直执行；</li>
 *   <li>{@code cast()} 在这里是<b>一次性起手</b>（挂效果、放音效、标记状态），
 *       不要把它当成"每 tick 都跑"的地方；</li>
 *   <li>{@code onKeepStop} <b>一定</b>会被调用：松手、法力耗尽、{@code onKeepTick}
 *       返回 false、手被换掉，四种情况都会通知，所以收尾逻辑放这里不会漏；</li>
 *   <li>冷却在<b>开始维持</b>时就写入了（启动那笔法力也确实扣了），
 *       所以短暂按住一下也会吃满冷却。</li>
 * </ul>
 */
public enum UsingType {

    /** 右键按下即生效。见类注释的 {@code IMMEDIATELY} 一节。 */
    IMMEDIATELY,

    /** 按住蓄力，松手释放；蓄满全额，提前放按比例减弱。见类注释的 {@code CHARGE} 一节。 */
    CHARGE,

    /** 按住不放就一直执行逻辑。见类注释的 {@code KEEP} 一节。 */
    KEEP
}
