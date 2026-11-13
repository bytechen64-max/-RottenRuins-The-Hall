package org.bytechen.rottenruins.api.magic;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 魔法数值的修饰来源：法力上限 / 回蓝 / 槽位 / 耗蓝 / 冷却 / 蓄力 / 伤害 / 击杀回蓝全部经 {@link MagicStats} 折叠 provider 求值。
 *  约定：必须返回合法值（返回 {@code base} = 不改）；按 {@link #priority()} 升序折叠、相等按注册顺序；负数量由 {@code MagicStats} 统一钳制；
 *  {@code player} 可能是客户端侧实例（HUD 要读），不要做只有服务端才有的操作。 */
public interface MagicStatProvider {

    default int modifyMaxMana(Player player, int base) {
        return base;
    }

    default double modifyManaRegenPerSecond(Player player, double base) {
        return base;
    }

    default int modifySlotCount(Player player, int base) {
        return base;
    }

    default int modifyManaCost(Player player, ItemStack spell, int base) {
        return base;
    }

    /** {@code KEEP} 模式维持期间每秒额外消耗（按时间扣，与触发快慢无关）。 */
    default int modifyKeepCostPerSecond(Player player, ItemStack spell, int base) {
        return base;
    }

    /** {@code KEEP} 模式每生效一次的额外消耗；与按秒那条是两条独立计费线、会同时生效，只想要一条时把另一条基值留 0。 */
    default int modifyKeepCostPerTrigger(Player player, ItemStack spell, int base) {
        return base;
    }

    /** 锁定目标的最大距离（格），返回值被钳到 [0, {@link MagicStats#MAX_TARGET_RANGE}]；调到 0 会让需要目标的法术永远锁不到东西。 */
    default double modifyTargetRange(Player player, ItemStack spell, double base) {
        return base;
    }

    /** {@code KEEP} 的触发速度：效果每多少 tick 触发一次（1 = 每 tick，也是上限；小于 1 会被 {@code MagicStats} 钳到 1）。 */
    default int modifyKeepTriggerInterval(Player player, ItemStack spell, int base) {
        return base;
    }

    /** 释放后的冷却 tick；同名物品共享这个冷却。 */
    default int modifyCooldownTicks(Player player, ItemStack spell, int base) {
        return base;
    }

    default int modifyChargeTicks(Player player, ItemStack spell, int base) {
        return base;
    }

    /** {@code CHARGE} 的最低蓄力比例（0.0~1.0）：低于它本次作废（不扣蓝不进冷却），达到则放出、威力按真实完成度缩放。 */
    default float modifyMinChargeRatio(Player player, ItemStack spell, float base) {
        return base;
    }

    default float modifySpellDamage(Player player, ItemStack spell, LivingEntity target,
                                    DamageSource source, float base) {
        return base;
    }

    /** 击杀回蓝，基值 = 受害者最大生命值 × {@link MagicStats#BASE_KILL_MANA_PER_HP}。 */
    default float modifyKillMana(Player player, LivingEntity victim, float base) {
        return base;
    }

    /** 折叠顺序，越小越先执行：建议属性修饰器 0~99、附魔 100~199、临时 Buff 200+。 */
    default int priority() {
        return 0;
    }
}
