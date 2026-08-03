package org.bytechen.hall.client.rend.twitch;

import net.minecraft.world.item.ItemDisplayContext;
import org.bytechen.hall.client.cosmic.BakedModelCosmic;

/**
 * Marker interface for items that should display the glitch twitch
 * visual effect during rendering.
 *
 * <p>The twitch effect periodically applies rapid position jitter,
 * non-uniform scale stretch, and rotation to the item model during
 * short bursts (~0.3s every ~3s), creating a "corrupted / glitchy"
 * aesthetic.
 *
 * <h3>Alternative: JSON model loader</h3>
 * Items that use the {@code "hall:cosmic"} model loader get twitch
 * automatically via {@link BakedModelCosmic}.  This interface is for
 * items that want the twitch effect without the cosmic starfield shader.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * public class MyGlitchItem extends SwordItem implements ITwitchItem {
 *     public MyGlitchItem() { super(Tiers.NETHERITE, 3, -2.4f, new Properties()); }
 * }
 * }</pre>
 *
 * @see ItemTwitchHelper#shouldTwitch
 */
public interface ITwitchItem {

    /**
     * Whether the twitch effect should render in this display context.
     * Default: enabled everywhere except GUI inventory.
     */
    default boolean twitchShouldRender(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.GUI;
    }
}
