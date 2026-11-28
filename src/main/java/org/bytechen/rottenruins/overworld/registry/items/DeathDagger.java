package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DeathDagger extends SwordItem {
    public DeathDagger() {
        super(Tiers.IRON, 1, -1.6F, new Properties());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.level().isClientSide && target.getRandom().nextFloat() < 0.25F) {
            target.addEffect(new MobEffectInstance(RegisterEffect.APOPTOSIS.get(), 160, 0), attacker);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.rotten_ruins.death_dagger").withStyle(ChatFormatting.GRAY));
    }
}
