#version 150 core

// ─────────────────────────────────────────────────────────────
//  tooltip 底板 · 热力学流动（顶点阶段）
//
//  几何是屏幕空间里的一个矩形，正好铺满绯红誓约 tooltip 的底板区域
//  （Java 侧算好 (x-4, y-4, w+8, h+8)，与 TooltipRenderUtil 实际涂到的
//   范围逐像素对齐，见 docs/crimson-vow-tooltip.md 第三节）。
//
//  UV0 是 0..1 的面板坐标：
//    texCoord.x = 0 左沿 → 1 右沿
//    texCoord.y = 0 上沿 → 1 下沿
//  片元阶段用它 + uPanelSize 换算出"像素级"的局部坐标，所以面板大小变了
//  热流的粗细也不会跟着拉伸。
//
//  与项目里其它 GUI 着色器（rendertype_gui_collapsar_bar.vsh /
//  rendertype_gui_glow.vsh）一样：Java 侧传的是 GUI 缩放坐标，
//  ProjMat / ModelViewMat 由 RenderSystem 提供，这里只做一次标准变换。
// ─────────────────────────────────────────────────────────────

in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord    = UV0;
}
