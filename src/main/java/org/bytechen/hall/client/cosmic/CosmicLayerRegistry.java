package org.bytechen.hall.client.cosmic;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.CosmicStyle;
import org.bytechen.hall.api.ICosmicLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * Central registry for cosmic starfield layer rendering, matching the
 * pattern of {@code GlintRenderManager}.
 *
 * <h3>Two ways to enable cosmic rendering</h3>
 * <ol>
 *   <li><b>Implement {@link ICosmicLayer}</b> on your Item class —
 *       detected automatically at render time, no registration needed.</li>
 *   <li><b>Manual registration</b> — register a mask texture against an
 *       item predicate.  Useful for vanilla items or items whose source
 *       you can't modify.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Register cosmic for a specific item
 * CosmicLayerRegistry.registerForItem(MyItems.SPECIAL_SWORD,
 *         CosmicLayerRegistry.simpleConfig(mask("modid:item/sword_mask")));
 *
 * // Register with full configuration
 * CosmicLayerRegistry.registerForItem(Items.DIAMOND_SWORD,
 *         CosmicLayerRegistry.config(mask("modid:item/diamond_mask"), 0.7f, false));
 *
 * // Match by predicate
 * CosmicLayerRegistry.register(
 *         stack -> stack.getItem() instanceof MyItemType,
 *         CosmicLayerRegistry.simpleConfig(mask("modid:item/generic_mask")));
 *
 * // Convenience: register with just a mask ResourceLocation
 * CosmicLayerRegistry.enableWithMask(MyItems.ARCANE_STAFF,
 *         ResourceLocation.fromNamespaceAndPath("modid", "item/staff_mask"));
 * }</pre>
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>Check if the Item implements {@link ICosmicLayer} — highest priority</li>
 *   <li>Check manual registrations in insertion order — first match wins</li>
 * </ol>
 */
public final class CosmicLayerRegistry {

    private static final List<Entry> REGISTRY = new ArrayList<>();

    private CosmicLayerRegistry() {}

    /**
     * Register a cosmic layer config for items matching the given predicate.
     * Earlier registrations take priority over later ones.
     */
    public static void register(Predicate<ItemStack> predicate, ICosmicLayer config) {
        REGISTRY.add(new Entry(predicate, config));
    }

    /**
     * Convenience method to register for a specific item.
     */
    public static void registerForItem(Item item, ICosmicLayer config) {
        register(stack -> stack.getItem() == item, config);
    }

    /**
     * Convenience: register an item with just a mask ResourceLocation.
     * Uses default opacity (1.0) and shows in all contexts.
     */
    public static void enableWithMask(Item item, ResourceLocation mask) {
        registerForItem(item, simpleConfig(mask));
    }

    /**
     * Convenience: register multiple items with the same config.
     */
    public static void enableWithConfig(ICosmicLayer config, Item... items) {
        Set<Item> set = new HashSet<>(Arrays.asList(items));
        register(stack -> set.contains(stack.getItem()), config);
    }

    /**
     * Resolve the effective {@link ICosmicLayer} config for an item stack.
     * Checks the Item interface first, then falls back to manual registrations.
     *
     * @return a non-null config if cosmic rendering should be applied, or null
     */
    @Nullable
    public static ICosmicLayer resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        // Priority 1: Item implements the interface directly
        if (stack.getItem() instanceof ICosmicLayer cl) return cl;

        // Priority 2: Manual registration (first match wins)
        for (Entry e : REGISTRY) {
            if (e.predicate.test(stack)) return e.config;
        }
        return null;
    }

    /**
     * Check if the stack has any cosmic layer configuration.
     */
    public static boolean hasConfig(ItemStack stack) {
        return resolve(stack) != null;
    }

    // ── factory helpers for creating ICosmicLayer configs ────────────

    /**
     * Create a simple config with just a mask texture.
     * Uses default values (opacity 1.0, show everywhere, DEEP_SPACE style).
     */
    public static ICosmicLayer simpleConfig(ResourceLocation mask) {
        return new SimpleConfig(mask, 1.0f, true, true, true, CosmicStyle.DEEP_SPACE);
    }

    /**
     * Create a config with mask and style.  Uses default opacity 1.0.
     */
    public static ICosmicLayer styledConfig(ResourceLocation mask, CosmicStyle style) {
        return new SimpleConfig(mask, 1.0f, true, true, true, style);
    }

    /**
     * Create a config with mask, opacity, style, and whether to show when held.
     */
    public static ICosmicLayer config(ResourceLocation mask, float opacity, CosmicStyle style, boolean showWhenHeld) {
        return new SimpleConfig(mask, opacity, true, showWhenHeld, true, style);
    }

    /**
     * Create a full config with all options specified.
     */
    public static ICosmicLayer fullConfig(ResourceLocation mask, float opacity,
                                          boolean showInGui, boolean showWhenHeld,
                                          boolean showInWorld, CosmicStyle style) {
        return new SimpleConfig(mask, opacity, showInGui, showWhenHeld, showInWorld, style);
    }

    /** Simple helper to make a ResourceLocation without repeating the modid. */
    public static ResourceLocation mask(String path) {
        return ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path);
    }

    // ── internal ────────────────────────────────────────────────────
    private record Entry(Predicate<ItemStack> predicate, ICosmicLayer config) {}

    private record SimpleConfig(ResourceLocation mask, float opacity,
                                boolean showInGui, boolean showWhenHeld,
                                boolean showInWorld, CosmicStyle style) implements ICosmicLayer {
        @Override public ResourceLocation cosmicMask() { return mask; }
        @Override public float cosmicOpacity() { return opacity; }
        @Override public boolean cosmicShowInGui() { return showInGui; }
        @Override public boolean cosmicShowWhenHeld() { return showWhenHeld; }
        @Override public boolean cosmicShowInWorld() { return showInWorld; }
        @Override public CosmicStyle cosmicStyle() { return style; }
    }
}
