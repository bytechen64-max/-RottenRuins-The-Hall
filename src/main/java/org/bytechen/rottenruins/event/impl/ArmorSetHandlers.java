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
 * <p>粗制活体：血不满时自己掏饱食度换生命，换来的生命按最大生命值等比给；全套击杀额外回血/回饱食/给力量。<br>
 * 如梦：受伤有 {@code 3% × 幸运}（上限 50%）概率免伤，触发时进入虚化。</p>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = HallMod.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorSetHandlers {

    /** 自动回血的间隔（tick）。 */
    private static final int HEAL_INTERVAL = 10;
    /**
     * 每消耗 1 点饱食度换来的生命，按最大生命值的比例算 —— 1 点饱食度是 20 点的 5%，
     * 所以「回复百分比 = 消耗百分比」正好对上 {@code 0.05}。
     */
    private static final double HEAL_RATIO_PER_FOOD = 0.05D;
    /** 吃饱和度时的额外治疗效力：1 点饱和度回 7.5% 而不是 5%。 */
    private static final double SATURATION_HEAL_BONUS = 0.5D;
    /** 饱食度最多消耗到这个值，再低就不回血了。 */
    private static final int MIN_FOOD_LEVEL = 10;
    /** 全套击杀回血量。 */
    private static final float KILL_HEAL = 4.0F;
    /** 虚化持续（tick）。 */
    private static final int ETHEREAL_TICKS = 10;
    /** 每点幸运提供的免伤概率。 */
    private static final double DODGE_PER_LUCK = 0.03D;
    /** 免伤概率上限。 */
    private static final double MAX_DODGE = 0.50D;

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
        int source = consumeFood(player.getFoodData());
        if (source == FOOD_NONE) {
            return;
        }
        double ratio = source == FOOD_SATURATION
                ? HEAL_RATIO_PER_FOOD * (1.0D + SATURATION_HEAL_BONUS)
                : HEAL_RATIO_PER_FOOD;
        player.heal((float) (player.getMaxHealth() * ratio));
    }

    private static final int FOOD_NONE = 0;
    private static final int FOOD_LEVEL = 1;
    private static final int FOOD_SATURATION = 2;

    /**
     * 先吃饱和度，饱和不够再吃饱食度；饱食度不降到 {@link #MIN_FOOD_LEVEL} 以下。
     *
     * @return {@link #FOOD_NONE} 没得吃 / {@link #FOOD_LEVEL} 吃的是饱食度 / {@link #FOOD_SATURATION} 吃的是饱和度
     */
    private static int consumeFood(FoodData food) {
        if (food.getSaturationLevel() >= 1.0F) {
            food.setSaturation(food.getSaturationLevel() - 1.0F);
            return FOOD_SATURATION;
        }
        if (food.getFoodLevel() > MIN_FOOD_LEVEL) {
            food.setFoodLevel(food.getFoodLevel() - 1);
            food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel()));
            return FOOD_LEVEL;
        }
        return FOOD_NONE;
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
        double chance = Math.min(MAX_DODGE, DODGE_PER_LUCK * player.getAttributeValue(Attributes.LUCK));
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }
        event.setCanceled(true);
        CustomEffects.addEffect(player, RegisterCustomEffect.ETHEREAL, ETHEREAL_TICKS, 0);
    }
}
