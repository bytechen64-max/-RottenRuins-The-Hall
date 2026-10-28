package org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

public class BaseInfectedEntity extends AbstractRottenEntity {
    @Override
    protected boolean getAttack(LivingEntity target) {
        return false;
    }

    public BaseInfectedEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

}
