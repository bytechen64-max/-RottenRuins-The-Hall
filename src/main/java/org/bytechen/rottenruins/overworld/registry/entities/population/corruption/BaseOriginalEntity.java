package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

import java.util.function.Consumer;

/** 腐化系 · 原虫薄层基类。 */
public abstract class BaseOriginalEntity extends BaseCorruptionEntity {

    protected BaseOriginalEntity(EntityType<? extends BaseOriginalEntity> entityType, Level level,
                                 Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }
}
