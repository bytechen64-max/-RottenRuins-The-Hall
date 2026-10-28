#version 150 core

// 描边「体积光雾」的模糊 pass：可分离高斯，9 tap。
//
// 由 ItemOutlinePipeline.buildHaloChain 调用四次，把剪影遮罩做成两级降采样模糊：
//
//     遮罩(全分辨率) --H(步长2源纹素)--> 1/2  --V--> 1/2 完成
//     1/2           --H(步长2源纹素)--> 1/4  --V--> 1/4 完成
//
// 合成 pass（outline_ring.fsh）再把这两级当成"中层雾"和"大范围雾"叠上去，
// 用噪声把它们打散成团块 —— 这就是那张参考图里"贴着物品的一团云"的来源。
//
// 为什么不像硬环那样在全分辨率上直接卷积：雾是低频的。降到 1/4 后面积只有 1/16，
// 而且整屏只需要做一次（同屏所有描边物品共用同一张遮罩模糊图）。

uniform sampler2D Source;

// 方向 × 步长。步长取 2/源尺寸 = 顺带降采样；取 1/目标尺寸 = 原地模糊。
uniform vec2 TexelStep;

// 整体扩散倍率（配置的 outlineAuraSpread）：一并放大降采样步长，
// 于是"雾更大"不需要多加 pass。
uniform float BlurScale;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 s = TexelStep * BlurScale;
    vec4 sum = texture(Source, texCoord) * 0.2270270;
    sum += (texture(Source, texCoord + s) + texture(Source, texCoord - s)) * 0.1945946;
    sum += (texture(Source, texCoord + s * 2.0) + texture(Source, texCoord - s * 2.0)) * 0.1216216;
    sum += (texture(Source, texCoord + s * 3.0) + texture(Source, texCoord - s * 3.0)) * 0.0540540;
    sum += (texture(Source, texCoord + s * 4.0) + texture(Source, texCoord - s * 4.0)) * 0.0162162;
    fragColor = sum;
}
