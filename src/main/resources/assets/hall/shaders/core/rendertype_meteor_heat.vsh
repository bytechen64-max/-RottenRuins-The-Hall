#version 150 core

// 陨石热浪折射：一块正对镜头的四边形（顶点契约与其它陨石特效一致）。

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ProjMat;

out vec4 vColor;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    vColor = Color;
}
