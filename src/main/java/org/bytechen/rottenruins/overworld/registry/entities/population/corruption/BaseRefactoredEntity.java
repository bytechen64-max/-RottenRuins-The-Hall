package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

import java.util.function.Consumer;

/** 腐化系 · 重构种薄层基类。 */
public abstract class BaseRefactoredEntity extends BaseCorruptionEntity {

    protected BaseRefactoredEntity(EntityType<? extends BaseRefactoredEntity> entityType, Level level,
                                   Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }
}
