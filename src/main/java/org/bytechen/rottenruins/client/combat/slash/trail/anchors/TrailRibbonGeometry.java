package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory.Sample;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Slightly curved cross-section, with exact authored endpoints, rather than a billboard. */
public final class TrailRibbonGeometry {
    private TrailRibbonGeometry() {}
    public static List<Point> section(List<Point> anchors,List<Point> previous,boolean legacyFirstPerson) {
        Vector3f root=vec(anchors.get(0)),tip=vec(anchors.get(anchors.size()-1));
        Vector3f velocity=new Vector3f(tip).sub(vec(previous.get(previous.size()-1)));
        Vector3f blade=new Vector3f(tip).sub(root),normal=new Vector3f(blade).cross(velocity);
        return section(anchors,normal);
    }
    /** Centered tangents and consistent normal signs prevent adjacent sections folding inside out. */
    public static List<List<Point>> sections(List<Sample> samples) {
        return sections(samples,1,.025F);
    }
    public static List<List<Point>> sections(List<Sample> samples,float width,float curvature) {
        return sections(samples,width,curvature,0);
    }
    public static List<List<Point>> sections(List<Sample> samples,float width,float curvature,float stability) {
        List<List<Point>> mesh=new ArrayList<>();
        // One swept plane per swing avoids amplifying pose jitter into a corrugated sheet.
        Vector3f axis=new Vector3f(),plane=new Vector3f();
        for(int i=0;i<samples.size();i++) {
            var p=samples.get(i).points();Vector3f blade=vec(p.get(p.size()-1)).sub(vec(p.get(0)));
            if(blade.lengthSquared()>1e-10F)axis.add(new Vector3f(blade).normalize());
            var a=samples.get(Math.max(0,i-1)).points();var b=samples.get(Math.min(samples.size()-1,i+1)).points();
            Vector3f n=new Vector3f(blade).cross(vec(b.get(b.size()-1)).sub(vec(a.get(a.size()-1))));
            if(n.dot(plane)<0)n.negate();plane.add(n);
        }
        if(axis.lengthSquared()>1e-10F)axis.normalize();
        if(plane.lengthSquared()>1e-10F)plane.normalize();
        for(int i=0;i<samples.size();i++) {
            List<Point> anchors=samples.get(i).points();
            List<Point> expanded=new ArrayList<>();Vector3f tip=vec(anchors.get(anchors.size()-1));
            float length=tip.distance(vec(anchors.get(0)));
            // Smooth neighboring axes only: a global average would erase a wide 140-degree sweep.
            Vector3f localAxis=new Vector3f();
            for(int neighbor=Math.max(0,i-1);neighbor<=Math.min(samples.size()-1,i+1);neighbor++){
                var p=samples.get(neighbor).points();Vector3f direction=vec(p.get(p.size()-1)).sub(vec(p.get(0)));
                if(direction.lengthSquared()>1e-10F)localAxis.add(direction.normalize().mul(neighbor==i?2:1));
            }
            if(localAxis.lengthSquared()>1e-10F)localAxis.normalize();else localAxis.set(axis);
            for(int j=0;j<anchors.size();j++) {
                if(j==anchors.size()-1 || (width==1&&stability==0)){expanded.add(anchors.get(j));continue;}
                Vector3f delta=vec(anchors.get(j)).sub(tip);
                Vector3f stable=new Vector3f(localAxis).mul(-length*(1-j/(float)(anchors.size()-1)));
                delta.lerp(stable,stability).mul(width).add(tip);
                expanded.add(new Point(delta.x,delta.y,delta.z));
            }
            mesh.add(section(expanded,plane,curvature));
        }
        return mesh;
    }
    private static List<Point> section(List<Point> anchors,Vector3f normal) {
        return section(anchors,normal,.025F);
    }
    private static List<Point> section(List<Point> anchors,Vector3f normal,float curvature) {
        // A small physical ridge in the swept plane's normal. No camera-facing expansion.
        float bow=normal.lengthSquared()<1e-10F?0:curvature;
        Vector3f across=new Vector3f(normal);
        if(across.lengthSquared()<1e-10F)across.set(1,0,0);else across.normalize();
        List<Point> out=new ArrayList<>();
        for(int segment=0;segment<anchors.size()-1;segment++)for(int i=0;i<8;i++) {
            if(i==0){out.add(anchors.get(segment));continue;}
            float t=i/8F;
            Vector3f p=vec(anchors.get(segment)).lerp(vec(anchors.get(segment+1)),t)
                    .fma(bow*(float)java.lang.Math.sin((segment+t)/(anchors.size()-1)*java.lang.Math.PI),across);
            out.add(new Point(p.x,p.y,p.z));
        }
        out.add(anchors.get(anchors.size()-1));return out;
    }
    public static float fade(double age,double lifetime) {
        float t=(float)java.lang.Math.max(0,java.lang.Math.min(1,1-age/lifetime));
        return t*t*(3-2*t);
    }
    private static Vector3f vec(Point p){return new Vector3f((float)p.x(),(float)p.y(),(float)p.z());}
}
