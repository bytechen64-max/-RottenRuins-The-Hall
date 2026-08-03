#version 150 core

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 OutlineColor;
uniform float Time;
uniform float FlowSpeed;
uniform float GradientSpan;
uniform int ColorCount;
uniform int Color0, Color1, Color2, Color3, Color4, Color5, Color6, Color7;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 unpackColor(int c) {
    return vec3(
        float((c >> 16) & 0xFF) / 255.0,
        float((c >> 8) & 0xFF) / 255.0,
        float(c & 0xFF) / 255.0
    );
}

vec3 palette(float t) {
    if (ColorCount <= 1) return unpackColor(Color0);

    int colors[8] = int[8](Color0, Color1, Color2, Color3, Color4, Color5, Color6, Color7);
    t = fract(t);
    float scaled = t * float(ColorCount - 1);
    int idx = clamp(int(floor(scaled)), 0, ColorCount - 2);
    float frac = fract(scaled);
    return mix(unpackColor(colors[idx]), unpackColor(colors[idx + 1]), frac);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.05) discard;

    // Flowing gradient across the item width
    float flow = (texCoord0.x * GradientSpan) - Time * FlowSpeed;
    vec3 col = palette(flow);

    // Brightness from texture as mask
    float brightness = (tex.r + tex.g + tex.b) / 3.0;
    float alpha = brightness * OutlineColor.a * vertexColor.a;

    fragColor = vec4(col * alpha, alpha) * ColorModulator;
}
