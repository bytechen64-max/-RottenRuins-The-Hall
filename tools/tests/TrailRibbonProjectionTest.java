import org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailRibbonGeometry;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
public final class TrailRibbonProjectionTest {
 static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
 public static void main(String[] args){
  for(float aspect:new float[]{4F/3,16F/9,21F/9,9F/16})for(float fov:new float[]{40,70,110})for(int direction=0;direction<4;direction++){
   float dx=direction==0?-1:direction==1?1:direction==2?-.7071F:.7071F,dy=direction<2?0:-.7071F;
   var current=List.of(new Point(dx*.25,dy*.25,-.85),new Point(dx*.25,dy*.25,-1.85));
   var old=List.of(new Point(dx*.20,dy*.20,-.85),new Point(dx*.20,dy*.20,-1.85));
   var section=TrailRibbonGeometry.section(current,old,true);
   check(section.get(0).equals(current.get(0))&&section.get(section.size()-1).equals(current.get(1)),"Authored endpoints preserved");
   Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.05F,100);
   Vector3f start=project(projection,section.get(0)),tip=project(projection,section.get(section.size()-1));float maxArea=0;
   for(var p:section){Vector3f q=project(projection,p);check(Float.isFinite(q.x+q.y+q.z),"Finite projected position");maxArea=Math.max(maxArea,Math.abs((tip.x-start.x)*(q.y-start.y)-(tip.y-start.y)*(q.x-start.x)));}
   check(maxArea>.0001F,"Edge-on diagonal/horizontal must retain projected area");
  }
  check(TrailRibbonGeometry.fade(0,7)==1&&TrailRibbonGeometry.fade(7,7)==0,"Fade endpoints");
  float prior=1;for(int i=0;i<=100;i++){float alpha=TrailRibbonGeometry.fade(i*.07,7);check(alpha<=prior,"Monotone age fade");prior=alpha;}
  System.out.println("PASS: four directions, 4 aspect ratios, 3 FOVs, exact endpoints, nonzero projected area, monotone lifetime fade");
 }
 static Vector3f project(Matrix4f p,Point q){return p.transformProject(new Vector3f((float)q.x(),(float)q.y(),(float)q.z()));}
}
