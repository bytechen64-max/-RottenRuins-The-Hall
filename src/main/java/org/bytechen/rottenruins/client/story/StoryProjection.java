package org.bytechen.rottenruins.client.story;

/** Pure camera-space geometry, shared by text visibility and its HUD pointer. */
public final class StoryProjection {
    private StoryProjection() {}

    public record Result(boolean readable, double directionX, double directionY) {}

    /** Camera coordinates: right = +x, up = +y, forward = -z. */
    public static Result project(double x, double y, double z, double focalX, double focalY,
                                 double halfWidth, double halfHeight) {
        double depth = -z;
        boolean visible = depth > 0.05
                && (Math.abs(x) + halfWidth) * focalX < depth * 0.92
                && (Math.abs(y) + halfHeight) * focalY < depth * 0.92;
        // Do not divide by depth: that would reverse the pointer behind the camera.
        return direction(visible, x * focalX, -y * focalY);
    }

    public static Result direction(boolean visible, double dx, double dy) {
        if (Math.hypot(dx, dy) < 1.0e-5) dx = 1;
        double length = Math.hypot(dx, dy);
        return new Result(visible, dx / length, dy / length);
    }
}
