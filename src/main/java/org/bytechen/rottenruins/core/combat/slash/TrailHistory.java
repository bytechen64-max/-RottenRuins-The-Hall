package org.bytechen.rottenruins.core.combat.slash;

import java.util.ArrayList;
import java.util.List;

/** Time-based polyline history. Multiple anchors describe the blade cross-section. */
public final class TrailHistory {
    public record Point(double x, double y, double z) {
        public double distance(Point p) { return Math.sqrt((x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z)); }
    }
    public record Sample(double time, List<Point> points) { public Sample { points = List.copyOf(points); } }
    private final List<Sample> samples = new ArrayList<>();
    public List<Sample> samples() { return List.copyOf(samples); }
    public void clear() { samples.clear(); }
    public void prune(double now, double lifetime) { samples.removeIf(sample -> now - sample.time > lifetime); }
    public boolean append(double time, List<Point> points, double maxDistance, int maxSamples) {
        if (points.size() < 2 || !Double.isFinite(time)) return false;
        for (Point p : points) if (!Double.isFinite(p.x + p.y + p.z)) return false;
        if (!samples.isEmpty()) {
            Sample last = samples.get(samples.size()-1);
            if (time <= last.time + 1.0e-6) return false;
            if (points.size() != last.points.size() || time-last.time > 2.5) clear();
            else for (int i=0;i<points.size();i++) if (points.get(i).distance(last.points.get(i)) > maxDistance) { clear(); break; }
        }
        samples.add(new Sample(time, points));
        while (samples.size() > maxSamples) samples.remove(0);
        return true;
    }
}
