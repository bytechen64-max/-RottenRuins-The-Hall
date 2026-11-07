#version 150 core

// ============================================================
// Spell constellation GUI - one glowing link between two slots.
// One quad per link.  UV0.x = distance along the link (already
// scaled to roughly 64 px per unit by the Java side), UV0.y = 0..1
// across the ribbon.  Additive blend in the json.
// ============================================================

uniform float uTime;
uniform vec4  uColor;
uniform float uIntensity;

in vec2 vUv;
out vec4 fragColor;

void main() {
    // 0 at the centre line, 1 at either edge.
    float across = abs(vUv.y - 0.5) * 2.0;

    // Hairline core + a very faint, thin sheen.  No wide bloom: a map
    // line should stay a line, not a glowing tube.
    float core  = pow(max(1.0 - across, 0.0), 6.0);
    float sheen = pow(max(1.0 - across, 0.0), 2.4) * 0.10;

    // Very slow, large-scale shimmer.  No travelling beads: the light
    // should feel like it is breathing, not like a particle on a rail.
    float shimmer = 0.88
        + 0.12 * sin(vUv.x * 1.1 - uTime * 0.35
                     + sin(vUv.x * 0.47 + uTime * 0.17) * 1.7);

    float alpha = (core * 0.62 + sheen) * shimmer * uIntensity;

    // Slightly whiter where the line is at its thinnest.
    vec3 rgb = mix(uColor.rgb, vec3(1.0, 0.97, 0.88), core * 0.40);
    fragColor = vec4(rgb, alpha);
}
