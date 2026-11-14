package org.bytechen.rottenruins.overworld.registry.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

import java.util.Map;
import java.util.WeakHashMap;

/** 狂躁不适：攻击伤害提高 25%，每三秒受到 4 点魔法伤害。 */
public final class ManicDiscomfortEffect extends MobEffect {
    private static final int DAMAGE_INTERVAL = 60;
    private final Map<LivingEntity, Integer> elapsedTicks = new WeakHashMap<>();

    public ManicDiscomfortEffect() {
        super(MobEffectCategory.HARMFUL, 0xA51C30);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, "ad9e3d4f-4f2f-4c38-bf15-dbb612b53249",
                0.25D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        // 当前效果为固定幅度，高等级也不改变伤害和百分比。
        return modifier.getAmount();
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) { return true; }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        int elapsed = elapsedTicks.getOrDefault(entity, 0) + 1;
        var instance = entity.getEffect(this);
        if (elapsed % 8 == 0 && instance != null && instance.isVisible()) {
            level.sendParticles(RegisterParticles.MANIC_DISCOMFORT.get(), entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.55D, entity.getZ(), 2,
                    entity.getBbWidth() * 0.35D, entity.getBbHeight() * 0.3D,
                    entity.getBbWidth() * 0.35D, 0.015D);
        }
        if (elapsed >= DAMAGE_INTERVAL) {
            elapsed = 0;
            entity.hurt(entity.damageSources().magic(), 4.0F);
        }
        elapsedTicks.put(entity, elapsed);
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        super.addAttributeModifiers(entity, attributes, amplifier);
        if (!entity.level().isClientSide) elapsedTicks.remove(entity);
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        super.removeAttributeModifiers(entity, attributes, amplifier);
        if (!entity.level().isClientSide) elapsedTicks.remove(entity);
    }
}
