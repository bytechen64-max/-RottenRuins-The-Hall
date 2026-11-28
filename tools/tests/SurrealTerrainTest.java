import org.bytechen.rottenruins.core.worldgen.SurrealTerrain;
import java.nio.file.*;
import java.io.*;

/** Standalone JDK checks; no Minecraft world or server. */
public final class SurrealTerrainTest {
    private static final SurrealTerrain.CellNoise NOISE=(x,z)->Math.sin(x*0.031+2)*Math.cos(z*0.017-3);
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        int layeredColumns=0,holes=0,seedChanges=0,foldOverhangs=0;
        for(int x=-768;x<768;x+=16) {
            int starts=0;boolean solid=false;
            for(int y=96;y<720;y+=4) {
                double d=SurrealTerrain.shattered(x,y,96,NOISE);
                if(d>0&&!solid)starts++;
                solid=d>0;
                double alternate=SurrealTerrain.shattered(x,y,96,(cx,cz)->Math.sin(cx*0.031+8)*Math.cos(cz*0.017));
                if((d>0)!=(alternate>0))seedChanges++;
                double p=SurrealTerrain.pierced(x,y,128,NOISE);
                if(y>100&&y<300&&p<0)holes++;
                check(Double.isFinite(d)&&Double.isFinite(p),"finite density at negative coordinates");
            }
            if(starts>=2)layeredColumns++;
            boolean gap=false;
            for(int y=160;y<640;y+=4) {
                double d=SurrealTerrain.folded(x,y,96,NOISE);
                if(d<0)gap=true;
                if(gap&&d>0) {foldOverhangs++;break;}
            }
            for(int y=0;y<768;y+=16) for(double region:new double[]{-.7,-.2,.1,.6}) {
                double original=(128-y)/16.0;
                check(SurrealTerrain.density(x,y,0,original,-.2,region,NOISE)==original,"ocean must retain original terrain");
                double density=SurrealTerrain.density(x,y,0,original,.5,region,NOISE);
                check(Double.isFinite(density),"finite biome mix");
                if(y>=720)check(density<0,"top boundary must remain empty");
            }
        }
        check(foldOverhangs>10,"folded sheets must overhang air");
        check(layeredColumns>10,"multiple separated island layers");
        check(holes>100,"large interior cavities");check(seedChanges>100,"world-noise-dependent layout");
        for(int x=-1024;x<1024;x++) {
            double a=SurrealTerrain.folded(x,240,0,NOISE),b=SurrealTerrain.folded(x+1,240,0,NOISE);
            check(Math.abs(a-b)<0.2,"fold surface discontinuity");
            for(double edge:new double[]{-.40,-.30,-.10,0,.20,.30}) {
                double a1=SurrealTerrain.density(x,230,0,-5,.5,edge-1e-7,NOISE);
                double b1=SurrealTerrain.density(x,230,0,-5,.5,edge+1e-7,NOISE);
                check(Math.abs(a1-b1)<0.001,"region seam");
            }
        }
        if(args.length>0) {
            Path dir=Path.of(args[0]);Files.createDirectories(dir);
            for(int kind=0;kind<3;kind++)try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(dir.resolve("section"+kind+".bin")))) {
                for(int y=0;y<768;y+=4)for(int x=-960;x<960;x+=4) {
                    double d=switch(kind){case 0->SurrealTerrain.shattered(x,y,96,NOISE);case 1->SurrealTerrain.folded(x,y,96,NOISE);default->SurrealTerrain.pierced(x,y,128,NOISE);};
                    out.writeByte(d>0?1:0);
                }
            }
        }
        System.out.println("PASS: layered islands, seed variation, internal holes, ocean preservation, empty sky, continuous folds and region boundaries");
    }
}
