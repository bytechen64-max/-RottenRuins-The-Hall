package org.bytechen.rottenruins.client.rend.slash;

import java.util.List;
import java.util.Optional;

/** Principal axes of the actual model vertices; no dependency on texture orientation. */
public final class BladeGeometry {
    public record Vec(double x, double y, double z) {
        public Vec add(Vec b) { return new Vec(x + b.x, y + b.y, z + b.z); }
        public Vec mul(double s) { return new Vec(x * s, y * s, z * s); }
        public double dot(Vec b) { return x * b.x + y * b.y + z * b.z; }
        public Vec cross(Vec b) { return new Vec(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
    }
    public record Profile(Vec edge, Vec blade, Vec normal, Vec grip, double radius) {}

    private BladeGeometry() {}

    /** Input vertices are centered and scaled, deduplicated by the model adapter. */
    public static Optional<Profile> analyze(List<Vec> points) {
        return analyze(points, true);
    }

    /** Broad-plane detection also accepts discs and boomerangs without a dominant long axis. */
    public static Optional<Profile> analyzeFlat(List<Vec> points) {
        return analyze(points, false);
    }

    private static Optional<Profile> analyze(List<Vec> points, boolean requireLongAxis) {
        if (points.size() < 6) return Optional.empty();
        Vec center = new Vec(0, 0, 0);
        for (Vec p : points) {
            if (!Double.isFinite(p.x + p.y + p.z)) return Optional.empty();
            center = center.add(p);
        }
        center = center.mul(1.0 / points.size());
        double[][] covariance = new double[3][3];
        for (Vec p : points) {
            double[] d = {p.x - center.x, p.y - center.y, p.z - center.z};
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) covariance[i][j] += d[i] * d[j];
        }
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        // Jacobi rotations for a real symmetric 3x3 covariance matrix.
        for (int iteration = 0; iteration < 32; iteration++) {
            int p = 0, q = 1;
            for (int i = 0; i < 3; i++) for (int j = i + 1; j < 3; j++)
                if (Math.abs(covariance[i][j]) > Math.abs(covariance[p][q])) { p = i; q = j; }
            if (Math.abs(covariance[p][q]) < 1.0e-12) break;
            double angle = 0.5 * Math.atan2(2 * covariance[p][q], covariance[q][q] - covariance[p][p]);
            double c = Math.cos(angle), s = Math.sin(angle);
            double pp = covariance[p][p], qq = covariance[q][q], pq = covariance[p][q];
            for (int i = 0; i < 3; i++) if (i != p && i != q) {
                double ip = covariance[i][p], iq = covariance[i][q];
                covariance[i][p] = covariance[p][i] = c * ip - s * iq;
                covariance[i][q] = covariance[q][i] = s * ip + c * iq;
            }
            covariance[p][p] = c * c * pp - 2 * s * c * pq + s * s * qq;
            covariance[q][q] = s * s * pp + 2 * s * c * pq + c * c * qq;
            covariance[p][q] = covariance[q][p] = 0;
            for (int i = 0; i < 3; i++) {
                double ip = axes[i][p], iq = axes[i][q];
                axes[i][p] = c * ip - s * iq;
                axes[i][q] = s * ip + c * iq;
            }
        }
        int thin = 0, longest = 0;
        for (int i = 1; i < 3; i++) {
            if (covariance[i][i] < covariance[thin][thin]) thin = i;
            if (covariance[i][i] > covariance[longest][longest]) longest = i;
        }
        if (thin == longest) return Optional.empty();
        int middle = 3 - thin - longest;
        // Reject cubes, discs, nearly cylindrical rods, and degenerate geometry.
        if (covariance[middle][middle] < 1.0e-10
                || (requireLongAxis && covariance[longest][longest] < covariance[middle][middle] * 1.45)
                || covariance[thin][thin] > covariance[middle][middle] * 0.35) return Optional.empty();
        Vec blade = positive(new Vec(axes[0][longest], axes[1][longest], axes[2][longest]));
        Vec normal = positive(new Vec(axes[0][thin], axes[1][thin], axes[2][thin]));
        Vec edge = blade.cross(normal);
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (Vec point : points) {
            double along = point.add(center.mul(-1)).dot(blade);
            low = Math.min(low, along); high = Math.max(high, along);
        }
        // Geometry determines axes, not hilt semantics. Prefer the conventional
        // positive model axis for the tip and a point near the opposite end for grip.
        Vec grip = center.add(blade.mul(low + (high - low) * 0.16));
        double radiusSquared = 0;
        for (Vec point : points) {
            Vec offset = point.add(grip.mul(-1));
            radiusSquared = Math.max(radiusSquared, offset.dot(offset));
        }
        return Optional.of(new Profile(edge, blade, normal, grip, Math.sqrt(radiusSquared)));
    }

    private static Vec positive(Vec v) {
        double main = Math.abs(v.y) > 0.2 ? v.y : Math.abs(v.x) > 0.2 ? v.x : v.z;
        return main < 0 ? v.mul(-1) : v;
    }
}
