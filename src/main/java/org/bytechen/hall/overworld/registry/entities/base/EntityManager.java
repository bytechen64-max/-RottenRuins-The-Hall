package org.bytechen.hall.overworld.registry.entities.base;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class EntityManager {
    private static final List<EntityType<? extends Entity>> SKILL_ENTITY_TYPES = new ArrayList<>();

    public static <T extends Entity> EntityType<T> registerSkill(EntityType<T> type) {
        SKILL_ENTITY_TYPES.add(type);
        return type;
    }

    @SuppressWarnings("unchecked")
    public static List<EntityType<? extends Entity>> consumeSkillEntityTypes() {
        List<EntityType<? extends Entity>> result = new ArrayList<>(SKILL_ENTITY_TYPES);
        SKILL_ENTITY_TYPES.clear();
        return result;
    }
}
