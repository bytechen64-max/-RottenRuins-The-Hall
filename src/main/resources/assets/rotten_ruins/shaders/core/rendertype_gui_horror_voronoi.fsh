#version 150 core

uniform vec2 ScreenSize;
uniform float Time;
uniform float uIntensity;

in vec2 texCoord;
out vec4 fragColor;

// ── Hash & Noise ──────────────────────────────────────────

float hash(float n) {
    return fract(sin(n) * 43758.4543621);
}

float noise(vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    float n = p.x + p.y * 57.0;
    return mix(
        mix(hash(n + 0.0), hash(n + 1.0), f.x),
        mix(hash(n + 57.0), hash(n + 58.0), f.x),
        f.y);
}

float fbm(vec2 p) {
    float f = 0.0;
    f += 0.5000 * noise(p); p *= 2.01;
    f += 0.2500 * noise(p); p *= 2.03;
    f += 0.1250 * noise(p); p *= 2.04;
    f += 0.0625 * noise(p); p *= 2.00;
    f /= 0.9375;
    return f;
}

vec2 hash2(vec2 x) {
    vec2 n = vec2(dot(x, vec2(171, 311)), dot(x, vec2(269, 382)));
    return fract(sin(n) * 43785.454621);
}

vec3 voronoi(vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    vec2 mr, mg;
    float md = 8.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash2(p + g);
            o = 0.5 + 0.5 * sin(6.28 * o + Time * 4.8);
            vec2 r = g + o - f;
            float d = dot(r, r);
            if (d < md) {
                md = d;
                mr = r;
                mg = g;
            }
        }
    }
    return vec3(sqrt(md), mr);
}

void main() {
    // Aspect-corrected UV for noise patterns (cells stay round)
    vec2 uv = texCoord * 2.0 - 1.0;
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 uvAspect = vec2(uv.x * aspect, uv.y);

    // ── Blood flow background ──────────────────────────────

    float t = Time * 0.7;

    // Layer 1: slow, large-scale dark red swirls
    vec2 flowUV1 = uvAspect * 2.3 + vec2(t * 0.4, -t * 0.55);
    float flow1 = fbm(flowUV1);

    // Layer 2: medium, maroon streaks with vertical bias
    vec2 flowUV2 = uvAspect * 4.5 + vec2(-t * 0.7, t * 0.3);
    float flow2 = fbm(flowUV2);

    // Layer 3: fast, fine dark veins
    vec2 flowUV3 = uvAspect * 8.0 + vec2(t * 1.1, -t * 0.8);
    float flow3 = fbm(flowUV3);

    // Layer 4: slow pulsing patches
    vec2 flowUV4 = uvAspect * 1.5 + vec2(sin(t * 0.6) * 0.4, cos(t * 0.7) * 0.3);
    float flow4 = fbm(flowUV4);

    float bloodFlow = flow1 * 0.45 + flow2 * 0.3 + flow3 * 0.18 + flow4 * 0.07;

    vec3 bloodDeep   = vec3(0.06, 0.0, 0.01);
    vec3 bloodMid    = vec3(0.28, 0.01, 0.02);
    vec3 bloodBright = vec3(0.45, 0.03, 0.04);
    vec3 bloodDark   = vec3(0.02, 0.0, 0.005);

    vec3 bloodBG = bloodDeep;
    bloodBG = mix(bloodBG, bloodMid,   smoothstep(0.2, 0.55, bloodFlow));
    bloodBG = mix(bloodBG, bloodBright, smoothstep(0.55, 0.75, bloodFlow));
    bloodBG = mix(bloodBG, bloodDark,  smoothstep(0.75, 0.95, bloodFlow));

    float warmth = 0.5 + 0.5 * sin(Time * 1.2 + flowUV1.y * 3.0);
    bloodBG = mix(bloodBG, bloodBG * 1.3, warmth * 0.15);

    float bgAlpha = 0.3 + 0.08 * sin(Time * 1.0);

    // ── Voronoi cells ─────────────────────────────────────

    vec3 v = voronoi(5.5 * uvAspect);
    float cellDist = length(v.yz);
    float cellAngle = atan(v.z, v.y);

    vec3 cellCol = vec3(0.0);
    float cellAlpha = 0.0;

    if (cellDist < 0.35) {
        cellDist -= 0.1 * smoothstep(0.05, 0.1, 0.5 * abs(0.2 * sin(Time * 2.8 + 3.0 * v.x)));

        cellCol = vec3(0.08, 0.0, 0.01);
        cellAlpha = 0.6;

        float f = smoothstep(0.24, 0.25, cellDist);
        cellCol = mix(cellCol, vec3(0.75, 0.03, 0.04), f);
        cellAlpha = mix(cellAlpha, 0.9, f);

        f = smoothstep(0.3, 1.0, fbm(vec2(5.0 * cellDist, 15.0 * cellAngle)));
        cellCol = mix(cellCol, vec3(0.5, 0.01, 0.03), 2.2 * f);
        cellAlpha = mix(cellAlpha, 0.75, f);

        f = smoothstep(0.05, 0.1, cellDist);
        cellCol = mix(cellCol, vec3(0.0, 0.0, 0.0), 1.0 - f);
        cellAlpha = mix(cellAlpha, 0.92, 1.0 - f);

        f = smoothstep(0.25, 0.35, cellDist);
        cellCol = mix(cellCol, vec3(0.95, 0.06, 0.05), f);
        cellAlpha = mix(cellAlpha, 0.8, f);

        float pulse = 0.45 + 0.55 * sin(Time * 4.0 + cellDist * 12.0);
        cellAlpha *= 0.75 + 0.25 * pulse;
    } else {
        float n = fbm(4.0 * uvAspect);
        cellCol = vec3(0.14, 0.0, 0.02) * (0.55 + 0.45 * n);
        cellAlpha = 0.17 * (0.5 + 0.5 * n);
    }

    // ── Composite ──────────────────────────────────────────

    vec3 col = bloodBG * (1.0 - cellAlpha) + cellCol * cellAlpha;
    float alpha = cellAlpha * 0.92 + bgAlpha * (1.0 - cellAlpha * 0.5);

    // Vignette: darken edges (on RGB), keep alpha full-coverage
    float vignette = smoothstep(0.0, 0.55, 1.0 - length(texCoord * 2.0 - 1.0));

    col *= 0.15 + 0.85 * vignette;

    // ── Film grain ─────────────────────────────────────────
    float grain = hash(texCoord.x * ScreenSize.x * 1.37 + texCoord.y * ScreenSize.y * 2.11 + Time * 123.456);
    grain = grain * 2.0 - 1.0;
    col += grain * 0.07;
    alpha += grain * 0.03;

    alpha *= uIntensity;

    fragColor = vec4(col, alpha);
}
