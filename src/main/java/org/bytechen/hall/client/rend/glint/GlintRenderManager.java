package org.bytechen.hall.client.rend.glint;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Central registry for item glint effects.
 * Register glint profiles against item predicates, then the rendering
 * system automatically applies them after each item render.
 *
 * <pre>{@code
 * // Register glint for a specific item
 * GlintRenderManager.registerForItem(MyItems.EXAMPLE_ITEM, GlintEffectProfile.rainbow());
 *
 * // Register glint for items matching a predicate
 * GlintRenderManager.register(
 *     stack -> stack.getItem() instanceof MyCustomItem,
 *     GlintEffectProfile.singleColor(1.0f, 0.5f, 0.0f)
 * );
 * }</pre>
 */
public class GlintRenderManager {

    private static final Map<Predicate<ItemStack>, GlintEffectProfile> REGISTRY = new LinkedHashMap<>();

    /**
     * Register a glint effect for items matching the given predicate.
     * Earlier registrations take priority over later ones.
     */
    public static void register(Predicate<ItemStack> predicate, GlintEffectProfile profile) {
        REGISTRY.put(predicate, profile);
    }

    /**
     * Convenience method to register a glint effect for a specific item.
     */
    public static void registerForItem(Item item, GlintEffectProfile profile) {
        register(stack -> stack.getItem() == item, profile);
    }

    /**
     * Get the first matching glint profile for the given stack, or null.
     */
    public static GlintEffectProfile getProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Map.Entry<Predicate<ItemStack>, GlintEffectProfile> entry : REGISTRY.entrySet()) {
            if (entry.getKey().test(stack)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Check if any glint profile matches the given stack.
     */
    public static boolean hasProfile(ItemStack stack) {
        return getProfile(stack) != null;
    }
}
