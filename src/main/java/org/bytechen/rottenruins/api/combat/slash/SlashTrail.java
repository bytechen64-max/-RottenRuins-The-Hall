package org.bytechen.rottenruins.api.combat.slash;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Common-side description only. Client renderer keys may be registered independently. */
public record SlashTrail(ResourceLocation renderer, ResourceLocation texture, int color, float width,
                         float radius, int lifetimeTicks) {
    public static final ResourceLocation TEXTURE = id("texture");
    public static final ResourceLocation SHADER = id("shader");
    public static final ResourceLocation NONE = id("none");
    public static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath("rotten_ruins", "textures/effect/slash_crescent.png");
    public static final SlashTrail DISABLED = new SlashTrail(NONE, DEFAULT_TEXTURE, -1, 0.25F, 1.5F, 6);
    public SlashTrail {
        Objects.requireNonNull(renderer); Objects.requireNonNull(texture);
        if (!Float.isFinite(width) || width <= 0 || width > 8 || !Float.isFinite(radius)
                || radius <= 0 || radius > 32 || lifetimeTicks < 1 || lifetimeTicks > 200)
            throw new IllegalArgumentException("Invalid slash trail dimensions/lifetime");
    }
    public static ResourceLocation id(String key) { return ResourceLocation.fromNamespaceAndPath("rotten_ruins", key); }
}
