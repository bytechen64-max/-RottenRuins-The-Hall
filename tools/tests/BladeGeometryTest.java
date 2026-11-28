import org.bytechen.rottenruins.client.rend.slash.BladeGeometry;
import org.bytechen.rottenruins.client.rend.slash.BladeGeometry.Vec;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class BladeGeometryTest {
    public static void main(String[] args) throws Exception {
        Vec blade = new Vec(0.6, 0.8, 0), normal = new Vec(0, 0, 1);
        checkAxes(box(blade, normal, 1, 0.14, 0.015), blade, normal);
        normal = new Vec(0.3, -0.4, Math.sqrt(0.75));
        blade = new Vec(0.8, 0.6, 0);
        checkAxes(box(blade, normal, 1, 0.15, 0.02), blade, normal);
        check(BladeGeometry.analyze(box(new Vec(0, 1, 0), new Vec(0, 0, 1), 1, 1, 1)).isEmpty(), "cube rejected");
        check(BladeGeometry.analyze(box(new Vec(0, 1, 0), new Vec(0, 0, 1), 1, 0.02, 0.02)).isEmpty(), "round-section rod rejected");
        for (String texture : new String[] {"domerite_sword", "void_sword", "domerite_longsword", "crude_sword"}) {
            var file = new File("src/main/resources/assets/rotten_ruins/textures/item/" + texture + ".png");
            if (!file.exists()) continue;
            var image = ImageIO.read(file);
            var points = new ArrayList<Vec>();
            // Sample opaque silhouette boundary vertices as produced by generated item side quads.
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                boolean boundary = x == 0 || y == 0 || x == image.getWidth() - 1 || y == image.getHeight() - 1
                        || (image.getRGB(Math.max(0, x - 1), y) >>> 24) == 0
                        || (image.getRGB(Math.min(image.getWidth() - 1, x + 1), y) >>> 24) == 0
                        || (image.getRGB(x, Math.max(0, y - 1)) >>> 24) == 0
                        || (image.getRGB(x, Math.min(image.getHeight() - 1, y + 1)) >>> 24) == 0;
                if (boundary) for (double z : new double[] {-0.03125, 0.03125})
                    points.add(new Vec((x + 0.5) / image.getWidth() - 0.5, 0.5 - (y + 0.5) / image.getHeight(), z));
            }
            // Front/back generated quads also contribute full texture rectangle corners.
            for (double x : new double[] {-0.5, 0.5}) for (double y : new double[] {-0.5, 0.5})
                for (double z : new double[] {-0.03125, 0.03125}) points.add(new Vec(x, y, z));
            var profile = BladeGeometry.analyze(points).orElseThrow(() -> new AssertionError(texture + " not detected"));
            check(Math.abs(profile.normal().z()) > 0.99, texture + " broad face detected");
            check(profile.blade().dot(new Vec(Math.sqrt(0.5), Math.sqrt(0.5), 0)) > 0.85, texture + " diagonal blade detected");
        }
        System.out.println("BladeGeometryTest passed: rotated 3D blades, ambiguous shapes, project texture silhouettes");
    }
    private static List<Vec> box(Vec blade, Vec normal, double length, double width, double thickness) {
        var points = new ArrayList<Vec>();
        Vec edge = blade.cross(normal), offset = new Vec(0.3, -0.2, 0.4);
        for (int i : new int[] {-1, 1}) for (int j : new int[] {-1, 1}) for (int k : new int[] {-1, 1})
            points.add(offset.add(blade.mul(i * length)).add(edge.mul(j * width)).add(normal.mul(k * thickness)));
        return points;
    }
    private static void checkAxes(List<Vec> points, Vec blade, Vec normal) {
        var p = BladeGeometry.analyze(points).orElseThrow();
        check(Math.abs(p.blade().dot(blade)) > 0.9999, "long axis");
        check(Math.abs(p.normal().dot(normal)) > 0.9999, "thin axis = broad face normal");
        check(Math.abs(p.edge().dot(blade)) < 0.0001 && Math.abs(p.edge().dot(normal)) < 0.0001, "edge axis");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
