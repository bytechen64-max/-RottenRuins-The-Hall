package org.bytechen.hall.api.magic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.overworld.registry.RegisterAttributes;

/**
 * 把 {@link RegisterAttributes} 里那几个属性接进 {@link MagicStats} 求值链。
 *
 * <p>由 {@code HallMod.commonSetup} 注册一次，之后所有魔法数值都会自动带上属性修饰器
 * 的效果 —— 法术物品、法力池、槽位容器都不需要知道"属性"这回事。</p>
 *
 * <h3>这里<b>不</b>实现的两个钩子，以及为什么</h3>
 * <p>{@code modifySpellDamage} 与 {@code modifyKillMana} 刻意留空：</p>
 * <ul>
 *   <li>{@code hall:spell_power} 由 {@code MagicDamageEvent} 的监听器应用
 *       （见 {@code MagicAttributeHandlers}）。那是"一次具体伤害"的场合，
 *       比在 {@link MagicStats} 里乘一个通用倍率更准 —— 那里拿不到目标与来源。</li>
 *   <li>{@code hall:mana_restore} 由 {@code ManaRestoreEvent} 的监听器应用，
 *       这样它同时覆盖自然回蓝、击杀回蓝、法术回蓝三条路，而且不会被重复乘。</li>
 * </ul>
 * <p>如果这里再实现一遍，同一个属性就会被乘两次。所以两者是<b>互斥</b>的两条路：
 * 属性走事件；{@link MagicStatProvider} 的那两个钩子留给非属性来源
 * （buff、地形、临时效果）。</p>
 *
 * <h3>{@link #priority()}</h3>
 * <p>取 0（最底层）：属性是"装备面板"，应该先算，附魔（建议 100+）与临时 buff（200+）
 * 再在其上叠。这样顺序稳定、可复现。</p>
 */
public class MagicAttributeProvider implements MagicStatProvider {

    @Override
    public int modifyMaxMana(Player player, int base) {
        double bonus = RegisterAttributes.valueOf(player, RegisterAttributes.MAX_MANA, 0.0d);
        return base + (int) Math.round(bonus);
    }

    @Override
    public double modifyManaRegenPerSecond(Player player, double base) {
        double bonus = RegisterAttributes.valueOf(player, RegisterAttributes.MANA_REGENERATION, 0.0d);
        return base + bonus;
    }

    @Override
    public int modifySlotCount(Player player, int base) {
        double bonus = RegisterAttributes.valueOf(player, RegisterAttributes.MAGIC_SLOTS, 0.0d);
        return base + (int) Math.round(bonus);
    }

    /**
     * 刻意留空 —— 见类注释。想按附魔改耗蓝请覆写这个钩子，
     * 但它与"属性"无关，所以不在这里做。
     */
    @Override
    public int modifyManaCost(Player player, ItemStack spell, int base) {
        return base;
    }

    @Override
    public int priority() {
        return 0;
    }
}
