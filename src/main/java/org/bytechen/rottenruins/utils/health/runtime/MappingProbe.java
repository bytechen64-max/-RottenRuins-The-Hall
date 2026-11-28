package org.bytechen.rottenruins.utils.health.runtime;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

public final class MappingProbe {

    private MappingProbe() {}

    @SuppressWarnings("unused")
    static float p$getHealth(LivingEntity entity) {
        return entity.getHealth();
    }

    @SuppressWarnings("unused")
    static void p$setHealth(LivingEntity entity, float value) {
        entity.setHealth(value);
    }

    @SuppressWarnings("unused")
    static float p$getMaxHealth(LivingEntity entity) {
        return entity.getMaxHealth();
    }

    @SuppressWarnings("unused")
    static Object p$entityDataGet(SynchedEntityData data, EntityDataAccessor<?> accessor) {
        return data.get(accessor);
    }

    @SuppressWarnings({"unused", "unchecked", "rawtypes"})
    static void p$entityDataSet(SynchedEntityData data, EntityDataAccessor accessor, Object value) {
        data.set(accessor, value);
    }
}
