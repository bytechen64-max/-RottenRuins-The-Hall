import org.bytechen.rottenruins.client.story.StoryPlayback;

public final class StoryPlaybackTest {
    public static void main(String[] args) {
        StoryPlayback playback = normal(new String[]{"甲乙丙", "丁戊", "己庚辛壬"});
        check(playback.visibleCharacters(0, 0) == 0, "no text appears immediately");
        for (int tick = 1; tick <= 104; tick++) {
            playback.tick();
            check(playback.visibleCharacters(0, 0) == Math.min(3, tick / 2), "first line types one character per two ticks");
            if (tick <= 14) check(playback.visibleCharacters(1, 0) == 0, "second line waits for first line and gap");
            if (tick <= 26) check(playback.visibleCharacters(2, 0) == 0, "third line waits for second line and gap");
            if (tick >= 34) check(playback.visibleCharacters(2, 0) == 4, "all characters are retained");
            if (tick < 54) check(playback.exitAge(0, 0) < 0, "hold begins after the entire page is typed");
            if (tick == 54) {
                check(playback.exitAge(0, 0) == 0, "first character launches after hold");
                check(playback.exitAge(1, 0) < 0, "next character stays in place until its own launch");
            }
            if (tick == 94) check(playback.exitAge(8, 0) < 40, "last character keeps falling after the first finishes");
            check(playback.finished() == (tick == 104), "next page waits for the last character's fall");
        }
        StoryPlayback paused = normal(new String[]{"甲乙"});
        paused.tick();
        for (int frame = 0; frame < 100; frame++) {
            check(paused.visibleCharacters(0, 0) == 0, "rendering alone does not advance unread text");
        }
        paused.tick();
        check(paused.visibleCharacters(0, 0) == 1, "resume continues from the same character");
        check(paused.characterAge(0, 0, 0) == 0, "entry age begins exactly when the character appears");
        check(paused.characterAge(0, 1, 0) < 0, "future character has not entered yet");
        fixedSpeeds();
        System.out.println("PASS: sequential typing, per-character ages, staggered exit, pause and final-character completion");
    }

    private static StoryPlayback normal(String[] lines) {
        return new StoryPlayback(lines, 20, StoryPlayback.DEFAULT_ENTRY_SPEED, StoryPlayback.DEFAULT_EXIT_SPEED);
    }

    private static void fixedSpeeds() {
        StoryPlayback shortWord = normal(new String[]{"Go"});
        StoryPlayback longWord = normal(new String[]{"unfamiliar"});
        shortWord.seek(2);
        longWord.seek(2);
        check(shortWord.visibleCharacters(0, 0) == 2 && longWord.visibleCharacters(0, 0) == 10,
                "one English word takes the same time regardless of letter count");
        StoryPlayback mixed = normal(new String[]{"你hello好 world"});
        mixed.seek(4);
        check(mixed.visibleCharacters(0, 0) == 6, "mixed text advances one Chinese character then one English word");
        StoryPlayback cn = normal(new String[]{"甲乙丙"});
        StoryPlayback cnLong = normal(new String[]{"甲乙丙丁戊己庚辛壬"});
        cn.seek(3);
        cnLong.seek(3);
        check(cn.characterAge(0, 0, 0) == cnLong.characterAge(0, 0, 0), "page length cannot change entry speed");
        StoryPlayback fixed = StoryPlayback.fixed(new String[]{"甲乙丙"}, 200, 10, 20f / 1.2f);
        StoryPlayback fixedLong = StoryPlayback.fixed(new String[]{"甲乙丙丁戊己庚辛壬"}, 200, 10, 20f / 1.2f);
        fixed.seek(4);
        fixedLong.seek(4);
        check(fixed.characterAge(0, 0, 0) == 2 && fixedLong.characterAge(0, 0, 0) == 2,
                "clocked pages use real ticks rather than a character-count time multiplier");
        fixed.seek(170);
        float age = fixed.exitAge(0, 0);
        fixed.tick();
        check(Math.abs(fixed.exitAge(0, 0) - age - 1) < 0.0001f, "physics ages by one real tick");
        check(Math.abs(fixed.exitAge(0, 0) - fixed.exitAge(1, 0) - 1.2f) < 0.0001f,
                "fixed Chinese departure spacing");
        fixed.seek(199);
        check(!fixed.finished(), "ritual page remains until deadline");
        fixed.tick();
        check(fixed.finished() && fixed.exitAge(2, 0) >= 39.999f, "last glyph finishes at ritual deadline");
        StoryPlayback fast = new StoryPlayback(new String[]{"甲乙"}, 20, 20, 25);
        fast.seek(1);
        check(fast.visibleCharacters(0, 0) == 1, "entry configuration uses units per second");
        StoryPlayback unicode = normal(new String[]{"\uD840\uDC00don't"});
        unicode.seek(4);
        check(unicode.visibleCharacters(0, 0) == 6, "supplementary Chinese and apostrophes keep codepoint indices");
        StoryPlayback invalid = new StoryPlayback(new String[]{"甲"}, 20, Float.NaN, 0);
        invalid.seek(2);
        check(invalid.visibleCharacters(0, 0) == 1, "invalid configured speeds fall back safely");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
