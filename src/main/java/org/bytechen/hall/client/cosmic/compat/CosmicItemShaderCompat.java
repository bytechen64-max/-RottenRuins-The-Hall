package org.bytechen.hall.client.cosmic.compat;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.fml.ModList;

/**
 * Shader pack compatibility detection for the cosmic shader system,
 * matching {@code mystery_buding.live.client.compat.oculus.ItemShaderModCompat} exactly.
 *
 * <p>Uses reflection to call the Iris API at runtime, avoiding compile-time
 * dependencies on Oculus/Iris.</p>
 */
public final class CosmicItemShaderCompat {

    private static final boolean OCULUS_LOADED    = ModList.get().isLoaded("oculus");
    private static final boolean EMBEDDIUM_LOADED = ModList.get().isLoaded("embeddium");

    /**
     * Returns {@code true} when an Oculus shader pack is currently active.
     * Reflectively calls {@code IrisApi.getInstance().isShaderPackInUse()}.
     */
    public static boolean isOculusShaderPackActive() {
        if (!OCULUS_LOADED) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object active = apiClass.getMethod("isShaderPackInUse").invoke(api);
            return Boolean.TRUE.equals(active);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isOculusEmbeddiumActive() {
        return OCULUS_LOADED && EMBEDDIUM_LOADED;
    }

    /**
     * Whether cosmic rendering should be deferred to after world render.
     * <p>
     * GUI contexts are not affected by shader pack pipelines and render
     * immediately.  All other contexts (world, first/third-person, ground,
     * item frames) must be deferred when a shader pack is active.
     */
    public static boolean shouldDeferItemShaderLayer(ItemDisplayContext context) {
        if (!isOculusShaderPackActive()) {
            return false;
        }
        return context != ItemDisplayContext.GUI;
    }

    private CosmicItemShaderCompat() {}
}
