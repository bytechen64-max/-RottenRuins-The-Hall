import org.bytechen.rottenruins.client.rend.slash.SlashPoseFrame;
import org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailSampling;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

public final class SlashReferenceTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(boolean left:new boolean[]{false,true})for(float angle:new float[]{-.9F,0,.9F}){
            var display=new Matrix4f().translate(.1F,.3F,-.2F).rotateXYZ(.3F,angle,.6F).scale(left?-.8F:.8F,1.2F,.5F);
            var frame=SlashPoseFrame.of(new Vector3f(.3F,-.2F,-.85F),new Quaternionf().rotateXYZ(.4F,angle,.7F),new Vector3f(1.02F,1.02F,.68F),new Vector3f(.1F,-.3F,0));
            Matrix4f rendered=SlashPoseFrame.modelPose(frame.itemPose(display),display);
            check(rendered.equals(frame.modelPose(),1e-5F),"Weapon vertices and anchor vertices share exactly one solved pose");
            for(Vector3f point:List.of(new Vector3f(0,0,.5F),new Vector3f(.9F,1,.5F)))
                check(rendered.transformPosition(new Vector3f(point)).distance(frame.modelPose().transformPosition(new Vector3f(point)))<1e-5F,"Blade and trail alignment");
        }
        var anchors=List.of(new Vector3f(2,3,8),new Vector3f(14,15,8));
        Matrix4f start=new Matrix4f();int small=TrailSampling.steps(start,new Matrix4f().translate(.02F,0,0),anchors,.1,4);
        int fast=TrailSampling.steps(start,new Matrix4f().translate(1.2F,0,0),anchors,.1,4);
        check(fast>small&&fast>=16,"Fast travel receives more samples than slow travel");
        check(TrailSampling.steps(start,new Matrix4f().translate(100,0,0),anchors,2,4)==32,"Sampling budget is bounded");
        check(TrailSampling.steps(start,start,anchors,1,4)>=4,"Per-tick temporal floor");
        check(TrailSampling.edgeFade(0,0,1)==0&&TrailSampling.edgeFade(1,0,1)==0,"Both ribbon ends fade");
        check(TrailSampling.edgeFade(.5,0,1)==1&&Float.isFinite(TrailSampling.edgeFade(1,1,1)),"Middle stays visible, degenerate span is safe");
        System.out.println("PASS: shared visual pose, handedness/display transforms, adaptive density, budget and head/tail envelopes");
    }
}
