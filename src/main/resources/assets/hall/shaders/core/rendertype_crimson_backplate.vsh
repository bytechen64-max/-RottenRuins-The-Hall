#version 150 core

// ─────────────────────────────────────────────────────────────────────────
//  绯红誓约背板（顶点阶段）
//
//  几何是一个单位平面上的扇形（x, y ∈ [-1, 1]，见 CrimsonVowBackplateMesh）。
//  它走的是**实体渲染通道**：RenderLayer 拿到的 PoseStack 里已经含了
//  「相机相对平移 × 实体朝向」，而 billboard 需要的"只有位置、没有朝向"
//  那个矩阵是在 Java 侧直接覆盖写进去的（见 CrimsonVowBackplateRig.transform），
//  所以这里的矩阵约定就是标准的 ModelViewMat × ProjMat。
//
//  —— 注意与 rendertype_collapsar_halo.vsh 的区别：那边把世界坐标烘成
//  相机相对坐标后**只乘 ProjMat**，两边的 Position 语义不同，不能照抄。
// ─────────────────────────────────────────────────────────────────────────

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

/** 到板中心的距离：1.0 = 内切圆，1.414 = 方框角上。 */
out float vRadial;
/** 方位角（弧度，裸值 —— 接缝处必须连续，见 Mesh 的类注释）。 */
out float vTheta;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vRadial = UV0.x;
    vTheta  = UV0.y;
}
