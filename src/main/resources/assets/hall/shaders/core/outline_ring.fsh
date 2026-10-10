#version 150 core

// 环形合成程序：把剪影遮罩膨胀成一个等宽硬边环。
//
//   ring = dilate(mask, radius) − mask
//
// 这个减法就是全部关键：剪影内部 mask=1，膨胀后仍是 1，相减得 0 ——
// 所以描边永远只出现在物品外侧，绝不会盖住物品表面。
// 宇宙星空层画在物品表面上，因此它跟描边连一个像素都不会重叠。
//
// 视觉风格对齐原版发光实体描边：纯色、等宽、硬边。这里没有羽化、
// 没有外发光、没有 bloom —— 唯一的模糊来自 smoothstep 提供的约 1 像素
// 抗锯齿，以及遮罩自身的双线性采样。

uniform sampler2D MaskSampler;
uniform vec2 ScreenSize;

// 环向外扩展的宽度，单位是像素。等宽就是靠它：与物品大小、远近无关。
uniform float OutlineWidth;

// 整体不透明度。
uniform float Opacity;

// 遮罩覆盖率的判定阈值。遮罩是二值的，双线性采样后在边缘会得到
// 0~1 的中间值，阈值决定"算不算物品的一部分"。
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

float coverageAt(vec2 uv) {
    return texture(MaskSampler, uv).a;
}

void main() {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float radius = max(OutlineWidth, 0.5);

    float selfCoverage = coverageAt(texCoord);

    // 圆盘采样：16 个方向 × 3 层半径。单圈采样在大半径下方向之间会
    // 留下空洞，补上内层半径就能填实，同时保持外缘正好落在 radius 上。
    float outerCoverage = 0.0;
    vec3 outerColor = vec3(1.0);
    for (int i = 0; i < RING_DIR_COUNT; i++) {
        vec2 dir = RING_DIRS[i];
        for (int r = 0; r < 3; r++) {
            float rr = radius * (0.45 + 0.275 * float(r));
            vec4 s = texture(MaskSampler, texCoord + dir * texel * rr);
            if (s.a > outerCoverage) {
                outerCoverage = s.a;
                outerColor = s.rgb;
            }
        }
    }

    // 约 1 像素宽的过渡带，只用来抗锯齿；不做更宽的羽化。
    float lo = AlphaTest;
    float hi = min(AlphaTest + 0.30, 1.0);
    float outer = smoothstep(lo, hi, outerCoverage);
    float inner = smoothstep(lo, hi, selfCoverage);

    // 硬边环 = 膨胀结果 − 自身。
    float ring = max(outer - inner, 0.0) * Opacity;
    if (ring <= 0.002) {
        discard;
    }

    fragColor = vec4(outerColor, ring);
}
