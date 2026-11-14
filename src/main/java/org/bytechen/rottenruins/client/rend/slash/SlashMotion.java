package org.bytechen.rottenruins.client.rend.slash;

/** A wide planar sweep; the blade points forward at mid-cut and the edge leads the rotation. */
public final class SlashMotion {
    public enum Direction {
        RIGHT_TO_LEFT, LEFT_TO_RIGHT, UPPER_RIGHT_TO_LOWER_LEFT, UPPER_LEFT_TO_LOWER_RIGHT
    }
    public record Vec(float x, float y, float z) {
        public Vec cross(Vec v) {
            return new Vec(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
        }
        public float dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
    }
    /** Model edge = local X, hilt-to-tip = local Y, face normal = local Z. */
    public record Pose(Vec edge, Vec blade, Vec normal, float weight, float screenX, float screenY, float progress) {
        public static final Pose IDLE = new Pose(new Vec(1, 0, 0), new Vec(0, 0, -1), new Vec(0, 1, 0), 0, 0, 0, 0);
    }
    public static final float PREPARE_END = 0.32F;
    public static final float CUT_END = 0.62F;
    public static final float RECOVER_START = 0.72F;
    public static final float SWEEP_DEGREES = 140;
    private static final float DIAGONAL = (float) Math.sqrt(0.5);
    private SlashMotion() {}
    public static Direction direction(int comboIndex) {
        return Direction.values()[Math.floorMod(comboIndex, Direction.values().length)];
    }
    /** Recovery fades out without reversing the cut. */
    public static Pose sample(Direction direction, float progress) {
        if (!Float.isFinite(progress) || progress <= 0 || progress >= 1) return Pose.IDLE;
        float dx = switch (direction) {
            case LEFT_TO_RIGHT -> 1;
            case RIGHT_TO_LEFT -> -1;
            case UPPER_LEFT_TO_LOWER_RIGHT -> DIAGONAL;
            case UPPER_RIGHT_TO_LOWER_LEFT -> -DIAGONAL;
        };
        float dy = direction == Direction.UPPER_LEFT_TO_LOWER_RIGHT
                || direction == Direction.UPPER_RIGHT_TO_LOWER_LEFT ? -DIAGONAL : 0;
        float cut = smooth(clamp((progress - PREPARE_END) / (CUT_END - PREPARE_END)));
        float angle=(float)Math.toRadians(SWEEP_DEGREES)*(cut-.5F);
        float sine=(float)Math.sin(angle),cosine=(float)Math.cos(angle);
        Vec blade = new Vec(dx*sine,dy*sine,-cosine);
        Vec edge = new Vec(dx*cosine,dy*cosine,sine);
        float weight = progress < PREPARE_END ? smooth(progress / PREPARE_END)
                : 1 - smooth(clamp((progress - RECOVER_START) / (1 - RECOVER_START)));
        return new Pose(edge, blade, edge.cross(blade), weight,
                Math.signum(dx) * (2 * cut - 1) * 1.05F, dy == 0 ? 0.05F : (1 - 2 * cut) * 0.82F, progress);
    }
    private static float clamp(float t) { return Math.max(0, Math.min(1, t)); }
    private static float smooth(float t) { return t * t * (3 - 2 * t); }
}
