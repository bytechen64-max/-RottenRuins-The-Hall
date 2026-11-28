package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.bytechen.rottenruins.api.boomerang.IBoomerangItem;
import org.bytechen.rottenruins.api.boomerang.BoomerangConsumption;
import org.bytechen.rottenruins.overworld.registry.entities.projectile.BoomerangEntity;

import java.util.List;

/** Reusable right-click launcher; subclasses customize the IBoomerangItem hooks. */
public class BoomerangItem extends Item implements IBoomerangItem {
    public BoomerangItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(tooltipLine("damage", number(ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(throwDamage(stack)))));
        tooltip.add(tooltipLine("cooldown", number(ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(
                Math.max(1, cooldownTicks(stack)) / 20.0D))));
    }

    private static Component tooltipLine(String suffix, Object... values) {
        return Component.translatable("tooltip.rotten_ruins.boomerang." + suffix, values).withStyle(ChatFormatting.GRAY);
    }

    private static Component number(String value) {
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int cost = Math.max(0, consumptionCost(stack));
        BoomerangConsumption mode = consumptionMode(stack);
        if (player.getCooldowns().isOnCooldown(this)
                || (!player.getAbilities().instabuild && cost > 0
                && ((mode == BoomerangConsumption.ITEM_COUNT && stack.getCount() < cost)
                || (mode == BoomerangConsumption.DURABILITY && !stack.isDamageableItem())))) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            BoomerangEntity projectile = BoomerangEntity.create(level, player, stack);
            if (!level.addFreshEntity(projectile)) return InteractionResultHolder.fail(stack);
            player.getCooldowns().addCooldown(this, Math.max(1, cooldownTicks(stack)));
            if (!player.getAbilities().instabuild) {
                switch (mode) {
                    case DURABILITY -> stack.hurtAndBreak(cost, player, p -> p.broadcastBreakEvent(hand));
                    case ITEM_COUNT -> stack.shrink(cost);
                    case NONE -> { }
                }
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7F, 1.2F);
            onThrown(projectile, player, projectile.getItem());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
