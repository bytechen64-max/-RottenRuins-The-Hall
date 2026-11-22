package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.armor.ModArmorItem;
import org.bytechen.rottenruins.content.armor.ModArmorMaterial;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;

/**
 * 两套盔甲的全套效果。
 *
 * <p>粗制活体：血不满时自己掏饱食度回血（相当于永远"满饱食度自动回血"）；全套击杀额外回血/回饱食/给力量。<br>
 * 如梦：受伤有 {@code 3% × 幸运}（上限 99%）概率免伤，触发时进入虚化。</p>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = HallMod.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorSetHandlers {

    /** 自动回血的间隔（tick）。 */
    private static final int HEAL_INTERVAL = 10;
    /** 每次回 1 点血消耗的饱和度。 */
    private static final float HEAL_SATURATION_COST = 6.0F;
    /** 全套击杀回血量。 */
    private static final float KILL_HEAL = 4.0F;
    /** 虚化持续（tick）。 */
    private static final int ETHEREAL_TICKS = 10;

    private ArmorSetHandlers() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide || player.tickCount % HEAL_INTERVAL != 0) {
            return;
        }
        if (!ModArmorItem.wearingFullSet(player, ModArmorMaterial.CRUDE_LIVING)) {
            return;
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        FoodData food = player.getFoodData();
        if (food.getSaturationLevel() > 0.0F) {
            food.setSaturation(Math.max(0.0F, food.getSaturationLevel() - HEAL_SATURATION_COST));
        } else if (food.getFoodLevel() > 0) {
            food.setFoodLevel(food.getFoodLevel() - 1);
            food.setSaturation(0.0F);
        } else {
            return;
        }
        player.heal(1.0F);
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModArmorItem.wearingFullSet(player, ModArmorMaterial.CRUDE_LIVING)) {
            return;
        }
        player.heal(KILL_HEAL);
        FoodData food = player.getFoodData();
        food.setFoodLevel(Math.min(20, food.getFoodLevel() + 4));
        food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + 2.0F));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModArmorItem.wearingFullSet(player, ModArmorMaterial.DREAMLIKE)) {
            return;
        }
        double chance = Math.min(0.99D, 0.03D * player.getAttributeValue(Attributes.LUCK));
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }
        event.setCanceled(true);
        CustomEffects.addEffect(player, RegisterCustomEffect.ETHEREAL, ETHEREAL_TICKS, 0);
    }
}
