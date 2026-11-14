import org.bytechen.rottenruins.client.combat.slash.trail.anchors.*;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public class SlashEffectTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  for(String name:List.of("true_copper","domerite_sword","domerite_longsword")){
   var j=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/rotten_ruins/slash_effects/"+name+".json"))).getAsJsonObject();
   var effect=SlashEffect.parse(j);check(effect.width()>1.5F&&effect.opacity()>.8F,"Visible presets");
   var roundtrip=SlashEffect.parse(JsonParser.parseString(new Gson().toJson(effect)).getAsJsonObject());check(roundtrip.equals(effect),"Lossless JSON round trip");
  }
  for(String invalid:List.of("{\"opacity\":2}","{\"width\":0}","{\"renderer\":\"bad\"}","{\"color\":\"blue\"}","{\"texture\":\"rotten_ruins:../x\"}")){
   boolean rejected=false;try{SlashEffect.parse(JsonParser.parseString(invalid).getAsJsonObject());}catch(Exception e){rejected=true;}check(rejected,"Invalid effect rejected");
  }
  var anchors=List.of(new Point(0,0,-1),new Point(0,0,-2));var samples=List.of(new Sample(0,anchors),new Sample(1,List.of(new Point(1,0,-1),new Point(1,0,-2))));
  var base=TrailRibbonGeometry.sections(samples,1,.02F);var wide=TrailRibbonGeometry.sections(samples,2,.2F);
  check(base.get(0).get(8).distance(base.get(0).get(0))<wide.get(0).get(8).distance(wide.get(0).get(0)),"Width changes real geometry");
  check(Math.abs(wide.get(0).get(4).y())>Math.abs(base.get(0).get(4).y()),"Curvature changes physical geometry");
  check(wide.get(0).get(8).distance(anchors.get(1))<1e-6,"Widening keeps the outer tip attached to the blade");
  var arc=new ArrayList<Sample>();
  for(int i=0;i<=100;i++){double angle=Math.toRadians(-70+i*1.4);arc.add(new Sample(i*.01,List.of(new Point(0,0,0),new Point(Math.sin(angle),0,-Math.cos(angle)))));}
  var stable=TrailRibbonGeometry.sections(arc,1.8F,0,.85F);
  for(int i=0;i<stable.size();i++)check(stable.get(i).get(8).equals(arc.get(i).points().get(1)),"Stability keeps actual outer blade path");
  var first=stable.get(0);var last=stable.get(100);
  check(first.get(8).distance(last.get(8))>1.8,"Stability preserves broad sweep instead of averaging the fan flat");
  var legacy=SlashEffect.parse(new JsonObject());check(legacy.refraction()>0&&legacy.stability()>.5F,"Old JSON receives compatible visual defaults");
  System.out.println("PASS: effect presets, JSON roundtrip, validation and actual mesh width/curvature");
 }
}
