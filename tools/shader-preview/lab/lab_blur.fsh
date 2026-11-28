#version 150 core

// 实验台 · 路线 3 的模糊 pass：可分离高斯（9 tap，σ≈2）。
// TexelStep 传「方向 × 步长」：步长给 1/源尺寸 就是原地模糊，给 2/源尺寸 就顺带降采样。
uniform sampler2D Source;
uniform vec2 TexelStep;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 s = TexelStep;
    vec4 sum = texture(Source, texCoord) * 0.2270270;
    sum += (texture(Source, texCoord + s * 1.0) + texture(Source, texCoord - s * 1.0)) * 0.1945946;
    sum += (texture(Source, texCoord + s * 2.0) + texture(Source, texCoord - s * 2.0)) * 0.1216216;
    sum += (texture(Source, texCoord + s * 3.0) + texture(Source, texCoord - s * 3.0)) * 0.0540540;
    sum += (texture(Source, texCoord + s * 4.0) + texture(Source, texCoord - s * 4.0)) * 0.0162162;
    fragColor = sum;
}
