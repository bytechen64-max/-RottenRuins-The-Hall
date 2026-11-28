#version 150 core

// ═════════════════════════════════════════════════════════════
//  绯红誓约 · 法阵上方的方块扭曲（顶点阶段）
//
//  几何是<b>开口圆柱的侧面</b>：Java 侧按 (高度, 角度) 网格发四边形，
//  没有任何端盖 —— 端盖会挡住视线，而且柱体的"边界"本来就该是软的。
//
//  Java 已经把 ModelViewMat 折进了顶点，所以 Position 就是视空间坐标，
//  这里只需要再过一道 ProjMat。片元阶段要用 vPos 反解视线方向
//  （相机在视空间原点 ⇒ normalize(vPos) 就是方向）。
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 vLocal;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    vLocal      = Position;
    vertexColor = Color;
}
