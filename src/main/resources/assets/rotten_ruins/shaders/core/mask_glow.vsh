#version 150

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

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // 距离只用来算雾衰减因子（见 fsh 里对 linear_fog_fade 的说明）。
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);

    // 自发光层：只取物品的 color（亮度/染色），**不乘光照贴图**。
    // 泛光的语义就是「自己会亮」，如果乘了光照，夜里和洞穴里它会跟本体一起
    // 变黑，等于没有效果。cosmic 那条路是同样的理由（它直接把 brightness 写死 1.35）。
    vertexColor = Color;

    texCoord0 = UV0;
}
