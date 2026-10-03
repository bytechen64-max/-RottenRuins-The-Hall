#version 150 core

// ═════════════════════════════════════════════════════════════
//  难度选择界面的「印记」—— 顶点阶段
//
//  ⚠ 顶点格式固定为 DefaultVertexFormat.POSITION_TEX（Position + UV0）。
//
//  为什么只用两个属性：调试过程中确认了一个事实 ——
//  顶点格式带第三个属性（POSITION_TEX_COLOR / POSITION_COLOR_NORMAL）时，
//  自定义着色器画不出任何东西；而只有 Position + UV0 时一切正常。
//  所以逐顶点参数一律编进 UV0 的两个通道，不再引入 Color / Normal。
//
//  UV0 约定：
//    UV0.x —— 径向左半段/右半段的标记：
//             < 0 表示"这一段从中线走向内缘"，> 0 表示"从中线走向外缘"
//             （顶点只放在中线上，所以 ±1，片元阶段据此判断衰减方向）
//    UV0.y —— 图案相位 = 图案倍数 × (θ + 自转 + 该环偏移)
//             相位在 0/2π 接缝处连续，插值不会跨圆
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

out vec2 vData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vData = UV0;
}
