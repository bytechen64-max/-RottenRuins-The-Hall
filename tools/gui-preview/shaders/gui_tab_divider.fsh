#version 150 core

// ============================================================
// 腐朽王庭 · 创造模式物品栏分区隔断行（极光缎带）
// 输入：POSITION_TEX 矩形，texCoord 覆盖 0..1（x: 左→右，y: 上→下）
// 混合：标准 srcalpha / 1-srcalpha，因此输出普通（非预乘）颜色
// ============================================================

in vec2 texCoord;
out vec4 fragColor;

uniform float uTime;      // 秒，持续增长，唯一动画来源
uniform vec2  uSize;      // 条带像素尺寸（典型 160×18），用于像素级频率换算
uniform vec4  uColorA;    // 调色板 1：.rgb 颜色，.a 混色权重(0..1)
uniform vec4  uColorB;    // 调色板 2：同上
uniform vec4  uColorC;    // 调色板 3：同上
uniform float uIntensity; // 全局不透明度 0..1
uniform float uGlow;      // 额外辉光强度 0..1

// 无贴图哈希：产出 0..1 伪随机
float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// 平滑 value noise（双线性 + smoothstep 插值）
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// 三段调色板：相位 t 的小数部展开成 [0,3)，A→B→C→A 三角权重循环
vec3 palette(float t) {
    float p  = fract(t) * 3.0;
    float wA = clamp(1.0 - abs(p),       0.0, 1.0) + clamp(1.0 - abs(p - 3.0), 0.0, 1.0);
    float wB = clamp(1.0 - abs(p - 1.0), 0.0, 1.0);
    float wC = clamp(1.0 - abs(p - 2.0), 0.0, 1.0);
    // .a 作为该颜色在混色中的权重，防止某色喧宾夺主
    wA *= uColorA.a;
    wB *= uColorB.a;
    wC *= uColorC.a;
    float ws = max(wA + wB + wC, 0.0001);
    return (uColorA.rgb * wA + uColorB.rgb * wB + uColorC.rgb * wC) / ws;
}

void main() {
    // 像素坐标：频率换算成像素级，条变宽变窄时花纹粗细不变
    vec2 px = texCoord * uSize;

    // ---- 主体流动噪声：x 低频、y 中频，向 +x 方向滚动 ----
    vec2 q = vec2(px.x / 46.0 - uTime * 0.42, px.y / 7.5);
    float w1 = vnoise(q * 1.6 + vec2(uTime * 0.17, 3.1));      // 轻度域扭曲，破掉竖条纹感
    float n  = vnoise(q + 0.55 * vec2(w1, w1 * 0.6));
    n += 0.50 * vnoise(q * 2.1 + vec2(-uTime * 0.63, 1.7));    // 第二倍频，速度不同
    n += 0.25 * vnoise(q * 4.3 + vec2(-uTime * 1.10, 5.9));    // 第三倍频，更快更细
    n /= 1.75;

    // 色相相位：噪声主导，沿条长缓变，随时间整体向 +x 流动
    float phase = n * 1.35 + px.x * 0.006 + uTime * 0.045;
    vec3 col = palette(phase);

    // 明暗起伏，让缎带有丝缎般的光泽流动
    col *= 0.72 + 0.55 * n;

    // ---- 竖直：整行铺满，只留亚像素羽化 ----
    // （早先是"中间一条约 10px 的细带"，会露出上下的槽位底色；
    //   后来改成整行但羽化了 0.035≈0.6px，仍然够让井边框透出来。）
    float band = smoothstep(0.0, 0.015, texCoord.y)
               * (1.0 - smoothstep(0.985, 1.0, texCoord.y));

    // ---- 中央芯丝：以 y=0.5 为中心的高斯 ----
    // 整行铺满之后芯丝也要跟着变宽，否则它只是一条压在色块上的细线
    float dy  = texCoord.y - 0.5;
    float sig = 0.16 + uGlow * 0.05;
    float core = pow(2.7182818, -dy * dy / (2.0 * sig * sig));

    // 芯丝自身的流动略快于主体
    float cn = vnoise(vec2(px.x / 26.0 - uTime * 0.95, 8.3));

    // 取调色板中最亮的一色（按 .a 加权亮度）作为芯丝倾向色
    float lA = dot(uColorA.rgb, vec3(0.299, 0.587, 0.114)) * uColorA.a;
    float lB = dot(uColorB.rgb, vec3(0.299, 0.587, 0.114)) * uColorB.a;
    float lC = dot(uColorC.rgb, vec3(0.299, 0.587, 0.114)) * uColorC.a;
    vec3 bright = mix(uColorA.rgb, uColorB.rgb, step(lA, lB));
    bright = mix(bright, uColorC.rgb, step(max(lA, lB), lC));

    // 芯丝叠加：比周围更亮，随 cn 呼吸流动
    col = mix(col, bright * 1.35, core * (0.35 + 0.40 * cn));

    // ---- 水平：同样只留亚像素羽化 ----
    // 两端淡出多少，就会被看成"这一行没铺满"多少（最早淡出 10% ≈ 16px，等于两端各空一格）
    float edge = smoothstep(0.0, 0.005, texCoord.x)
               * (1.0 - smoothstep(0.995, 1.0, texCoord.x));

    // ---- 动态颗粒：±3% 亮度随时间与坐标变化，压掉色带（banding） ----
    float gt = floor(uTime * 24.0);
    float g  = hash21(px * 1.37 + vec2(gt * 0.37, gt * 0.91));
    col *= 0.97 + 0.06 * g;

    // ---- 合成 alpha：整行**不透明** ----
    // 噪声只作用在颜色上，绝不作用在不透明度上：一旦 alpha 掉到 0.7 左右，
    // 底下的槽位井和格线就会透出来（截图里能看见格子的那次就是这个原因）。
    // 想让整条淡下去就调 uIntensity 这一个旋钮。
    float alpha = clamp(band * edge * uIntensity, 0.0, 1.0);

    if (alpha < 0.004) discard;

    fragColor = vec4(col, alpha);
}
