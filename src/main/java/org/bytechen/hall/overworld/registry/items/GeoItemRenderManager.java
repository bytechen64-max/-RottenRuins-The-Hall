package org.bytechen.hall.overworld.registry.items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.bytechen.hall.api.IAutoRenderableItem;
import org.bytechen.hall.client.entity.model.GeoItemModel;
import org.bytechen.hall.client.entity.render.GeoItemRenderer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages GeoItemRenderer instances for {@link IAutoRenderableItem} items.
 * <p>
 * Items are registered during mod construction. Renderers are created lazily
 * on first access (triggered by {@code Item.initializeClient()} ISTER lookup).
 * </p>
 *
 * <h3>Flow</h3>
 * <pre>
 * RegisterItem.registerGeoItem("my_item", MyItem::new)
 *   → stores supplier in GeoItemRenderManager
 *
 * BaseGlowingGeoItem.initializeClient()
 *   → provides IClientItemExtensions with lazy renderer lookup
 *
 * At render time:
 *   Vanilla applies display transforms from item model JSON
 *   → calls BakedModel.getCustomRenderer().renderByItem()
 *   → GeoItemRenderer renders the geo model
 * </pre>
 */
public class GeoItemRenderManager {

    /** Pending suppliers, keyed by registry name, consumed on first getOrCreateRenderer call. */
    private static final Map<String, Supplier<? extends Item>> PENDING = new LinkedHashMap<>();

    /** Created GeoItemRenderers, keyed by registry ResourceLocation. */
    private static final Map<ResourceLocation, GeoItemRenderer<?>> RENDERERS = new ConcurrentHashMap<>();

    /**
     * Register an item for lazy GeoItemRenderer creation.
     *
     * @param registryName the item's registry name (e.g. "acid_anomaly_extract")
     * @param itemSupplier a supplier that returns the registered Item instance
     */
    public static <T extends Item & IAutoRenderableItem> void register(
            String registryName, Supplier<T> itemSupplier) {
        PENDING.put(registryName, itemSupplier);
    }

    /**
     * Get or lazily create the {@link GeoItemRenderer} for an item.
     * <p>
     * Called from {@code initializeClient()} when the ISTER is first needed.
     * The item's registry key must be resolvable via {@link BuiltInRegistries#ITEM}.
     * </p>
     *
     * @param item the item instance
     * @return the renderer, or {@code null} if the item isn't registered or isn't an IAutoRenderableItem
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static GeoItemRenderer<?> getOrCreateRenderer(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return null;

        // Fast path: already created
        GeoItemRenderer<?> existing = RENDERERS.get(key);
        if (existing != null) return existing;

        // Slow path: create from pending or directly from the item
        if (!(item instanceof IAutoRenderableItem geoItem)) return null;

        GeoItemModel model = new GeoItemModel<>();
        GeoItemRenderer renderer = new GeoItemRenderer(model);

        if (geoItem.hasGlowLayer() && geoItem.glowTexture() != null) {
            renderer.addRenderLayer(new ItemGlowLayer(renderer, geoItem.glowTexture()));
        }

        RENDERERS.put(key, renderer);
        return renderer;
    }

    /**
     * Get an existing {@link GeoItemRenderer} for an item.
     *
     * @return the renderer, or {@code null} if not yet created
     */
    @SuppressWarnings("rawtypes")
    public static GeoItemRenderer<?> getRenderer(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? RENDERERS.get(key) : null;
    }

    /**
     * Whether this item has a GeoItemRenderer (already created).
     */
    public static boolean hasRenderer(Item item) {
        return getRenderer(item) != null;
    }
}
