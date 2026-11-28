#version 150 core

uniform sampler2D IconTexture;
uniform vec2 IconUVMin;
uniform vec2 IconUVMax;
uniform vec4 GlowColor;
uniform float uTime;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // distance from quad center (radial, 0..0.71)
    float dist = length(texCoord - 0.5);

    // smoothstep fade: fully bright at inner, fully gone at outer
    // outer=0.58 leaves 10px margin before square quad edge at 0.71
    float glow = 1.0 - smoothstep(0.252, 0.504, dist);

    if (glow < 0.003) discard;

    // icon-local uv for alpha modulation
    vec2 iconLocal = (texCoord - IconUVMin) / (IconUVMax - IconUVMin);
    vec2 samplePt = clamp(iconLocal, 0.0, 1.0);
    float iconAlpha = texture(IconTexture, samplePt).a;

    // kill glow in areas where icon has no content
    glow *= step(0.03, iconAlpha);

    float pulse = 0.86 + 0.14 * sin(uTime * 3.5 + dist * 5.0);
    float alpha = glow * GlowColor.a * pulse;

    if (alpha < 0.003) discard;

    fragColor = vec4(GlowColor.rgb, alpha);
}
