package org.bytechen.hall.api.magic;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 魔法数值的<b>修饰来源</b>。
 *
 * <p>这是给「以后要做属性修饰器和附魔」准备的统一插槽：本模组所有魔法相关的数值
 * （法力上限、回蓝速度、槽位数、耗蓝、冷却、蓄力时长、法术伤害、击杀回蓝）
 * <b>全部</b>经由 {@link MagicStats} 求值，而 {@code MagicStats} 会把基值依次交给
 * 注册过的 {@link MagicStatProvider} 折叠。</p>
 *
 * <h3>怎么用它做「属性修饰器」</h3>
 * <pre>{@code
 * MagicStatRegistry.register(new MagicStatProvider() {
 *     @Override
 *     public int modifyMaxMana(Player player, int base) {
 *         AttributeInstance inst = player.getAttribute(MyAttributes.MAX_MANA.get());
 *         return inst == null ? base : (int) (base + inst.getValue());
 *     }
 * });
 * }</pre>
 *
 * <h3>怎么用它做「附魔」</h3>
 * <pre>{@code
 * MagicStatRegistry.register(new MagicStatProvider() {
 *     @Override
 *     public int modifyManaCost(Player player, ItemStack spell, int base) {
 *         int lv = EnchantmentHelper.getItemEnchantmentLevel(MyEnchants.SIPHON.get(), spell);
 *         return lv <= 0 ? base : Math.max(0, base - lv * 5);
 *     }
 * });
 * }</pre>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>所有方法都<b>必须</b>返回一个合法值；返回 {@code base} 表示"不改"。</li>
 *   <li>按 {@link #priority()} 升序依次折叠，相等时按注册顺序 —— 所以多个来源叠加时
 *       顺序是确定的，可复现。</li>
 *   <li>不得为负数的量（耗蓝 / 冷却 / 蓄力 / 槽位）由 {@link MagicStats} 统一做下限钳制，
 *       provider 里<b>不需要</b>自己钳。</li>
 *   <li>{@code player} 可能是客户端侧的 {@code Player}（槽位数、法力上限要给 HUD 读），
 *       所以不要在这里做只有服务端才有的操作。</li>
 * </ul>
 */
public interface MagicStatProvider {

    /** 法力上限的基值是 {@link MagicStats#BASE_MAX_MANA}。 */
    default int modifyMaxMana(Player player, int base) {
        return base;
    }

    /** 每秒回蓝的基值是 {@link MagicStats#BASE_REGEN_PER_SECOND}。 */
    default double modifyManaRegenPerSecond(Player player, double base) {
        return base;
    }

    /** 可用的魔法槽位数，基值是 {@link MagicStats#BASE_SLOT_COUNT}（10）。 */
    default int modifySlotCount(Player player, int base) {
        return base;
    }

    /** 单次释放的法力消耗（{@code KEEP} 模式下是"启动消耗"）。 */
    default int modifyManaCost(Player player, ItemStack spell, int base) {
        return base;
    }

    /** {@code KEEP} 模式维持期间每秒额外消耗。 */
    default int modifyKeepCostPerSecond(Player player, ItemStack spell, int base) {
        return base;
    }

    /** 释放后的冷却 tick；同名物品共享这个冷却。 */
    default int modifyCooldownTicks(Player player, ItemStack spell, int base) {
        return base;
    }

    /** {@code CHARGE} 模式需要蓄力的 tick 数。 */
    default int modifyChargeTicks(Player player, ItemStack spell, int base) {
        return base;
    }

    /**
     * {@code CHARGE} 模式的<b>最低蓄力比例</b>（{@code 0.0~1.0}）。
     *
     * <p>低于它这次释放作废（不扣蓝不进冷却）；达到它就放出去，威力按真实完成度缩放。
     * 想让某个附魔"降低蓄力门槛"就在这里减它。</p>
     */
    default float modifyMinChargeRatio(Player player, ItemStack spell, float base) {
        return base;
    }

    /** 法术伤害的数值修正（基值由法术物品给出）。 */
    default float modifySpellDamage(Player player, ItemStack spell, LivingEntity target,
                                    DamageSource source, float base) {
        return base;
    }

    /** 击杀回蓝量，基值 = 受害者最大生命值 × {@link MagicStats#BASE_KILL_MANA_PER_HP}。 */
    default float modifyKillMana(Player player, LivingEntity victim, float base) {
        return base;
    }

    /**
     * 折叠顺序。数值越小越先执行（即越"底层"）。
     * <p>建议：属性修饰器用 0～99，附魔用 100～199，临时 Buff 用 200+，
     * 这样"先算装备、再算附魔、最后算 buff"是稳定的。
     */
    default int priority() {
        return 0;
    }
}
