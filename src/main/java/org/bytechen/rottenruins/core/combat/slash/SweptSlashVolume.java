package org.bytechen.rottenruins.core.combat.slash;

import java.util.ArrayList;
import java.util.List;

/** Annular sector extruded along its plane normal; SAT against actual axis-aligned boxes. */
public final class SweptSlashVolume {
    public record Vec(double x, double y, double z) {
        public Vec add(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        public Vec mul(double s) { return new Vec(x * s, y * s, z * s); }
        public double dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
        public Vec cross(Vec v) { return new Vec(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x); }
    }
    public record Box(Vec center, Vec halfSize) {
        public Box { if (halfSize.x < 0 || halfSize.y < 0 || halfSize.z < 0) throw new IllegalArgumentException("Negative box size"); }
    }
    private record Axis(Vec vector, double low, double high) {}
    private record Prism(List<Axis> axes) {}
    private static final Vec[] BOX_AXES = {new Vec(1, 0, 0), new Vec(0, 1, 0), new Vec(0, 0, 1)};
    private final List<Prism> slices = new ArrayList<>();

    /** Coordinates are relative to the swing origin. forward and direction must be orthonormal. */
    public SweptSlashVolume(Vec forward, Vec direction, double reach, double innerReach, double arcDegrees, double halfThickness) {
        if (!Double.isFinite(reach + innerReach + arcDegrees + halfThickness) || reach <= 0 || innerReach < 0
                || innerReach >= reach || arcDegrees <= 0 || arcDegrees > 360 || halfThickness <= 0
                || Math.abs(forward.dot(forward) - 1) > 1.0e-6 || Math.abs(direction.dot(direction) - 1) > 1.0e-6
                || Math.abs(forward.dot(direction)) > 1.0e-6) throw new IllegalArgumentException("Invalid slash volume");
        Vec normal = forward.cross(direction);
        int segments = Math.max(1, (int) Math.ceil(arcDegrees / 5));
        for (int i = 0; i < segments; i++) {
            double a = Math.toRadians(-arcDegrees / 2 + arcDegrees * i / segments);
            double b = Math.toRadians(-arcDegrees / 2 + arcDegrees * (i + 1) / segments);
            Vec da = forward.mul(Math.cos(a)).add(direction.mul(Math.sin(a)));
            Vec db = forward.mul(Math.cos(b)).add(direction.mul(Math.sin(b)));
            Vec[] polygon = {da.mul(innerReach), da.mul(reach), db.mul(reach), db.mul(innerReach)};
            Vec[] vertices = new Vec[8];
            for (int j = 0; j < 4; j++) {
                vertices[j] = polygon[j].add(normal.mul(halfThickness));
                vertices[j + 4] = polygon[j].add(normal.mul(-halfThickness));
            }
            List<Axis> axes = new ArrayList<>();
            for (Vec axis : BOX_AXES) addAxis(axes, vertices, axis);
            addAxis(axes, vertices, normal);
            for (Vec axis : BOX_AXES) addAxis(axes, vertices, normal.cross(axis));
            for (int j = 0; j < 4; j++) {
                Vec edge = polygon[(j + 1) % 4].add(polygon[j].mul(-1));
                addAxis(axes, vertices, edge.cross(normal));
                for (Vec axis : BOX_AXES) addAxis(axes, vertices, edge.cross(axis));
            }
            slices.add(new Prism(axes));
        }
    }

    public boolean intersects(Box box) {
        for (Prism prism : slices) {
            boolean overlaps = true;
            for (Axis axis : prism.axes) {
                Vec v = axis.vector;
                double center = box.center.dot(v);
                double radius = Math.abs(v.x) * box.halfSize.x + Math.abs(v.y) * box.halfSize.y + Math.abs(v.z) * box.halfSize.z;
                if (center + radius < axis.low - 1.0e-8 || center - radius > axis.high + 1.0e-8) { overlaps = false; break; }
            }
            if (overlaps) return true;
        }
        return false;
    }

    private static void addAxis(List<Axis> axes, Vec[] vertices, Vec v) {
        double norm = v.dot(v);
        if (norm < 1.0e-16) return;
        v = v.mul(1 / Math.sqrt(norm));
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (Vec vertex : vertices) { double p = vertex.dot(v); low = Math.min(low, p); high = Math.max(high, p); }
        axes.add(new Axis(v, low, high));
    }
}
