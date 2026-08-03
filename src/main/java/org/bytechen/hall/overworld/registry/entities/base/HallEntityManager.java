package org.bytechen.hall.overworld.registry.entities.base;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class HallEntityManager {
    private static final Map<EntityType<?>, Set<LivingEntity>> TRACKED_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<EntityType<? extends LivingEntity>, Supplier<AttributeSupplier.Builder>> ATTRIBUTE_BLUEPRINTS = new HashMap<>();
    private static final List<EntityType<? extends LivingEntity>> RENDER_TYPES = new ArrayList<>();

    public static <T extends LivingEntity> EntityType<T> registerAll(
            EntityType<T> type, Supplier<AttributeSupplier.Builder> attrSupplier) {
        ATTRIBUTE_BLUEPRINTS.put(type, attrSupplier);
        RENDER_TYPES.add(type);
        return type;
    }

    public static void createAttributes(EntityAttributeCreationEvent event) {
        ATTRIBUTE_BLUEPRINTS.forEach((type, supplier) -> event.put(type, supplier.get().build()));
        ATTRIBUTE_BLUEPRINTS.clear();
    }

    @SuppressWarnings("unchecked")
    public static List<EntityType<? extends LivingEntity>> consumeRenderTypes() {
        List<EntityType<? extends LivingEntity>> result = new ArrayList<>(RENDER_TYPES);
        RENDER_TYPES.clear();
        return result;
    }

    public static void register(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        TRACKED_ENTITIES.computeIfAbsent(entity.getType(),
                k -> Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()))).add(entity);
    }

    public static void unregister(LivingEntity entity) {
        Set<LivingEntity> instances = TRACKED_ENTITIES.get(entity.getType());
        if (instances != null) instances.remove(entity);
    }

    public static List<LivingEntity> getAllTrackedEntities() {
        return TRACKED_ENTITIES.values().stream().flatMap(Collection::stream).filter(LivingEntity::isAlive).collect(Collectors.toList());
    }

    public static void clearAll() {
        TRACKED_ENTITIES.values().forEach(Set::clear);
        TRACKED_ENTITIES.clear();
    }
}
