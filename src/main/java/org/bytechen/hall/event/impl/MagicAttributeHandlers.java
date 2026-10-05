package org.bytechen.hall.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.magic.MagicDamageEvent;
import org.bytechen.hall.api.magic.ManaRestoreEvent;
import org.bytechen.hall.overworld.registry.RegisterAttributes;

/**
 * 把<b>属性修饰器</b>接进两个魔法事件的监听器。
 *
 * <p>这是"用属性修饰器改魔法数值"真正生效的地方 —— 两个倍率属性都在这里应用：</p>
 * <ul>
 *   <li>{@link MagicDamageEvent} ← {@code hall:spell_power}（法术伤害 ×它）</li>
 *   <li>{@link ManaRestoreEvent} ← {@code hall:mana_restore}（一切法力回复 ×它）</li>
 * </ul>
 *
 * <p>另外三个属性（{@code max_mana} / {@code mana_regeneration} / {@code magic_slots}）
 * 是"每次求值现算"的量，走 {@code MagicAttributeProvider} → {@code MagicStats}
 * 那条路，不在这里。</p>
 *
 * <h3>怎么在游戏里验证（无需写任何物品）</h3>
 * <pre>
 *   # 1) 先确认属性挂上了（应能列出 hall:max_mana 等）
 *   /attribute &#64;s
 *
 *   # 2) 法术伤害翻倍
 *   /attribute &#64;s hall:spell_power modifier add hall:test_sp 1.0 multiply_total
 *
 *   # 3) 法力回复 ×3（默认 1.0，加 2.0）
 *   /attribute &#64;s hall:mana_restore modifier add hall:test_mr 2.0 multiply_total
 *
 *   # 4) 法力上限 +500、每秒回蓝 +4、槽位 +4
 *   /attribute &#64;s hall:max_mana         modifier add hall:test_mm 500 add
 *   /attribute &#64;s hall:mana_regeneration modifier add hall:test_rm 4 add
 *   /attribute &#64;s hall:magic_slots      modifier add hall:test_ms 4 add
 *
 *   # 移除
 *   /attribute &#64;s hall:spell_power modifier remove hall:test_sp
 * </pre>
 *
 * <h3>为什么乘在事件上而不是写在物品里</h3>
 * <p>因为属性可以来自任意地方（装备、药水、别的模组的修饰器），
 * 而且只在"这一下真的打出去"的时候才有意义。放在事件上，
 * 时序也确定：先算法术自己的基值，再乘这里的属性倍率，
 * 别的模组/你的 buff 监听器再叠在更靠后的优先级上。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicAttributeHandlers {

    private MagicAttributeHandlers() {
    }

    /**
     * 法术伤害：乘以施法者的 {@code hall:spell_power}。
     *
     * <p>施法者为 {@code null}（陷阱 / 环境法术）时不动 —— 那种情况没有"谁的法术强度"可言。</p>
     */
    @SubscribeEvent
    public static void onMagicDamage(MagicDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ServerPlayer caster = event.getCaster();
        if (caster == null) {
            return;
        }
        double power = RegisterAttributes.valueOf(caster, RegisterAttributes.SPELL_POWER, 1.0d);
        if (power == 1.0d) {
            return;
        }
        event.setAmount((float) (event.getAmount() * Math.max(0.0d, power)));
    }

    /**
     * 法力回复：乘以玩家的 {@code hall:mana_restore}。
     *
     * <p>覆盖全部三种来源：自然回蓝（{@code REGEN}）、击杀回蓝（{@code KILL}）、
     * 主动加蓝（{@code SPELL} / {@code COMMAND} / {@code OTHER}）。</p>
     *
     * <p>数值是 {@code float} 且下游有小数累加器，所以 {@code ×1.5} 得到的是精确的
     * 1.5 倍，不会被每次取整吃掉（见 {@code TwistedPoint#applyRestore}）。</p>
     */
    @SubscribeEvent
    public static void onManaRestore(ManaRestoreEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        double multiplier = RegisterAttributes.valueOf(player, RegisterAttributes.MANA_RESTORE, 1.0d);
        if (multiplier == 1.0d) {
            return;
        }
        event.multiplyAmount((float) Math.max(0.0d, multiplier));
    }
}
