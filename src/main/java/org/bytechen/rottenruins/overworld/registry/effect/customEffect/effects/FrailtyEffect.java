package org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** <b>衰竭</b> —— 负面效果池之一：攻击力 -{@code 2 × 层数}（加算修饰器）。 */
public class FrailtyEffect extends CustomEffectBase {

    private static final UUID ATTACK_MODIFIER_ID =
            UUID.fromString("5b2e9c41-8d63-4f7a-a0c8-1e4f7b6d2c09");

    public FrailtyEffect(@NotNull ResourceLocation id) {
        super(id, CustomEffectCategory.HARMFUL, 0x7A3B3B);
    }

    @Override
    public void applyAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
        addModifier(entity, Attributes.ATTACK_DAMAGE, new AttributeModifier(
                ATTACK_MODIFIER_ID, "rotten_ruins.frailty.attack",
                -2.0d * (1 + Math.max(0, amplifier)),
                AttributeModifier.Operation.ADDITION));
    }

    @Override
    public void removeAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
        var attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(ATTACK_MODIFIER_ID);
        }
    }
}
