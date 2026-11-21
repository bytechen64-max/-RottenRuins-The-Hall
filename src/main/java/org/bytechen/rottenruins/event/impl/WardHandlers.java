package org.bytechen.rottenruins.event.impl;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.magic.effect.FlameSlashEffect;
import org.bytechen.rottenruins.content.magic.effect.ShieldEffect;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.utils.SpellDamageUtil;

/**
 * 护盾 / 藤甲 / 焰斩三个效果的结算入口。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WardHandlers {

    private static final double SLASH_RANGE = 3.5d;
    private static final double SLASH_MIN_DOT = 0.5d;

    private WardHandlers() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (ShieldEffect.absorb(entity)) {
            event.setCanceled(true);
            event.setAmount(0.0f);
            return;
        }
        if (!CustomEffects.hasEffect(entity, RegisterCustomEffect.BARK_ARMOR)) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setAmount(event.getAmount() * 2.0f);
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_LIGHTNING)) {
            return;
        }
        event.setCanceled(true);
        event.setAmount(0.0f);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (!CustomEffects.hasEffect(player, RegisterCustomEffect.FLAME_SLASH)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof SwordItem)) {
            return;
        }
        if (!FlameSlashEffect.consume(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();

        SwordAuraEntity.spawnFlameSlash(level, eye.add(look.scale(1.6d)),
                player.getRandom().nextFloat() * 0.3f + 0.85f, SwordAuraEntity.ORIENT_HORIZONTAL);

        float damage = slashDamage(player, weapon);
        for (LivingEntity target : coneTargets(player, look, eye)) {
            SpellDamageUtil.hurt(target, player.damageSources().playerAttack(player), weapon, player, damage);
        }
    }

    private static List<LivingEntity> coneTargets(ServerPlayer player, Vec3 look, Vec3 eye) {
        AABB box = player.getBoundingBox().inflate(SLASH_RANGE);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player || !target.isAlive() || target instanceof Player) {
                continue;
            }
            if (target.isAlliedTo(player)) {
                continue;
            }
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            if (delta.lengthSqr() > SLASH_RANGE * SLASH_RANGE) {
                continue;
            }
            if (delta.normalize().dot(look) < SLASH_MIN_DOT) {
                continue;
            }
            result.add(target);
        }
        return result;
    }

    /** 武器攻击力 + 玩家自身攻击力。 */
    private static float slashDamage(ServerPlayer player, ItemStack weapon) {
        double total = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        for (AttributeModifier modifier : weapon.getAttributeModifiers(
                net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                total += modifier.getAmount();
            }
        }
        return (float) Math.max(1.0d, total);
    }
}
