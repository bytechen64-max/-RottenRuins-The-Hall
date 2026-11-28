#version 150 core

in vec3 Position;
in vec4 Color;       /* 修复：POSITION_COLOR_NORMAL 的 Color 是 vec4 */
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 viewPos;    /* view space 位置，fragment 用于计算菲涅耳 */
out vec3 viewNormal;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    vertexColor = Color;
    viewPos = pos.xyz;

/*
     * ModelViewMat 此处是纯旋转矩阵（无缩放），
     * 所以法线矩阵 = transpose(inverse(M)) = M 的 3x3 部分直接用即可。
     * 不再需要 NormalMat uniform。
     */
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
}