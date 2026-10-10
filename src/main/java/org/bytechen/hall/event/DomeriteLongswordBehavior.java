package org.bytechen.hall.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDash;

/**
 * 天穹裁决在<b>事件层</b>需要的那些行为。
 *
 * <h3>为什么单独一个类，而不是塞进 {@code ForgeEventHelpers}</h3>
 * <p>这个项目里 {@code ForgeEventHandler} 只做事件订阅、把逻辑转发给一个 Helper。
 * 裁决的这条逻辑只服务一把剑、只有一个方法，塞进那个已经有感染/威胁/异常
 * 三条互不相关职责的 Helper 里只会让它更杂。单独成类之后，"裁决在事件层做了什么"
 * 一句话就能回答：<b>只有突进减伤这一条</b>。</p>
 */
public final class DomeriteLongswordBehavior {

    /**
     * 突进期间的减伤比例。
     *
     * <p>38% —— 也就是"吃到约六成伤害"。取这个量级而不是无敌：
     * 无敌会让突进变成一个无风险的位移按钮，玩家会把它当成逃跑键；
     * 而完全不少又不对，因为突进的定位是"一步踏出去"，
     * 它要求玩家主动进入危险区域（路径终点往往是怪堆里），
     * 这种<b>主动接敌</b>必须有一点回报。</p>
     *
     * <p>用 {@link LivingHurtEvent} 的 {@code setAmount} 而不是直接给
     * {@code invulnerableTime}：后者会让突进期间吃到的第一下伤害把后续
     * 若干 tick 全部免疫掉，那既不可预期、又会被别的东西（着火、中毒）
     * 意外触发。改伤害倍率是干净且可叠加的。</p>
     */
    public static final float DASH_DAMAGE_MULTIPLIER = 0.38f;

    private DomeriteLongswordBehavior() {}

    /**
     * 事件入口：正在突进的玩家受到的伤害打折。
     *
     * <p>只对<b>玩家</b>生效，且只在自己的突进状态下生效 ——
     * 用 {@code VerdictDash.isActive} 判断，它读的是玩家持久数据里的那个标记，
     * 因此"突进被撞墙中止"之后减伤会立刻失效，不存在状态残留。</p>
     */
    public static void verdictDashMitigation(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAmount() <= 0f) return;
        if (!VerdictDash.isActive(player)) return;
        event.setAmount(event.getAmount() * DASH_DAMAGE_MULTIPLIER);
    }
}
