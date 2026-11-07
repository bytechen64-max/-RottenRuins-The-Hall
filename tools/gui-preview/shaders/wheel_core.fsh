#version 150 core

// ============================================================
// Magic casting wheel - central core.
// Additive (json blend: src_alpha / one). The scene draws a dark
// disc (and text) first; this orb glows on top of it.
//
//     vec2 px = uQuadOrigin + vUv * uQuadSize;
//
// Colour convention (matches WheelKit):
//   uColorA = primary / school colour  -> the orb's outer body
//   uColorB = gold chrome              -> hot centre, mana arc
// (The cooldown arc keeps a fixed icy blue so it always reads cold.)
//
// Layers:
//   * layered rotating swirl orb (sin filaments + fbm modulation),
//     a hot but SMALL centre so the scene's text stays readable
//   * mana arc at ~1.13 x uCoreRadius, filled clockwise from 12 o'clock,
//     with a faint track and a bright head bead
//   * cooldown arc just outside it, filling counter-clockwise (cold)
//   * charge pulse ring: radius grows with uCharge, fades as it grows,
//     and the centre flares up while charging
//
// Everything is border-faded, so the core quad (2.6x uCoreRadius side)
// can be dropped on any backdrop without showing a rectangle.
// ============================================================

uniform vec2  uCenter;
uniform vec2  uQuadOrigin;
uniform vec2  uQuadSize;
uniform float uCoreRadius;
uniform float uTime;
uniform float uMana;       // 0..1
uniform float uCharge;     // 0..1
uniform float uCooldown;   // 0..1
uniform vec4  uColorA;     // primary / school colour
uniform vec4  uColorB;     // gold chrome
uniform float uIntensity;

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

    float coreR  = max(uCoreRadius, 1.0);
    float rr     = r / coreR;
    float mana   = clamp(uMana, 0.0, 1.0);
    float charge = clamp(uCharge, 0.0, 1.0);
    float cd     = clamp(uCooldown, 0.0, 1.0);

    float edgeFade = smoothstep(0.0, 4.0, quadBorderDistance(px));

    vec3 school = uColorA.rgb;
    vec3 gold   = uColorB.rgb;
    vec3 radiance = vec3(0.0);

    // ── 1. layered swirl orb (hot small centre, school-coloured body) ──
    // Additive layering instead of colour-mixing school vs gold: mixing a
    // purple school colour with gold produces a muddy brown, adding light
    // keeps both hues saturated.
    float radial = exp(-pow(rr * 2.60, 2.0));                 // tight hot centre
    float orbShape = 1.0 - smoothstep(0.42, 1.0, rr);          // soft disc boundary
    float bodyGlow = orbShape * (0.24 + 0.62 * exp(-pow((rr - 0.38) * 1.7, 2.0)));

    vec2 rq1 = vec2(cos(theta - uTime * 0.55), sin(theta - uTime * 0.55))
             * (1.6 + rr * 5.0) + vec2(3.7, 1.9);
    vec2 rq2 = vec2(cos(theta + uTime * 0.31 + 1.7), sin(theta + uTime * 0.31 + 1.7))
             * (2.8 + rr * 8.0) - vec2(1.3, 4.1);
    float n1 = fbm(rq1);
    float n2 = fbm(rq2);

    float swirl = 0.0;
    swirl += 0.55 * pow(0.5 + 0.5 * sin(theta * 3.0 + rr * 7.0 - uTime * 1.35), 2.2);
    swirl += 0.38 * pow(0.5 + 0.5 * sin(theta * 5.0 - rr * 11.0 + uTime * 0.95 + 1.1), 2.6);
    swirl += 0.28 * pow(0.5 + 0.5 * sin(theta * 8.0 + rr * 19.0 - uTime * 2.05), 3.2);
    swirl *= 0.45 + 0.75 * n1 * (0.55 + 0.45 * n2);
    float filament = swirl * (0.55 + 0.45 * orbShape);

    radiance += school * bodyGlow * 0.80;
    radiance += mix(school, gold, 0.55) * filament * orbShape * 0.40;
    radiance += mix(gold, vec3(1.0, 0.98, 0.92), 0.60) * radial * (0.46 + 0.16 * charge);
    radiance += gold * exp(-pow((rr - 0.80) * 4.5, 2.0)) * 0.05;

    // ── 2. mana arc: from 12 o'clock, clockwise (gold chrome) ──
    // Thin and quiet on purpose: the arcs are chrome, the text on top of
    // the orb must stay readable. Radii sit ON the orb rim / just outside
    // it, well inside the scene's dark core disc, so they read as a thin
    // engraved gauge instead of a fat white halo.
    float manaR = coreR * 0.96 + 0.5;
    float dm = mod(theta + PI * 0.5, TAU);           // 0 at the top, grows clockwise
    float manaFill = 1.0 - smoothstep(mana * TAU - 0.018, mana * TAU + 0.018, dm);
    float manaArc  = exp(-pow((r - manaR) / 0.95, 2.0));
    float manaTrack = exp(-pow((r - manaR) / 0.9, 2.0)) * 0.020;
    vec3 manaCol = mix(gold, vec3(1.0, 0.97, 0.86), 0.15);
    radiance += manaCol * (manaTrack + manaArc * manaFill * 0.45);
    radiance += manaCol * exp(-pow((r - manaR) / 5.0, 2.0)) * manaFill * 0.024;

    // bright head bead of the mana arc
    if (mana > 0.015) {
        float headAng = -PI * 0.5 + mana * TAU;
        float hd = abs(mod(theta - headAng + PI, TAU) - PI);
        float bead = exp(-pow(hd / 0.026, 2.0)) * exp(-pow((r - manaR) / 2.6, 2.0));
        radiance += mix(gold, vec3(1.0), 0.45) * bead
                  * (0.10 + 0.10 * smoothstep(0.0, 0.12, mana));
    }

    // ── 3. cooldown arc: from 12 o'clock, counter-clockwise, icy ──
    if (cd > 0.001) {
        float coolR = manaR + max(3.0, coreR * 0.055);
        float dmc = mod(-PI * 0.5 - theta, TAU);     // grows counter-clockwise
        float coolFill = 1.0 - smoothstep(cd * TAU - 0.018, cd * TAU + 0.018, dmc);
        float coolArc = exp(-pow((r - coolR) / 0.90, 2.0));
        float coolTrack = exp(-pow((r - coolR) / 0.9, 2.0)) * 0.018;
        vec3 coolCol = mix(vec3(0.55, 0.83, 1.0), vec3(0.30, 0.55, 0.95), 0.35);
        radiance += coolCol * (coolTrack + coolArc * coolFill * 0.36);
        radiance += coolCol * exp(-pow((r - coolR) / 4.5, 2.0)) * coolFill * 0.018;

        // cold dust settling in the cooled region
        float dust = pow(0.5 + 0.5 * sin(theta * 46.0 + uTime * 0.8), 6.0)
                   * exp(-pow((r - coolR) / 6.0, 2.0)) * coolFill;
        radiance += coolCol * dust * 0.025;
    }

    // ── 4. charge pulse ring + centre flare ──
    if (charge > 0.002) {
        float pulseR = coreR * (0.40 + 0.85 * charge);
        float pulseW = 1.8 + 2.2 * charge;
        float fade   = 1.0 - 0.85 * charge;
        float ring = exp(-pow((r - pulseR) / pulseW, 2.0));
        radiance += mix(gold, vec3(1.0), 0.35) * ring * fade * 0.55;

        float echo = exp(-pow((r - pulseR * 0.58) / (pulseW * 0.8), 2.0));
        radiance += gold * echo * fade * 0.18;

        float flare = exp(-pow(rr * 3.4, 2.0)) * charge;
        radiance += mix(gold, vec3(1.0), 0.50) * flare * 0.36;
    }

    // ── 5. soft chrome halo around the whole core (very quiet) ──
    radiance += mix(gold, vec3(1.0, 0.92, 0.75), 0.35)
              * exp(-pow((r - coreR * 1.06) / (coreR * 0.22), 2.0)) * 0.016;

    // very light grain to break 8-bit banding
    radiance *= 0.96 + 0.08 * vnoise(px * 0.9 + 5.0);

    fragColor = vec4(radiance * intensity * edgeFade, 1.0);
}
