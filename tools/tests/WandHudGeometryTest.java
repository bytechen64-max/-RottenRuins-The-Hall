import org.bytechen.rottenruins.client.gui.WandHudGeometry;
import org.bytechen.rottenruins.client.gui.WandHudGeometry.Target;

public final class WandHudGeometryTest {
    public static void main(String[] args) {
        check(WandHudGeometry.target(0, 0) == Target.ICON, "center names the spell");
        for (int degrees = 0; degrees < 360; degrees++) {
            double angle = Math.toRadians(degrees);
            check(at(24, angle) == Target.PLAYER, "inner ring shows player mana");
            check(at(30, angle) == Target.WAND, "outer ring shows wand reserve");
            check(at(47, angle) == Target.NONE, "outside the expanded ring is empty");
            check(at(21, angle) == Target.NONE, "icon/ring gap is empty");
            check(at(27, angle) == Target.NONE, "ring gap stays separate");
            for (int frame = 0; frame <= 100; frame++) {
                float hover = frame / 100.0F;
                var player = WandHudGeometry.playerRing(hover);
                var wand = WandHudGeometry.wandRing(hover, 0);
                check(Math.abs(wand.inner() - player.outer() - 3) < 0.0001F,
                        "outer ring moves outwards with a constant gap");
                check(Math.abs(wand.outer() - wand.inner() - 2.5F) < 0.0001F, "pushing preserves outer thickness");
                check(WandHudGeometry.target(24 * Math.cos(angle), 24 * Math.sin(angle), hover, 0)
                        == Target.PLAYER, "stationary inner hover stays stable during expansion");
                double radius = (wand.inner() + wand.outer()) / 2;
                check(WandHudGeometry.target(radius * Math.cos(angle), radius * Math.sin(angle), hover, 0)
                        == Target.WAND, "outer hit region follows the pushed ring");
            }
            check(WandHudGeometry.target(30 * Math.cos(angle), 30 * Math.sin(angle), 1, 0)
                    == Target.PLAYER, "expanded player ring retains hover");
            check(WandHudGeometry.target(35 * Math.cos(angle), 35 * Math.sin(angle), 0, 1)
                    == Target.WAND, "expanded wand ring retains hover");
        }
        check(WandHudGeometry.cooldownWidth(16, 1) == 16, "cooldown starts fully covered");
        check(WandHudGeometry.cooldownWidth(16, 0.5F) == 8, "half cooldown leaves left half covered");
        check(WandHudGeometry.cooldownWidth(16, 0) == 0, "ready icon has no cover");
        check(WandHudGeometry.cooldownWidth(16, 0.51F) > 8
                && WandHudGeometry.cooldownWidth(16, 0.51F) < 9, "cooldown edge moves at subpixel precision");
        float previous = 16;
        for (int tick = 0; tick <= 200; tick++) {
            float width = WandHudGeometry.cooldownWidth(16, 1 - tick / 200.0F);
            check(width <= previous && width >= 0, "mask edge moves only to the left");
            previous = width;
        }
        System.out.println("PASS: compact rings, animated push and hit regions, gaps, cooldown direction and endpoints");
    }

    private static Target at(double radius, double angle) {
        return WandHudGeometry.target(radius * Math.cos(angle), radius * Math.sin(angle));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
