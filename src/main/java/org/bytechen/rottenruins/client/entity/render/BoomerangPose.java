package org.bytechen.rottenruins.client.entity.render;

import org.bytechen.rottenruins.client.rend.slash.BladeGeometry;
import org.bytechen.rottenruins.client.rend.slash.SlashProjection;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Align the detected broad face to the flight plane, independently of the camera. */
public final class BoomerangPose {
    private BoomerangPose() {}

    public static Quaternionf rotation(BladeGeometry.Profile geometry, Vector3f direction,
                                       float age, long seed) {
        Vector3f forward = new Vector3f(direction);
        if (forward.lengthSquared() < 1.0e-8F) forward.set(0, 0, 1);
        forward.normalize();
        Vector3f up = Math.abs(forward.y) > 0.98F ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f edge = new Vector3f(forward).cross(up).normalize();
        Vector3f normal = new Vector3f(edge).cross(forward).normalize();
        float bank = signed(seed, 0) * 10.0F;
        float pitch = signed(seed, 16) * 6.0F;
        float phase = (signed(seed, 32) + 1.0F) * 180.0F;
        float spin = age * (36.0F + signed(seed, 48) * 5.0F) + phase;
        Quaternionf source = SlashProjection.orientation(vector(geometry.edge()), vector(geometry.blade()),
                vector(geometry.normal())).conjugate();
        return SlashProjection.orientation(edge, forward, normal)
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(bank))
                .rotateZ((float) Math.toRadians(spin)).mul(source);
    }

    private static float signed(long seed, int shift) { return ((seed >>> shift) & 65535) / 32767.5F - 1.0F; }
    private static Vector3f vector(BladeGeometry.Vec v) { return new Vector3f((float) v.x(), (float) v.y(), (float) v.z()); }
}
