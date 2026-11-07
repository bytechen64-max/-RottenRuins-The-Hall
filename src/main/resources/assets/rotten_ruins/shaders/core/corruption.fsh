#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;
uniform float intensity;   // 0.0–1.0  corruption strength

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos;

out vec4 fragColor;

// ── noise / hash ──────────────────────────────────────────────

float hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash1(float n) { return fract(sin(n) * 43758.5453123); }

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x),
               mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

// ── main ──────────────────────────────────────────────────────

void main() {
    vec2 uv = texCoord0;
    float i = clamp(intensity, 0.0, 1.0);
    vec4 base = texture(Sampler0, uv);

    if (i < 0.005) {
        fragColor = base * vertexColor * ColorModulator;
        return;
    }

    // ── 1. RGB chromatic split ──
    float split = i * 0.012;
    // direction rotates slowly
    float ang = time * 0.7;
    vec2 dir = vec2(cos(ang), sin(ang));
    float r = texture(Sampler0, uv + dir * split).r;
    float g = texture(Sampler0, uv).g;
    float b = texture(Sampler0, uv - dir * split).b;
    vec3 rgb = vec3(r, g, b);

    // ── 2. colour shift → corrupted purple/magenta ──
    vec3 purple = vec3(0.55, 0.08, 0.50);
    rgb = mix(rgb, purple, i * 0.35);

    // desaturate toward a cold-magenta grey
    float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
    vec3 cold = vec3(gray * 0.7, gray * 0.25, gray * 0.85);
    rgb = mix(rgb, cold, i * 0.45);

    // ── 3. scanlines ──
    float sl = sin(uv.y * 350.0 + time * 8.0) * 0.5 + 0.5;
    float slStrength = i * 0.18;
    float sl2 = sin(uv.y * 87.0 - time * 3.0) * 0.5 + 0.5;  // wider secondary
    rgb *= 1.0 - sl * slStrength - sl2 * slStrength * 0.4;

    // ── 4. horizontal glitch strips ──
    float row = floor(uv.y * 55.0);
    float gh = hash1(row * 137.0 + floor(time * 4.0));
    float glitch = step(0.94, gh) * i;
    if (glitch > 0.5) {
        float goff = (hash1(row * 311.0 + time * 2.7) - 0.5) * 0.08 * i;
        rgb.r = texture(Sampler0, uv + vec2(goff, 0.0)).r;
        rgb.b = texture(Sampler0, uv - vec2(goff * 0.6, 0.0)).b;
        rgb *= 0.7 + 0.3 * hash1(row + time);
    }

    // ── 5. film grain ──
    float grain = noise(uv * 400.0 + time * 20.0) * i * 0.1;
    rgb -= grain;
    float grain2 = hash2(uv * 700.0 + time * 13.0) * i * 0.04;
    rgb -= grain2;

    // ── 6. random dead pixels ──
    float dp = hash2(uv * 200.0 + vec2(time * 5.0, time * 3.0 + 71.0));
    float dpMask = step(0.975, dp) * i * 0.55;
    rgb = mix(rgb, vec3(0.0, 0.0, 0.0), dpMask);

    // bright flicker pixels
    float bp = hash2(uv * 300.0 + vec2(time * 11.0, time * 7.0 + 13.0));
    float bpMask = step(0.985, bp) * i * 0.3;
    rgb = mix(rgb, vec3(0.9, 0.2, 0.8), bpMask);

    // ── 7. vignette / edge darkening ──
    vec2 vig = abs(uv - 0.5) * 2.0;
    float v = 1.0 - dot(vig, vig) * 0.55 * i;
    rgb *= v;

    // ── 8. pulsing low-frequency dark wave ──
    float wave = sin(uv.y * 6.0 + time * 2.0) * sin(uv.x * 5.0 + time * 1.7) * i * 0.08;
    rgb += wave;

    vec4 color = vec4(rgb, 1.0);
    color.a *= base.a;

    // vertexColor contributes a small amount so item lighting still matters
    vec3 lit = mix(color.rgb, color.rgb * vertexColor.rgb, 0.2);
    fragColor = linear_fog(vec4(lit, color.a) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
