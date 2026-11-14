package org.bytechen.rottenruins.client.rend.slash;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Projection-aware math, isolated from the live renderer for regression checks. */
public final class SlashProjection {
    private SlashProjection() {}

    public static Vector3f screenPoint(float x, float y, float depth, Matrix4f projection) {
        Vector4f ray = new Vector4f(x, y, 0, 1).mul(new Matrix4f(projection).invert());
        return new Vector3f(ray.x, ray.y, ray.z).mul(-depth / ray.z);
    }

    public static Quaternionf screenOrientation(SlashMotion.Pose motion, Matrix4f projection) {
        Vector3f blade = new Vector3f(motion.blade().x(),motion.blade().y(),motion.blade().z());
        Vector3f edge = new Vector3f(motion.edge().x(),motion.edge().y(),motion.edge().z());
        return orientation(edge, blade, new Vector3f(edge).cross(blade));
    }

    /** Keep the traversal in the same camera-space cutting plane as the world slash.
     * Horizontal/vertical reach can grow to leave the viewport, but slope stays fixed. */
    public static Vector3f weaponPoint(SlashMotion.Pose motion, float depth, Matrix4f projection) {
        if (motion.edge().y() == 0) return screenPoint(motion.screenX(), motion.screenY(), depth, projection);
        Vector3f side = screenPoint(Math.copySign(1.05F, motion.edge().x()), 0, depth, projection)
                .sub(screenPoint(0, 0, depth, projection));
        Vector3f top = screenPoint(0, 1.05F, depth, projection).sub(screenPoint(0, 0, depth, projection));
        float reach = Math.max(side.length(), top.length());
        float along = motion.screenX() / Math.copySign(1.05F, motion.edge().x());
        // A 45-degree world diagonal has equal horizontal and vertical displacement.
        return new Vector3f(Math.copySign(reach, motion.edge().x()) * along, -reach * along, -depth);
    }

    public static Quaternionf orientation(Vector3f edge, Vector3f blade, Vector3f normal) {
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0, edge)
                .setColumn(1, blade).setColumn(2, normal));
    }

    /** Undo model down/left axes, then align the weapon frame with the head view, including pitch. */
    public static Matrix4f viewRelativeFrame(Matrix4f bodyRoot, float bodyYaw, float viewYaw, float pitch, float height) {
        return new Matrix4f(bodyRoot).translate(0, height, 0).rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(-(viewYaw - bodyYaw)))
                .rotateX((float) Math.toRadians(-pitch));
    }
}
