#version 150 core

// ─────────────────────────────────────────────────────────────
//  冲击波（顶点阶段）
//
//  几何是一个以爆心为球心、半径 = 当前波前半径的球壳。
//  Java 侧已经把 ModelViewMat 设为 view 矩阵（不含世界平移），
//  所以 Position 输出后即为真正的视图空间坐标。
//
//  片元阶段需要四样东西：
//    vPos       —— 视图空间片元位置。相机在视图空间原点，因此
//                  normalize(vPos) 就是该像素的视线方向。
//    vCenterVS  —— 爆心在视图空间的位置，用来算“从爆心指向该像素”
//                  的方向，把畸变严格约束在径向（沿爆心的透视方向，
//                  而不是屏幕中心）。
//    vWaveRadius —— 球壳的真实半径（方块）。
//                  这是刻意用 uniform 传进来的：如果片元阶段用
//                  length(vPos) 反推半径，拿到的是**插值后**的量，
//                  它会随三角形边界一格格抖动（球面网格 40x96，
//                  相邻顶点相差约 1.4%），而半径直接决定波前环的
//                  带宽与位置 —— 于是屏幕上就出现沿网格走的锯齿。
//                  改成 uniform 之后半径在整个球面上是常数，
//                  锯齿消失。
// ─────────────────────────────────────────────────────────────

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ProjMat;
uniform vec3 uCenterVS;
uniform float uWaveRadius;

out vec4 vertexColor;
out vec3 vNormal;
out vec3 vPos;
out vec3 vCenterVS;
out float vWaveRadius;

void main() {
    gl_Position  = ProjMat * vec4(Position, 1.0);
    vPos         = Position;
    vNormal      = Normal;
    vertexColor  = Color;
    vCenterVS    = uCenterVS;
    vWaveRadius  = uWaveRadius;
}
