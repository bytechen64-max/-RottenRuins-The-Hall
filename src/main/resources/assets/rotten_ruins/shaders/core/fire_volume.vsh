#version 150 core

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    // 全屏四边形：体积盒子在片元里做 ray-box 求交，几何只需要覆盖它的屏幕投影。
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
