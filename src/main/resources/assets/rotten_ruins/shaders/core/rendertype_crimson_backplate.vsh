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
//
//  ⚠ UV0 传的是**单位平面局部坐标 (x, y)**，不是 (r, θ)。原因见
//  CrimsonVowBackplateMesh 的类注释：扇形三角形的顶点角度线性插值不是真实
//  角度，48 条三角形边上会有 48 次角度跳变，一切依赖 θ 的图案都会被撕成
//  "花瓣"。位置是线性插值正确的量，角度必须在片元里 atan() 现算。
// ─────────────────────────────────────────────────────────────────────────

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

/** 单位平面局部坐标：x 向右、y 向上，半径 1.0 = 内切于单位方框的圆。 */
out vec2 vLocal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vLocal = UV0;
}
