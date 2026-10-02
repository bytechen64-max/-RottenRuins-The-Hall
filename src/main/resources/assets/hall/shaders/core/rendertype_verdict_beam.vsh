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
uniform mat3 uNormalToView;

out vec4 vertexColor;
out vec3 vLocal;
out vec3 vRadial;
/** 视图空间的径向法线（已归一化）。z > 0 表示该面背对相机。 */
out vec3 vRadialView;

void main() {
    // 顶点变换与第一版完全一致：poseStack 已由 EntityRenderDispatcher 处理了
    // 相机旋转与"实体位置 − 相机位置"的平移，顶点喂进来就已经在视图空间里，
    // 所以这里只差一次投影。
    //
    // 扩散（缩放）**不在这里做**：它由 Java 侧压进 poseStack 的矩阵
    // （push → scale → pop），于是"位移"和"缩放"待在同一个地方。
    // 曾经把缩放写在这一行上（`Position * scale`），它会把矩阵里的平移一起乘掉，
    // 柱子会从脚底上方几百格开始长 —— 那一版的症状就是"光柱没固定在坐标上"。
    gl_Position = ProjMat * vec4(Position, 1.0);

    // 局部坐标原样传出：片元侧靠它算径向距离与高度，也是第一版的语义。
    vLocal      = Position;
    // 径向法线不受等比缩放影响（方向不变）；本几何的侧面法线 Y 分量为 0，
    // 且 x/z 同比，所以旋转矩阵给出的法线方向正确。
    vRadial     = Normal;
    vertexColor = Color;

    // 归一化之后再转换。几何上 Normal 本来就是单位向量，但插值到面片中间会变短，
    // 所以片元里还会再归一化一次（见 .fsh 的 ⓪ 段）。
    vRadialView = uNormalToView * normalize(Normal);
}
