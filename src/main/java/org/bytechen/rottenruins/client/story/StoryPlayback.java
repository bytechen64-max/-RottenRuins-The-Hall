package org.bytechen.rottenruins.client.story;

/** Fixed reading-unit speeds: one CJK character or one Latin word, independent of page length. */
public final class StoryPlayback {
    public static final int LINE_GAP = 8;
    public static final float DEFAULT_ENTRY_SPEED = 10f;
    public static final float DEFAULT_EXIT_SPEED = 20f / StoryGlyphMotion.EXIT_STAGGER_TICKS;
    private final float[][] arrivals;
    private final float[] departures;
    private final float exitStart;
    private final float end;
    private final int fixedDuration;
    private int ticks;

    public StoryPlayback(String[] lines, int holdTicks, float entrySpeed, float exitSpeed) {
        this(lines, holdTicks, 0, entrySpeed, exitSpeed);
    }

    private StoryPlayback(String[] lines, int holdTicks, int fixedDuration, float entrySpeed, float exitSpeed) {
        arrivals = new float[lines.length][];
        departures = new float[java.util.Arrays.stream(lines).mapToInt(s -> s.codePointCount(0, s.length())).sum()];
        float entryStep = 20f / validSpeed(entrySpeed, DEFAULT_ENTRY_SPEED);
        float exitStep = 20f / validSpeed(exitSpeed, DEFAULT_EXIT_SPEED);
        float entry = 0, exit = 0;
        int global = 0;
        for (int line = 0; line < lines.length; line++) {
            int[] codePoints = lines[line].codePoints().toArray();
            arrivals[line] = new float[codePoints.length];
            for (int i = 0; i < codePoints.length;) {
                if (Character.isWhitespace(codePoints[i])) {
                    arrivals[line][i++] = entry;
                    departures[global] = global == 0 ? 0 : departures[global - 1];
                    global++;
                    continue;
                }
                int next = i + 1;
                if (wordPart(codePoints[i])) {
                    while (next < codePoints.length && (wordPart(codePoints[next])
                            || ((codePoints[next] == '\'' || codePoints[next] == 0x2019)
                            && next + 1 < codePoints.length && wordPart(codePoints[next + 1])))) next++;
                }
                int count = next - i;
                for (int letter = 0; letter < count; letter++) {
                    arrivals[line][i + letter] = entry + entryStep * (letter + 1f) / count;
                    departures[global++] = exit + exitStep * letter / count;
                }
                entry += entryStep;
                exit += exitStep;
                i = next;
            }
            if (line + 1 < lines.length) entry += LINE_GAP;
        }
        this.fixedDuration = fixedDuration;
        float exitDuration = StoryGlyphMotion.EXIT_FLIGHT_TICKS;
        for (float departure : departures) exitDuration = Math.max(exitDuration, departure + StoryGlyphMotion.EXIT_FLIGHT_TICKS);
        // Clocked scenes adjust only their reading hold. Animation time always uses real ticks.
        exitStart = entry + (fixedDuration > 0 ? Math.max(0, fixedDuration - entry - exitDuration) : Math.max(20, holdTicks));
        end = exitStart + exitDuration;
    }

    public static StoryPlayback fixed(String[] lines, int duration, float entrySpeed, float exitSpeed) {
        return new StoryPlayback(lines, 0, Math.max(1, duration), entrySpeed, exitSpeed);
    }

    private static boolean wordPart(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN
                || Character.isDigit(codePoint) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static float validSpeed(float speed, float fallback) {
        return Float.isFinite(speed) && speed > 0 ? Math.max(0.1f, Math.min(100, speed)) : fallback;
    }

    public void seek(long elapsed) { ticks = (int) Math.max(0, Math.min(Integer.MAX_VALUE, elapsed)); }
    public void tick() { ticks++; }

    public int visibleCharacters(int line, float partialTick) {
        int count = 0;
        for (float arrival : arrivals[line]) {
            if (ticks + partialTick < arrival) break;
            count++;
        }
        return count;
    }

    public float characterAge(int line, int character, float partialTick) {
        return ticks + partialTick - arrivals[line][character];
    }

    public float exitAge(int globalCharacter, float partialTick) {
        return ticks + partialTick - exitStart - departures[globalCharacter];
    }

    public boolean finished() { return ticks >= (fixedDuration > 0 ? fixedDuration : end); }
}
