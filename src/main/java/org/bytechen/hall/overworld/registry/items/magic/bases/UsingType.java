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
 *   <li>需要目标的法术在这里<b>按下瞬间</b>解析准星目标；</li>
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
 *       松手时会得到 {@code CastResult.NOT_ENOUGH_MANA}；</li>
 *   <li>需要目标的法术在<b>松手那一刻</b>才解析准星目标 —— 所以蓄力期间瞄准是有效的，
 *       而按下时瞄的是谁无所谓。</li>
 * </ul>
 *
 * <h3>{@link #KEEP} 按住持续</h3>
 * <pre>
 *   按下 ──► MagicCastEvent ──► 扣【启动】法力 ──► 进冷却 ──► cast()（做一次"起手"）
 *            └─ 成功则进入维持
 *   按住 ──► 按【触发速度】回调 onKeepTick(caster, stack, heldTicks, triggerInterval, target)
 *            · 触发速度 1 = 每 tick、2 = 每 2 tick、20 = 每秒（第 1 tick 立刻触发一次）
 *            · 返回 false 可主动停止维持
 *            每次触发后扣一次【每次触发】耗蓝（keepCostPerTrigger，0 = 不扣）
 *            同时每 1 秒扣一次【维持】耗蓝；两者扣不动了都立刻结束
 *   松手/耗尽/喊停 ──► onKeepStop(caster, stack, heldTicks)
 * </pre>
 * <ul>
 *   <li>持续逻辑写在 {@code onKeepTick} 里。注意触发<b>频率由「触发速度」决定，不是每 tick</b>
 *       —— 想让伤害每 5 tick 结算一次就声明 {@code 5}，框架只在该触发时才回调，
 *       不用自己数 tick；</li>
 *   <li>触发速度的三种声明方式：{@code CMagicBaseItem} 的 10 参构造器、覆写
 *       {@code baseKeepTriggerInterval()}、或注册 {@code MagicStatProvider} 用
 *       {@code modifyKeepTriggerInterval} 由属性/附魔动态改；</li>
 *   <li>两条耗蓝线<b>互相独立</b>：{@code keepCostPerSecond} 按秒扣（跟触发快慢无关），
 *       {@code keepCostPerTrigger} 跟着触发节奏扣（"生效有间隔，扣费也有间隔"）。
 *       新法术建议只用 {@code keepCostPerTrigger}，把按秒那条留 0；</li>
 *   <li>{@code onKeepTick} 的<b>五参版本</b>能读到本次触发速度与<b>锁定的目标</b>；
 *       覆写四参/三参版本的旧代码照常工作（新版默认实现会逐级转发）；</li>
 *   <li>需要目标的法术在<b>开始维持那一刻</b>解析并<b>锁定</b>目标，整段维持都用它
 *       —— 按住期间准星划过去不会换人。想做"光束跟准星走"，就在 {@code onKeepTick} 里
 *       自己再调一次 {@code MagicHandle.resolveTarget(caster, stack)}；</li>
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
