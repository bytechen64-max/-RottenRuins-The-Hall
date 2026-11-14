import org.bytechen.rottenruins.client.gui.WandHudPicker;
import org.bytechen.rottenruins.client.gui.WandHudMotion;
import org.bytechen.rottenruins.client.gui.WandHudVisuals;

/** Quick-selection expansion with real spell assets and edge-safe geometry. */
final class SceneWandPicker {
    public static void render(PreviewCanvas c, float time) {
        c.clear(0xFF0D1520);
        c.shader("sdf_ui");
        c.text("PREPARED SPELL / QUICK SELECT", 25, 25, 0xFFCCDCE7, false);
        c.text("RMB center to expand / LMB outer spell to prepare", 25, 45, 0xFF7B98AD, false);
        draw(c, 155, 180, 1.6F, 0.45F, -1);
        draw(c, 470, 180, 1.6F, 1, 2);
        c.textCentered("EXPANDING", 155, 310, 0xFF91ACBF, false);
        c.textCentered("CHOOSE / PREPARE", 470, 310, 0xFF91ACBF, false);
        c.save("wand_picker");
    }

    private static void draw(PreviewCanvas c, float cx, float cy, float scale, float open, int hovered) {
        var m = new WandHudMotion();
        m.update(0, org.bytechen.rottenruins.client.gui.WandHudGeometry.Target.NONE,
                0.78F, 0.65F, 0, "fire", "staff");
        WandHudVisuals.draw(new WandHudVisuals.Canvas() {
            public void arc(float inner, float outer, float a, float span, int color) {
                SdfShapes.wedge(c, cx, cy, inner * scale, outer * scale, a, a + span, color, color);
            }
            public void disc(float x, float y, float r, int color) {
                SdfShapes.disc(c, cx + x * scale, cy + y * scale, r * scale, color, color);
            }
            public void line(float x1, float y1, float x2, float y2, float width, int color) {
                SdfShapes.line(c, cx + x1 * scale, cy + y1 * scale,
                        cx + x2 * scale, cy + y2 * scale, width * scale, color);
            }
        }, m, 0xFFE4B478, 0xFF91C9DE, 0xFFE4B478, 2, 0, false);
        c.blit(c.texture("textures/item/fire_ball.png"), Math.round(cx - 11 * scale),
                Math.round(cy - 11 * scale), Math.round(22 * scale), Math.round(22 * scale));
        SdfShapes.ring(c, cx, cy, 57.7F * scale, 58.2F * scale,
                WandHudVisuals.alpha(0x8CAFC6, 65 * open), WandHudVisuals.alpha(0x8CAFC6, 65 * open), false);
        String[] icons = {"arcane_step", "briar_snare", "spear_of_judgment", "purifying_light",
                "tidal_pulse", "suppression_field", "fire_ball", "arcane_step"};
        int[] colors = {0xFFA9CCEC, 0xFF8CCBA2, 0xFFE4B478, 0xFFE2E0B7,
                0xFF80C9E0, 0xFFB29AD8, 0xFFE4B478, 0xFFA9CCEC};
        for (int i = 0; i < 8; i++) {
            if (i == 6) continue; // Current prepared spell does not reappear in the outer ring.
            var p = WandHudPicker.position(i, open);
            float x = cx + p.x() * scale, y = cy + p.y() * scale;
            float hover = i == hovered ? 1 : 0, radius = (10 + hover * 1.8F) * scale;
            int color = colors[i];
            SdfShapes.disc(c, x, y, radius + 2 * scale,
                    WandHudVisuals.alpha(color, open * (18 + hover * 35)),
                    WandHudVisuals.alpha(color, open * (18 + hover * 35)));
            SdfShapes.disc(c, x, y, radius, WandHudVisuals.alpha(0x091320, 240 * open),
                    WandHudVisuals.alpha(0x091320, 240 * open));
            SdfShapes.ring(c, x, y, radius - 0.7F * scale, radius,
                    WandHudVisuals.alpha(color, open * (110 + hover * 120)),
                    WandHudVisuals.alpha(color, open * (110 + hover * 120)), false);
            float size = 16 * scale * open * (1 + hover * 0.12F);
            c.blit(c.texture("textures/item/" + icons[i] + ".png"), Math.round(x - size / 2),
                    Math.round(y - size / 2), Math.round(size), Math.round(size));
        }
    }
}
