package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

import java.util.function.Consumer;

/** 腐化系 · 联结种（站桩塔类）薄层基类。 */
public abstract class BaseLinkedEntity extends BaseCorruptionEntity {

    protected BaseLinkedEntity(EntityType<? extends BaseLinkedEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }
}
