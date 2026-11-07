#version 150 core

// ═════════════════════════════════════════════════════════════
//  裁决领域 · 地面光纹（顶点阶段）
//
//  几何是本地点面中心的一个**水平圆盘**（三角扇，中心 + 若干同心环）。
//  Java 侧已把 ModelViewMat 设为 view 矩阵，所以 Position 即视图空间坐标。
//
//  片元阶段需要的是极坐标，而圆盘本身就是极坐标网格，
//  于是 Normal 属性里塞的就是归一化极坐标 —— 不做插值从笛卡尔反算：
//    vRadial.x = 径向归一化 0..1
//    vRadial.y = 角度归一化 0..1
//  逐顶点给定、线性插值之后，角度在扇区里是单调的，
//  足够画六分对称的光纹（不需要真正的 atan）。
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 vLocal;
out vec3 vRadial;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    vLocal      = Position;
    vRadial     = Normal;
    vertexColor = Color;
}
