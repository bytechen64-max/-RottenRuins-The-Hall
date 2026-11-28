import org.bytechen.rottenruins.client.story.StoryGlyphMotion;

public final class StoryGlyphMotionTest {
    public static void main(String[] args) {
        near(StoryGlyphMotion.sample(0, -1, 0).scale(), 1.2f, "new character starts at 1.2x");
        float lastScale = 1.2f;
        for (int frame = 0; frame <= 120; frame++) {
            var pose = StoryGlyphMotion.sample(frame / 20f, -1, 0);
            check(pose.scale() <= lastScale + 0.00001f && pose.scale() >= 1, "entry settles without shrinking below normal");
            check(pose.x() == 0 && pose.y() == 0 && pose.rotation() == 0, "typing does not shift the character's slot");
            lastScale = pose.scale();
        }
        near(lastScale, 1, "settles to normal size after six ticks");
        var launch = StoryGlyphMotion.sample(100, 0, 2);
        near(launch.y(), 0, "exit begins without teleporting");
        check(StoryGlyphMotion.sample(100, 5, 2).y() < 0, "character first jumps up");
        check(StoryGlyphMotion.sample(100, 30, 2).y() > 0, "gravity brings it below its original position");
        for (int t = 1; t < 39; t++) {
            float previous = StoryGlyphMotion.sample(100, t - 1, 2).y();
            float current = StoryGlyphMotion.sample(100, t, 2).y();
            float next = StoryGlyphMotion.sample(100, t + 1, 2).y();
            near(next - 2 * current + previous, StoryGlyphMotion.GRAVITY, "trajectory has constant gravitational acceleration");
        }
        check(StoryGlyphMotion.sample(100, 24, 2).dissolve() == 0, "glyph stays intact through initial fall");
        near(StoryGlyphMotion.sample(100, 32, 2).dissolve(), 0.5f, "glyph dissolves during the fall");
        near(StoryGlyphMotion.sample(100, 40, 2).dissolve(), 1, "glyph is gone at the end of its trajectory");
        check(StoryGlyphMotion.sample(100, 12.25f, 5).equals(StoryGlyphMotion.sample(100, 12.25f, 5)),
                "sampling is independent of frame history");
        for (int i = 0; i <= 100; i++) {
            float phase = i / 100f;
            int packed = StoryGlyphMotion.packedLight(phase);
            check((packed & 0xffff) <= 32767 && (packed >>> 16) == 240, "encoded values fit signed UV2 and preserve fallback Y");
            near((packed & 0xffff) / 32767f, phase, "per-glyph dissolve encoding keeps precision");
        }
        System.out.println("PASS: 1.2x entry, settling, ballistic jump/gravity, fall dissolve and per-glyph vertex encoding");
    }

    private static void near(float value, float expected, String message) {
        check(Math.abs(value - expected) < 0.0001f, message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
