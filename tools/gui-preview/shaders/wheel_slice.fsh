#version 150 core

// ============================================================
// Magic casting wheel - ONE sector  (tool-private shader).
//
// The scene draws a single quad covering the whole wheel bounding
// box and tells the fragment shader where that quad lives in GUI
// pixels (uQuadOrigin / uQuadSize). UV0 = 0..1 across the quad:
//
//     vec2 px = uQuadOrigin + vUv * uQuadSize;
//
// Angles follow screen space (y points down):
//     0 = right, +pi/2 = down, so positive angles run clockwise.
//
// Blend in the json: srcalpha / 1-srcalpha  (glassy sector panel).
// States: idle / hover / selected / locked / cooldown are all driven
// by uniforms; the sector never changes shape on the CPU side.
//
// IMPORTANT: every emissive term is zeroed before the quad border
// (quadBorderDistance + halo hard cutoff). The quad is only 2.2x the
// wheel radius, so a glow that runs past the border would show up as
// a straight rectangle edge in the frame - never allow that.
// ============================================================

uniform vec2  uCenter;
uniform vec2  uQuadOrigin;
uniform vec2  uQuadSize;
uniform float uRadius;       // outer radius in GUI pixels
uniform float uInnerRadius;  // inner radius (the empty hub)
uniform float uStartAngle;   // sector start, radians (screen atan2)
uniform float uEndAngle;     // sector end,   radians (screen atan2)
uniform vec4  uColorA;       // bright rim / outer colour
uniform vec4  uColorB;       // deep panel colour
uniform float uTime;
uniform float uHover;        // 0..1
uniform float uSelected;     // 0..1
uniform float uLocked;       // 0..1
uniform float uCooldown;     // 0 = ready, 1 = fully cooling down
uniform float uIntensity;    // 0 -> fully transparent

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

    float hover    = clamp(uHover, 0.0, 1.0);
    float selected = clamp(uSelected, 0.0, 1.0);
    float locked   = clamp(uLocked, 0.0, 1.0);
    float cooldown = clamp(uCooldown, 0.0, 1.0);
    float state    = clamp(hover * 0.75 + selected, 0.0, 1.0);

    // quad border guard: nothing additive survives near the quad outline
    float border = quadBorderDistance(px);
    float edgeFade = smoothstep(0.0, 18.0, border);

    // ── geometry: hovered / selected sectors grow 4..7 px outward ──
    float lift   = max(hover * 5.0, selected * 6.5);
    float outerR = uRadius + lift;
    float innerR = uInnerRadius;

    // ── angular envelope, measured in screen-space arc pixels ──
    float span = clamp(uEndAngle - uStartAngle, 0.0001, TAU);
    float d    = mod(theta - uStartAngle, TAU);
    float arcStart = d * r;
    float arcEnd   = (span - d) * r;

    // 1..2 px feather + a real seam between the sectors
    float gap     = 1.25;
    float feather = 1.45;
    float angMask = smoothstep(gap, gap + feather, arcStart)
                  * smoothstep(gap, gap + feather, arcEnd);
    // wide version: lets soft glows bleed a little past the seam
    float angWide = smoothstep(-1.0, 6.0, arcStart) * smoothstep(-1.0, 6.0, arcEnd);

    // ── radial envelope ──
    float body = smoothstep(innerR - 0.7, innerR + 1.3, r)
               * (1.0 - smoothstep(outerR - 1.5, outerR + 0.3, r));
    float t = clamp((r - innerR) / max(outerR - innerR, 1.0), 0.0, 1.0);

    // ── glass body: deep interior lifted towards a bright outer rim ──
    vec3 deep = uColorB.rgb;
    vec3 rim  = uColorA.rgb;
    vec3 col  = mix(deep, rim, smoothstep(0.10, 1.0, pow(t, 1.35)) * 0.92);
    col += rim * pow(t, 5.0) * 0.22;
    col *= mix(0.52, 1.0, smoothstep(0.0, 0.32, t));   // inner shadow at the hub

    // ── faint surface texture: grain + slow angular ribs ──
    float grain = vnoise(px * 0.30) * 0.55 + vnoise(px * 1.10 + 17.0) * 0.45;
    col *= 0.93 + 0.15 * grain;
    float rib = 0.5 + 0.5 * sin(d / span * 44.0 + sin(theta * 7.0) * 1.5);
    col *= 0.985 + 0.024 * rib;
    col *= 0.985 + 0.030 * (0.5 + 0.5 * sin(theta * 9.0 + uTime * 0.25));

    // ── bright arc lines on the inner and outer edge ──
    // edgeFade is applied to every ADDITIVE term below, so the emissive
    // parts die out long before the quad border (no straight rectangle
    // edge over the night-sky backdrop), whatever quad size the scene uses.
    vec3  lineColor = mix(rim, vec3(1.0), 0.34);
    float lineSize  = 0.95 + state * 0.35;
    float innerLine = exp(-pow((r - (innerR + 1.0)) / lineSize, 2.0));
    float outerLine = exp(-pow((r - (outerR - 1.0)) / lineSize, 2.0));
    float lineBoost = 0.55 + 0.45 * state;
    col += lineColor * (innerLine * 0.50 + outerLine * 0.90) * lineBoost * edgeFade;

    // ── cooldown: darken from the start angle clockwise + sweep edge ──
    float cdMask = 0.0;
    float sweep  = 0.0;
    if (cooldown > 0.001) {
        float boundary = span * cooldown;
        float side = (d - boundary) * r;                 // px from the sweep edge
        cdMask = 1.0 - smoothstep(-1.2, 1.2, side);      // 1 inside the cooled part
        float inSector = 1.0 - smoothstep(span * r - 0.5, span * r + 0.5, arcStart);
        sweep = exp(-pow(side / 1.25, 2.0)) * inSector;
        vec3 coldTint = vec3(0.34, 0.55, 0.88);
        vec3 cooled   = mix(col * 0.16, coldTint * 0.055, 0.30);
        col = mix(col, cooled, cdMask * 0.94);
    }
    // glows must not paint light back onto the cooled part
    float stateGlow = state * (1.0 - cdMask * 0.85);

    // ── fine ticks hugging the inner edge (texture + cooldown readout) ──
    float tickCell    = d / span * 7.0;
    float tickFrac    = abs(fract(tickCell + 0.5) - 0.5);
    float tickAngular = smoothstep(0.075, 0.012, tickFrac);
    float tickRadial  = smoothstep(innerR + 0.6, innerR + 1.3, r)
                      * (1.0 - smoothstep(innerR + 3.4, innerR + 4.1, r));
    float tick = tickAngular * tickRadial;
    col += lineColor * tick * mix(0.16, 0.55, max(cdMask, state * 0.6)) * edgeFade;

    // ── locked: desaturate, pull cold blue, dim ──
    if (locked > 0.001) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 cold = vec3(0.34, 0.47, 0.78);
        vec3 frozen = mix(vec3(lum) * 0.82, cold * (0.22 + lum * 0.95), 0.85);
        col = mix(col, frozen, locked * 0.92) * mix(1.0, 0.60, locked);
    }

    // ── hover / selected: breathing rim light + inner wash ──
    float pulse = 0.5 + 0.5 * sin(uTime * 2.4 - t * 2.6 + d * 5.0);
    col += rim * stateGlow * (0.10 + 0.18 * pulse) * smoothstep(0.22, 1.0, t) * edgeFade;
    col += rim * stateGlow * 0.10 * (1.0 - t) * (1.0 - t) * edgeFade;

    // ── cooldown sweep edge (kept bright, so it reads as "time left") ──
    if (cooldown > 0.001) {
        col += mix(rim, vec3(1.0), 0.45) * sweep * 1.55 * edgeFade;
    }

    // ── outer glow when hovered / selected ──
    // Hard radial cutoff + quad border fade: the glow can never touch the
    // quad border (that is what produced the "rectangle haze" in the HUD).
    float haloW = 2.5 + hover * 1.5 + selected * 2.0;
    float outside = max(r - outerR, 0.0);
    float halo = exp(-outside / max(haloW, 0.5))
               * (1.0 - smoothstep(outerR + haloW * 0.45, outerR + haloW, r))
               * step(outerR - 2.0, r)
               * edgeFade;
    col += mix(rim, vec3(1.0), 0.20) * halo * (0.10 + stateGlow * 0.55) * angWide;

    // ── alpha ──
    float baseAlpha = mix(0.72, 0.88, state);
    baseAlpha = mix(baseAlpha, 0.86, locked);
    float alpha = body * angMask * baseAlpha;
    alpha += body * angMask * (innerLine * 0.30 + outerLine * 0.50) * lineBoost * edgeFade;
    alpha += body * angMask * tick * 0.22 * edgeFade;
    alpha += sweep * body * angMask * 0.85 * edgeFade;
    alpha += halo * angWide * (0.06 + stateGlow * 0.22);
    alpha = clamp(alpha * intensity, 0.0, 1.0);
    // absolute border guard (a few px, invisible for the body itself)
    alpha *= smoothstep(0.0, 2.0, border);

    fragColor = vec4(col, alpha);
}
