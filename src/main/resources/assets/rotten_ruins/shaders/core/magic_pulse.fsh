#version 150 core

// ============================================================
// Spell constellation GUI - one-shot light pulse.
//
// Drawn once when a spell is placed / moved into a slot: a small
// comet runs along one connection and disappears (no loop).
// One quad per pulse.  UV0.x = 0..1 along the link, UV0.y = 0..1
// across the ribbon.  Additive blend, same as the constellation.
// ============================================================

uniform float uProgress;
uniform vec4  uColor;
uniform float uIntensity;

in vec2 vUv;
out vec4 fragColor;

void main() {
    float across = abs(vUv.y - 0.5) * 2.0;
    float core = pow(max(1.0 - across, 0.0), 3.0);

    // Distance from the head, in "link length" units.
    float d = vUv.x - uProgress;

    // Head: a tight glowing dot.
    float head = exp(-d * d * 2800.0);

    // Tail: only behind the head, short and soft.
    float tail = d < 0.0 ? exp(d * 11.0) : 0.0;
    tail *= 0.45;

    float alpha = (head + tail) * core * uIntensity;

    // Fade at both ends so the dot never pops out of the slot frame.
    alpha *= smoothstep(0.0, 0.05, vUv.x)
           * (1.0 - smoothstep(0.95, 1.0, vUv.x));

    // Whiter at the head, warmer in the tail.
    vec3 rgb = mix(uColor.rgb, vec3(1.0, 0.98, 0.90), head);
    fragColor = vec4(rgb, alpha);
}
