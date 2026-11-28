package org.bytechen.rottenruins.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Items implementing this interface get an automatic cosmic starfield overlay; the mask's red channel sets visibility.
 */
public interface ICosmicLayer {

    ResourceLocation cosmicMask();

    default float cosmicOpacity() {
        return 1.0f;
    }

    default boolean cosmicShowInGui() {
        return true;
    }

    default boolean cosmicShowWhenHeld() {
        return true;
    }

    default boolean cosmicShowInWorld() {
        return true;
    }

    default CosmicStyle cosmicStyle() {
        return CosmicStyle.DEEP_SPACE;
    }

    /** Per-context visibility; called every frame, defaults to the cosmicShowIn* flags. */
    default boolean cosmicShouldRender(ItemDisplayContext ctx) {
        return switch (ctx) {
            case GUI, FIXED -> cosmicShowInGui();
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> cosmicShowWhenHeld();
            case GROUND, NONE, HEAD -> cosmicShowInWorld();
        };
    }
}
