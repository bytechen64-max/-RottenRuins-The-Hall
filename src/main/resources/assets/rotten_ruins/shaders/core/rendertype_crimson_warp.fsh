#version 150 core

// ═════════════════════════════════════════════════════════════
//  绯红誓约 · 法阵上方的方块扭曲（片元阶段）
//
//  参考的是 Shadertoy 上那个经典的"屏幕空间折射"小片段：
//
//      vec2 p_m = fragCoord.xy / iResolution.xy;
//      vec2 p_d = p_m;  p_d.t -= iTime * 0.1;
//      vec2 dst_offset = texture(iChannel1, p_d).xy - vec2(.5);
//      dst_offset *= 2.;
//      dst_offset *= 0.01;
//      dst_offset *= (1. - p_m.t);        // ← 越靠近屏幕顶部越弱
//      fragColor = texture(iChannel0, p_m.st + dst_offset);
//
//  它有三件事值得原样保留：
//    ① 位移来自一张**随时间滚动**的扰动图，所以是流动的而不是静止的；
//    ② 位移量很小（0.01 量级），只要"看出画面在动"就够了，晃大了像在晕船；
//    ③ 位移量按一个归一化坐标**线性衰减到 0**。
//
//  ③ 是关键。原版按屏幕 y 衰减（"reduce effect towards Y top"），
//  搬到世界空间之后，这条衰减必须绑在"离地高度"上，而不是屏幕 y：
//  需求要的是"法阵照耀的 <b>y 轴区域</b>覆盖上方块扭曲"，
//  于是这里的衰减是 (1 - h/H)² —— 贴着地面最强，到柱顶归零。
//  如果照搬屏幕 y，玩家一低头整根柱子就没了，一抬头又糊满全屏。
//
//  ── 几何与判定 ────────────────────────────────────────────────
//  Java 画一个开口圆柱的侧面（半径 uRadius、高 uHeight），关深度测试、
//  关背面剔除。片元阶段在**视空间**里做一次解析的"射线 × 圆柱侧面"求交：
//
//    · 视空间里相机在原点，所以 rd = normalize(vPos) 就是这条视线的方向；
//    · 圆柱轴与底面中心也都在视空间给出（uAxisVS / uBaseVS）；
//    · 取最近的**正**交点 tHit，并检查它落在 [0, uHeight] 的高度区间里。
//
//  为什么不去剔背面：玩家常常就站在柱子里面，一剔背面整根柱子当场消失。
//  正/反两面的片元算出来的 (tHit, h) 是**同一个值**（同一像素上的所有
//  片元共线），所以两面都画不会出现叠色，也就不需要靠剔除来保证唯一。
//
//  ── 遮挡 ──────────────────────────────────────────────────────
//  命中点必须比场景深度更近才生效 —— 否则"墙后的空气"也会跟着扭曲。
//  与 rendertype_shockwave 用的是同一套深度线性化公式。
// ═════════════════════════════════════════════════════════════

uniform sampler2D ScreenTexture;
uniform sampler2D DepthTexture;
uniform mat4 ProjMat;

uniform float uTime;
uniform float uIntensity;   // 0..1，已含淡入淡出
uniform float uRadius;      // 圆柱半径（格）
uniform float uHeight;      // 圆柱高（格）
uniform float uWarp;        // 位移强度倍率
uniform float uChroma;      // 色散强度
uniform float uHasDepth;    // 1 = 深度纹理可用
uniform float uFinale;      // 0..1 终末进度（越接近 1 越亮越乱）
uniform vec2  uPixelSize;   // (1/width, 1/height)，必须是场景拷贝的尺寸
uniform vec3  uBaseVS;      // 圆柱底面中心在视空间的位置
uniform vec3  uAxisVS;      // 圆柱轴（世界 +Y）在视空间的方向（单位向量）

in vec4 vertexColor;
in vec3 vLocal;

out vec4 fragColor;

// ── 值噪声 / fbm ──────────────────────────────────────────────
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float s = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        s += amp * vnoise(p);
        p = p * 2.03 + 17.3;
        amp *= 0.5;
    }
    return s / 0.875;
}

void main() {
    vec2 baseUV = clamp(gl_FragCoord.xy * uPixelSize, vec2(0.002), vec2(0.998));
    vec3 scene = texture(ScreenTexture, baseUV).rgb;

    float I = max(uIntensity, 0.0);
    if (I <= 0.003) { fragColor = vec4(scene, 1.0); return; }

    vec3 rd = normalize(vLocal);
    vec3 a  = normalize(uAxisVS);

    // ── 射线 × 无限圆柱侧面 ──
    vec3 d     = -uBaseVS;                       // ro(=0) - uBaseVS
    vec3 dPerp = d - a * dot(d, a);
    vec3 rPerp = rd - a * dot(rd, a);
    float A = dot(rPerp, rPerp);
    float B = 2.0 * dot(rPerp, dPerp);
    float C = dot(dPerp, dPerp) - uRadius * uRadius;

    if (A < 1.0e-6) { fragColor = vec4(scene, 1.0); return; }
    float disc = B * B - 4.0 * A * C;
    if (disc < 0.0) { fragColor = vec4(scene, 1.0); return; }

    float sq = sqrt(disc);
    float t0 = (-B - sq) / (2.0 * A);
    float t1 = (-B + sq) / (2.0 * A);
    float tHit = (t0 > 0.0) ? t0 : t1;
    if (tHit <= 0.0) { fragColor = vec4(scene, 1.0); return; }

    vec3  hp = rd * tHit;
    float h  = dot(hp - uBaseVS, a);
    if (h < 0.0 || h > uHeight) { fragColor = vec4(scene, 1.0); return; }

    // ── 深度遮挡：墙后的空气不扭曲 ──
    //  与 rendertype_crimson_cube 同一套做法：把视空间 z **正推**成深度缓冲
    //  里那个值，再与深度纹理直接比较。不走"反解视空间 z"的线性化 ——
    //  那个写法差一个 2·far 量级的系数，会让判定永远不成立（详见 cube 里的长注释）。
    if (uHasDepth > 0.5) {
        float ndcZ = -(ProjMat[2][2] + ProjMat[3][2] / min(hp.z, -1.0e-4));
        float dHit = ndcZ * 0.5 + 0.5;
        float dScene = texture(DepthTexture, baseUV).r;
        if (dHit > dScene + 1.0e-5) { fragColor = vec4(scene, 1.0); return; }
    }

    float hn = clamp(h / max(uHeight, 1.0e-3), 0.0, 1.0);

    // ── 位移量：贴地最强、到柱顶归零 ──
    //  用平方而不是线性：线性衰减会让整根柱子上半段都还在动，
    //  平方之后"法阵照亮的那一层"才是真正被扭曲的那一层。
    float w = 1.0 - hn;
    float amp = uWarp * I * w * w * (1.0 + 1.8 * uFinale);

    // ── 扰动方向：世界锚定的两层噪声，整体向上滚动 ──
    //  世界锚定（而不是屏幕锚定）—— 转头时扰动图案不会跟着画面一起滑，
    //  否则玩家会觉得"是镜头在抖"而不是"这片空气在动"。
    vec3 q = vec3(hp.x * 0.85, hp.y * 1.7 + uTime * 0.9, hp.z * 0.85);
    float n1 = fbm2(q.xz + q.y * 0.35 + vec2(0.0, -uTime * 0.6));
    float n2 = fbm2(q.xz * 1.9 - q.y * 0.22 + vec2(uTime * 0.4, 11.0));
    vec2 off = vec2(n1, n2) - 0.5;

    vec2 disp = off * amp * 0.030;

    vec2 dispR = disp * (1.0 + uChroma);
    vec2 dispB = disp * (1.0 - uChroma);

    vec3 col = vec3(
        texture(ScreenTexture, clamp(baseUV + dispR, vec2(0.002), vec2(0.998))).r,
        texture(ScreenTexture, clamp(baseUV + disp,  vec2(0.002), vec2(0.998))).g,
        texture(ScreenTexture, clamp(baseUV + dispB, vec2(0.002), vec2(0.998))).b);

    // ── 自发光：位移在平坦背景（天空 / 纯色墙）上几乎看不出来，
    //    真正让这根柱子被"看见"的是它自己的光。分两层层叠：
    //    ① 掠射边缘（视线与柱面越接近相切越亮，柱体因此有体积感）；
    //    ② 向上流动的竖直纹理（读起来是"能量在往上升"）。
    vec3 wallN = normalize(hp - uBaseVS - a * h);
    float fres = 1.0 - abs(dot(rd, wallN));
    fres = fres * fres;

    float streak = fbm2(vec2(hp.x + hp.z, hp.y * 2.4 - uTime * 1.6) * 1.4);

    vec3 aura = vec3(1.00, 0.28, 0.70) * (0.10 + 0.34 * fres) * (0.55 + 0.75 * streak) * w;
    aura += vec3(0.78, 0.38, 1.00) * 0.10 * w;
    aura *= I * (1.0 + 2.6 * uFinale);

    col += aura;

    fragColor = vec4(col, 1.0);
}
