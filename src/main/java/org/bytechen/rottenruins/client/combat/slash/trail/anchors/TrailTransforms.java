package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TrailTransforms {
    private TrailTransforms() {}
    private record Affine(Quaternionf rotation,Matrix3f stretch) {}

    /** Polar decomposition retains projection/model shear as well as old rotation and scale. */
    public static Matrix4f interpolate(Matrix4f oldPose,Matrix4f pose,float t) {
        if(t<=0)return new Matrix4f(oldPose);
        if(t>=1)return new Matrix4f(pose);
        Affine a=decompose(oldPose),b=decompose(pose);
        if(a==null||b==null)return new Matrix4f(oldPose).lerp(pose,t);
        Vector3f position=oldPose.getTranslation(new Vector3f()).lerp(pose.getTranslation(new Vector3f()),t);
        Quaternionf rotation=new Quaternionf(a.rotation).slerp(b.rotation,t);
        Matrix3f stretch=new Matrix3f(a.stretch).lerp(b.stretch,t);
        return new Matrix4f().translation(position).rotate(rotation).mul(new Matrix4f(stretch));
    }
    private static Affine decompose(Matrix4f pose) {
        Matrix3f linear=pose.get3x3(new Matrix3f());
        float determinant=linear.determinant();
        if(!Float.isFinite(determinant)||Math.abs(determinant)<1e-8F)return null;
        Matrix3f orthogonal=new Matrix3f(linear);
        for(int i=0;i<16;i++) {
            Matrix3f next=new Matrix3f(orthogonal).add(new Matrix3f(orthogonal).invert().transpose()).scale(.5F);
            boolean converged=next.equals(orthogonal,1e-6F);
            orthogonal=next;if(converged)break;
        }
        // Keep reflection in the stretch matrix, so the quaternion remains a proper rotation.
        if(orthogonal.determinant()<0)orthogonal.scale(-1,1,1);
        Matrix3f stretch=new Matrix3f(orthogonal).transpose().mul(linear);
        return new Affine(new Quaternionf().setFromNormalized(orthogonal).normalize(),stretch);
    }
}
