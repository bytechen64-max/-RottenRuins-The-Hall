package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

public final class TrailSampling {
    private TrailSampling(){}
    /** World-distance coverage plus a per-tick floor, independent of the number of render passes. */
    public static int steps(Matrix4f oldPose,Matrix4f pose,List<Vector3f> anchors,double ticks,int quality){
        float distance=0;
        for(Vector3f anchor:anchors){
            Vector3f point=new Vector3f(anchor).div(16);
            distance=Math.max(distance,oldPose.transformPosition(new Vector3f(point)).distance(pose.transformPosition(point)));
        }
        return Math.max(1,Math.min(32,(int)Math.ceil(Math.max(ticks*quality,distance/.075))));
    }
    public static float edgeFade(double time,double first,double last){
        double span=last-first;if(span<1e-6)return 0;
        float u=(float)Math.max(0,Math.min(1,(time-first)/span));
        float tail=Math.min(1,u/.14F),head=Math.min(1,(1-u)/.08F);
        return tail*tail*(3-2*tail)*head*head*(3-2*head);
    }
}
