package org.bytechen.hall.client.entity.render;

import net.minecraft.world.item.Item;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * Project-specific Geo item renderer extending GeckoLib's {@link software.bernie.geckolib.renderer.GeoItemRenderer}.
 * <p>
 * This subclass exists as an extension point for custom item rendering behavior
 * while remaining compatible with the auto-registration system.
 * Glow/emissive layers are added via the standard {@link #addRenderLayer} mechanism.
 * </p>
 *
 * @param <T> the item type, extending Item & GeoAnimatable
 */
public class GeoItemRenderer<T extends Item & GeoAnimatable> extends software.bernie.geckolib.renderer.GeoItemRenderer<T> {

    public GeoItemRenderer(GeoModel<T> model) {
        super(model);
    }
}
