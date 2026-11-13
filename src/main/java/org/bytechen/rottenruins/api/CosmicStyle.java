package org.bytechen.rottenruins.api;

/** Preset visual styles for the cosmic shader overlay, passed as the {@code useType} uniform. */
public enum CosmicStyle {
    /** Deep purple-black void with FBM nebula and aurora bands. Default. */
    DEEP_SPACE(0),

    /** Slow rainbow gradient background with rainbow twinkling stars. */
    RAINBOW_FLOW(1),

    /** Near-black background with bright gold/white high-contrast stars. */
    PURE_DARK(2),

    /** Deep blue-purple with crystal blinking stars and blue glow. */
    CRYSTAL_DREAM(3),

    /** Rich purple nebula layers with dust particles and trailing stars. */
    NEBULA_RICH(4),

    /** Dark grey background with pink/blue dual-colour stars. */
    PINK_BLUE_DUAL(16),

    /** Deep-pink plasma swirl — no starfield, a wholly different fragment path (used by crimson_vow). */
    CRIMSON_VOW(17),

    /**
     * Water turbulence — no starfield. Two inputs differ from the other styles:
     * pattern UV comes from the mask sprite's own 0..1 UV ({@code maskUvMin/maskUvSize}), and
     * time is game ticks × {@code waveTimeScale} (default 0.025 = half-speed tick conversion).
     */
    SILENT_DAYLIGHT(18);

    public final int shaderValue;

    CosmicStyle(int shaderValue) {
        this.shaderValue = shaderValue;
    }

    /** Reverse-lookup by uniform value; falls back to {@link #DEEP_SPACE}. */
    public static CosmicStyle fromShaderValue(int shaderValue) {
        for (CosmicStyle style : values()) {
            if (style.shaderValue == shaderValue) return style;
        }
        return DEEP_SPACE;
    }
}
