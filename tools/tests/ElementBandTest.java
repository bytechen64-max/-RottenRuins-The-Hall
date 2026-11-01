import org.bytechen.rottenruins.overworld.energy.ElementBand;

public final class ElementBandTest {
    public static void main(String[] args) {
        for (ElementBand band:ElementBand.values()) {
            int[] preset=band.preset();
            check(ElementBand.match(preset[0],preset[1],preset[2],preset[3]!=0)==band,"quick tune preset: "+band);
            check(preset[3]==(band==ElementBand.VOID?1:0),"automatic preset phase: "+band);
            check(ElementBand.match(band.minFc,band.minB,band.minG,band.negative)==band,"lower inclusive boundary: "+band);
            check(ElementBand.match(band.maxFc,band.maxB,band.maxG,band.negative)==band,"upper inclusive boundary: "+band);
            check(ElementBand.match((band.minFc+band.maxFc)/2,(band.minB+band.maxB)/2,(band.minG+band.maxG)/2,band.negative)==band,"midpoint: "+band);
            check(ElementBand.match((band.minFc+band.maxFc)/2,(band.minB+band.maxB)/2,(band.minG+band.maxG)/2,!band.negative)!=band,"wrong phase: "+band);
        }
        check(ElementBand.match(55,45,45,false)==ElementBand.WATER,"default water settings");
        check(ElementBand.match(5,65,20,false)==null,"void must be inverted");
        check(ElementBand.match(5,90,0,false)==ElementBand.MYSTERIOUS,"zero gain mysterious window");
        check(ElementBand.match(45,45,45,false)==null,"gap between frequency windows");
        check(ElementBand.match(105,15,25,false)==null,"outside parameter range");
        System.out.println("PASS: seven element windows, boundaries, gaps, phase and zero-gain mysterious band");
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
