package org.bytechen.rottenruins.content.combat.slash.copper;

import org.bytechen.rottenruins.api.combat.slash.*;
import net.minecraft.resources.ResourceLocation;

/** Texture-based warm copper trail; separate tuning from all other weapons. */
public final class CopperSlash {
    public static final SlashSpec SPEC = SlashSpec.builder().range(3.1, 0, 116, 0.9)
            .timing(12, 10, 0.47F).damage(1, 0.3F, 12, 1)
            .trail(new SlashTrail(SlashTrail.TEXTURE, ResourceLocation.fromNamespaceAndPath("rotten_ruins", "textures/effect/true_copper_slash_crescent.png"), 0xFFFFFFFF, 0.48F, 2.0F, 7)).build();
    private CopperSlash() {}
}
