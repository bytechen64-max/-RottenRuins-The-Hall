#version 150 core

/*
 * UI 有向距离场（SDF）形状 —— 逐物理像素抗锯齿。
 *
 * 思路参考 FIS-Cordyceps（MIT）的 shaders/core/wheel_sdf.fsh：
 *   GuiGraphics.fill 只能画轴对齐矩形，用三角光栅化拼圆/圆环/扇形/圆角矩形时，
 *   边缘没有覆盖率抗锯齿；而 SDF 在片元里算"到形状边界的距离"，再用
 *   coverage = clamp(0.5 - sdf / aa, 0, 1) 得到覆盖比例，aa 对齐到 1 个物理像素。
 *
 *   aa = 1 / uScale（uScale = 1 个 GUI 像素对应的物理像素数）。
 *   本工具里 uScale = 原生渲染倍率（默认 2）；进游戏时传 GUI scale。
 *
 * 四种模式：
 *   uMode = 0 圆环 / 圆环扇区（uRIn、uROut、uA0、uA1；整圈 = a1-a0 >= TAU）
 *   uMode = 1 圆角矩形（uHalfSize、uRound、uBorder 内描边）
 *   uMode = 2 胶囊线段（uP0、uP1、uThickness）
 *   uMode = 3 星形/多边形尖角（uRIn、uROut、uPoints、uRot；极坐标半径插值近似）
 *
 * 颜色：uColorIn → uColorOut；uGradMode 0 = 径向，1 = 竖直，2 = 纯 uColorIn。
 */

const float TAU = 6.28318530718;

uniform vec2  uCanvas;        // quad 的 GUI 尺寸
uniform vec2  uCenter;        // 形状中心（quad 本地 GUI 坐标）
uniform vec2  uHalfSize;      // 圆角矩形半宽半高
uniform vec2  uP0;            // 胶囊起点（quad 本地）
uniform vec2  uP1;            // 胶囊终点（quad 本地）
uniform float uRIn;
uniform float uROut;
uniform float uA0;
uniform float uA1;
uniform float uRound;
uniform float uBorder;        // 内描边宽度（0 = 不描边）
uniform float uThickness;     // 胶囊半宽
uniform float uPoints;        // 星形尖数
uniform float uRot;           // 星形旋转（弧度）
uniform float uScale;         // 1 个 GUI 像素 = uScale 个物理像素
uniform float uGradMode;
uniform float uMode;
uniform float uSoft;          // 额外柔化（GUI 像素，用于辉光形状；0 = 锐利边缘）
uniform vec4  uColorIn;
uniform vec4  uColorOut;
uniform vec4  uBorderColor;

in vec2 texCoord0;
out vec4 fragColor;

float sdRoundRect(vec2 p, vec2 halfSize, float round) {
    vec2 q = abs(p) - halfSize + vec2(round);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - round;
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

/** 精确的星形 SDF：对 2n 条边求最近距离 + 奇偶规则判断内外（尖角不会变成花瓣）。 */
float sdStarPoly(vec2 p, float rOut, float rIn, float points, float rot) {
    float count = points * 2.0;
    float pi = 3.14159265359;
    float d = 1.0e9;
    vec2 v0 = vec2(0.0);
    vec2 v1 = vec2(0.0);
    for (int i = 0; i < 16; i++) {
        if (float(i) >= count) break;
        float a0 = rot + float(i) * pi / points;
        float a1 = rot + float(i + 1) * pi / points;
        float r0 = mod(float(i), 2.0) < 0.5 ? rOut : rIn;
        float r1 = mod(float(i + 1), 2.0) < 0.5 ? rOut : rIn;
        v0 = vec2(cos(a0), sin(a0)) * r0;
        v1 = vec2(cos(a1), sin(a1)) * r1;
        d = min(d, sdSegment(p, v0, v1));
    }
    float crossings = 0.0;
    for (int i = 0; i < 16; i++) {
        if (float(i) >= count) break;
        float a0 = rot + float(i) * pi / points;
        float a1 = rot + float(i + 1) * pi / points;
        float r0 = mod(float(i), 2.0) < 0.5 ? rOut : rIn;
        float r1 = mod(float(i + 1), 2.0) < 0.5 ? rOut : rIn;
        v0 = vec2(cos(a0), sin(a0)) * r0;
        v1 = vec2(cos(a1), sin(a1)) * r1;
        if ((v0.y > p.y) != (v1.y > p.y)) {
            float xi = v0.x + (p.y - v0.y) * (v1.x - v0.x) / (v1.y - v0.y);
            if (p.x < xi) crossings += 1.0;
        }
    }
    return mod(crossings, 2.0) > 0.5 ? -d : d;
}

float sdStar(vec2 p, float rOut, float rIn, float points, float rot) {
    float seg = TAU / max(points, 2.0);
    float a = atan(p.y, p.x) + rot;
    float t = abs(mod(a, seg) - seg * 0.5) / (seg * 0.5);   // 0 = 尖端, 1 = 内凹
    float radius = mix(rOut, rIn, t);
    return length(p) - radius;
}

void main() {
    vec2 p = texCoord0 * uCanvas;
    float aa = 1.0 / max(uScale, 1.0);
    vec4 col;
    float sdf;

    if (uMode < 0.5) {
        // 圆环 / 扇区
        vec2 v = p - uCenter;
        float d = length(v);
        sdf = max(uRIn - d, d - uROut);
        if (uA1 - uA0 < TAU - 0.001) {
            float ang = atan(v.y, v.x);
            float span = uA1 - uA0;
            float rel = mod(ang - uA0, TAU);
            float outside = max(-rel, rel - span);
            sdf = max(sdf, outside * max(d, 0.001));
        }
        float t = uGradMode > 1.5
                ? 0.0
                : (uGradMode > 0.5
                    ? clamp((p.y - uCenter.y + uROut) / max(2.0 * uROut, 0.001), 0.0, 1.0)
                    : clamp((d - uRIn) / max(uROut - uRIn, 0.001), 0.0, 1.0));
        col = mix(uColorIn, uColorOut, t);
    } else if (uMode < 1.5) {
        // 圆角矩形
        sdf = sdRoundRect(p - uCenter, uHalfSize, uRound);
        float ty = clamp((p.y - uCenter.y + uHalfSize.y) / max(2.0 * uHalfSize.y, 0.001),
                0.0, 1.0);
        col = uGradMode > 1.5 ? uColorIn : mix(uColorIn, uColorOut, ty);
        if (uBorder > 0.0) {
            float band = abs(sdf + uBorder * 0.5) - uBorder * 0.5;
            float borderCoverage = clamp(0.5 - band / aa, 0.0, 1.0);
            col = mix(col, uBorderColor, borderCoverage * uBorderColor.a);
        }
    } else if (uMode < 2.5) {
        // 胶囊线段
        sdf = sdSegment(p, uP0, uP1) - uThickness;
        col = uColorIn;
    } else {
        // 星形（精确多边形边距离，保证尖角是尖的）
        sdf = sdStarPoly(p - uCenter, uROut, uRIn, uPoints, uRot);
        col = uColorIn;
    }

    float width = aa + max(uSoft, 0.0);
    float coverage = clamp(0.5 - sdf / width, 0.0, 1.0);
    fragColor = vec4(col.rgb, col.a * coverage);
}
