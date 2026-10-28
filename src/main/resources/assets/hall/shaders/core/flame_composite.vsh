#version 150 core

// 火焰合成专用的顶点着色器：和 outline_ring.vsh 一样吐全屏四边形，
// 但**深度由 uniform 指定** —— 这是"火焰被方块挡住"的关键。
//
// 为什么需要它：火焰在 renderLevel 的 TAIL 合成，而 vanilla 在画手之前就把世界深度清掉了，
// 那一步已经没有可比的深度。所以世界物品的火焰改在**捕获点**合成（那里世界深度还在），
// 并且让四边形带上"物品所在深度平面"的 z：
//     · 挡在物品**前面**的方块 → 深度更小 → LEQUAL 失败 → 火焰被真正挡住；
//     · 物品**后面**的东西 → 深度更大 → 通过 → 火焰照常画出来；
//     · 羽流高度不受任何限制（不像"可见性包络"那样被模糊支撑域卡死）。
//
// 手持物品不走这条路（它本来就在最前面，测深度反而会被自己的手挡掉）。

in vec3 Position;
in vec2 UV0;

uniform float FlameDepthNdc;   // 物品所在深度平面的 NDC z（CPU 用 modelView × projection 算出）

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, FlameDepthNdc, 1.0);
    texCoord = UV0;
}
