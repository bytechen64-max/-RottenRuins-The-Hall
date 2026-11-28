#version 150 core

// ============================================================
// Magic casting wheel - ambient aura behind the wheel.
// Additive (json blend: src_alpha / one).
//
// One quad covers the whole halo.  UV0 = 0..1 across it and the
// scene passes the quad rectangle in GUI pixels:
//
//     vec2 px = uQuadOrigin + vUv * uQuadSize;
//
// Layers, from back to front:
//   * wide breathing bloom that dies out by ~1.30 x uOuter
//   * 24 very slow narrow light beams
//   * a rotating rune ring just outside the rim (96 slots, every
//     6th slot a long/bright tick)
//   * 8 spokes / eight-pointed star hint
//   * a warm cone in the uHoverAngle direction (+-14 degrees)
//
// Every layer is radial-bounded and the whole radiance is multiplied
// by a quad-border fade, so the quad outline can never show up as a
// straight rectangle edge over the world background.
//
// The shader outputs alpha = 1 and puts every brightness term in
// rgb, because "src_alpha / one" then adds exactly rgb to the frame.
// uIntensity = 0 outputs transparent black (nothing added at all).
// ============================================================

uniform vec2  uCenter;
uniform vec2  uQuadOrigin;
uniform vec2  uQuadSize;
uniform float uOuter;        // wheel outer radius in GUI pixels
uniform float uInner;        // wheel inner radius in GUI pixels
uniform float uTime;
uniform float uHoverAngle;   // screen atan2 angle of the highlighted direction
uniform float uHoverStrength;// 0..1
uniform float uIntensity;
uniform vec4  uColorA;       // gold
uniform vec4  uColorB;       // cyan
uniform vec4  uColorC;       // violet

in vec2 vUv;
out vec4 fragColor;

const float TAU = 6.28318530718;
const float PI  = 3.14159265359;

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

/** Distance in pixels from this fragment to the nearest quad border. */
float quadBorderDistance(vec2 px) {
    vec2 lo = px - uQuadOrigin;
    vec2 hi = uQuadOrigin + uQuadSize - px;
    return min(min(lo.x, lo.y), min(hi.x, hi.y));
}

void main() {
    float intensity = clamp(uIntensity, 0.0, 1.0);
    if (intensity < 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    vec2  px    = uQuadOrigin + vUv * uQuadSize;
    vec2  rel   = px - uCenter;
    float r     = length(rel);
    float theta = atan(rel.y, rel.x);

    float outer = max(uOuter, 1.0);
    float inner = max(uInner, 1.0);

    // border guard: 0 at the quad outline, 1 inside it
    float edgeFade = smoothstep(0.0, 15.0, quadBorderDistance(px));

    vec3 gold   = uColorA.rgb;
    vec3 cyan   = uColorB.rgb;
    vec3 violet = uColorC.rgb;
    vec3 radiance = vec3(0.0);

    // ── 1. wide bloom behind / around the wheel (dies by 1.30 x outer) ──
    float outside = max(r - outer, 0.0);
    float bloom = exp(-outside / (outer * 0.150))
                * (1.0 - smoothstep(outer * 1.02, outer * 1.30, r));
    vec3 bloomCol = mix(violet, cyan, smoothstep(outer * 0.90, outer * 1.30, r));
    bloomCol = mix(bloomCol, gold, 0.16);
    radiance += bloomCol * bloom * 0.150;

    // thin breathing rim light on both sides of the wheel edge
    float rim = exp(-pow((r - outer * 1.015) / (outer * 0.035), 2.0));
    radiance += mix(gold, vec3(1.0, 0.93, 0.75), 0.35) * rim
              * (0.050 + 0.016 * sin(uTime * 1.1));

    // ── 2. 24 very slow narrow beams ──
    float beamAngle = theta - uTime * 0.060;
    float beam = pow(0.5 + 0.5 * cos(beamAngle * 24.0), 7.0);
    float beamRad = smoothstep(inner * 0.55, outer * 0.55, r)
                  * (1.0 - smoothstep(outer * 0.92, outer * 1.22, r));
    vec3 beamCol = mix(cyan, gold, 0.5 + 0.5 * sin(theta * 2.0 - uTime * 0.07));
    radiance += beamCol * beam * beamRad * 0.032;

    // six broader, slower rays so the field is not perfectly regular
    float wideBeam = pow(0.5 + 0.5 * cos(theta * 6.0 + uTime * 0.04 + 1.0), 2.5);
    radiance += gold * wideBeam
              * smoothstep(inner * 0.70, outer * 0.85, r)
              * (1.0 - smoothstep(outer * 0.98, outer * 1.18, r)) * 0.016;

    // ── 3. rotating rune ring just outside the rim ──
    float runeBase = outer + 4.5;
    float ringLine = exp(-pow((r - runeBase) / 0.8, 2.0));
    radiance += gold * ringLine * 0.10;

    float rot  = uTime * 0.055;                    // rad/s, slow clockwise drift
    float cell = (theta - rot) / TAU * 96.0;
    float idx  = floor(cell);
    float frac = fract(cell);
    float dw   = abs(frac - 0.5) * 2.0;            // 0 at the slot centre
    float isLong = 1.0 - step(0.5, mod(idx, 6.0)); // every 6th slot is long
    float tickLen = mix(8.0, 12.5, isLong);
    float tickAngular = smoothstep(0.20, 0.06, dw);
    float tickRadial = smoothstep(runeBase + 0.2, runeBase + 0.9, r)
                     * (1.0 - smoothstep(outer + tickLen - 0.9, outer + tickLen, r));
    float tick = tickAngular * tickRadial;

    float h = hash21(vec2(idx, 7.3));
    vec3  tickCol = mix(gold, cyan, step(0.82, h));
    float tickBright = mix(0.070, 0.150, isLong) * (0.70 + 0.60 * h);
    radiance += tickCol * tick * tickBright;

    // faint inner glow of the rune ring so the ticks sit on light
    radiance += gold * tickAngular
              * exp(-pow((r - (outer + 6.5)) / 5.0, 2.0)) * 0.012;

    // ── 4. eight spokes / eight-pointed star hint ──
    float spoke = pow(abs(cos(4.0 * (theta - 0.06 * uTime))), 30.0);
    float spokeRad = smoothstep(inner * 0.30, inner * 1.05, r)
                   * (1.0 - smoothstep(outer * 0.88, outer * 1.05, r));
    radiance += mix(violet, gold, 0.45) * spoke * spokeRad * 0.042;

    // tiny orbit beads on the eight-pointed star
    float bead = pow(0.5 + 0.5 * cos(theta * 8.0 - uTime * 0.35), 40.0);
    radiance += gold * bead * spokeRad * 0.022;

    // ── 5. warm cone in the highlighted direction (+-14 degrees) ──
    // Angular + radial bounds are both hard: the cone may only live
    // between inner*0.85 and outer*1.14, never near the quad border.
    float hs = clamp(uHoverStrength, 0.0, 1.0);
    if (hs > 0.001) {
        float ad = abs(mod(theta - uHoverAngle + PI, TAU) - PI);
        float cone = smoothstep(0.245, 0.020, ad);
        float coneRad = smoothstep(inner * 0.85, inner * 1.9, r)
                      * (1.0 - smoothstep(outer * 1.05, outer * 1.14, r));
        float coneRays = 0.75 + 0.25 * sin(ad * 60.0 + uTime * 2.0);
        radiance += mix(gold, vec3(1.0, 0.94, 0.76), 0.40)
                  * cone * coneRad * coneRays * hs * 0.100;

        // hairline centre of the cone
        float axis = exp(-pow(ad / 0.012, 2.0));
        radiance += gold * axis * coneRad * hs * 0.085;

        // light pulse travelling outwards along the cone
        float pulseR = inner + mod(uTime * 26.0, max(outer * 1.05 - inner, 1.0));
        float pulse = exp(-pow((r - pulseR) / 4.0, 2.0)) * cone * hs;
        radiance += gold * pulse * 0.055;
    }

    // ── 6. very light noise so the gradient never bands ──
    radiance *= 0.92 + 0.16 * vnoise(px * 0.055 + vec2(0.0, uTime * 0.03));

    // border guard: guarantees the quad outline is never visible
    radiance *= edgeFade;

    fragColor = vec4(radiance * intensity, 1.0);
}
