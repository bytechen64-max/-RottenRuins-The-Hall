import org.bytechen.rottenruins.client.gui.WandHudMotion;
import org.bytechen.rottenruins.client.gui.WandHudGeometry.Target;

public final class WandHudMotionTest {
    public static void main(String[] args) {
        float at30 = hoverAfter(30), at60 = hoverAfter(60), at144 = hoverAfter(144);
        check(Math.abs(at30 - at60) < 0.0001F && Math.abs(at60 - at144) < 0.0001F,
                "spring must feel the same at different frame rates");
        var spring = new WandHudMotion.Spring();
        float peak = 0;
        for (int i = 0; i < 180; i++) { spring.step(1, 1 / 144.0F); peak = Math.max(peak, spring.value); }
        check(peak > 1 && peak < 1.08F, "hover has restrained overshoot");
        for (int i = 0; i < 240; i++) spring.step(0, 1 / 144.0F);
        check(Math.abs(spring.value) < 0.0001F, "hover settles after release");
        var m = new WandHudMotion();
        m.update(0, Target.NONE, 1, 1, 0, "fire", "staff");
        m.update(1 / 60.0F, Target.NONE, 0.5F, 0.7F, 1, "fire", "staff");
        check(m.playerTrail > m.player && m.wandTrail > m.wand, "spending leaves a delayed trail");
        check(m.playerFlash > 0 && m.wandFlash > 0, "spending triggers feedback");
        m.update(1 / 60.0F, Target.NONE, 0.5F, 0.7F, 0, "fire", "staff");
        check(m.ready > 0.9F, "cooldown completion emits a pulse");
        m.update(1 / 60.0F, Target.NONE, 0.5F, 0.3F, 0.5F, "ice", "other");
        check(m.ready == 0 && m.switched > 0, "changing spells cannot fake cooldown completion");
        check(m.wandFlash == 0 && Math.abs(m.wandTrail - 0.3F) < 0.0001F,
                "changing wands resets reserve feedback");
        for (int i = 0; i < 180; i++) m.update(1 / 60.0F, Target.NONE, 0.5F, 0.3F, 0.5F, "ice", "other");
        check(m.playerFlash == 0 && m.switched == 0 && Math.abs(m.playerTrail - m.player) < 0.001F,
                "feedback decays instead of looping");
        System.out.println("PASS: frame-rate-independent springs, restrained rebound, resource trails, event pulses and resets");
    }
    private static float hoverAfter(int fps) {
        var s = new WandHudMotion.Spring();
        for (int i = 0; i < fps / 2; i++) s.step(1, 1.0F / fps);
        return s.value;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
