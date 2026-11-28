import org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailWorldTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class TrailWorldTransformTest {
    static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
    public static void main(String[] args){
        for(float aspect:new float[]{4F/3,16F/9,21F/9})for(float worldFov:new float[]{50,75,110}) {
            Matrix4f hand=new Matrix4f().perspective((float)Math.toRadians(70),aspect,.05F,100);
            Matrix4f world=new Matrix4f().perspective((float)Math.toRadians(worldFov),aspect,.05F,100);
            Matrix4f view=new Matrix4f().rotateX(.3F).rotateY(.8F),inverse=new Matrix4f(view).invert();
            Vector3f offset=new Vector3f(2,4,-3);
            Matrix4f model=new Matrix4f().translation(.3F,-.2F,-.85F).rotateZ(.7F);
            Matrix4f placed=TrailWorldTransform.fromHand(model,hand,world,inverse,offset);
            Vector3f point=new Vector3f(.2F,.4F,-.7F);
            Vector3f expected=new Matrix4f(hand).mul(model).transformProject(new Vector3f(point));
            Vector3f born=placed.transformPosition(new Vector3f(point));
            Vector3f actual=new Matrix4f(world).mul(view).translate(-offset.x,-offset.y,-offset.z).transformProject(new Vector3f(born));
            check(Math.abs(actual.x-expected.x)<1e-5&&Math.abs(actual.y-expected.y)<1e-5,"Birth projection must match the actual hand under differing FOVs");
            Vector3f moved=new Matrix4f(world).mul(view).translate(-offset.x-.25F,-offset.y,-offset.z).transformProject(new Vector3f(born));
            check(Math.abs(moved.x-actual.x)>.01F,"Camera translation must produce parallax; old trail must not follow the screen");
            Vector3f turned=new Matrix4f(world).rotateY(.2F).mul(view).translate(-offset.x,-offset.y,-offset.z).transformProject(new Vector3f(born));
            check(Math.abs(turned.x-actual.x)>.01F,"Turning the view must move a historic world sample on screen");
        }
        System.out.println("PASS: hand/world FOV alignment, stable world placement, translation parallax and camera-turn independence");
    }
}
