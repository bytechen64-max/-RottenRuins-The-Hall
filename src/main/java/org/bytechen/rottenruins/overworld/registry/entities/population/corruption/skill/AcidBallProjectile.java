package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;

import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.rottenruins.client.entity.IBillboardRenderable;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractCorruptionProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class AcidBallProjectile extends AbstractCorruptionProjectile implements IBillboardRenderable {

    public AcidBallProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public AcidBallProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, double x, double y, double z, Level level) {
        super(entityType, x, y, z, level);
        this.setNoGravity(true);
    }

    public AcidBallProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, LivingEntity shooter, Level level) {
        super(entityType, shooter, level);
        this.setNoGravity(true);
    }

    public AcidBallProjectile(EntityType<? extends AbstractCorruptionProjectile> entityType, Level level, Consumer<AcidBallProjectile> consumer) {
        super(entityType, level);
        this.setNoGravity(true);
        consumer.accept(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                this.level().addParticle(ParticleTypes.ITEM_SLIME,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.3,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.3,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                        0, 0, 0);
            }
        }
    }

    @Override
    protected void onHitLivingEntity(LivingEntity target) {
        var owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner &&!(CorruptionSkillUtil.isCorruptionAlly(target))) {
            target.hurt(target.damageSources().mobAttack(livingOwner), 5.0F);
            CorruptionSkillUtil.stackEffect(target, CorruptionEffects.FEAR.get(), 40, 0);
        }
    }
}
