import org.bytechen.rottenruins.client.gui.WandHudGeometry;
import org.bytechen.rottenruins.client.gui.WandHudMotion;
import org.bytechen.rottenruins.client.gui.WandHudVisuals;

/** Real HUD drawing recipe and spring simulation, rendered without starting Minecraft. */
final class SceneWandHud {
    public static void render(PreviewCanvas c, float time) {
        c.shader("sdf_ui");
        if (time < 0) {
            try { java.nio.file.Files.createDirectories(c.outDir().resolve("wand_motion")); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            WandHudMotion m = new WandHudMotion();
            String detail = "";
            int tint = 0xFF91C9DE;
            for (int frame = 0; frame < 204; frame++) {
                float t = frame / 30.0F;
                c.clear(0xFF0D1520);
                c.text("WAND / MOTION STUDY", 25, 25, 0xFFCCDCE7, false);
                c.text("Hover / reserve / spell / cast / ready", 25, 47, 0xFF688295, false);
                WandHudGeometry.Target target = t < 0.6F ? WandHudGeometry.Target.NONE
                        : t < 1.8F ? WandHudGeometry.Target.PLAYER
                        : t < 2.9F ? WandHudGeometry.Target.WAND
                        : t < 3.8F ? WandHudGeometry.Target.ICON : WandHudGeometry.Target.NONE;
                boolean spent = t >= 4.2F;
                float cooldown = spent ? Math.max(0, 1 - (t - 4.2F) / 1.15F) : 0;
                m.update(1 / 30.0F, target, spent ? 0.42F : 0.78F, spent ? 0.38F : 0.68F,
                        cooldown, t < 3.9F ? "fire" : "ice", "staff");
                float angle = -1.2F + t * 0.9F;
                draw(c, m, 470, 165, 1.7F, t, angle, cooldown);
                if (target != WandHudGeometry.Target.NONE) {
                    detail = target == WandHudGeometry.Target.PLAYER ? "PLAYER MANA   780 / 1000"
                            : target == WandHudGeometry.Target.WAND ? "WAND RESERVE   340 / 500" : "Fireball";
                    tint = target == WandHudGeometry.Target.PLAYER ? 0xFF91C9DE : 0xFFE4B478;
                }
                float opacity = Math.max(0, Math.min(1, m.focus.value));
                if (opacity > 0.02F) {
                    float x = 90 - (1 - opacity) * 7;
                    SdfShapes.roundRect(c, x, 147, 270, 36, 5,
                            WandHudVisuals.alpha(0x091321, 233 * opacity),
                            WandHudVisuals.alpha(0x091321, 233 * opacity),
                            WandHudVisuals.alpha(tint, 80 * opacity), 0.6F);
                    c.fill((int) x + 8, 156, 2, 8, WandHudVisuals.alpha(tint, 220 * opacity));
                    c.text(detail, (int) x + 15, 155, WandHudVisuals.alpha(0xE9F3FA, 255 * opacity), false);
                    c.textScaled("Drag move / Wheel scale / RMB reset", x + 9, 170,
                            WandHudVisuals.alpha(0xA1B4C5, 220 * opacity), false, 0.8F);
                    float radius = target == WandHudGeometry.Target.PLAYER ? 24
                            : target == WandHudGeometry.Target.WAND
                            ? (WandHudGeometry.wandRing(m.playerHover.value, m.wandHover.value).inner()
                            + WandHudGeometry.wandRing(m.playerHover.value, m.wandHover.value).outer()) / 2 : 7;
                    float px = 470 + (float) Math.cos(angle) * radius * 1.7F;
                    float py = 165 + (float) Math.sin(angle) * radius * 1.7F;
                    SdfShapes.line(c, px, py, px + 5, py + 8, 0.7F, 0xEDEFFAFF);
                    SdfShapes.line(c, px, py, px, py + 9, 0.7F, 0xEDEFFAFF);
                }
                String state = t < 0.6F ? "Idle" : t < 1.8F ? "Inner ring / elastic push"
                        : t < 2.9F ? "Outer ring / pointer light" : t < 3.8F ? "Spell / gentle lift"
                        : t < 4.2F ? "Spell change" : t < 5.35F ? "Cast / mana trail" : "Ready / ripple";
                c.text(state, 25, 267, 0xFF9EB9CD, false);
                c.save(String.format(java.util.Locale.ROOT, "wand_motion/frame_%03d", frame));
            }
            return;
        }
        c.clear(0xFF0D1520);
        c.text("WAND / REFINED MOTION", 25, 25, 0xFFCCDCE7, false);
        String[] labels = {"REST", "INNER / PUSH", "OUTER / LIGHT", "READY / RIPPLE"};
        for (int i = 0; i < 4; i++) {
            WandHudMotion m = new WandHudMotion();
            m.update(0, WandHudGeometry.Target.NONE, 0.78F, 0.68F, 0, "fire", "staff");
            m.playerHover.value = i == 1 ? 1 : 0;
            m.wandHover.value = i == 2 ? 1 : 0;
            m.ready = i == 3 ? 0.65F : 0;
            draw(c, m, 80 + i * 158, 140, 1.2F, time, -0.7F, 0);
            c.textCentered(labels[i], 80 + i * 158, 225, 0xFF91ACBF, false);
        }
        c.save("wand_hud");
    }

    private static void draw(PreviewCanvas c, WandHudMotion m, float cx, float cy, float scale,
                              float time, float pointer, float cooldown) {
        WandHudVisuals.draw(new WandHudVisuals.Canvas() {
            public void arc(float inner, float outer, float start, float span, int color) {
                SdfShapes.wedge(c, cx, cy, inner * scale, outer * scale, start, start + span, color, color);
            }
            public void disc(float x, float y, float radius, int color) {
                SdfShapes.disc(c, cx + x * scale, cy + y * scale, radius * scale, color, color);
            }
            public void line(float x1, float y1, float x2, float y2, float width, int color) {
                SdfShapes.line(c, cx + x1 * scale, cy + y1 * scale,
                        cx + x2 * scale, cy + y2 * scale, width * scale, color);
            }
        }, m, 0xFFE4B478, 0xFF91C9DE, 0xFFE4B478, time, pointer, false);
        float icon = 22 * (1 + Math.max(0, m.iconHover.value) * 0.08F
                + (float) Math.sin(m.ready * Math.PI) * 0.10F - m.switched * 0.12F);
        int size = Math.round(icon * scale), x = Math.round(cx - size / 2.0F), y = Math.round(cy - size / 2.0F);
        c.blit(c.texture("textures/item/fire_ball.png"), x, y, size, size);
        if (cooldown > 0) {
            c.pushTransform();
            c.translate(x, y);
            c.scale(cooldown, 1);
            c.fill(0, 0, size, size, 0x99050A16);
            c.popTransform();
        }
    }
}
