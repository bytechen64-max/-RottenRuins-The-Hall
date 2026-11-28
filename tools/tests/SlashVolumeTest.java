import org.bytechen.rottenruins.core.combat.slash.SweptSlashVolume;
import org.bytechen.rottenruins.core.combat.slash.SweptSlashVolume.*;
import java.util.Random;

public final class SlashVolumeTest {
    private static final Vec FORWARD = new Vec(0, 0, 1), RIGHT = new Vec(1, 0, 0);
    public static void main(String[] args) {
        var slash = new SweptSlashVolume(FORWARD, RIGHT, 4, 0, 110, 0.5);
        check(slash.intersects(box(0, 0, 2, .2)), "forward target");
        check(!slash.intersects(box(0, 0, -2, .2)), "behind target");
        check(!slash.intersects(box(4, 0, 0, .2)), "outside arc");
        check(!slash.intersects(box(0, 1, 2, .2)), "outside plane thickness");
        check(slash.intersects(box(0, .65, 2, .2)), "box overlaps thickness despite center outside");
        check(slash.intersects(box(0, 0, 4.15, .2)), "large target edge overlaps reach");
        check(!slash.intersects(box(0, 0, 4.3, .2)), "entire box beyond reach");
        var ring = new SweptSlashVolume(FORWARD, RIGHT, 4, 1, 110, .3);
        check(!ring.intersects(box(0, 0, .3, .05)), "inner hole");
        check(ring.intersects(box(0, 0, 1.1, .05)), "outside inner hole");
        var full = new SweptSlashVolume(FORWARD, RIGHT, 4, 1, 360, .3);
        check(full.intersects(box(0, 0, -2, .05)), "360 arc supports rear targets");
        check(!full.intersects(box(0, 0, 0, .05)), "360 arc still preserves inner hole");
        Vec diagonal = new Vec(Math.sqrt(.5), -Math.sqrt(.5), 0);
        var tilted = new SweptSlashVolume(FORWARD, diagonal, 4, 0, 120, .12);
        check(tilted.intersects(box(1, -1, 2, .05)), "diagonal cutting plane");
        check(!tilted.intersects(box(1, 1, 2, .05)), "other diagonal lies outside thickness");
        var verticalAim = new SweptSlashVolume(new Vec(0, 1, 0), RIGHT, 4, 0, 90, .2);
        check(verticalAim.intersects(box(0, 2, 0, .05)), "vertical aim");
        Random random = new Random(5317);
        for (int i = 0; i < 10000; i++) {
            double x = random.nextDouble() * 10 - 5, y = random.nextDouble() * 2 - 1, z = random.nextDouble() * 10 - 5;
            double radius = Math.hypot(x, z), angle = Math.abs(Math.toDegrees(Math.atan2(x, z)));
            // Polygonal arc has <0.004 units error here. Avoid the known discretization boundary.
            if (Math.abs(radius - 1) < .01 || Math.abs(radius - 4) < .01 || Math.abs(angle - 55) < .01 || Math.abs(Math.abs(y) - .3) < .001) continue;
            boolean reference = radius >= 1 && radius <= 4 && angle <= 55 && Math.abs(y) <= .3;
            check(ring.intersects(box(x, y, z, 0.000001)) == reference, "analytic point-sector comparison");
        }
        System.out.println("SlashVolumeTest passed: box edges, reach, hole, arc, thickness, rotated planes, 10000 reference samples");
    }
    private static Box box(double x, double y, double z, double half) { return new Box(new Vec(x, y, z), new Vec(half, half, half)); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
