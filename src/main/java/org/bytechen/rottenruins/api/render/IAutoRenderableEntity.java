package org.bytechen.rottenruins.api.render;

import software.bernie.geckolib.animatable.GeoEntity;

public interface IAutoRenderableEntity extends IGeoResources, GeoEntity {
    /** Whether the non-living auto renderer should blend partial texture alpha. */
    default boolean translucentTexture() { return false; }
}
