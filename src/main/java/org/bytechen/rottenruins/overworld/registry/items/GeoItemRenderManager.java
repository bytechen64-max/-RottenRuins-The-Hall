package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.bytechen.rottenruins.api.IAutoRenderableItem;
import org.bytechen.rottenruins.client.entity.model.GeoItemModel;
import org.bytechen.rottenruins.client.entity.render.GeoItemRenderer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Manages GeoItemRenderer instances for {@link IAutoRenderableItem} items: suppliers are registered during mod construction, renderers are created lazily on the first ISTER lookup in {@code Item.initializeClient()}. */
public class GeoItemRenderManager {

    private static final Map<String, Supplier<? extends Item>> PENDING = new LinkedHashMap<>();

    private static final Map<ResourceLocation, GeoItemRenderer<?>> RENDERERS = new ConcurrentHashMap<>();

    /** Register an item for lazy GeoItemRenderer creation. */
    public static <T extends Item & IAutoRenderableItem> void register(
            String registryName, Supplier<T> itemSupplier) {
        PENDING.put(registryName, itemSupplier);
    }

    /** Get or lazily create the renderer; {@code null} when the item is not an IAutoRenderableItem. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static GeoItemRenderer<?> getOrCreateRenderer(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return null;

        GeoItemRenderer<?> existing = RENDERERS.get(key);
        if (existing != null) return existing;

        if (!(item instanceof IAutoRenderableItem geoItem)) return null;

        GeoItemModel model = new GeoItemModel<>();
        GeoItemRenderer renderer = new GeoItemRenderer(model);

        if (geoItem.hasGlowLayer() && geoItem.glowTexture() != null) {
            renderer.addRenderLayer(new ItemGlowLayer(renderer, geoItem.glowTexture()));
        }

        RENDERERS.put(key, renderer);
        return renderer;
    }

    /** Get an existing renderer; {@code null} if not created yet. */
    @SuppressWarnings("rawtypes")
    public static GeoItemRenderer<?> getRenderer(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? RENDERERS.get(key) : null;
    }

    public static boolean hasRenderer(Item item) {
        return getRenderer(item) != null;
    }
}
