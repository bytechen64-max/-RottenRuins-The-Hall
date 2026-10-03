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
 *   <tr><td>{@link #CRIMSON_VOW}</td><td>Deep-pink plasma swirl</td><td>(no starfield)</td><td>Warped sine-flow glow</td></tr>
 *   <tr><td>{@link #SILENT_DAYLIGHT}</td><td>Water turbulence</td><td>(no starfield)</td><td>Iterated trig feedback, cold blue caustics</td></tr>
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
    PINK_BLUE_DUAL(16),

    /**
     * Deep-pink plasma swirl — <b>no starfield</b>.
     *
     * <p>An entirely different fragment path: instead of sampling the 12 cosmic
     * star sprites it iterates a shadertoy-style UV warp
     * ({@code uv += 0.6/i * cos(i*k*uv.yx + time)}) and then divides a deep-pink
     * base colour by {@code abs(sin(...))} to produce the flowing filament
     * bands.  Used by {@code crimson_vow}.</p>
     */
    CRIMSON_VOW(17),

    /**
     * Water turbulence — <b>no starfield</b>.
     *
     * <p>移植 shadertoy 的 "water turbulence"（作者 David Hoskins，湍流本体来自
     * joltz0r）：5 层迭代三角反馈
     * ({@code i = p + vec2(cos(t-i.x)+sin(t+i.y), sin(t-i.y)+cos(t+i.x))})，
     * 再把 {@code 1.17 - pow(c,1.4)} 取 8 次幂压成"暗底 + 极窄亮丝"，
     * 最后整体抬向青蓝。用在 {@code silent_daylight} 的剑刃上，像一层流动的水光。</p>
     *
     * <p>两处输入与其它 style 不同，都在 {@code cosmic.fsh} 里注解过：
     * 图案坐标用遮罩 sprite 自己的 0..1 UV（{@code maskUvMin/maskUvSize}）而不是
     * 图集 UV；时间用 {@code time}（游戏 tick）× {@code waveTimeScale}（默认 0.025
     * ＝原作 0.5 倍速的 tick 换算）。</p>
     */
    SILENT_DAYLIGHT(18);

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
