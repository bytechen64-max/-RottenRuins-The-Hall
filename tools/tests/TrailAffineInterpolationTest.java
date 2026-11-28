import org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailTransforms;
import org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailRibbonGeometry;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class TrailAffineInterpolationTest {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    static Vector3f tip(Matrix4f matrix){return matrix.transformPosition(new Vector3f(.85F,.9F,.5F));}
    public static void main(String[] args){
        for(float fovScale:new float[]{.7F,1.0F,1.4F})for(boolean left:new boolean[]{false,true}) {
            Matrix4f shape=new Matrix4f().scale(fovScale,fovScale,1).rotateZ(.7F).rotateY(.9F).scale(left?-1.02F:1.02F,1.02F,.68F);
            Matrix4f a=new Matrix4f(shape).setTranslation(-.4F,.3F,-1.5F);
            Matrix4f b=new Matrix4f(shape).setTranslation(.4F,-.3F,-1.5F);
            for(int i=1;i<16;i++) {
                float t=i/16F;
                Vector3f expected=tip(a).lerp(tip(b),t),actual=tip(TrailTransforms.interpolate(a,b,t));
                check(actual.distance(expected)<2e-5F,"Sheared projection must not deform intermediate anchors: scale="+fovScale+" left="+left+" error="+actual.distance(expected));
                check(tip(TrailTransforms.interpolate(a,a,t)).distance(tip(a))<2e-5F,"A stationary matrix must remain stationary");
            }
            Matrix4f rotated=new Matrix4f(shape).rotateX(.25F).setTranslation(.4F,-.3F,-1.5F);
            check(tip(TrailTransforms.interpolate(a,rotated,.99999F)).distance(tip(rotated))<.0001F,"Last synthetic sample must converge to exact real frame without a snap");
        }
        List<TrailHistory.Sample> samples=new ArrayList<>();
        double[] jitter={0,.3,.1,-.1,.2,.3,.3,.3};
        for(int i=0;i<jitter.length;i++)samples.add(new TrailHistory.Sample(i*.1,List.of(new TrailHistory.Point(jitter[i],0,-1),new TrailHistory.Point(jitter[i],0,-2))));
        var sections=TrailRibbonGeometry.sections(samples);
        for(int i=0;i<sections.size();i++) {
            var section=sections.get(i);
            check(section.get(0).equals(samples.get(i).points().get(0))&&section.get(8).equals(samples.get(i).points().get(1)),"Mesh keeps the actual authored endpoints");
            check(section.get(4).y()<-.02,"Ridge normal must not flip when a tiny motion reverses or stops");
        }
        System.out.println("PASS: projection shear, stationary and translating anchors, mirrored models, real-frame continuity and stable ribbon normals");
    }
}
