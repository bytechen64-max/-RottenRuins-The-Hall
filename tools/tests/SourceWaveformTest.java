import org.bytechen.rottenruins.overworld.energy.ElementBand;
import org.bytechen.rottenruins.overworld.energy.SourceWaveform;

public final class SourceWaveformTest {
    public static void main(String[] args) {
        for(ElementBand band:ElementBand.values()) {
            int[] preset=band.preset();
            float[] points=SourceWaveform.points(preset[0],preset[1],preset[2],preset[3]!=0,2.0,256,256,44);
            for(int i=0;i<points.length;i+=2) {
                check(Float.isFinite(points[i]) && Float.isFinite(points[i+1]),"finite samples");
                check(points[i]>=0 && points[i]<=256 && points[i+1]>=3 && points[i+1]<=41,"within scope");
            }
            for(float scale:new float[]{1,2,4}) {
                float[] mesh=SourceWaveform.mesh(points,0.6F,1/scale,0xFF000000|band.color);
                check(mesh.length==255*18*6,"triangle count");
                boolean clear=false,solid=false;
                for(int i=0;i<mesh.length;i++) check(Float.isFinite(mesh[i]),"finite vertices");
                for(int i=5;i<mesh.length;i+=6) { clear|=mesh[i]==0; solid|=mesh[i]==1; }
                check(clear && solid,"AA fringe and opaque core");
            }
        }
        System.out.println("PASS: seven waveform meshes, scope bounds and anti-alias fringe at GUI scales 1/2/4");
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
