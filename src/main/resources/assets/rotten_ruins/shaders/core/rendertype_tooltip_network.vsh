#version 150 core

// tooltip 底板 · 细胞网络（顶点阶段）：GUI 空间的一个矩形，
// ProjMat / ModelViewMat 由 RenderSystem 提供，UV0 为面板 0..1 坐标。
in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord    = UV0;
}
