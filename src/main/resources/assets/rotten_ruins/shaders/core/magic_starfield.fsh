#version 150 core

// ============================================================
// Spell constellation GUI - deep space background.
// Fully procedural: drifting nebula + three parallax star layers.
// Drawn as one fullscreen quad, pixel coordinates come in through
// uSize, so star sizes stay stable when the window is resized.
// ============================================================

uniform float uTime;
uniform vec2  uSize;
uniform vec2  uMouse;
uniform float uIntensity;

in vec2 vUv;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.02 + vec2(11.3, 7.7);
        a *= 0.5;
    }
    return v;
}

// One parallax layer of point stars.  px is the pixel coordinate.
vec3 starLayer(vec2 px, float cellSize, float speed,
               float threshold, float brightness) {
    vec2 p = px / cellSize;
    p.x += uTime * speed;

    vec2 cell = floor(p);
    vec2 f = fract(p);

    float h = hash21(cell);
    if (h < threshold) {
        return vec3(0.0);
    }

    // Keep the star away from the cell border so the wrap is invisible.
    vec2 center = vec2(hash21(cell + 3.7), hash21(cell + 8.1)) * 0.8 + 0.1;
    float dist = length(f - center);

    float twinkle = 0.55 + 0.45 * sin(uTime * 1.7 + h * 43.0);
    // Sharp little core + a whisper of halo: crisp stars, not blurry blobs.
    float sharp = smoothstep(0.016, 0.0, dist);
    float glow = smoothstep(0.050, 0.0, dist) * 0.35;
    float star = (sharp + glow) * brightness * twinkle;

    vec3 tint = mix(vec3(0.52, 0.66, 1.0),
                    vec3(1.0, 0.84, 0.52),
                    hash21(cell + 17.3));
    return tint * star;
}

void main() {
    vec2 px = vUv * uSize;

    // Deep space vertical gradient.
    vec3 top    = vec3(0.030, 0.050, 0.100);
    vec3 bottom = vec3(0.006, 0.012, 0.028);
    vec3 col = mix(top, bottom, vUv.y);

    // Slowly drifting nebula (two scales + a faint gold dust layer).
    // uMouse tilts the whole sky by a few pixels, so the background has parallax.
    vec2 q = vUv * vec2(2.4, 1.4)
           + vec2(uTime * 0.006, -uTime * 0.004)
           + uMouse * 0.012;
    float n1 = fbm(q);
    float n2 = fbm(q * 1.9 + vec2(3.1, -uTime * 0.012));
    col += vec3(0.10, 0.16, 0.34) * pow(n1, 2.0) * 0.85;
    col += vec3(0.20, 0.10, 0.26) * pow(n2, 3.0) * 0.55;
    col += vec3(0.18, 0.13, 0.05)
            * pow(fbm(q * 3.1 + vec2(uTime * 0.004, 2.4)), 3.5) * 1.4;

    // Three star layers at different sizes / drift speeds / parallax depths.
    vec2 parallax = uMouse * 26.0;
    col += starLayer(px + parallax * 0.35,  44.0, 0.020, 0.84, 0.95);
    col += starLayer(px + parallax * 0.65,  76.0, 0.012, 0.78, 0.70);
    col += starLayer(px + parallax * 1.00, 120.0, 0.007, 0.86, 1.00);

    // Vignette: pull attention towards the constellation in the centre.
    vec2 v = (vUv - 0.5) * vec2(1.15, 1.0);
    float vig = smoothstep(0.95, 0.25, length(v));
    col *= mix(0.68, 1.0, vig);

    col *= uIntensity;
    fragColor = vec4(col, 1.0);
}
