import org.bytechen.rottenruins.client.entity.render.BoomerangPose;
import org.bytechen.rottenruins.client.rend.slash.BladeGeometry;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;

public final class BoomerangPoseTest {
    public static void main(String[] args) {
        // Equal broad dimensions deliberately exercise the case rejected by sword-only detection.
        for (Quaternionf source : new Quaternionf[] {new Quaternionf(), new Quaternionf().rotateXYZ(.7F, .4F, .9F)}) {
            var points = new ArrayList<BladeGeometry.Vec>();
            for (int x : new int[] {-1, 1}) for (int y : new int[] {-1, 1}) for (int z : new int[] {-1, 1}) {
                var p = source.transform(new Vector3f(x * .5F, y * .5F, z * .025F));
                points.add(new BladeGeometry.Vec(p.x, p.y, p.z));
            }
            check(BladeGeometry.analyze(points).isEmpty(), "Sword detection must still reject discs");
            var profile = BladeGeometry.analyzeFlat(points).orElseThrow();
            for (Vector3f direction : new Vector3f[] {new Vector3f(0, 0, 1), new Vector3f(1, .4F, -1),
                    new Vector3f(0, 1, 0), new Vector3f(0, -1, 0)}) {
                Vector3f forward = new Vector3f(direction).normalize();
                Vector3f up = Math.abs(forward.y) > .98F ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
                Vector3f expectedNormal = new Vector3f(forward).cross(up).normalize().cross(forward).normalize();
                for (long seed : new long[] {0, -1, 0x123456789ABCDEFL}) {
                    Vector3f lastNormal = null;
                    for (float age = 0; age < 30; age += .25F) {
                        var rotation = BoomerangPose.rotation(profile, direction, age, seed);
                        var n = profile.normal();
                        Vector3f normal = rotation.transform(new Vector3f((float)n.x(), (float)n.y(), (float)n.z()));
                        check(normal.isFinite() && normal.dot(expectedNormal) > .97F,
                                "Detected face must lie in the flight plane with only a small bank");
                        if (lastNormal != null) check(normal.distance(lastNormal) < 1e-5F, "Spin must stay in its plane");
                        lastNormal = normal;
                        Matrix4f display = new Matrix4f().translation(.2F, -.1F, .3F).rotateXYZ(.4F, .7F, .2F).scale(.5F, .8F, 1.2F);
                        Matrix4f corrected = new Matrix4f().rotate(rotation).mul(new Matrix4f(display).invert()).mul(display);
                        Vector3f actual = corrected.transformDirection(new Vector3f((float)n.x(), (float)n.y(), (float)n.z())).normalize();
                        check(actual.distance(normal) < 1e-5F, "Item display transform must not tilt the measured plane");
                    }
                }
            }
        }
        System.out.println("BoomerangPoseTest passed: rotated flat models, sword compatibility, vertical flight, stable spin, display cancellation");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
