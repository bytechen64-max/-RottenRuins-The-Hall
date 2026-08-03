package org.bytechen.hall.api;

/**
 * Preset visual styles for the cosmic starfield shader overlay.
 * Passed as the {@code useType} uniform to the fragment shader.
 *
 * <h3>Available presets</h3>
 * <table>
 *   <tr><th>Preset</th><th>Background</th><th>Star colour</th><th>Aesthetic</th></tr>
 *   <tr><td>{@link #DEEP_SPACE}</td><td>FBM nebula, aurora bands</td><td>Cool white/blue mix</td><td>Default — dark purple void</td></tr>
 *   <tr><td>{@link #RAINBOW_FLOW}</td><td>Slow rainbow gradient</td><td>Rainbow twinkling</td><td>Colourful, dynamic</td></tr>
 *   <tr><td>{@link #PURE_DARK}</td><td>Near-black</td><td>Bright gold/white</td><td>Minimal, high contrast</td></tr>
 *   <tr><td>{@link #CRYSTAL_DREAM}</td><td>Deep blue-purple</td><td>Crystal blink, blue glow</td><td>Ethereal, magical</td></tr>
 *   <tr><td>{@link #NEBULA_RICH}</td><td>Rich purple nebula + dust</td><td>Purple-white, trailing</td><td>Lush, immersive</td></tr>
 *   <tr><td>{@link #PINK_BLUE_DUAL}</td><td>Dark grey</td><td>Pink + blue binary</td><td>Sharp contrast, futuristic</td></tr>
 * </table>
 *
 * @see ICosmicLayer#cosmicStyle()
 */
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
    PINK_BLUE_DUAL(16);

    /** The value sent to the shader {@code useType} uniform. */
    public final int shaderValue;

    CosmicStyle(int shaderValue) {
        this.shaderValue = shaderValue;
    }

    /**
     * Reverse-lookup a {@link CosmicStyle} from its shader uniform value.
     * Returns {@link #DEEP_SPACE} if no match is found.
     */
    public static CosmicStyle fromShaderValue(int shaderValue) {
        for (CosmicStyle style : values()) {
            if (style.shaderValue == shaderValue) return style;
        }
        return DEEP_SPACE;
    }
}
