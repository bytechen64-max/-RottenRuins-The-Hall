package org.bytechen.rottenruins.client.rend.slash;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Follow through out of view, turn below the viewport, then lift into the vanilla grip. */
public final class SlashRecovery {
    public static final float EXIT_END = SlashTimeline.recoveryBoundary(SlashTimeline.EXIT_TICKS);
    public static final float HOLD_END = SlashTimeline.recoveryBoundary(SlashTimeline.EXIT_TICKS + SlashTimeline.HOLD_TICKS);
    public static final float LOWER_END = SlashTimeline.recoveryBoundary(SlashTimeline.EXIT_TICKS
            + SlashTimeline.HOLD_TICKS + SlashTimeline.LOWER_TICKS);
    public static final float LIFT_START = SlashTimeline.recoveryBoundary(SlashTimeline.EXIT_TICKS
            + SlashTimeline.HOLD_TICKS + SlashTimeline.LOWER_TICKS + SlashTimeline.TRANSFER_TICKS);
    public record Frame(Vector3f grip, Quaternionf rotation) {}

    private SlashRecovery() {}

    /** Each attack enters from its own hidden start; never drag the old endpoint across the viewport. */
    public static Frame entry(float progress, Vector3f startGrip, Quaternionf rotation, float radius, Matrix4f projection) {
        float depth = Math.max(0.85F, 2 * radius + 0.25F);
        Vector3f outside = new Vector3f(Math.copySign(radius + (depth + radius) / Math.abs(projection.m00()) + 0.15F, startGrip.x),
                startGrip.y, -depth);
        if (startGrip.y > 0.2F) outside.y = radius + (depth + radius) / Math.abs(projection.m11()) + 0.15F;
        return blend(outside, startGrip, rotation, rotation, smooth(progress, 0, SlashMotion.PREPARE_END));
    }

    public static Frame sample(float progress, Vector3f cutGrip, Quaternionf cutRotation,
                               Vector3f idleGrip, Quaternionf idleRotation, float radius, Matrix4f projection) {
        // A sphere around the detected grip encloses all baked model vertices. Place
        // that whole sphere outside the frustum, rather than hiding just the handle.
        float depth = Math.max(0.85F, 2 * radius + 0.25F);
        float xScale = Math.abs(projection.m00()), yScale = Math.abs(projection.m11());
        float outsideX = radius + (depth + radius) / xScale + 0.15F;
        float belowY = -radius - (depth + radius) / yScale - 0.15F;
        Vector3f outside;
        if (cutGrip.y < -0.2F) {
            // Continue the downward cut along its original line. Changing depth
            // while Y stayed fixed used to project the sword back up into view.
            float distance = Math.max(1, Math.max(outsideX / Math.max(0.0001F, Math.abs(cutGrip.x)),
                    -belowY / Math.abs(cutGrip.y)));
            outside = new Vector3f(cutGrip.x * distance, cutGrip.y * distance, cutGrip.z);
        } else {
            outside = new Vector3f(Math.copySign(outsideX, cutGrip.x), cutGrip.y, cutGrip.z);
        }
        // Depth changes and rotation only begin once the complete model is hidden.
        Vector3f lower = new Vector3f(outside.x, Math.min(outside.y, belowY), -depth);
        Vector3f belowIdle = new Vector3f(idleGrip.x,
                Math.min(idleGrip.y - 0.2F, -radius - (Math.abs(idleGrip.z) + radius) / yScale - 0.15F), idleGrip.z);
        if (progress < EXIT_END) return blend(cutGrip, outside, cutRotation, cutRotation,
                smooth(progress, SlashMotion.CUT_END, EXIT_END));
        if (progress < HOLD_END) return new Frame(new Vector3f(outside), new Quaternionf(cutRotation));
        if (progress < LOWER_END) return blend(outside, lower, cutRotation, idleRotation,
                smooth(progress, HOLD_END, LOWER_END));
        if (progress < LIFT_START) return blend(lower, belowIdle, idleRotation, idleRotation,
                smooth(progress, LOWER_END, LIFT_START));
        // Only vertical motion is visible during the return. The sword has already
        // regained its authored orientation and native grip X/depth below the screen.
        return blend(belowIdle, idleGrip, idleRotation, idleRotation, smooth(progress, LIFT_START, 1));
    }

    private static Frame blend(Vector3f a, Vector3f b, Quaternionf qa, Quaternionf qb, float t) {
        return new Frame(new Vector3f(a).lerp(b, t), new Quaternionf(qa).slerp(qb, t));
    }

    private static float smooth(float progress, float from, float to) {
        float t = Math.max(0, Math.min(1, (progress - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
