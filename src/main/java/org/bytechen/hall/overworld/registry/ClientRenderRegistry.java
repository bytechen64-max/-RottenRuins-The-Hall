package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.client.entity.IGeoLayerProvider;
import org.bytechen.hall.client.entity.IGeoModelBehavior;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientRenderRegistry {

    private static final Map<EntityType<?>, IGeoModelBehavior> MODEL_BEHAVIORS = new HashMap<>();
    private static final Map<EntityType<?>, List<IGeoLayerProvider>> RENDER_LAYERS = new HashMap<>();

    public static void registerModelBehavior(EntityType<?> type, IGeoModelBehavior behavior) {
        MODEL_BEHAVIORS.put(type, behavior);
    }

    public static void registerRenderLayer(EntityType<?> type, IGeoLayerProvider layer) {
        RENDER_LAYERS.computeIfAbsent(type, k -> new ArrayList<>()).add(layer);
    }

    public static IGeoModelBehavior getModelBehavior(EntityType<?> type) {
        return MODEL_BEHAVIORS.get(type);
    }

    public static List<IGeoLayerProvider> getLayerProviders(EntityType<?> type) {
        return RENDER_LAYERS.getOrDefault(type, List.of());
    }
}
