import org.bytechen.rottenruins.client.rend.slash.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SlashRecoveryTest {
    public static void main(String[] args) {
        for (float aspect : new float[] {4F / 3, 16F / 9, 21F / 9, 9F / 16})
            for (float fov : new float[] {40, 70, 110})
                for (float radius : new float[] {0.2F, 0.8F, 1.5F})
                    for (var direction : SlashMotion.Direction.values()) {
                        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.05F, 1000);
                        var cut = SlashMotion.sample(direction, SlashMotion.CUT_END);
                        Vector3f cutGrip = SlashProjection.weaponPoint(cut, 0.85F, projection);
                        Quaternionf cutRotation = SlashProjection.screenOrientation(cut, projection);
                        var prepare = SlashMotion.sample(direction, SlashMotion.PREPARE_END);
                        Vector3f entryTarget = SlashProjection.weaponPoint(prepare, .85F, projection);
                        var entry = SlashRecovery.entry(0, entryTarget, cutRotation, radius, projection);
                        Vector3f projectedEntry = projection.transformProject(new Vector3f(entry.grip()));
                        check(Math.abs(projectedEntry.x) > 1, "next attack begins outside its own viewport edge");
                        var ready = SlashRecovery.entry(SlashMotion.PREPARE_END, entryTarget, cutRotation, radius, projection);
                        near(ready.grip().distance(entryTarget), 0, "entry joins its own stroke without old endpoint travel");
                        for (int i = 0; i <= 30; i++) {
                            var step = SlashRecovery.entry(SlashMotion.PREPARE_END * i / 30F, entryTarget, cutRotation, radius, projection);
                            Vector3f onScreen = projection.transformProject(new Vector3f(step.grip()));
                            check(onScreen.x * prepare.screenX() > 0, "entry never traverses the opposite side to reach its start");
                            near(Math.abs(step.rotation().dot(cutRotation)), 1, "blade already points forward before entering view");
                        }
                        // Check both hand sides independently of the direction of the cut.
                        for (float side : new float[] {-1, 1}) {
                            Vector3f idle = new Vector3f(side * 0.5F, -0.4F, -0.72F);
                            Quaternionf idleRotation = new Quaternionf().rotationXYZ(0.3F, side * 0.6F, -0.2F);
                            var start = frame(SlashMotion.CUT_END, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                            near(start.grip().distance(cutGrip), 0, "continuous cut exit");
                            near(Math.abs(start.rotation().dot(cutRotation)), 1, "cut rotation preserved");
                            float previousExitY = projection.transformProject(new Vector3f(cutGrip)).y;
                            float previousExitX = projection.transformProject(new Vector3f(cutGrip)).x;
                            boolean downward = direction == SlashMotion.Direction.UPPER_RIGHT_TO_LOWER_LEFT
                                    || direction == SlashMotion.Direction.UPPER_LEFT_TO_LOWER_RIGHT;
                            for (int i = 1; i <= 100; i++) {
                                float t = SlashMotion.CUT_END + (SlashRecovery.EXIT_END - SlashMotion.CUT_END) * i / 100F;
                                var exit = frame(t, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                                near(exit.grip().z, cutGrip.z, "no perspective shrink while exiting");
                                near(Math.abs(exit.rotation().dot(cutRotation)), 1, "no visible turn before leaving");
                                Vector3f projected = projection.transformProject(new Vector3f(exit.grip()));
                                if (downward) {
                                    check(projected.y <= previousExitY + 0.0001F, "downward stroke never bounces up at the bottom");
                                    near(exit.grip().x / exit.grip().y, cutGrip.x / cutGrip.y, "exit keeps the original diagonal");
                                }
                                check(Math.abs(projected.x) >= Math.abs(previousExitX) - 0.0001F, "exit never mirrors back across the viewport");
                                previousExitY = projected.y; previousExitX = projected.x;
                            }
                            for (float boundary : new float[] {SlashRecovery.EXIT_END, SlashRecovery.HOLD_END,
                                    SlashRecovery.LOWER_END, SlashRecovery.LIFT_START, 1}) {
                                var before = frame(boundary - 0.000001F, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                                var after = frame(boundary + 0.000001F, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                                near(before.grip().distance(after.grip()), 0, "continuous recovery position");
                                near(Math.abs(before.rotation().dot(after.rotation())), 1, "continuous recovery angle");
                            }
                            var holdStart = frame(SlashRecovery.EXIT_END, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                            var holdEnd = frame(SlashRecovery.HOLD_END, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                            near(holdStart.grip().distance(holdEnd.grip()), 0, "offscreen hold before return");
                            near(Math.abs(holdStart.rotation().dot(holdEnd.rotation())), 1, "no premature turn during hold");
                            for (int i = 0; i <= 40; i++) {
                                float t = SlashRecovery.EXIT_END + (SlashRecovery.LIFT_START - SlashRecovery.EXIT_END) * i / 40F;
                                var hidden = frame(t, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                                // A bounding sphere covers the model regardless of its rotation.
                                for (int n = 0; n < 80; n++) {
                                    float z = 1 - 2 * (n + 0.5F) / 80;
                                    float a = n * 2.399963F, r = (float) Math.sqrt(1 - z * z);
                                    Vector3f point = new Vector3f((float) Math.cos(a) * r,
                                            (float) Math.sin(a) * r, z).mul(radius).add(hidden.grip());
                                    if (point.z >= -0.05F) continue;
                                    projection.transformProject(point);
                                    check(Math.abs(point.x) > 1 || point.y < -1, "repositioning is outside the viewport");
                                }
                            }
                            float lastY = -Float.MAX_VALUE;
                            for (int i = 0; i <= 40; i++) {
                                var lift = frame(SlashRecovery.LIFT_START + (1 - SlashRecovery.LIFT_START) * i / 40F,
                                        cutGrip, cutRotation, idle, idleRotation, radius, projection);
                                near(lift.grip().x, idle.x, "no visible sideways return");
                                near(lift.grip().z, idle.z, "native depth during lift");
                                check(lift.grip().y >= lastY - 0.0001, "lift only upwards");
                                near(Math.abs(lift.rotation().dot(idleRotation)), 1, "no visible twisting during lift");
                                lastY = lift.grip().y;
                            }
                            var end = frame(1, cutGrip, cutRotation, idle, idleRotation, radius, projection);
                            near(end.grip().distance(idle), 0, "exact vanilla endpoint");
                        }
                    }
        System.out.println("SlashRecoveryTest passed: offscreen turn/transfer, vertical lift, both hands, viewport/FOV/model sizes");
    }
    private static SlashRecovery.Frame frame(float t, Vector3f cut, Quaternionf cutQ, Vector3f idle,
                                              Quaternionf idleQ, float radius, Matrix4f projection) {
        return SlashRecovery.sample(t, cut, cutQ, idle, idleQ, radius, projection);
    }
    private static void near(float a, float b, String message) { check(Math.abs(a - b) < 0.0002F, message); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
