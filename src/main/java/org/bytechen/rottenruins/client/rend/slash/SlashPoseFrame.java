package org.bytechen.rottenruins.client.rend.slash;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One authoritative visual pose: item rendering and authored trail anchors consume this frame. */
public final class SlashPoseFrame {
    private final Matrix4f centered;
    private SlashPoseFrame(Matrix4f centered){this.centered=centered;}
    public static SlashPoseFrame of(Vector3f grip,Quaternionf rotation,Vector3f scale,Vector3f modelGrip){
        return new SlashPoseFrame(new Matrix4f().translation(grip).rotate(rotation).scale(scale)
                .translate(-modelGrip.x,-modelGrip.y,-modelGrip.z));
    }
    public Matrix4f itemPose(Matrix4f display){return new Matrix4f(centered).mul(new Matrix4f(display).invert());}
    public Matrix4f modelPose(){return new Matrix4f(centered).translate(-.5F,-.5F,-.5F);}
    public static Matrix4f modelPose(Matrix4f transformedItem,Matrix4f display){
        return new Matrix4f(transformedItem).mul(display).translate(-.5F,-.5F,-.5F);
    }
}
