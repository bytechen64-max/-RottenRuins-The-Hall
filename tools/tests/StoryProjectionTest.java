import org.bytechen.rottenruins.client.story.StoryProjection;

public final class StoryProjectionTest {
    public static void main(String[] args) {
        check(project(0, 0, -3).readable(), "front text is readable");
        check(!project(0, 0, 3).readable(), "directly behind is hidden");
        check(project(0, 0, 3).directionX() == 1, "directly behind has deterministic turn direction");
        check(project(4, 0, -3).directionX() > 0, "right target points right");
        check(project(-4, 0, -3).directionX() < 0, "left target points left");
        check(project(0, 4, -3).directionY() < 0, "above target points up on screen");
        check(project(0, -4, -3).directionY() > 0, "below target points down on screen");
        check(project(4, 0, 3).directionX() > 0, "behind right does not invert");
        check(project(-4, 0, 3).directionX() < 0, "behind left does not invert");
        check(!project(0, 0, 0).readable(), "camera plane is hidden");
        check(!StoryProjection.project(0, 0, -3, 1, 1, 3, 0.4).readable(),
                "center alone cannot mark clipped page readable");
        check(!StoryProjection.project(0, 0, -3, 4, 1, 1, 0.4).readable(),
                "narrow FOV respects complete page width");
        for (int degrees = -180; degrees <= 180; degrees++) {
            double a = Math.toRadians(degrees);
            var p = project(3 * Math.sin(a), 0, -3 * Math.cos(a));
            check(Double.isFinite(p.directionX()) && Double.isFinite(p.directionY()), "finite at all yaw angles");
            check(Math.abs(Math.hypot(p.directionX(), p.directionY()) - 1) < 1e-9, "unit pointer direction");
            if (Math.abs(degrees) > 90) check(!p.readable(), "rear hemisphere is hidden");
        }
        System.out.println("PASS: story visibility, page bounds, FOV, rear targets and 361 yaw angles");
    }

    private static StoryProjection.Result project(double x, double y, double z) {
        return StoryProjection.project(x, y, z, 1, 1, 1, 0.4);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
