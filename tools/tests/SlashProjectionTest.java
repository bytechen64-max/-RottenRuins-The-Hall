import org.bytechen.rottenruins.client.rend.slash.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SlashProjectionTest {
    public static void main(String[] args) {
        for (float aspect : new float[] {4F / 3, 16F / 9, 21F / 9, 9F / 16})
            for (float fov : new float[] {40, 70, 110})
                for (var direction : SlashMotion.Direction.values()) {
                    Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.05F, 1000);
                    var a = SlashMotion.sample(direction, SlashMotion.PREPARE_END);
                    var b = SlashMotion.sample(direction, SlashMotion.CUT_END);
                    Vector3f start = SlashProjection.weaponPoint(a, 0.85F, projection);
                    Vector3f end = SlashProjection.weaponPoint(b, 0.85F, projection);
                    Vector3f projectedStart = projection.transformProject(new Vector3f(start));
                    Vector3f projectedEnd = projection.transformProject(new Vector3f(end));
                    if (a.edge().y() == 0) {
                        near(projectedStart.x, a.screenX()); near(projectedStart.y, a.screenY());
                        near(projectedEnd.x, b.screenX()); near(projectedEnd.y, b.screenY());
                    } else {
                        near(Math.abs(start.x), Math.abs(start.y));
                        near(Math.abs(end.x), Math.abs(end.y));
                        // Convert NDC back to pixel proportions: diagonal is 45 degrees
                        // regardless of window aspect or FOV, matching the world plane.
                        near(Math.abs((projectedEnd.y - projectedStart.y) / ((projectedEnd.x - projectedStart.x) * aspect)), 1);
                    }
                    check(Math.abs(projectedStart.x - projectedEnd.x) > 2, "covers full viewport");
                    boolean rightward = direction == SlashMotion.Direction.LEFT_TO_RIGHT
                            || direction == SlashMotion.Direction.UPPER_LEFT_TO_LOWER_RIGHT;
                    check(rightward ? projectedStart.x < projectedEnd.x : projectedStart.x > projectedEnd.x, "screen direction");
                    boolean diagonal = direction == SlashMotion.Direction.UPPER_LEFT_TO_LOWER_RIGHT
                            || direction == SlashMotion.Direction.UPPER_RIGHT_TO_LOWER_LEFT;
                    check(diagonal ? projectedStart.y > projectedEnd.y : Math.abs(projectedStart.y - projectedEnd.y) < 0.0001,
                            "screen cut slope");
                    Vector3f displacement = end.sub(start).normalize();
                    for (int i = 0; i <= 30; i++) {
                        var motion = SlashMotion.sample(direction, SlashMotion.PREPARE_END
                                + (SlashMotion.CUT_END - SlashMotion.PREPARE_END) * i / 30F);
                        Quaternionf rotation = SlashProjection.screenOrientation(motion, projection);
                        Vector3f face = rotation.transform(new Vector3f(0, 0, 1));
                        Vector3f edge = rotation.transform(new Vector3f(1, 0, 0));
                        near(displacement.dot(face), 0);
                        Vector3f tip = rotation.transform(new Vector3f(0, 1, 0));
                        near(tip.x,motion.blade().x());near(tip.y,motion.blade().y());near(tip.z,motion.blade().z());
                        check(displacement.dot(edge) > 0.3, "edge leads, flat face does not slap");
                    }
                }
        for (float bodyYaw : new float[] {-160, 0, 80}) for (float viewYaw : new float[] {-130, 0, 35, 175})
            for (float pitch : new float[] {-89, -45, 0, 45, 89}) {
                Matrix4f modelToWorld = new Matrix4f().rotateY((float) Math.toRadians(180 - bodyYaw)).scale(-1, -1, 1);
                Matrix4f frame = SlashProjection.viewRelativeFrame(modelToWorld, bodyYaw, viewYaw, pitch, 0.65F);
                Vector3f tip = frame.transformDirection(new Vector3f(0, 0, -1)).normalize();
                double yaw = Math.toRadians(viewYaw), p = Math.toRadians(pitch);
                Vector3f expected = new Vector3f((float) (-Math.sin(yaw) * Math.cos(p)),
                        (float) -Math.sin(p), (float) (Math.cos(yaw) * Math.cos(p)));
                near(tip.dot(expected), 1);
            }
        System.out.println("SlashProjectionTest passed: full-screen traversal, 140 degree blade rotation, third-person head yaw/pitch");
    }
    private static void near(float a, float b) { check(Math.abs(a - b) < 0.0002F, a + " != " + b); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
