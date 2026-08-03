#version 150 core

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 OutlineColor;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);

    // Discard fully transparent pixels — only render where item has visible texture
    if (texColor.a < 0.05) discard;

    // Solid outline color with edge softness
    fragColor = vec4(OutlineColor.rgb, OutlineColor.a * vertexColor.a) * ColorModulator;
}
