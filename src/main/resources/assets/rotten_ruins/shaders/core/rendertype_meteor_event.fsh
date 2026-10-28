#version 150 core

// ═════════════════════════════════════════════════════════════
//  陨石事件 · 天空十字星 / 下落拖尾 / 火球（片元阶段）
//
//  这是同一次事件的三件东西，共用一套配色与时间轴，所以放在一个文件里：
//
//    uMode = 0  十字星    事件前 10 秒悬在天上，闪烁、缓慢自转、越来越大
//    uMode = 1  拖尾      后 10 秒的坠落：竖直圆柱，头白热 → 尾暗红
//    uMode = 2  火球      头部 / 落点的加法光球
//
//  ── 为什么拖尾用"竖直圆柱"而不是屏幕空间拖影 ──────────────
//  屏幕空间的拖影会随镜头转动而改变形状，一眼就是贴纸（这个项目在激
//  光那一招上踩过一次）。事件里陨石是**垂直下落**的，所以世界空间的
//  竖直圆柱正好就是它的轨迹，不需要任意轴。
// ═════════════════════════════════════════════════════════════

uniform float uTime;
uniform float uIntensity;   // 0..1，Java 已含淡入淡出
uniform float uMode;        // 0 十字星 / 1 拖尾 / 2 火球
uniform float uProgress;    // 0..1 整段事件进度（出现时用来淡入）
uniform float uHeadY;       // 拖尾：这一帧"头"的世界 Y
uniform float uSpan;        // 拖尾：头到顶的长度（格）
/**
 * 输出不透明度。
 *
 * <p>火焰那一趟是加法混合，alpha 不参与；**扬尘那一趟是 alpha 混合**，
 * 要靠它把云团压成半透明的暗尘 —— 加法混合永远画不出"暗"的东西，
 * 这是必须单开一趟的原因。</p>
 */
uniform float uOpacity;

uniform vec3 uColorA;       // 白热
uniform vec3 uColorB;       // 橙
uniform vec3 uColorC;       // 暗红

in vec4 vertexColor;
in vec3 vLocal;
in float vWorldY;

out vec4 fragColor;

#define TAU 6.28318530718

// 4×4 Bayer 抖动表：把连续亮度压成"硬边的小方块"，
// 3bit 屏效与火球都要它 —— 平滑渐变一出现，像素感就没了。
float bayer4(vec2 p) {
    int x = int(mod(p.x, 4.0));
    int y = int(mod(p.y, 4.0));
    float m[16] = float[16](
         0.0,  8.0,  2.0, 10.0,
        12.0,  4.0, 14.0,  6.0,
         3.0, 11.0,  1.0,  9.0,
        15.0,  7.0, 13.0,  5.0);
    return m[y * 4 + x] / 16.0;
}

// ═════════════════════════════════════════════════════════════
//  ① 天空十字星
//
//  "十字交叉闪烁的星星"：两条正交的光条给出硬十字，外面一圈四芒尖，
//  中心一个过曝的核。闪烁用两个互质频率叠加 —— 单频会读成"呼吸灯"。
// ═════════════════════════════════════════════════════════════
void meteorStar(out vec3 col) {
    vec2 uv = vertexColor.rg * 2.0 - 1.0;
    float r = length(uv);
    float ang = atan(uv.y, uv.x) + uTime * 0.12;

    // 两条正交光条（十字本体）。
    //  ⚠️ 必须"沿轴收尖"：第一版沿轴用的是 exp(-|x|*2.1)，到四边形边缘还有
    //     0.12 的亮度 —— 于是十字的两条长臂**被四边形的边硬生生切断**，
    //     实机截图里就是一根"平头顶"的光柱。现在乘 (1-|along|)^1.7，
    //     到边缘恰好归零，臂是收尖的。
    float ax = abs(uv.x);
    float ay = abs(uv.y);
    float taperX = pow(max(0.0, 1.0 - ax), 1.7);
    float taperY = pow(max(0.0, 1.0 - ay), 1.7);
    float bars = exp(-ay * ay * 200.0) * taperX * 2.6      // 横条
               + exp(-ax * ax * 200.0) * taperY * 2.6;     // 竖条
    // 四芒尖
    float spike = pow(max(0.0, cos(ang * 2.0)), 14.0) * exp(-r * 2.0) * taperX * taperY;
    // 核与晕
    float core = exp(-r * r * 120.0);
    float halo = exp(-r * 1.4);

    float tw = 0.72 + 0.28 * sin(uTime * 7.0) + 0.12 * sin(uTime * 23.0 + 1.3);
    col = uColorA * (core * 5.0 + bars * 1.35)
        + uColorB * spike * 2.2
        + uColorC * halo * 0.30;
    col *= tw * (0.55 + 0.45 * uProgress);

    // 四边形边缘强制透明：双保险，任何一项都不许在边框上留一条硬边
    // ⚠️ 反过来的 smoothstep（edge0 > edge1）在 GLSL 里是未定义行为，
    //     必须写成 1-smoothstep(lo, hi, x) —— 这个项目已经踩过一次，别再写反。
    col *= 1.0 - smoothstep(0.78, 1.0, r);
}

// ═════════════════════════════════════════════════════════════
//  ② 坠落拖尾
//
//  火焰颜色沿柱身从"头部的白热"过渡到"尾部的暗红"，中间夹一层会向上
//  跑的湍流条纹。`rel` 以头为 0、顶为 1，所以陨石往下落时，整条尾迹
//  是**从头顶被拉长**的，而不是整根平移。
// ═════════════════════════════════════════════════════════════
void meteorTrail(out vec3 col) {
    float wy = vWorldY;
    float a = vertexColor.g * TAU;
    float rel = clamp((wy - uHeadY) / max(uSpan, 0.01), 0.0, 1.0);

    vec3 fire = mix(uColorA, uColorB, smoothstep(0.0, 0.20, rel));
    fire = mix(fire, uColorC, smoothstep(0.25, 0.85, rel));

    // 湍流：沿柱面绕，并且**朝头部的反方向跑**（火被甩在后面）。
    // 相位里 wy 的系数为正、uTime 为负 ⇒ 等相位点随时间向 +wy 移动，
    // 也就是离头越来越远，读起来才是"尾迹在流走"。
    float n = 0.5 + 0.5 * sin(a * 7.0 + wy * 0.62 - uTime * 11.0)
                         * sin(a * 3.0 - wy * 0.23 + uTime * 5.0);
    // 衰减从 1.15 提到 2.4：第一版到尾端还有一半亮度，整根柱子"顶"在
    // 起点上不动；现在只有头后面二三十格是实的。
    float body = (0.30 + 0.70 * n) * pow(1.0 - rel, 2.4);
    // 再叠一层**很长、很淡**的余烬：让尾迹不是"咔"地断掉，而是慢慢散。
    // 它跟着头走（尾端被钳到 trailTop），所以不会重新变回挂在天上的光柱。
    float haze = 0.10 * pow(1.0 - rel, 0.8);
    // 顶端软收口：几何被钳到 trailTop，这里再保一道，杜绝硬边
    body *= 1.0 - smoothstep(0.82, 1.0, rel);
    float head = exp(-rel * 18.0) * 2.6;

    // 柱面朝向：正对镜头的一侧更亮（与法阵那一套同一个做法）
    vec3 nVS = normalize(vec3(cos(a), 0.0, sin(a)));
    float facing = 1.0 - abs(dot(normalize(vLocal + vec3(1.0e-5)), nVS));
    facing = pow(clamp(facing, 0.0, 1.0), 1.2);

    col = fire * (body * (0.45 + 0.90 * facing) + haze + head) * uIntensity;
    col /= (1.0 + col * 0.35);
}

// ═════════════════════════════════════════════════════════════
//  ③ 火球（头部 / 落点）
// ═════════════════════════════════════════════════════════════
void meteorFireball(out vec3 col) {
    vec2 uv = vertexColor.rg * 2.0 - 1.0;
    float r = length(uv);
    float n = 0.5 + 0.5 * sin(uv.x * 9.0 + uTime * 11.0) * sin(uv.y * 11.0 - uTime * 7.0);
    float core = 1.0 - smoothstep(0.05, 1.0, r);
    float ball = pow(core, 2.2);
    col = uColorA * ball * 2.6 + uColorB * ball * 1.2 + uColorC * pow(core, 4.0) * 1.5;
    col *= (0.75 + 0.50 * n) * uIntensity;
}

// ═════════════════════════════════════════════════════════════
//  ④ 云团（蘑菇云的冠 / 裙边）
//
//  一个正对镜头的四边形，画一团**不规则**的火云：
//  边缘用「角度 × 半径」的正弦扰动打碎，所以叠十几个之后
//  是一个连绵的云冠，而不是十几个发光球。
//
//  vertexColor.b 当每个云团的随机种子（Java 侧写进颜色里），
//  这样同一帧里十几个云团的边缘不会长得一样。
// ═════════════════════════════════════════════════════════════
/** 由各模式写出的不透明度（云团用它做软边）。 */
float gAlpha = 1.0;

void meteorPuff(out vec3 col) {
    vec2 uv = vertexColor.rg * 2.0 - 1.0;
    float r = length(uv);
    float seed = vertexColor.b * 6.2831;
    float ang = atan(uv.y, uv.x);

    // 边缘扰动：三层不同频率的正弦，叠出不规则的团块轮廓
    float n = 0.5
            + 0.28 * sin(ang * 3.0 + seed + r * 3.0 - uTime * 0.9)
            + 0.16 * sin(ang * 5.0 - seed * 1.7 + r * 6.0 + uTime * 1.3)
            + 0.10 * sin(ang * 9.0 + seed * 2.3 - r * 9.0);
    float edge = 1.0 - smoothstep(0.30, 0.95, r + (n - 0.5) * 0.55);
    edge = clamp(edge, 0.0, 1.0);

    // 中心亮（火），外圈暗（烟/余烬）。
    //  core 压到 0.45：云团叠十几层，每层都带一个亮核的话会变成
    //  一串发光球，而不是一朵云。
    float core = exp(-r * r * 3.0);
    col = (uColorA * core * 0.45 + uColorB * edge * 0.70 + uColorC * edge * edge * 0.30)
        * uIntensity;
    gAlpha = edge;
}

void main() {
    vec3 col;
    if (uMode < 0.5)      meteorStar(col);
    else if (uMode < 1.5) meteorTrail(col);
    else if (uMode < 2.5) meteorFireball(col);
    else                  meteorPuff(col);

    // 抖动：把亮度压成 3bit 的台阶，避免加法混合把整块烧成纯白
    float d = bayer4(gl_FragCoord.xy) - 0.5;
    col *= 1.0 + d * 0.18;
    fragColor = vec4(col, clamp(gAlpha * uOpacity, 0.0, 1.0));
}
