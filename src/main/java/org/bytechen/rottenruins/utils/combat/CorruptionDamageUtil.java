package org.bytechen.rottenruins.utils.combat;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

/**
 * 伤害应用工具类，支持忽略无敌帧、盾牌格挡、不死图腾、分级伤害处理等。
 */
public class CorruptionDamageUtil {

    /**
     * 统一伤害入口
     *
     * @param attacker           攻击者（用于伤害来源归属）
     * @param target             目标
     * @param ignoreInvulDamage  无视无敌帧的最小伤害（若目标有吸收伤害会先消耗）
     * @param commonDamage       普通伤害（受无敌帧限制）
     * @param level              伤害等级：
     *                           <ul>
     *                           <li>1 - 物理伤害（可被盾牌格挡）</li>
     *                           <li>2 - 虚空伤害（不可格挡，直接扣血）</li>
     *                           <li>3 - 原版setHealth</li>
     *                           <li>4+ - 自定义（当前与3相同）</li>
     *                           </ul>
     * @param hurtTick           是否重置无敌帧（true 则允许连续伤害）
     * @return 是否造成了任何伤害（包括最小伤害或普通伤害）
     */
    public static boolean TrueHurt(LivingEntity attacker, LivingEntity target,
                                   float ignoreInvulDamage, float commonDamage,
                                   int level, boolean hurtTick) {
        if (isInvulnerableTarget(target)) return false;

        // 等级1：可被盾牌格挡
        if (level == 1 && isBlockingWithShield(target)) {
            handleShieldBlocking(target, ignoreInvulDamage);
            return false;
        }

        // 等级1、2：可能触发不死图腾
        if (level <= 2 && willTriggerTotem(target, ignoreInvulDamage)) {
            triggerTotemEffect(target);
            return false;
        }

        boolean minApplied = false;
        if (ignoreInvulDamage != 0) {
            minApplied = processMinDamageByLevel(attacker, target, ignoreInvulDamage, level);
        }

        boolean commonApplied = false;
        if (commonDamage != 0) {
            commonApplied = processCommonDamage(attacker, target, commonDamage, hurtTick);
        }

        return minApplied || commonApplied;
    }

    // ============= 辅助方法 =============
    private static boolean isInvulnerableTarget(LivingEntity target) {
        if (target instanceof Player player) {
            return player.getAbilities().instabuild || player.isSpectator();
        }
        return false;
    }

    private static boolean isBlockingWithShield(LivingEntity entity) {
        if (entity instanceof Player player && player.isUsingItem()) {
            return player.getUseItem().getItem() instanceof ShieldItem;
        }
        return entity.isBlocking() &&
                (entity.getMainHandItem().getItem() instanceof ShieldItem ||
                        entity.getOffhandItem().getItem() instanceof ShieldItem);
    }

    private static void handleShieldBlocking(LivingEntity target, float damage) {
        ItemStack shield = ItemStack.EMPTY;
        if (target instanceof Player player && player.isUsingItem()) {
            shield = player.getUseItem();
        } else {
            if (target.getMainHandItem().getItem() instanceof ShieldItem) shield = target.getMainHandItem();
            else if (target.getOffhandItem().getItem() instanceof ShieldItem) shield = target.getOffhandItem();
        }
        if (!shield.isEmpty()) {
            int cost = Math.max(1, (int) damage);
            shield.hurtAndBreak(cost, target, entity -> entity.broadcastBreakEvent(entity.getUsedItemHand()));
        }
        playShieldBlockSound(target);
    }

    private static boolean processMinDamageByLevel(LivingEntity attacker, LivingEntity target,
                                                   float damage, int level) {
        switch (level) {
            case 1: return processLevel1MinDamage(attacker, target, damage);
            case 2: return processLevel2MinDamage(target, damage);
            case 3: return processLevel3MinDamage(target, damage);
            default: return processLevel4PlusMinDamage(target, damage, level);
        }
    }

    private static boolean processLevel1MinDamage(LivingEntity attacker, LivingEntity target, float damage) {
        float originalHealth = target.getHealth();
        int savedInvul = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean applied = false;
        try {
            if (damage > 0) {
                target.hurt(target.damageSources().mobAttack(attacker), damage);
                // 确保伤害生效（某些情况 may 被吸收）
                if (target.getHealth() == originalHealth && target.getAbsorptionAmount() == 0) {
                    target.setHealth(Math.max(0, originalHealth - damage));
                }
                applied = true;
            } else if (damage < 0) {
                target.hurt(target.damageSources().mobAttack(attacker), damage);
                applied = true;
            }
        } finally {
            target.invulnerableTime = savedInvul;
        }
        if (applied && damage > 0 && target.level().isClientSide) playHurtSound(target);
        return applied;
    }

    private static boolean processLevel2MinDamage(LivingEntity target, float damage) {
        if (damage == 0) return false;

        target.setHealth(Math.max(0, target.getHealth() - damage));
        if (damage > 0 && target.level().isClientSide) {
            target.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.1f);
        }
        return true;
    }

    private static boolean processLevel3MinDamage(LivingEntity target, float damage) {
        if (damage == 0) return false;
        target.setHealth(Math.max(0, Math.min(target.getMaxHealth(), target.getHealth() - damage)));
        if (damage > 0 && target.level().isClientSide) playHurtSound(target);
        return true;
    }

    private static boolean processLevel4PlusMinDamage(LivingEntity target, float damage, int level) {
        // 目前与等级3相同，可扩展
        return processLevel3MinDamage(target, damage);
    }

    private static boolean processCommonDamage(LivingEntity attacker, LivingEntity target,
                                               float damage, boolean hurtTick) {
        if (damage == 0) return false;
        int savedInvul = target.invulnerableTime;
        if (hurtTick) target.invulnerableTime = 0;
        boolean applied = false;
        try {
            if (damage > 0) {
                if (target.getAbsorptionAmount() > 0 || target.invulnerableTime == 0) {
                    target.hurt(target.damageSources().mobAttack(attacker), damage);
                    applied = true;
                    if (hurtTick && target instanceof Player && !target.level().isClientSide) {
                        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1.0F, 1.0F);
                    }
                }
            } else if (damage < 0) {
                target.hurt(target.damageSources().mobAttack(attacker), damage);
                applied = true;
            }
        } finally {
            if (hurtTick) target.invulnerableTime = savedInvul;
        }
        if (applied && damage > 0 && target.level().isClientSide) playHurtSound(target);
        return applied;
    }

    private static boolean willTriggerTotem(LivingEntity target, float damage) {
        if (!(target instanceof Player player)) return false;
        return player.getHealth() - damage <= 0 && hasTotemOfUndying(player);
    }

    private static boolean hasTotemOfUndying(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return true;
        }
        return player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING;
    }

    private static void triggerTotemEffect(LivingEntity target) {
        if (!(target instanceof Player player)) return;
        ItemStack totem = findAndConsumeTotem(player);
        if (totem.isEmpty()) return;
        player.setHealth(1);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        playTotemSound(player);
    }

    private static ItemStack findAndConsumeTotem(Player player) {
        if (player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
            ItemStack totem = player.getOffhandItem();
            totem.shrink(1);
            return totem;
        }
        if (player.getMainHandItem().getItem() == Items.TOTEM_OF_UNDYING) {
            ItemStack totem = player.getMainHandItem();
            totem.shrink(1);
            return totem;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                stack.shrink(1);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void playShieldBlockSound(LivingEntity target) {
        if (!target.level().isClientSide) {
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            target.playSound(SoundEvents.SHIELD_BLOCK, 1.0f, 1.0f);
        }
    }

    private static void playHurtSound(LivingEntity target) {
        if (target instanceof Player) target.playSound(SoundEvents.PLAYER_HURT, 1.0f, 1.0f);
        else target.playSound(SoundEvents.GENERIC_HURT, 1.0f, 1.0f);
    }

    private static void playTotemSound(Player player) {
        if (!player.level().isClientSide) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.0F);
        }
    }
}