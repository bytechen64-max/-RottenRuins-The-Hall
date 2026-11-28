package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;


import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractCorruptionProjectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class PoisonArrowProjectile extends AbstractCorruptionProjectile {

    // 标准构造器
    public PoisonArrowProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public PoisonArrowProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, double x, double y, double z, Level level) {
        super(entityType, x, y, z, level);
    }

    public PoisonArrowProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, LivingEntity shooter, Level level) {
        super(entityType, shooter, level);
    }
    public PoisonArrowProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, Level level, Consumer<PoisonArrowProjectile> consumer) {
        super(entityType, level);
        consumer.accept(this);
    }


    @Override
    protected void onHitLivingEntity(LivingEntity target) {
        var owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            if (!CorruptionSkillUtil.isCorruptionAlly(target)) {
                boolean hurtSuccessful = target.hurt(target.damageSources().mobAttack(livingOwner), 6.0F);
                if (hurtSuccessful && target.isAlive()) {
                    target.addEffect(new MobEffectInstance(CorruptionEffects.CORRUPTION.get(), 100, 1));
                }
            }
        }
    }


}