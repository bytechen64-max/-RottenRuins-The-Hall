#version 150 core

in vec4 vertexColor;
in vec2 uv0;
in vec2 uv1;
in vec2 uv2;
in vec3 normal;

uniform vec4 GlintColor;
uniform vec4 SecondaryColor;
uniform float ColorMode;
uniform float GlintSpeed;
uniform float GlintIntensity;
uniform float BloomStrength;
uniform float BloomRadius;
uniform float PaletteSize;
uniform vec4 PaletteColor0;
uniform vec4 PaletteColor1;
uniform vec4 PaletteColor2;
uniform vec4 PaletteColor3;
uniform vec4 PaletteColor4;
uniform vec4 PaletteColor5;
uniform vec4 PaletteColor6;
uniform vec4 PaletteColor7;
uniform float Time;

uniform vec4 ColorModulator;

out vec4 fragColor;

vec3 hsv2rgb(float h, float s, float v) {
    vec3 c = vec3(h * 6.0, s, v);
    vec3 rgb = clamp(abs(mod(c.x + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

vec4 getPaletteColor(float idx) {
    float i = clamp(idx, 0.0, 7.0);
    vec4 cols[8] = vec4[8](
        PaletteColor0, PaletteColor1, PaletteColor2, PaletteColor3,
        PaletteColor4, PaletteColor5, PaletteColor6, PaletteColor7
    );
    float fi = floor(i);
    float ci = ceil(i);
    float frac = i - fi;
    return mix(cols[int(fi)], cols[int(ci)], frac);
}

void main() {
    float t = Time * GlintSpeed * 0.8;

    // Primary shimmer pattern
    float p1 = sin((uv0.x + uv0.y) * 10.0 + t) * 0.5 + 0.5;
    float p2 = sin((uv0.x - uv0.y) * 7.5 - t * 0.7) * 0.5 + 0.5;
    float p3 = sin(uv0.x * 5.0 + t * 1.3) * 0.5 + 0.5;

    float pattern = p1 * 0.5 + p2 * 0.3 + p3 * 0.2;
    pattern = clamp(pattern, 0.0, 1.0);

    // Bloom: lower-frequency, wider glow
    float b1 = sin((uv0.x + uv0.y) * 4.0 + t * 0.5) * 0.5 + 0.5;
    float b2 = sin((uv0.x - uv0.y) * 3.0 - t * 0.35) * 0.5 + 0.5;
    float bloomPattern = b1 * 0.6 + b2 * 0.4;
    bloomPattern = clamp(bloomPattern, 0.0, 1.0);

    // Edge glow
    float edge = 1.0 - abs(pattern - 0.5) * 2.0;
    float brightness = mix(pattern, edge, 0.3);

    // Choose color based on mode
    vec3 color;
    if (ColorMode < 0.5) {
        // SINGLE
        color = GlintColor.rgb;
    } else if (ColorMode < 1.5) {
        // DUAL_SCROLL
        color = mix(GlintColor.rgb, SecondaryColor.rgb, pattern);
    } else if (ColorMode < 2.5) {
        // RAINBOW
        float hue = fract(pattern * 1.5 + Time * 0.05);
        color = hsv2rgb(hue, 0.85, 1.0);
    } else {
        // AUTO_SAMPLE_SCROLL - scroll through palette
        float palScroll = fract(pattern * 0.7 + Time * 0.12);
        float palIdx = palScroll * (PaletteSize - 1.0);
        color = getPaletteColor(palIdx).rgb;
    }

    // Base glint alpha
    float alpha = brightness * GlintIntensity * GlintColor.a;
    alpha = smoothstep(0.0, 0.12, alpha);

    // Bloom contribution (softer, wider glow)
    float bloomAlpha = bloomPattern * BloomStrength * 0.4;
    bloomAlpha = smoothstep(0.0, 0.2, bloomAlpha);
    bloomAlpha *= smoothstep(1.0, 0.0, length(uv0 - 0.5) / (BloomRadius * 0.7 + 0.01));

    float totalAlpha = alpha + bloomAlpha;
    totalAlpha = clamp(totalAlpha, 0.0, 1.0);

    vec3 finalColor = mix(color, vec3(1.0), bloomAlpha * 0.3);
    fragColor = vec4(finalColor * totalAlpha, totalAlpha) * ColorModulator;
}
