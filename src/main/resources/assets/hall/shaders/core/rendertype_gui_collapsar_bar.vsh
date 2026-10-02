#version 150 core

// ─────────────────────────────────────────────────────────────
//  坍缩使徒 boss 血条（顶点阶段）
//
//  几何是屏幕空间里的一张贴图大小的矩形（182×32 GUI 像素），
//  UV0 直接是 0..1 的条形坐标：
//    texCoord.x = 0 左端（血条空）→ 1 右端（血条满）
//    texCoord.y = 0 上沿 → 1 下沿
//
//  与项目里其它 GUI 着色器（见 rendertype_gui_glow.vsh）一样：
//  Java 侧传的是 GUI 缩放坐标，ProjMat / ModelViewMat 由 RenderSystem 提供，
//  所以这里只做一次标准变换，不做任何世界/相机相关的事情。
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
