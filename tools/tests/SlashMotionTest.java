import org.bytechen.rottenruins.client.rend.slash.SlashMotion;

public final class SlashMotionTest {
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String name) {
        check(Math.abs(actual - expected) < 0.0002F, name + ": " + actual);
    }
    public static void main(String[] args) {
        for (var direction : SlashMotion.Direction.values()) {
            var start = SlashMotion.sample(direction, SlashMotion.PREPARE_END);
            var end = SlashMotion.sample(direction, SlashMotion.CUT_END);
            near(start.blade().dot(end.blade()),(float)Math.cos(Math.toRadians(140)),"Actual blade sweeps 140 degrees");
            var mid=SlashMotion.sample(direction,(SlashMotion.PREPARE_END+SlashMotion.CUT_END)*.5F);near(mid.blade().z(),-1,"Mid-swing points forward");
            boolean rightward = direction == SlashMotion.Direction.LEFT_TO_RIGHT
                    || direction == SlashMotion.Direction.UPPER_LEFT_TO_LOWER_RIGHT;
            check(rightward ? start.screenX() < end.screenX()
                    : start.screenX() > end.screenX(), "cut direction");
            boolean diagonal = direction == SlashMotion.Direction.UPPER_LEFT_TO_LOWER_RIGHT
                    || direction == SlashMotion.Direction.UPPER_RIGHT_TO_LOWER_LEFT;
            check(diagonal ? start.screenY() > end.screenY()
                    : Math.abs(start.screenY() - end.screenY()) < 0.0001F, "cut slope");
            for (int i = 1; i < 1000; i++) {
                float t = i / 1000F;
                var p = SlashMotion.sample(direction, t);
                near(p.blade().dot(p.blade()), 1, "constant radius");
                check(p.blade().z()<-.33F,"Blade remains in the forward hemisphere throughout the 140 degree cut");
                near(p.edge().dot(p.edge()), 1, "unit edge");
                near(p.normal().dot(p.normal()), 1, "unit face normal");
                near(p.edge().dot(p.blade()), 0, "edge perpendicular to blade");
                near(p.normal().dot(p.edge()), 0, "edge lies in cutting plane");
                near(p.edge().cross(p.blade()).dot(p.normal()), 1, "right-handed frame");
                check(p.weight() >= 0 && p.weight() <= 1, "bounded blend");
                if (t > SlashMotion.PREPARE_END + 0.001F && t < SlashMotion.CUT_END - 0.001F) {
                    var next = SlashMotion.sample(direction, t + 0.0001F);
                    var delta = new SlashMotion.Vec((next.screenX() - p.screenX()) / 1.05F,
                            diagonal ? (next.screenY() - p.screenY()) / 0.82F : 0, 0);
                    near(delta.dot(p.normal()), 0, "no flat-side slap");
                    check(delta.dot(p.edge()) > 0, "edge follows velocity, never flies sideways");
                    near(p.weight(), 1, "whole cut stays in plane");
                }
            }
            for (float t : new float[] {SlashMotion.PREPARE_END, SlashMotion.CUT_END,
                    SlashMotion.RECOVER_START, 1}) {
                var a = SlashMotion.sample(direction, t - 0.00001F);
                var b = SlashMotion.sample(direction, t + 0.00001F);
                near(a.weight(), b.weight(), "blend continuity");
                if (t < 1) near(a.blade().dot(b.blade()), 1, "orientation continuity");
            }
            check(SlashMotion.sample(direction, 0).equals(SlashMotion.Pose.IDLE), "rest at start");
            check(SlashMotion.sample(direction, 1).equals(SlashMotion.Pose.IDLE), "rest at end");
        }
        for (int i = 0; i < 12; i++)
            check(SlashMotion.direction(i) == SlashMotion.Direction.values()[i % 4], "combo sequence");
        System.out.println("SlashMotionTest passed: directions, rigid radius, edge/velocity alignment, plane, recovery");
    }
}
