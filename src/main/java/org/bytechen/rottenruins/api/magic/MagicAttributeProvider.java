package org.bytechen.rottenruins.api.magic;
import org.bytechen.rottenruins.HallMod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;

/**
 * 把 RegisterAttributes 的属性接进 MagicStats 求值链（HallMod.commonSetup 注册一次）。
 * spell_power / mana_restore 由事件监听器应用，此处不实现否则会被乘两次；priority=0 最先算。
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

    /** 冷却 = 基值 × (1 − 缩减)；缩减钳在 [0, MAX_COOLDOWN_REDUCTION] 防绕过属性直接塞值。 */
    @Override
    public int modifyCooldownTicks(Player player, ItemStack spell, int base) {
        double reduction = RegisterAttributes.valueOf(player,
                RegisterAttributes.COOLDOWN_REDUCTION, 0.0d);
        reduction = Math.max(0.0d, Math.min(MagicStats.MAX_COOLDOWN_REDUCTION, reduction));
        if (reduction <= 0.0d) {
            return base;
        }
        // 用 round 而不是截断：21 × 0.5 = 10.5 截断成 10 会让人觉得"少减了"
        return Math.max(0, (int) Math.round(base * (1.0d - reduction)));
    }

    @Override
    public int modifyManaCost(Player player, ItemStack spell, int base) {
        return base;
    }

    @Override
    public int priority() {
        return 0;
    }
}
