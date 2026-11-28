#version 150

// ── mask 效果层（方块版）：death_core ─────────────────────────────────
//
// 顶点侧只做三件事：
//   1. 常规 MVP 变换；
//   2. 把「视空间位置」传给片元 —— Ghosts 原作用屏幕像素当光线方向，这里用
//      相机到片元的方向（视空间位置归一化）还原等价的光线，再在 fsh 里用
//      IViewRotMat 转回世界方向。不用屏幕 NDC 是因为 NDC 除过 w，作为
//      varying 在近平面裁切/跨相机平面时会在屏幕上留下一条裂缝；
//      视空间位置是线性变换结果，插值始终连续。
//   3. 传方块的雾距离 / 颜色 / 遮罩 UV（UV0 已被渲染器重映射到遮罩 sprite）。

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;

    viewPos = view.xyz;

    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
}
