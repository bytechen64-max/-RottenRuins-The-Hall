package org.bytechen.rottenruins.client.combat.slash.trail;

import org.joml.Vector3f;

/** The original radial ribbon in the actual cutting plane, with no normal/height offset. */
public final class SlashTrailGeometry {
    public record Frame(Vector3f forward, Vector3f cutDirection) {}
    private SlashTrailGeometry() {}

    public static Frame frame(Vector3f forward, Vector3f cutDirection) {
        return new Frame(new Vector3f(forward).normalize(), new Vector3f(cutDirection).normalize());
    }

    /** Relative to the actor's eyes; add the actor-camera difference before uploading float vertices. */
    public static Vector3f point(Frame frame, double angle, float radius, float crossWidth) {
        float radial = Math.max(0, radius + crossWidth);
        // Keep the original small forward offset, but remove the old -normal*0.30:
        // horizontal cuts were pushed down and diagonal cuts were pushed sideways.
        return new Vector3f(frame.forward).mul(0.25F + radial * (float) Math.cos(angle))
                .fma(radial * (float) Math.sin(angle), frame.cutDirection);
    }
}
