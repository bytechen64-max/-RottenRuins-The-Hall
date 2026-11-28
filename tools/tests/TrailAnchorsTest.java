import org.bytechen.rottenruins.core.combat.slash.TrailHistory;
import org.bytechen.rottenruins.client.combat.slash.trail.anchors.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.google.gson.*;
import java.util.List;
import java.nio.file.*;

public class TrailAnchorsTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static List<TrailHistory.Point> points(double x){return List.of(new TrailHistory.Point(x,0,0),new TrailHistory.Point(x,1,0));}
    public static void main(String[] args)throws Exception{
        TrailHistory h=new TrailHistory();check(h.append(0,points(0),4,8),"initial");
        check(!h.append(0,points(1),4,8),"outline duplicate");h.append(1,points(1),4,8);check(h.samples().size()==2,"continuous");
        h.append(2,points(20),4,8);check(h.samples().size()==1,"teleport breaks ribbon");
        h.append(6,points(21),4,8);check(h.samples().size()==1,"frame gap breaks ribbon");
        for(int i=1;i<30;i++)h.append(6+i*.1,points(21+i*.01),4,8);check(h.samples().size()==8,"bounded history");
        h.prune(100,7);check(h.samples().isEmpty(),"age expiration");
        check(!h.append(101,points(Double.NaN),4,8),"NaN rejected");
        Matrix4f a=new Matrix4f(),b=new Matrix4f().rotateZ((float)Math.PI).translate(1,0,0);
        Matrix4f mid=TrailTransforms.interpolate(a,b,.5F);check(Math.abs(mid.getScale(new Vector3f()).x-1)<1e-5,"slerp does not shrink blade");
        check(TrailTransforms.interpolate(a,b,1).equals(b,1e-6F),"exact endpoint");
        Matrix4f mirrored=new Matrix4f().rotateY(.4F).scale(-2,3,4);
        Matrix4f mirrored2=new Matrix4f().translate(1,2,3).rotateY(.8F).scale(-2,3,4);
        check(TrailTransforms.interpolate(mirrored,mirrored2,.5F).determinant()<0,"mirrored hand preserves handedness");
        String minimal="{\"points\":[[4,4,8],[14,14,8]]}";
        var profile=AnchorProfile.parse(JsonParser.parseString(minimal).getAsJsonObject());check(profile.firstPerson().points().size()==2,"root fallback");
        for(String name:List.of("true_copper","domerite_sword"))AnchorProfile.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/rotten_ruins/slash_anchors/"+name+".json"))).getAsJsonObject());
        for(String bad:List.of("{\"points\":[[1,2,3]]}","{\"points\":[[1,2,3],[4,5,6]],\"firstPerson\":{\"start\":0.8,\"end\":0.2}}")){
            boolean rejected=false;try{AnchorProfile.parse(JsonParser.parseString(bad).getAsJsonObject());}catch(Exception e){rejected=true;}check(rejected,"malformed profile rejected");
        }
        System.out.println("PASS: trail history, duplicates, teleport, gaps, lifetime, bounded sampling, quaternion interpolation, mirrored transforms, profile validation");
    }
}
