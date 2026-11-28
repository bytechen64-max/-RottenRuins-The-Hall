package org.bytechen.rottenruins.client.gui;

/** Outer quick-select wheel, using the same eight directions as the spell loadout. */
public final class WandHudPicker {
    public static final float RADIUS = 58, NODE_RADIUS = 11, MARGIN = 74;
    public record Point(float x, float y) { }
    private WandHudPicker() { }

    public static Point position(int index, float expansion) {
        double angle = -Math.PI / 2 + index * Math.PI / 4;
        float radius = 40 + 18 * Math.max(0, expansion);
        return new Point((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius);
    }

    public static int hit(double x, double y, int[] indices, float expansion) {
        if (expansion < 0.6F) return -1;
        for (int index : indices) {
            Point p = position(index, expansion);
            if (Math.hypot(x - p.x, y - p.y) <= NODE_RADIUS + 2) return index;
        }
        return -1;
    }

    public static float safeCenter(float center, float extent, float scale, float expansion) {
        float margin = Math.min(extent / 2, MARGIN * scale);
        float safe = Math.max(margin, Math.min(extent - margin, center));
        return center + (safe - center) * Math.max(0, Math.min(1, expansion));
    }
}
