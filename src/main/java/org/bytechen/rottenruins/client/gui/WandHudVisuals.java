package org.bytechen.rottenruins.client.gui;

/** Shared drawing recipe used by Minecraft and the GL preview, in HUD-local coordinates. */
public final class WandHudVisuals {
    public interface Canvas {
        void arc(float inner, float outer, float start, float span, int color);
        void disc(float x, float y, float radius, int color);
        void line(float x1, float y1, float x2, float y2, float halfWidth, int color);
    }
    private static final float TAU = (float) (Math.PI * 2), TOP = -(float) Math.PI / 2;
    private WandHudVisuals() { }

    public static int alpha(int color, float alpha) {
        return (color & 0xFFFFFF) | (Math.round(Math.max(0, Math.min(255, alpha))) << 24);
    }

    public static void draw(Canvas c, WandHudMotion m, int skill, int player, int wand,
                            float seconds, float pointerAngle, boolean dragging) {
        float breath = 0.5F + 0.5F * (float) Math.sin(seconds * 1.8);
        float icon = Math.max(0, m.iconHover.value);
        c.disc(0, 0, 20, 0x60070E19);
        c.disc(0, 0, 17.5F, 0xCF080F1D);
        c.arc(18.5F, 19, 0, TAU, alpha(skill, 62 + icon * 90));
        c.arc(17.4F, 17.8F, TOP, TAU, 0x303F6078);
        c.arc(19.4F, 20.2F, seconds * 0.12F, 0.55F, alpha(skill, 35 + breath * 25 + icon * 90));
        ring(c, WandHudGeometry.playerRing(m.playerHover.value), m.player, m.playerTrail,
                m.playerHover.value, m.playerFlash, player, seconds, pointerAngle);
        ring(c, WandHudGeometry.wandRing(m.playerHover.value, m.wandHover.value), m.wand, m.wandTrail,
                m.wandHover.value, m.wandFlash, wand, seconds + 2, pointerAngle);
        if (m.ready > 0) {
            float radius = 19 + (1 - m.ready) * 23;
            c.arc(radius, radius + 0.75F, 0, TAU, alpha(skill, m.ready * m.ready * 160));
            c.disc(0, 0, 18, alpha(skill, m.ready * 40));
            for (int i = 0; i < 6; i++) {
                float a = i * TAU / 6 + 0.3F;
                c.disc((float) Math.cos(a) * radius, (float) Math.sin(a) * radius,
                        0.65F * m.ready, alpha(skill, 190 * m.ready));
            }
        }
        if (m.switched > 0) c.arc(18, 19 + m.switched * 2, TOP,
                TAU * m.switched, alpha(skill, 155 * m.switched));
        if (m.resize > 0 || dragging) c.arc(41, 41.6F, 0, TAU,
                alpha(0xA3CBE2, dragging ? 65 : 100 * m.resize));
    }

    private static void ring(Canvas c, WandHudGeometry.Ring ring, float fraction, float trail,
                             float hover, float flash, int color, float time, float pointer) {
        hover = Math.max(0, hover);
        float inner = ring.inner(), outer = ring.outer(), mid = (inner + outer) / 2;
        c.arc(inner - 0.55F, outer + 0.55F, 0, TAU, 0x94060D17);
        c.arc(inner, outer, 0, TAU, 0x453B5065);
        if (trail - fraction > 0.002F) c.arc(inner, outer, TOP + fraction * TAU,
                (trail - fraction) * TAU, alpha(0xE8C99C, 150));
        if (fraction > 0.001F) {
            c.arc(inner - 1, outer + 1, TOP, fraction * TAU, alpha(color, 15 + hover * 18));
            c.arc(inner, outer, TOP, fraction * TAU, alpha(color, 205 + hover * 40));
            c.arc(outer - 0.55F, outer, TOP, fraction * TAU, alpha(0xEAF9FF, 90 + hover * 70));
            float a = TOP + fraction * TAU;
            float x = (float) Math.cos(a) * mid, y = (float) Math.sin(a) * mid;
            c.disc(x, y, 2.3F + hover, alpha(color, 28 + hover * 25));
            c.disc(x, y, 0.85F + hover * 0.25F, alpha(0xF2FCFF, 220));
            // A faint sheen travels only inside the occupied portion.
            float sweep = (time * 0.065F % 1) * fraction;
            c.arc(inner, outer, TOP + sweep * TAU, Math.min(0.12F, (fraction - sweep) * TAU),
                    alpha(0xFFFFFF, 20 + hover * 35));
        }
        for (int i = 0; i < WandHudGeometry.TICKS; i++) {
            float a = TOP + i * TAU / WandHudGeometry.TICKS;
            boolean major = i % 5 == 0;
            float from = outer + 1, to = from + (major ? 1.8F : 0.7F);
            int tint = i / (float) WandHudGeometry.TICKS <= fraction ? color : 0x597084;
            c.line((float) Math.cos(a) * from, (float) Math.sin(a) * from,
                    (float) Math.cos(a) * to, (float) Math.sin(a) * to,
                    major ? 0.33F : 0.24F, alpha(tint, (major ? 140 : 55) + hover * 65));
        }
        if (hover > 0.01F) {
            c.arc(outer + 3.3F, outer + 3.8F, pointer - 0.24F, 0.48F, alpha(color, hover * 175));
            c.arc(inner, outer, pointer - 0.14F, 0.28F, alpha(0xE5F6FF, hover * 38));
        }
        if (flash > 0.01F) c.arc(inner, outer, TOP, TAU, alpha(color, flash * 60));
    }
}
