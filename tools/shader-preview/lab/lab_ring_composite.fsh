#version 150 core

// 实验台 · 路线 3 的合成 pass：亮核（全分辨率遮罩）+ 多尺度光雾（降采样模糊过的遮罩）。
// 雾在采样时被 fBm 扰动 UV，再过一个"波浪阈值" → 从光滑椭圆变成一团一团的云。
uniform sampler2D MaskSampler;    // 全分辨率剪影遮罩（亮核用）
uniform sampler2D HaloHalf;       // 1/2 分辨率、已模糊
uniform sampler2D HaloQuarter;    // 1/4 分辨率、已模糊（更宽的一层）
uniform vec2 ScreenSize;
uniform float OutlineWidth;
uniform float AlphaTest;
uniform float Opacity;
uniform float HaloStrength;
uniform float NoiseScale;
uniform float Time;

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

    // ── 亮核：与现役 outline_ring 一致 ──
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

    // ── 光雾：两块降采样模糊图叠起来（多尺度），采样 UV 被噪声扰动 ──
    float n = fbm(texCoord * NoiseScale * 0.35 + vec2(Time * 0.05, -Time * 0.03));
    float nDetail = fbm(texCoord * NoiseScale * 1.9 + vec2(-Time * 0.08, Time * 0.05));
    vec2 warp = vec2(fbm(texCoord * NoiseScale * 0.6 + 11.3),
                     fbm(texCoord * NoiseScale * 0.6 + 27.9)) - 0.5;

    float wide = texture(HaloQuarter, texCoord + warp * 0.16).a;
    float mid = texture(HaloHalf, texCoord + warp * 0.07).a;
    float halo = max(wide * 0.75, mid);
    // ★ 同路线 2：模糊后的遮罩在剪影内部也是 1，必须减掉 self，只留溢出的那一圈
    halo = max(halo - self, 0.0);

    // 把光滑的雾按噪声打成云团
    float wisps = smoothstep(0.05, 0.35, halo * (0.45 + 1.20 * nDetail) * (0.6 + 0.9 * n));
    float aura = max(halo * 1.9, wisps) * HaloStrength;

    float alpha = max(ring, aura) * Opacity;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(outerCol, alpha);
}
