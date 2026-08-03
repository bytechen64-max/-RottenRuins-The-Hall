package org.bytechen.hall.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Items implementing this interface gain automatic cosmic starfield
 * rendering on their surface without needing a custom model JSON.
 *
 * <h3>How it works</h3>
 * {@code MixinItemRendererCosmic} checks each rendered item — if the
 * item implements {@code ICosmicLayer} or is registered in
 * {@link org.bytechen.hall.client.cosmic.CosmicLayerRegistry},
 * a cosmic shader overlay is rendered on top of the standard item
 * texture after the base model finishes drawing.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * public class MySword extends SwordItem implements ICosmicLayer {
 *     public MySword() {
 *         super(Tiers.NETHERITE, 3, -2.4f, new Properties());
 *     }
 *
 *     // The RED channel of this texture controls where stars appear.
 *     // White = stars visible, Black = stars hidden.
 *     public ResourceLocation cosmicMask() {
 *         return ResourceLocation.fromNamespaceAndPath("splendiding", "item/my_sword_mask");
 *     }
 *
 *     // Optional: custom opacity
 *     public float cosmicOpacity() { return 0.8f; }
 * }
 * }</pre>
 *
 * <h3>Mask texture</h3>
 * The mask texture's <b>red channel</b> determines where the cosmic
 * starfield is visible.  Typically the mask is a copy of the item's
 * base texture with the alpha channel converted to grayscale red.
 * White (R=255) = full star visibility, Black (R=0) = hidden.
 *
 * <h3>Manual registration (no interface)</h3>
 * Items that can't implement the interface directly (e.g. vanilla items)
 * can be registered via
 * {@link org.bytechen.hall.client.cosmic.CosmicLayerRegistry}:
 * <pre>{@code
 * CosmicLayerRegistry.registerForItem(Items.DIAMOND_SWORD, mask("splendiding:item/diamond_sword_mask"));
 * }</pre>
 *
 * @see org.bytechen.hall.client.cosmic.CosmicLayerRegistry
 */
public interface ICosmicLayer {

    /**
     * The mask texture resource location.
     * The red channel of this texture controls where the cosmic
     * starfield is visible on the item surface.
     *
     * @return a non-null ResourceLocation, e.g.
     *         {@code ResourceLocation.fromNamespaceAndPath("splendiding", "item/my_mask")}
     */
    ResourceLocation cosmicMask();

    /**
     * Cosmic layer opacity (0.0–1.0).  Default {@code 1.0}.
     * Lower values make the starfield more transparent.
     */
    default float cosmicOpacity() {
        return 1.0f;
    }

    /**
     * Show the cosmic starfield when rendered in GUI / creative
     * inventory.  Default {@code true}.
     */
    default boolean cosmicShowInGui() {
        return true;
    }

    /**
     * Show the cosmic starfield when the item is held in hand
     * (first or third person).  Default {@code true}.
     */
    default boolean cosmicShowWhenHeld() {
        return true;
    }

    /**
     * Show the cosmic starfield when the item is on the ground
     * or in an item frame.  Default {@code true}.
     */
    default boolean cosmicShowInWorld() {
        return true;
    }

    /**
     * The visual style preset for this item.  Controls background
     * colour, nebula pattern, and star colour scheme.
     * Default {@link CosmicStyle#DEEP_SPACE}.
     */
    default CosmicStyle cosmicStyle() {
        return CosmicStyle.DEEP_SPACE;
    }

    /**
     * Per-context visibility check.  Called every frame; override
     * for dynamic show/hide logic.  Default delegates to the
     * three {@code cosmicShowIn*} methods above.
     */
    default boolean cosmicShouldRender(ItemDisplayContext ctx) {
        return switch (ctx) {
            case GUI, FIXED -> cosmicShowInGui();
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> cosmicShowWhenHeld();
            case GROUND, NONE, HEAD -> cosmicShowInWorld();
        };
    }
}
