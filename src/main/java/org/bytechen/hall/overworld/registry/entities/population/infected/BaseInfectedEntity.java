package org.bytechen.hall.overworld.registry.entities.population.infected;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;

public class BaseInfectedEntity extends AbstractHallEntity {
    @Override
    protected boolean getAttack(LivingEntity target) {
        return false;
    }

    public BaseInfectedEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

}
