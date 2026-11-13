package org.bytechen.rottenruins.client.cosmic;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.CosmicStyle;
import org.bytechen.rottenruins.api.ICosmicLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * Central registry for cosmic starfield layer rendering.
 * Resolution order: an Item implementing {@link ICosmicLayer} wins, then manual
 * registrations in insertion order (first match wins).
 */
public final class CosmicLayerRegistry {

    private static final List<Entry> REGISTRY = new ArrayList<>();

    private CosmicLayerRegistry() {}

    public static void register(Predicate<ItemStack> predicate, ICosmicLayer config) {
        REGISTRY.add(new Entry(predicate, config));
    }

    public static void registerForItem(Item item, ICosmicLayer config) {
        register(stack -> stack.getItem() == item, config);
    }

    public static void enableWithMask(Item item, ResourceLocation mask) {
        registerForItem(item, simpleConfig(mask));
    }

    public static void enableWithConfig(ICosmicLayer config, Item... items) {
        Set<Item> set = new HashSet<>(Arrays.asList(items));
        register(stack -> set.contains(stack.getItem()), config);
    }

    @Nullable
    public static ICosmicLayer resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        if (stack.getItem() instanceof ICosmicLayer cl) return cl;

        for (Entry e : REGISTRY) {
            if (e.predicate.test(stack)) return e.config;
        }
        return null;
    }

    public static boolean hasConfig(ItemStack stack) {
        return resolve(stack) != null;
    }

    public static ICosmicLayer simpleConfig(ResourceLocation mask) {
        return new SimpleConfig(mask, 1.0f, true, true, true, CosmicStyle.DEEP_SPACE);
    }

    public static ICosmicLayer styledConfig(ResourceLocation mask, CosmicStyle style) {
        return new SimpleConfig(mask, 1.0f, true, true, true, style);
    }

    public static ICosmicLayer config(ResourceLocation mask, float opacity, CosmicStyle style, boolean showWhenHeld) {
        return new SimpleConfig(mask, opacity, true, showWhenHeld, true, style);
    }

    public static ICosmicLayer fullConfig(ResourceLocation mask, float opacity,
                                          boolean showInGui, boolean showWhenHeld,
                                          boolean showInWorld, CosmicStyle style) {
        return new SimpleConfig(mask, opacity, showInGui, showWhenHeld, showInWorld, style);
    }

    public static ResourceLocation mask(String path) {
        return ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path);
    }

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
