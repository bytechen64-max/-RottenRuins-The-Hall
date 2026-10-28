#version 150 core

// ═════════════════════════════════════════════════════════════
//  绯红誓约 · 终幕「绯红之线」—— 顶点阶段
//
//  全屏叠加通道：几何是一个铺满屏幕的四边形，顶点只做一件事 ——
//  用 ProjMat 把 Position 送到裁剪空间。
//
//  · 这里**没有** ModelViewMat：顶点坐标已经是对齐到正交投影的
//    「投影空间」角点，再乘一次视图矩阵会把四边形推出屏幕。
//  · UV0 本效果完全不用（片元只读 gl_FragCoord，json 的 samplers 也为空），
//    但仍按 json 的 attributes 声明原样透传，保持与同族 GUI 通道一致。
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec2 UV0;
uniform mat4 ProjMat;
out vec2 vUV;
void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    vUV         = UV0;
}
