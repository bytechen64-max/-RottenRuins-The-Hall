package org.bytechen.rottenruins.client.gui;

/** Shared unscaled ring geometry for drawing, animated hit regions and previews. */
public final class WandHudGeometry {
    public enum Target { NONE, ICON, PLAYER, WAND }
    public static final int TICKS = 20;
    public record Ring(float inner, float outer) { }

    private WandHudGeometry() { }

    public static Target target(double dx, double dy) {
        return target(dx, dy, 0, 0);
    }

    public static Ring playerRing(float hover) {
        return new Ring(22.5F, 25 + 5 * Math.max(0, hover));
    }

    public static Ring wandRing(float playerHover, float wandHover) {
        // Push the whole outer ring by exactly the inner ring's expansion.
        float push = 5 * Math.max(0, playerHover);
        return new Ring(28 + push, 30.5F + push + 5 * Math.max(0, wandHover));
    }

    public static Target target(double dx, double dy, float playerHover, float wandHover) {
        double radius = Math.hypot(dx, dy);
        if (radius <= 20) return Target.ICON;
        Ring player = playerRing(playerHover), wand = wandRing(playerHover, wandHover);
        // Half a GUI pixel of tolerance also keeps thin rings easy to acquire.
        if (radius >= player.inner - 0.5 && radius <= player.outer + 0.5) return Target.PLAYER;
        if (radius >= wand.inner - 0.5 && radius <= wand.outer + 0.5) return Target.WAND;
        return Target.NONE;
    }

    /** The uncovered area grows from the right edge towards the left. */
    public static float cooldownWidth(int iconWidth, float remainingFraction) {
        return iconWidth * Math.max(0, Math.min(1, remainingFraction));
    }
}
