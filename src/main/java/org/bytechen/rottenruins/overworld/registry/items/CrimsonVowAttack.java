package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.compat.BCCoreCompat;
import org.bytechen.rottenruins.network.s2c.HeartLosePacket;

/**
 * {@link CrimsonVow} 的攻击：原版伤害 + {@code FLAT_DAMAGE} + 最大生命值 {@code PERCENT_OF_MAX_HEALTH}%，经
 * {@link BCCoreCompat#damage} 改血（无视无敌帧/护甲/抗改血，未装 VitalProbe 时回退原版 hurt），两段串联不重复扣血。
 */
public final class CrimsonVowAttack {

    public static final float FLAT_DAMAGE = 20.0f;

    public static final float PERCENT_OF_MAX_HEALTH = 20.0f;

    public static final int HEART_LOSE_COUNT = 26;

    private CrimsonVowAttack() {}

    public static boolean isWielding(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getItem() instanceof CrimsonVow;
    }

    /** 事件入口：必须在服务端调用，且不要取消事件（原版受击反馈与落剑都靠它走完）。 */
    public static boolean onAttackLanded(Player player, Entity target) {
        if (player == null || target == null) return false;
        if (!isWielding(player)) return false;
        if (player.level().isClientSide()) return false;
        if (!(target instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;

        DamageSource source = player.damageSources().playerAttack(player);

        boolean dealt = BCCoreCompat.damage(living, FLAT_DAMAGE, source);

        if (living.isAlive()) {
            dealt |= BCCoreCompat.damagePercentOfMax(living, PERCENT_OF_MAX_HEALTH, source);
        }

        if (!dealt) return false;

        HeartLosePacket.sendBurst(player, living, HEART_LOSE_COUNT);

        return true;
    }
}
