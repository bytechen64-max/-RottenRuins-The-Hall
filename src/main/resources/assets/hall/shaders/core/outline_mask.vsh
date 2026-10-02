#version 150 core

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 modelPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    // 物品自身模型空间坐标：渐变 / 流动相位用它而不是屏幕坐标，
    // 这样同一个物品不管被摆到屏幕哪里，花纹都稳定不变。
    modelPos = Position;
}
