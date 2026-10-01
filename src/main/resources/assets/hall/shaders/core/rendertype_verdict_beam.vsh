#version 150 core

// ═════════════════════════════════════════════════════════════
//  天穹裁决 · 垂直光柱（顶点阶段）
//
//  几何是一个沿 +Y 的圆柱侧面，底面中心在实体位置（玩家脚底）。
//  Java 侧已经把 ModelViewMat 设为 view 矩阵（不含世界平移），
//  所以 Position 输出后即为真正的视图空间坐标 —— 与冲击波同一套约定。
//
//  片元阶段需要四样东西：
//    Position  —— 视图空间片元位置（用于噪声采样）
//    vLocal    —— 模型空间局部位置。光柱是轴对齐的，于是：
//                   length(vLocal.xz) = 到光柱轴线的距离（径向）
//                   vLocal.y          = 沿光柱的高度
//                 两者都不需要额外的自定义属性，直接用 Position 就能算。
//    vRadial   —— 径向膨胀法线（Normal 属性传的是归一化的 (x, 0, z)）。
//                 片元用它做"内侧被点亮"的边缘光：|vRadial| 在正对视线的
//                 那一侧接近 0、在剪影边缘接近 1，于是得到一个免费的菲涅尔。
//    Color     —— 顶点色。Java 侧把"贴图坐标"塞进了 color.rg：
//                   r = 径向归一化 0..1
//                   g = 高度归一化 0..1
//                 这样即使将来光柱改用非圆柱几何（锥形收束），
//                 片元侧那两行取 r/g 的代码也不用动。
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
