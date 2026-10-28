package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

import java.util.function.Consumer;

/** 腐化系 · 同化种（原 corruption host 族）薄层基类。 */
public abstract class BaseHostEntity extends BaseCorruptionEntity {

    protected BaseHostEntity(EntityType<? extends BaseHostEntity> entityType, Level level,
                             Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }
}
