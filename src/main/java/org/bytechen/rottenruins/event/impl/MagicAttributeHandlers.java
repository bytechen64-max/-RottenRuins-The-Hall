package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.magic.MagicDamageEvent;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.api.magic.ManaRestoreEvent;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;

/**
 * 属性修饰器接入魔法事件：spell_power 乘法术伤害，mana_restore 乘法力回复。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicAttributeHandlers {

    private MagicAttributeHandlers() {
    }

    /** 法术伤害 × 有效法强（通用+流派，由 {@link MagicStats#spellPower} 相加）；施法者 null 则不动。 */
    @SubscribeEvent
    public static void onMagicDamage(MagicDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ServerPlayer caster = event.getCaster();
        if (caster == null) {
            return;
        }
        // 法杖等级是同流派的固定点数加成：先加再乘倍率，所以法强同样放大它
        float amount = event.getAmount() + MagicStats.staffGradeBonus(caster, event.getSpell());
        double power = MagicStats.spellPower(caster, event.getSpell());
        if (power != 1.0d) {
            amount *= (float) Math.max(0.0d, power);
        }
        event.setAmount(Math.max(0f, amount));
    }

    /** 法力回复 × hall:mana_restore（含 REGEN/KILL/SPELL/COMMAND/OTHER）；float 小数不被取整吃掉。 */
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
