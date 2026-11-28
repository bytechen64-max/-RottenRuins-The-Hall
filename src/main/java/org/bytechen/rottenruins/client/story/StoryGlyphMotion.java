package org.bytechen.rottenruins.client.story;

/** Deterministic per-character animation in the panel's logical font coordinates (positive Y is down). */
public final class StoryGlyphMotion {
    public static final int ENTRY_TICKS = 6;
    public static final float EXIT_STAGGER_TICKS = 1.2f;
    public static final int EXIT_FLIGHT_TICKS = 40;
    public static final int EXIT_FADE_START = 24;
    public static final float GRAVITY = 0.2f;
    public static final float JUMP_VELOCITY = -2.2f;

    public record Pose(float scale, float x, float y, float rotation, float dissolve) {}

    private StoryGlyphMotion() {}

    public static Pose sample(float entryAge, float exitAge, int index) {
        float t = clamp(entryAge / ENTRY_TICKS);
        float scale = 1 + 0.2f * (1 - t * t * (3 - 2 * t));
        if (exitAge < 0) return new Pose(scale, 0, 0, 0, 0);
        float age = Math.min(EXIT_FLIGHT_TICKS, exitAge);
        // A stable horizontal drift and spin keep the fall natural without frame-to-frame randomness.
        float variation = ((index * 37 + 11) % 19 - 9) / 9f;
        float x = variation * 0.18f * age;
        float y = JUMP_VELOCITY * age + 0.5f * GRAVITY * age * age;
        float rotation = variation * 0.015f * age;
        float dissolve = clamp((age - EXIT_FADE_START) / (EXIT_FLIGHT_TICKS - EXIT_FADE_START));
        return new Pose(scale, x, y, rotation, dissolve);
    }

    /** UV2.x carries a per-glyph dissolve value; UV2.y retains a valid full-bright fallback. */
    public static int packedLight(float dissolve) {
        return 240 << 16 | Math.round(clamp(dissolve) * 32767);
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
}
