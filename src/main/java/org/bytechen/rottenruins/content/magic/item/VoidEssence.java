package org.bytechen.rottenruins.content.magic.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 虚空精粹：能喝，但喝完会饿。 */
public class VoidEssence extends Item {

    public static final int HUNGER_COST = 5;
    public static final float SATURATION_COST = 5.0F;

    public VoidEssence() {
        super(new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(0)
                .saturationMod(0.0F)
                .alwaysEat()
                .build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            FoodData food = player.getFoodData();
            food.setFoodLevel(Math.max(0, food.getFoodLevel() - HUNGER_COST));
            food.setSaturation(Math.max(0.0F, food.getSaturationLevel() - SATURATION_COST));
        }
        return result;
    }
}
