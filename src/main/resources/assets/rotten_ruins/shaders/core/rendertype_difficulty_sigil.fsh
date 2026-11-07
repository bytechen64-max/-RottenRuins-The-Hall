#version 150 core

// ═════════════════════════════════════════════════════════════
//  难度选择界面的「印记」—— 片元阶段
//
//  四档共用本 shader，靠 uProfile 分支给出四套角度图案。
//
//  顶点属性只有两个：
//    vData.x —— 径向坐标：0 = 环带内缘，1 = 环带外缘（0.5 = 环带中心）
//    vData.y —— 图案相位 = 图案倍数 × (θ + 自转 + 该环偏移)
//
//  烘"相位"而不是原始角度 θ：θ 在 0/2π 接缝处硬跳变，插值会跨过整个圆、
//  在接缝那个四边形上算出假扇区；相位在接缝处连续，插值才是对的。
//
//  四条 profile 刻意在不同维度上拉开差别（都做成锯齿的话四个看起来还是一个）：
//    0 简单     —— 净环、缓慢呼吸、无角度图案
//    1 普通     —— 3 段缺口 + 低阶波纹，正弦谐波给出秩序感
//    2 困难     —— 5 段撕裂，硬边（不等长尖刺在 CPU 侧烘进几何）
//    3 无法理解 —— 7 扇区独立闪烁，秩序被破坏
// ═════════════════════════════════════════════════════════════

uniform float uTime;
uniform float uHover;
uniform float uAlpha;
uniform float uProfile;    // float：int 类型没有 float 缓冲，set(float) 会崩
uniform float uEdgePower;  // 径向边缘硬度：越大边缘越锐（1 = 纯二次曲线）

uniform vec4 uQuad0Color;
uniform vec4 uQuad1Color;
uniform vec4 uQuad2Color;
uniform vec4 uQuad3Color;

in vec2 vData;

out vec4 fragColor;

const float TAU = 6.2831853;

/** 稳定伪随机：同一输入必须给同一输出，否则闪烁会变成噪点抖动。 */
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

/** 该难度对应的基色。 */
vec4 profileColor() {
    if (uProfile < 0.5) return uQuad0Color;
    if (uProfile < 1.5) return uQuad1Color;
    if (uProfile < 2.5) return uQuad2Color;
    return uQuad3Color;
}

/** 该 profile 的角度图案倍数，必须与 Java 侧 Ring.patternMultiplicity 一致。 */
float patternMultiplicity() {
    if (uProfile < 0.5) return 1.0;   // 无图案
    if (uProfile < 1.5) return 3.0;   // 3 段缺口
    if (uProfile < 2.5) return 5.0;   // 5 段撕裂
    return 7.0;                       // 7 扇区闪烁
}

/** 角度维度的图案门控。0 = 这里不画（缺口 / 熄灭）。 */
float angularGate(float theta) {
    if (uProfile < 0.5) {
        return 1.0;                                   // 净环：无角度图案
    }
    if (uProfile < 1.5) {
        return smoothstep(0.07, 0.22, abs(sin(3.0 * theta + 0.5)));
    }
    if (uProfile < 2.5) {
        return step(0.20, abs(sin(5.0 * theta)));     // 硬边撕裂
    }
    float sector = floor(7.0 * (theta + TAU) / TAU);  // 7 个扇区各自闪烁
    return 0.30 + 0.70 * hash11(sector * 5.7 + floor(uTime * 7.0));
}

void main() {
    // 径向柔化：0.5 是环带中心、0 与 1 是两侧边缘。
    // 用"到中心的距离"而不是"到边缘的贴近度"，是为了让环带看起来是一条
    // 有厚度的光带（中间最亮、两侧平滑收住），而不是一块实心色条。
    float d = abs(clamp(vData.x, 0.0, 1.0) - 0.5) * 2.0;   // 0 = 中心，1 = 边缘
    float radial = pow(max(1.0 - d, 0.0), uEdgePower);

    // 相位还原成活角：相位 = 该 profile 的倍数 × (θ + 自转 + 环偏移)
    float theta = vData.y / max(patternMultiplicity(), 1.0);

    float gate = angularGate(theta);

    float intensity = radial * gate * uAlpha;

    // 悬浮时提亮；常态也保持可见（常态动画是明确要求）
    intensity *= 0.68 + 0.32 * uHover;

    if (intensity < 0.004) discard;

    vec4 base = profileColor();

    fragColor = vec4(base.rgb, clamp(intensity, 0.0, 1.0) * base.a);
}
