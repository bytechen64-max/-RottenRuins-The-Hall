package org.bytechen.rottenruins.core.worldgen;

/** Signed terrain density, positive inside rock. Pure Java; sampling is supplied by the version adapter. */
public final class SurrealTerrain {
    public static final int HEIGHT = 768;
    public static final double SHATTERED_END = -0.35;
    public static final double FOLDED_END = -0.05;
    public static final double PIERCED_END = 0.25;
    @FunctionalInterface public interface CellNoise { double sample(int x, int z); }
    private SurrealTerrain() {}

    public static double density(int x, int y, int z, double original, double continent,
                                 double region, CellNoise noise) {
        double land = smooth((continent - 0.05) / 0.13);
        if (land == 0) return original;
        double value;
        // Adjacent formulas overlap over a 0.10-wide climate band, without chunk-edge switches.
        if (region < SHATTERED_END - 0.05) value = shattered(x, y, z, noise);
        else if (region < SHATTERED_END + 0.05) value = mix(shattered(x,y,z,noise), folded(x,y,z,noise), smooth((region-SHATTERED_END+0.05)/0.10));
        else if (region < FOLDED_END - 0.05) value = folded(x,y,z,noise);
        else if (region < FOLDED_END + 0.05) value = mix(folded(x,y,z,noise), pierced(x,y,z,noise), smooth((region-FOLDED_END+0.05)/0.10));
        else if (region < PIERCED_END - 0.05) value = pierced(x,y,z,noise);
        else if (region < PIERCED_END + 0.05) value = mix(pierced(x,y,z,noise), original, smooth((region-PIERCED_END+0.05)/0.10));
        else value = original;
        // Keep solid footing near the bottom and leave ample clear sky above the highest islands.
        value = Math.max(value, (8-y)/16.0);
        value = Math.min(value, (720-y)/16.0);
        return mix(original, value, land);
    }

    public static double shattered(int x, int y, int z, CellNoise noise) {
        double result = (80-y)/16.0;
        int cx = Math.floorDiv(x,192), cz = Math.floorDiv(z,192);
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
            int gx=cx+dx, gz=cz+dz;
            long salt=Double.doubleToLongBits(noise.sample(gx*192+96,gz*192+96));
            for (int layer=0;layer<3;layer++) {
                long h=hash(gx,layer,gz,salt);
                double px=gx*192+96+random(h,1)*96-48;
                double pz=gz*192+96+random(h,2)*96-48;
                double py=208+layer*168+random(h,3)*64-32;
                double half=40+random(h,4)*34;
                if (Math.abs(y-py)>130) continue;
                double yaw=random(h,5)*Math.PI*2, tilt=0.25+random(h,6)*0.65, roll=(random(h,7)-0.5)*0.8;
                double a=x-px,b=y-py,c=z-pz;
                double u=Math.cos(yaw)*a+Math.sin(yaw)*c;
                double v=-Math.sin(yaw)*a+Math.cos(yaw)*c;
                double w=Math.cos(tilt)*b+Math.sin(tilt)*v;
                double q=-Math.sin(tilt)*b+Math.cos(tilt)*v;
                double localX=Math.cos(roll)*u+Math.sin(roll)*w;
                double localY=-Math.sin(roll)*u+Math.cos(roll)*w;
                result=Math.max(result, Math.min(half-Math.abs(localX),Math.min(half-Math.abs(localY),half-Math.abs(q)))/16);
            }
        }
        return result;
    }

    public static double folded(int x, int y, int z, CellNoise noise) {
        double axis=x*0.82+z*0.57+48*Math.sin(z/410.0);
        double u=axis-800*Math.floor(axis/800);
        double v=y-noise.sample(x,z)*6;
        // A thick sheet folds upward, leans backward, then returns to the ground.
        // The overhanging return is a real second surface, not just a taller heightmap ridge.
        double distance=Math.min(segment(u,v,0,150,240,150),segment(u,v,240,150,360,500));
        distance=Math.min(distance,segment(u,v,360,500,300,570));
        distance=Math.min(distance,segment(u,v,300,570,500,570));
        distance=Math.min(distance,segment(u,v,500,570,620,150));
        distance=Math.min(distance,segment(u,v,620,150,800,150));
        return Math.max((128-y)/16.0,(30-distance)/16);
    }

    private static double segment(double x,double y,double ax,double ay,double bx,double by) {
        double dx=bx-ax,dy=by-ay;
        double t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(x-ax-t*dx,y-ay-t*dy);
    }

    public static double pierced(int x, int y, int z, CellNoise noise) {
        double result=(340+35*Math.sin(x/430.0)+30*Math.sin(z/370.0)+noise.sample(x,z)*16-y)/16;
        int cx=Math.floorDiv(x,256),cz=Math.floorDiv(z,256);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            int gx=cx+dx,gz=cz+dz;
            long salt=Double.doubleToLongBits(noise.sample(gx*256+128,gz*256+128));
            for(int layer=0;layer<2;layer++) {
                long h=hash(gx,layer,gz,salt);
                double px=gx*256+128+random(h,1)*64-32;
                double pz=gz*256+128+random(h,2)*64-32;
                double py=136+layer*148+random(h,3)*48-24;
                double r=82+random(h,4)*34;
                double a=x-px,b=y-py,c=z-pz;
                result=Math.min(result,(Math.sqrt(a*a+b*b+c*c)-r)/16);
            }
        }
        return result;
    }

    private static long hash(int x,int y,int z,long seed) {
        long h=seed ^ x*341873128712L ^ y*132897987541L ^ z*42317861L;
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L; h=(h^(h>>>27))*0x94d049bb133111ebL;
        return h^(h>>>31);
    }
    private static double random(long h,int channel) { return (hash(channel,0,0,h)>>>11)*0x1.0p-53; }
    private static double smooth(double t) { t=Math.max(0,Math.min(1,t));return t*t*(3-2*t); }
    private static double mix(double a,double b,double t) {return a+(b-a)*t;}
}
