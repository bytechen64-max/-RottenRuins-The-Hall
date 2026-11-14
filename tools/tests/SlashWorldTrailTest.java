import org.bytechen.rottenruins.client.combat.slash.trail.SlashTrailGeometry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SlashWorldTrailTest {
    public static void main(String[] args) {
        for (float yaw : new float[] {-130,0,75}) for (float pitch : new float[] {-70,0,60})
            for (int pose=0;pose<4;pose++) {
                double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
                Vector3f forward=new Vector3f((float)(-Math.sin(y)*Math.cos(p)),(float)-Math.sin(p),(float)(Math.cos(y)*Math.cos(p)));
                Vector3f right=new Vector3f((float)-Math.cos(y),0,(float)-Math.sin(y));
                Vector3f up=new Vector3f(right).cross(forward).normalize();
                float dx=pose==0?-1:pose==1?1:pose==2?-(float)Math.sqrt(.5):(float)Math.sqrt(.5);
                float dy=pose<2?0:-(float)Math.sqrt(.5);
                Vector3f direction=new Vector3f(right).mul(dx).fma(dy,up);
                Vector3f normal=new Vector3f(forward).cross(direction).normalize();
                var frame=SlashTrailGeometry.frame(forward,direction);
                Vector3f center=new Vector3f(forward).mul(.25F);
                for (float radius:new float[]{1,2,2.5F}) for(int i=0;i<=40;i++)
                    for(float width:new float[]{-.24F,0,.24F}) {
                        double angle=Math.toRadians(-58+116*i/40.0);
                        Vector3f point=SlashTrailGeometry.point(frame,angle,radius,width);
                        near(point.dot(normal),0,"no downward/sideways normal displacement");
                        near(new Vector3f(point).sub(center).length(),radius+width,"original radial ribbon geometry");
                        if(pose<2)near(point.dot(up),0,"horizontal trail stays at aim height");
                    }
                Vector3f mid=SlashTrailGeometry.point(frame,0,2,0);
                near(mid.distance(new Vector3f(forward).mul(2.25F)),0,"original forward offset only");
                Vector3f tiny=SlashTrailGeometry.point(frame,0,.1F,-.5F);
                near(tiny.distance(center),0,"negative inner radius clamps to zero");
                for(float aspect:new float[]{1.6F,16F/9,21F/9})for(float fov:new float[]{40,70,110}) {
                    Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.05F,100);
                    Matrix4f owner=new Matrix4f(projection).lookAt(new Vector3f(),forward,up);
                    Vector3f ownMid=owner.transformProject(new Vector3f(mid));
                    near(ownMid.x,0,"center has no side bias");near(ownMid.y,0,"center has no downward bias");
                    Vector3f observer=new Vector3f(normal).mul(.8F).fma(-.3F,forward);
                    Matrix4f moved=new Matrix4f(projection).lookAt(observer,mid,up);
                    Vector3f inner=moved.transformProject(SlashTrailGeometry.point(frame,0,2,-.24F));
                    Vector3f outer=moved.transformProject(SlashTrailGeometry.point(frame,0,2,.24F));
                    check(outer.distance(inner)>.01F,"moving off the cutting plane reveals its real width");
                }
            }
        System.out.println("SlashWorldTrailTest passed: original cutting plane/radii, zero normal bias, natural off-plane visibility");
    }
    private static void near(float a,float b,String message){check(Math.abs(a-b)<.0002F,message);}
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
