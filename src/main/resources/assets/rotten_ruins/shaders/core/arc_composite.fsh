#version 150 core

// 电弧合成 —— 框架效果 "arc" 的合成 pass。
//
// ── 与旧描边算法的根本区别 ───────────────────────────────────────────
// 电弧的**带宽来自它自己的场**（Edge：1/8 分辨率、大跨度模糊的剪影），
// 不再蹭描边光雾链那张只有 52px 跨度的图。之前"怎么调都扩散不出去"的根因就是
// 带宽被旧算法的中间产物锁死了 —— 场是我自己的，跨度就是我说了算。
//
// Edge 场是一个**有符号距离代理**：
//     h = 0   远在外侧
//     h = 0.5 **恰好落在轮廓上**
//     h = 1   深入物品内部
// 所以到轮廓的归一化距离 = |2h − 1|，取补即"越靠轮廓越亮"：
// 电弧于是**骑在边缘上**（外面伸出一段、里面压进一段），带宽由 ArcBand 控制。
//
// 细丝本身用噪声等值线的**距离**形式：d = |n − 阈值| / |∇n|，再用固定线宽截断。
// （不要用 `宽度/|n−阈值|`：那个形式的实际线宽 ≈ 2·宽度/|∇n| 正比于频率倒数，
//   频率一高线就变细到亚像素、整片电弧消失 —— 这是踩过的坑。）
//
// uniform 声明必须与 arc_composite.json 的 count 一一对应：
// MC 的 JSON uniforms **不会**向 GLSL 注入声明，漏一行就是整个 program 编译失败。

uniform sampler2D Edge;         // 1/8 分辨率、大跨度模糊的剪影（距离代理）

uniform vec2 ScreenSize;
uniform vec2 ArcNoiseOrigin;    // 物品屏幕中心（UV），噪声坐标系原点
uniform vec3 ArcColorA;         // 细丝色 A
uniform vec3 ArcColorB;         // 细丝色 B
uniform float Time;
uniform float ArcScale;         // 噪声频率（决定疏密）
uniform float ArcLine;          // 线宽（np 单位 = 屏幕高；由像素换算而来）
uniform float ArcThreshold;     // 阈值牵引（主体跟着低频噪声走，保证一定有穿过点）
uniform float ArcGain;          // 亮度
uniform float ArcSpeed;         // 流动速度
uniform float ArcBand;          // 带宽：|2h−1| 的上限（0.6 ≈ 内外各覆盖一段）
uniform float ArcSub;           // 距离扣减（远场收口）
uniform float ArcAniso;         // 各向异性（>1.05 时沿切向拉长 → 放射状触须）
uniform float ArcTonemap;       // >0.5 = ACES+sqrt（白热核心的关键）
uniform float ArcDebug;         // >0.5 = 读数模式：直接显示距离代理场（定位"偏移"这类问题）

in vec2 texCoord;
out vec4 fragColor;

float hash21(vec2 n) {
    n = mod(n, 1000.0);
    return fract(sin(dot(n, vec2(12.9898, 12.1414))) * 83758.5453);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * valueNoise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return s / 0.9375;
}

/** ACES 近似（Narkowicz）：把 1/|Δ| 的爆亮压成"亮到发白的芯"，而不是糊成色斑。 */
vec3 acesApprox(vec3 v) {
    v = max(v, vec3(0.0)) * 0.6;
    return clamp((v * (2.51 * v + 0.03)) / (v * (2.43 * v + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    // ── 读数模式 ──
    // 把距离代理场直接画成灰度（0=远外 → 1=内部深处；0.5 应该正好落在轮廓上）。
    // 用途：区分"场本身偏了"和"细丝摆放偏了" —— 前者灰度图整体错位，
    // 后者灰度图正确但细丝画错。这类"看起来对不上"的问题没有读数就只能猜。
    if (ArcDebug > 0.5) {
        fragColor = vec4(vec3(texture(Edge, texCoord).a), 1.0);
        return;
    }

    // ── 跨界带：以轮廓为中心，内外各一段 ──
    float h = texture(Edge, texCoord).a;
    float distToEdge = abs(2.0 * h - 1.0);
    float band = 1.0 - smoothstep(0.0, max(ArcBand, 0.02), distToEdge);
    if (band <= 0.002) {
        discard;
    }

    // 以物品中心为原点的各向同性噪声坐标（单位 ≈ 1 个屏幕高）
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 np = (texCoord - ArcNoiseOrigin) * vec2(aspect, 1.0);

    // 各向异性：只在真的需要时才走极坐标基
    // （极坐标基随位置旋转，即使倍率=1 也不是恒等变换，会把噪声"扭"糊 —— 踩过）
    vec2 stretch = np;
    if (ArcAniso > 1.05) {
        vec2 radial = normalize(np + vec2(1e-5));
        vec2 tangential = vec2(-radial.y, radial.x);
        stretch = vec2(dot(np, radial), dot(np, tangential) * ArcAniso);
    }

    float t = Time * ArcSpeed;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float slow = fbm((stretch + vec2(0.0, -t * 0.62)) * ArcScale + fi * 7.3);
        float fast = fbm((stretch + vec2(0.0, -t * 0.34)) * (ArcScale * 0.60) + fi * 3.1);

        // 阈值主体跟着另一个低频噪声走 → 一定存在穿过点（写死绝对值时可能永远穿不过）
        float seg = fbm(stretch * 1.7 + fi * 11.0 + 3.0);
        float thr = mix(seg, ArcThreshold, 0.25);

        // 到等值线的距离（np 单位）→ 固定屏幕线宽
        float w1 = abs(thr - slow) / max(ArcScale * 0.90, 0.001);
        float w2 = abs(thr * 0.85 - fast) / max(ArcScale * 0.54, 0.001);
        float lineW = max(ArcLine, 0.0008);   // 护栏：lineW=0 时 smoothstep 的 edge 相等是 UB
        float l1 = 1.0 - smoothstep(0.0, lineW, w1);
        float l2 = 1.0 - smoothstep(0.0, lineW, w2);

        vec3 c = mix(ArcColorA, ArcColorB, fract(0.37 * fi + 0.13));
        acc += c * l1 * 0.55 + c * l2 * 0.20;

        // 距离扣减（原版 sub 的思路，用乘法形式避免负值）：远场收口
        acc *= 1.0 - clamp(length(np) * ArcSub * 0.34, 0.0, 0.55);
    }

    vec3 col = acc * ArcGain * band;
    if (ArcTonemap > 0.5) {
        col = sqrt(acesApprox(col));
    }

    // 加性混合：贡献 = rgb × alpha，所以 alpha 取亮度峰值（否则细丝会被 alpha 压没）
    float a = clamp(max(col.r, max(col.g, col.b)) * 1.4, 0.0, 1.0);
    fragColor = vec4(col, a);
}
