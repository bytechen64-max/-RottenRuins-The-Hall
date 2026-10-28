package org.bytechen.rottenruins.overworld.registry.entities.population.ecological;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

public class BaseEcologicalEntity extends AbstractRottenEntity {
    @Override
    protected boolean getAttack(LivingEntity target) {
        return false;
    }

    public BaseEcologicalEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

}
