package org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** <b>迟滞</b> —— 负面效果池之一：移动速度 -{@code 20% × 层数}（乘算修饰器）。 */
public class LethargyEffect extends CustomEffectBase {

    private static final UUID SPEED_MODIFIER_ID =
            UUID.fromString("d3f8b6a2-71e5-4a08-bb47-9c2d5e8f3a61");

    public LethargyEffect(@NotNull ResourceLocation id) {
        super(id, CustomEffectCategory.HARMFUL, 0x3E6BA8);
    }

    @Override
    public void applyAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
        addModifier(entity, Attributes.MOVEMENT_SPEED, new AttributeModifier(
                SPEED_MODIFIER_ID, "rotten_ruins.lethargy.speed",
                -0.2d * (1 + Math.max(0, amplifier)),
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @Override
    public void removeAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
        var speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
    }
}
