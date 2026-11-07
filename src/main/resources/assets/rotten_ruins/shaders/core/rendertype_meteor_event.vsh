#version 150 core

// ═════════════════════════════════════════════════════════════
//  陨石事件 · 天空十字星 / 下落拖尾 / 火球（顶点阶段）
//
//  三种几何共用一套属性打包，靠 uMode 分支（见 .fsh）：
//    uMode = 0  十字星    面向相机的四边形，Color.rg = 局部 uv（0..1）
//    uMode = 1  拖尾      竖直圆柱，Color.g = 角度，Normal.x = 世界 Y
//    uMode = 2  火球      同 0，面向相机的四边形
//
//  Java 侧已经把 ModelViewMat 折进顶点，所以 Position 就是视空间坐标。
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 vLocal;
/** 拖尾模式：顶点的世界 Y（格）；其它模式无意义。 */
out float vWorldY;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    vLocal      = Position;
    vWorldY     = Normal.x;
    vertexColor = Color;
}
