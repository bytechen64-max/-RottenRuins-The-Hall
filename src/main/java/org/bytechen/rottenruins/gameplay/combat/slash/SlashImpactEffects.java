package org.bytechen.rottenruins.gameplay.combat.slash;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 挥砍自己的命中管线需要补上的原版 {@code Player#attack} 命中前后处理。
 *
 * <h3>为什么不能靠附魔回调</h3>
 * <p>1.20.1 的 {@code FireAspectEnchantment} 只覆写 {@code getMinCost / getMaxCost / getMaxLevel}，
 * <b>没有</b> {@code Enchantment#doPostAttack}；火焰附加与击退都写在 {@code Player#attack} 里。
 * 挥砍武器把 {@code AttackEntityEvent} 取消后那条路径整段不执行，所以这两项只能在结算处复刻。</p>
 *
 * <h3>火焰附加的三段式</h3>
 * <p>顺序照抄原版：命中判定前先点 1 秒（此次伤害因此算作点燃命中）→ 命中成功后补
 * {@code level * 4} 秒 → 命中失败（目标处于受伤无敌帧、被免疫等）则撤销那 1 秒，
 * 否则会出现"没打中却着火"。</p>
 */
public final class SlashImpactEffects {
    private SlashImpactEffects() {}

    /** 一次挥砍命中携带的火焰附加状态，按原版三段式分阶段应用。 */
    public record FireAspect(int level, boolean litByUs) {
        /** 命中成功：补足 {@code level * 4} 秒，与原版 1 级 4 秒一致。 */
        public void afterDealt(Entity target) {
            if (level > 0) target.setSecondsOnFire(level * 4);
        }

        /** 命中失败：撤销命中前的 1 秒占位点燃。 */
        public void afterMissed(Entity target) {
            if (litByUs) target.clearFire();
        }
    }

    /** 命中判定之前的占位点燃，必须在实际 {@code hurt} 调用之前执行。 */
    public static FireAspect beforeHit(Player attacker, LivingEntity target) {
        int level = EnchantmentHelper.getFireAspect(attacker);
        if (level <= 0) return new FireAspect(0, false);
        if (target.isOnFire()) return new FireAspect(level, false);
        target.setSecondsOnFire(1);
        return new FireAspect(level, true);
    }

    /** 击退附魔折算到 {@code LivingEntity#knockback} 的强度：每级 0.5，与原版一致。 */
    public static float knockbackBonus(Player attacker) {
        return EnchantmentHelper.getKnockbackBonus(attacker) * 0.5F;
    }

    /** 击退附魔命中时补上原版的挥击音效。 */
    public static void playKnockbackSound(Player attacker) {
        attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
