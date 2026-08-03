#version 150 core

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in vec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uv0;
out vec2 uv1;
out vec2 uv2;
out vec3 normal;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    vertexColor = Color;
    uv0 = UV0;
    uv1 = UV1;
    uv2 = UV2;
    normal = normalize(mat3(ModelViewMat) * Normal);
}
