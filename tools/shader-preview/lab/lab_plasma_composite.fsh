#version 150 core

// 实验台 · 候选方案「经向平流的反馈缓冲」的 **合成 pass**。
//
// 密度 → 黑体色带 → 加法自发光；并且用剪影自身做遮挡门（火不能盖住物品表面）。
// 关键点：色带是"低密度 = 深红且暗、高密度 = 白热" —— 于是火舌有根部与尖端之分，
// 而等宽描边只有一种颜色，没有"尖端"这个概念。

uniform sampler2D Density;   // 平流结果（1/2 分辨率）
uniform sampler2D Mask;      // 剪影遮罩（全分辨率）
uniform vec2 ScreenSize;
uniform float Time;
uniform float Gain;          // 整体亮度
uniform float MaskGate;      // 1 = 剪影内完全不画（不盖住物品表面）

in vec2 texCoord;
out vec4 fragColor;

// 黑体近似色带：0 = 熄灭（暗红），1 = 白热
vec3 blackbody(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c = mix(vec3(0.28, 0.030, 0.005), vec3(1.00, 0.30, 0.04), smoothstep(0.00, 0.40, t));
    c = mix(c, vec3(1.00, 0.72, 0.22), smoothstep(0.38, 0.70, t));
    c = mix(c, vec3(1.00, 0.97, 0.88), smoothstep(0.72, 1.00, t));
    return c;
}

void main() {
    // 密度存在 .r（平流 pass 里 alpha 恒为 1，别读错通道 —— 这是我自己踩过的坑）
    float density = texture(Density, texCoord).r;
    // 密度压成 0..1 的"热度"：低密度保持暗，高密度才白热
    float heat = clamp(density * 0.85, 0.0, 1.0);
    float self = texture(Mask, texCoord).a;

    float gate = 1.0 - smoothstep(0.35, 0.65, self) * MaskGate;

    // 亮度也随热度：低密度几乎不发光 → 火舌边缘自然熄灭
    float intensity = pow(heat, 1.35) * Gain;
    vec3 color = blackbody(heat) * intensity;

    fragColor = vec4(color * gate, intensity * gate);
}
