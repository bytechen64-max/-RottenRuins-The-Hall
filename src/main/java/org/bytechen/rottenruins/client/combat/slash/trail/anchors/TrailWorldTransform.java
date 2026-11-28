package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Match the hand projection at birth, then leave the sample in world space. */
public final class TrailWorldTransform {
    private TrailWorldTransform() {}
    public static Matrix4f fromHand(Matrix4f item,Matrix4f handProjection,Matrix4f worldProjection,
                                    Matrix4f inverseWorldView,Vector3f cameraOffset) {
        // Uniform depth moves the near-hand geometry into the combat volume without changing NDC.
        float depth=1.6F;
        float x=handProjection.m00()/worldProjection.m00(),y=handProjection.m11()/worldProjection.m11();
        return new Matrix4f().translation(cameraOffset).mul(inverseWorldView).scale(depth*x,depth*y,depth).mul(item);
    }
}
