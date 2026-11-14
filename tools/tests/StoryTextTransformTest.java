import org.bytechen.rottenruins.client.story.StoryTextTransform;
import org.bytechen.rottenruins.client.story.StoryGlyphMotion;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class StoryTextTransformTest {
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 256);

    public static void main(String[] args) {
        Matrix4f glyphPanel = new Matrix4f().rotateY(0.4f).translate(1, 2, -3).scale(0.01f);
        for (float exitAge : new float[]{-1, 0, 5, 20, 39}) {
            var pose = StoryGlyphMotion.sample(0, exitAge, 3);
            var animated = StoryTextTransform.glyph(glyphPanel, -12, 7, 9, pose);
            Vector3f actualCenter = animated.transformPosition(new Vector3f(4.5f, 4.5f, 0));
            Vector3f expectedCenter = glyphPanel.transformPosition(new Vector3f(-7.5f + pose.x(), 11.5f + pose.y(), 0));
            check(actualCenter.distance(expectedCenter) < 0.0001f, "glyph scales/spins around its own center and follows its trajectory");
        }
        // Minecraft yaw zero looks along world +Z, while the GPU view looks along -Z.
        for (int bearing : new int[] {0, 90, -90, 180}) {
            float angle = radians(bearing);
            Vector3f anchor = new Vector3f(-(float) Math.sin(angle) * 3, 0, (float) Math.cos(angle) * 3);
            Matrix4f centered = panel(bearing, 0, anchor, angle);
            var front = StoryTextTransform.project(PROJECTION, centered, 110, 36);
            check(front.readable(), "facing each radial bearing is readable");
            Vector4f left = centered.transform(new Vector4f(-100, 0, 0, 1));
            Vector4f right = centered.transform(new Vector4f(100, 0, 0, 1));
            check(right.x > left.x, "text is not mirrored");
            for (int turn = -180; turn <= 180; turn++) {
                Matrix4f view = view(bearing + turn, 0);
                Matrix4f model = panel(bearing + turn, 0, anchor, angle);
                var projected = StoryTextTransform.project(PROJECTION, model, 110, 36);
                Vector4f center = model.transform(new Vector4f(0, 0, 0, 1));
                near(center.x, -3 * Math.sin(radians(turn)), "anchor moves opposite view turn");
                near(center.z, -3 * Math.cos(radians(turn)), "anchor has correct depth through full turn");
                Matrix4f expectedWorld = StoryTextTransform.panel(new Matrix4f(), anchor, angle, 0.01f);
                check(new Matrix4f(view).invert().mul(model).equals(expectedWorld, 0.0001f),
                        "world orientation remains fixed while turning");
                if (Math.abs(turn) > 90) check(!projected.readable(), "rear text is hidden");
                if (turn > 5 && turn < 175) check(projected.directionX() < 0, "turning right puts target left");
                if (turn < -5 && turn > -175) check(projected.directionX() > 0, "turning left puts target right");
            }
        }
        Vector3f anchor = new Vector3f(0, 0, 3);
        check(StoryTextTransform.project(PROJECTION, panel(0, 60, anchor, 0), 110, 36).directionY() < 0,
                "looking down points up toward text");
        check(StoryTextTransform.project(PROJECTION, panel(0, -60, anchor, 0), 110, 36).directionY() > 0,
                "looking up points down toward text");
        // Camera displacement in third person changes the eye-to-anchor vector, never the radial bearing.
        check(StoryTextTransform.project(PROJECTION, panel(0, 0, new Vector3f(0, 0, 7), 0), 110, 36).readable(),
                "rear third person camera sees the same panel");
        check(!StoryTextTransform.project(PROJECTION, panel(180, 0, new Vector3f(0, 0, -0.5f), 0), 110, 36).readable(),
                "front third person camera does not mark an oversized clipped panel readable");
        System.out.println("PASS: actual Minecraft view matrices, four radial bearings, 1444 yaw poses, pitch, text orientation and third person");
    }

    private static Matrix4f panel(float yaw, float pitch, Vector3f anchor, float bearing) {
        return StoryTextTransform.panel(view(yaw, pitch), anchor, bearing, 0.01f);
    }

    private static Matrix4f view(float yaw, float pitch) {
        return new Matrix4f().rotateX(radians(pitch)).rotateY(radians(yaw + 180));
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }

    private static void near(double value, double expected, String message) {
        check(Math.abs(value - expected) < 0.0001, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
