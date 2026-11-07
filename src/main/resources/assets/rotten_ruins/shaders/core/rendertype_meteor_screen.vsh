#version 150 core

// ═════════════════════════════════════════════════════════════
//  陨石事件 · 全屏 3bit 红黑爆炸屏效（顶点阶段）
//
//  几何是一个 [0,1]² 的四边形，Java 侧把投影临时换成像素空间正交，
//  所以这里只要 ProjMat * Position 即可 —— 不读 ModelViewMat，
//  也就不会被世界 pass 留下来的模型矩阵影响。
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec4 Color;

uniform mat4 ProjMat;

out vec2 vUV;
out vec4 vColor;

void main() {
    gl_Position = ProjMat * vec4(Position.xy, 0.0, 1.0);
    vUV = Position.xy;
    vColor = Color;
}
