package org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.jetbrains.annotations.NotNull;

/**
 * <b>腐蚀</b> —— 负面效果池之一：护甲 -{@code 2 × 层数}。
 *
 * <p>这三个（腐蚀 / 迟滞 / 衰竭）是奥数印记"随机塞负面效果"用的<b>演示用池子</b>，
 * 都做成纯属性削减，行为直观、不依赖别的系统。要换成自己的负面效果：
 * 在 {@code RegisterCustomEffect} 里注册新效果、把
 * {@link CustomEffectCategory#HARMFUL} 分类带上即可（印记那边是按分类随机抽的）。</p>
 */
public class CorrosionEffect extends CustomEffectBase {

    private static final java.util.UUID ARMOR_MODIFIER_ID =
            java.util.UUID.fromString("a1c4f2e8-3b7d-4c19-9f52-6d8e0b7a1c30");

    public CorrosionEffect(@NotNull ResourceLocation id) {
        super(id, CustomEffectCategory.HARMFUL, 0x8A6B2D);
    }

    @Override
    public void applyAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
        addModifier(entity, Attributes.ARMOR, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                ARMOR_MODIFIER_ID, "rotten_ruins.corrosion.armor",
                -2.0d * (1 + Math.max(0, amplifier)),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    @Override
    public void removeAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
        var armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(ARMOR_MODIFIER_ID);
        }
    }
}
