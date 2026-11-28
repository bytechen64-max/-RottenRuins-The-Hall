#version 150 core
in vec3 Position; in vec4 Color; in vec3 Normal;
uniform mat4 ProjMat;
out vec4 vertexColor; out vec3 vNormal; out vec3 vPos;
void main() {
    // 顶点已在 Java 侧经 modelView（视图*平移*缩放）变换为视图空间坐标，
    // 这里仅做投影。vPos 为球面表面点（视图空间），供片元重建视线方向。
    gl_Position = ProjMat * vec4(Position, 1.0);
    vertexColor = Color; vNormal = Normal; vPos = Position.xyz;
}
