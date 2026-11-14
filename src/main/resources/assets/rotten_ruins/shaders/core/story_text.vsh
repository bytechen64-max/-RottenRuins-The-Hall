#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;
out float glyphDissolve;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Story text is always full bright. UV2.x is reserved for independent glyph dissolve progress.
    vertexColor = Color * texelFetch(Sampler2, min(ivec2(15), textureSize(Sampler2, 0) - 1), 0);
    glyphDissolve = clamp(float(UV2.x) / 32767.0, 0.0, 1.0);
    texCoord = UV0;
}
