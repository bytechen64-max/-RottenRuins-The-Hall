package org.bytechen.hall.api;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.client.entity.IGeoResources;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Interface for items that automatically register their Geo model and renderer.
 * <p>
 * Mirroring the entity auto-registration pattern ({@code SplendidingEntityManager}),
 * items implementing this interface are collected at registration time and their
 * {@link org.bytechen.hall.client.entity.render.GeoItemRenderer} instances are
 * created automatically during client setup.
 * </p>
 *
 * <h3>Glow Layer</h3>
 * Override {@link #glowTexture()} and {@link #hasGlowLayer()} to enable
 * an emissive glow/emission render pass on top of the base model.
 */
public interface IAutoRenderableItem extends IGeoResources, GeoItem {

    /**
     * Returns the texture ResourceLocation for the emissive glow layer,
     * or {@code null} if no glow layer is needed.
     * <p>
     * The glow layer is rendered with {@link net.minecraft.client.renderer.RenderType#eyes}
     * (full-bright, additive blending).
     * </p>
     *
     * @return glow texture path, or null
     */
    default ResourceLocation glowTexture() {
        return null;
    }

    /**
     * Whether this item has an active glow (emissive) layer.
     * <p>
     * Default implementation returns {@code glowTexture() != null}.
     * Override to toggle dynamically based on item state.
     * </p>
     */
    default boolean hasGlowLayer() {
        return glowTexture() != null;
    }
}
