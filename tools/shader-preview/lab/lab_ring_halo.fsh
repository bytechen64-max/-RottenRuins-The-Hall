#version 150 core

// 实验台 · 路线 2：单 pass「亮核 + 加权径向核软光晕 + fBm 扭曲半径」。
// 现役 outline_ring.fsh 的几何是 max(48 次采样) → 硬台阶；这里把「光晕」那一半换成
// 归一化加权平均：cov(r) 随半径平滑下降，外缘再被 fBm 推来推去 → 一团一团的毛边。
//
// 与现役版本的差别只有两处：① 增加 HaloWidth/HaloStrength/NoiseScale；
// ② 采样半径乘以 warp（噪声）。（真正进游戏时，噪声坐标要换成"跟着物品走"的坐标，
//   这里为了对比先直接用屏幕 UV。）

uniform sampler2D MaskSampler;
uniform vec2 ScreenSize;
uniform float OutlineWidth;      // 亮核宽度（像素）
uniform float HaloWidth;         // 光雾外扩半径（像素）
uniform float HaloStrength;
uniform float NoiseScale;
uniform float Time;
uniform float Opacity;
uniform float AlphaTest;

in vec2 texCoord;
out vec4 fragColor;

const int RING_DIR_COUNT = 16;

const vec2 RING_DIRS[RING_DIR_COUNT] = vec2[RING_DIR_COUNT](
    vec2( 1.00000,  0.00000),
    vec2( 0.92388,  0.38268),
    vec2( 0.70711,  0.70711),
    vec2( 0.38268,  0.92388),
    vec2( 0.00000,  1.00000),
    vec2(-0.38268,  0.92388),
    vec2(-0.70711,  0.70711),
    vec2(-0.92388,  0.38268),
    vec2(-1.00000,  0.00000),
    vec2(-0.92388, -0.38268),
    vec2(-0.70711, -0.70711),
    vec2(-0.38268, -0.92388),
    vec2( 0.00000, -1.00000),
    vec2( 0.38268, -0.92388),
    vec2( 0.70711, -0.70711),
    vec2( 0.92388, -0.38268)
);

float hash21(vec2 n) {
    n = mod(n, 64.0);
    return fract(sin(dot(n, vec2(12.9898, 12.1414))) * 83758.5453);
}

float vnoise(vec2 n) {
    const vec2 d = vec2(0.0, 1.0);
    vec2 b = floor(n);
    vec2 f = fract(n);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(b), hash21(b + d.yx), f.x),
               mix(hash21(b + d.xy), hash21(b + d.yy), f.x), f.y);
}

float fbm(vec2 n) {
    return vnoise(n) * 0.60 + vnoise(n * 2.3) * 0.28 + vnoise(n * 5.1) * 0.12;
}

void main() {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float radius = max(OutlineWidth, 0.5);
    float self = texture(MaskSampler, texCoord).a;

    // ── 亮核：与现役 outline_ring 完全一致 ──
    float outerCov = 0.0;
    vec3 outerCol = vec3(1.0);
    for (int i = 0; i < RING_DIR_COUNT; i++) {
        for (int r = 0; r < 3; r++) {
            float rr = radius * (0.45 + 0.275 * float(r));
            vec4 s = texture(MaskSampler, texCoord + RING_DIRS[i] * texel * rr);
            if (s.a > outerCov) {
                outerCov = s.a;
                outerCol = s.rgb;
            }
        }
    }
    float lo = AlphaTest;
    float hi = min(AlphaTest + 0.30, 1.0);
    float ring = max(smoothstep(lo, hi, outerCov) - smoothstep(lo, hi, self), 0.0);

    // ── 光雾：分层加权平均 + 噪声扭曲半径 ──
    // 两个尺度：低频决定"哪里伸出一团"（波瓣/火舌），高频决定毛边的细节。
    // 关键是 warp 直接乘在采样半径上 —— 某像素的 warp 大，它的采样就能够到剪影（cov=1，
    // 于是亮）；warp 小就全都够不到（cov=0，于是暗）。这就是"一团一团"的成因。
    float nLow = fbm(texCoord * NoiseScale * 0.35 + vec2(Time * 0.05, -Time * 0.03));
    float nHigh = fbm(texCoord * NoiseScale * 1.9 + vec2(-Time * 0.09, Time * 0.06));
    float warp = 0.22 + 2.3 * nLow;

    float haloAcc = 0.0;
    float haloWeight = 0.0;
    for (int i = 0; i < RING_DIR_COUNT; i++) {
        for (int r = 1; r <= 6; r++) {
            float t = float(r) / 6.0;
            float rr = HaloWidth * t * warp;
            float cov = texture(MaskSampler, texCoord + RING_DIRS[i] * texel * rr).a;
            float w = 1.0 - 0.75 * t;                 // 内层权重高 → 平滑衰减
            haloAcc += cov * w;
            haloWeight += w;
        }
    }
    float halo = haloWeight > 0.0 ? haloAcc / haloWeight : 0.0;
    // ★ 只取"溢出剪影之外"的那部分：加权平均在剪影**内部**也等于 1，
    //   不减 self 的话整把武器会被糊上一层（游戏里等于把物品贴图和星空层一起盖掉）。
    halo = max(halo - self, 0.0);

    // 把平滑包络按噪声打散成云团：这就是"雾"和"光晕"的区别
    float wisps = smoothstep(0.15, 0.55, halo * (0.50 + 1.10 * nHigh));
    float aura = max(halo * 1.4, wisps) * HaloStrength;

    float alpha = max(ring, aura) * Opacity;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(outerCol, alpha);
}
