#version 150 core

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    // 全屏四边形，直接吐 NDC：这一 pass 不再需要任何变换矩阵。
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
