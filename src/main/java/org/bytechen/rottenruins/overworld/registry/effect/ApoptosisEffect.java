package org.bytechen.rottenruins.overworld.registry.effect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.bytechen.rottenruins.overworld.registry.ModDamageTypes;

import java.util.Map;
import java.util.WeakHashMap;

public final class ApoptosisEffect extends MobEffect {
    // MobEffect is shared: keep a separate clock for each affected entity.
    private final Map<LivingEntity, Integer> elapsedTicks = new WeakHashMap<>();

    public ApoptosisEffect() {
        super(MobEffectCategory.HARMFUL, 0x486877);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return;
        int elapsed = elapsedTicks.getOrDefault(entity, 0) + 1;
        if (elapsed >= 40) {
            elapsed = 0;
            var type = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ModDamageTypes.APOPTOSIS);
            entity.hurt(new DamageSource(type), amplifier + 1.0F);
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
