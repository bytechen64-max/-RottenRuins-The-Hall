package org.bytechen.hall.overworld.registry.entities.base;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class HallProjectileManager {
    private static final List<EntityType<? extends Entity>> PROJECTILE_TYPES = new ArrayList<>();

    public static <T extends Entity> EntityType<T> registerProjectile(EntityType<T> type) {
        PROJECTILE_TYPES.add(type);
        return type;
    }

    @SuppressWarnings("unchecked")
    public static List<EntityType<? extends Entity>> consumeProjectileTypes() {
        List<EntityType<? extends Entity>> result = new ArrayList<>(PROJECTILE_TYPES);
        PROJECTILE_TYPES.clear();
        return result;
    }
}
