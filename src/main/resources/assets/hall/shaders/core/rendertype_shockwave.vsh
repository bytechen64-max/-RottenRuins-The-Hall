#version 150 core
in vec3 Position; in vec4 Color; in vec3 Normal;
uniform mat4 ProjMat;
out vec4 vertexColor; out vec3 vNormal; out vec3 vPos;
void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    vertexColor = Color; vNormal = Normal; vPos = Position.xyz;
}
