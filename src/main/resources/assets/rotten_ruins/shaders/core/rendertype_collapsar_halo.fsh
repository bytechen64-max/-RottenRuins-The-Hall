#version 150 core

// ─────────────────────────────────────────────────────────────
//  坍缩使徒「背部六芒星光环」
//
//  内容语汇来自所给参考片段（glorb/field 彩虹噪声星形）：
//  · 2D simplex noise（Ashima Arts, MIT）
//  · hsv2rgb 的彩虹取色
//  · 极坐标 pq() 与 glorb/field 的「相减成环」构造
//
//  参考片段画的是「1 中心 + 4 斜向」共 5 个 field 相加 = 五瓣花；本体要六芒星，
//  所以轮廓换成解析六芒星 SDF（等边三角形 SDF 与它转 180° 后的交集），
//  field 那份留作六芒星内部的内容。
//
//  结构：空心厚边（沿轮廓等宽的一圈）+ 内部铺参考片内容。
//  两个区域的划分只用一条 SDF 的 d 与 |d|，边框内缘与内容外缘天然对齐。
//
//  与参考片的三处必要差异：
//   1) iTime → uTime（Java 传入，含 partialTick 插值）；
//   2) 8./iResolution.y → 常量 SOFT；
//   3) glorb/field 多带一个 t 参数，便于控制相位。
// ─────────────────────────────────────────────────────────────

#define PI       3.14159265358979323846264338327950288419716939937511
#define TAU      6.28318530717958647692528676655900576839433879875021
#define HALF_PI  1.57079632679489661923132169163975144209858469968755

uniform float uTime;       // 秒（含 partialTick 插值）
uniform float uRot;        // 额外基础旋转（弧度）
uniform float uAlpha;      // 整体不透明度
uniform float uSize;       // 六芒星尖角半径（uv 空间，1.0 = 顶到 quad 边缘）
uniform float uBorder;     // 边框厚度（uv 空间）
uniform float uContent;    // 内部内容强度
uniform float uAgitation;  // 0 = 满血，1 = 濒死：自转加快、亮度升高

in vec2 texCoord;
out vec4 fragColor;

// ══════════════════════════════════════════════════════════════
//  参考片段原样搬移部分
// ══════════════════════════════════════════════════════════════

vec3 mod289(vec3 x) {
    return x - floor(x * (1. / 289.)) * 289.;
}

vec2 mod289(vec2 x) {
    return x - floor(x * (1. / 289.)) * 289.;
}

vec3 permute(vec3 x) {
    return mod289(((x * 34.) + 1.) * x);
}

float snoise(vec2 v) {
    const vec4 C = vec4(.211324865405187, .366025403784439, -.577350269189626, .024390243902439);
    vec2 i  = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1., 0.) : vec2(0., 1.);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod289(i);
    vec3 p = permute(permute(i.y + vec3(0., i1.y, 1.)) + i.x + vec3(0., i1.x, 1.));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.);
    m = m * m;
    m = m * m;
    vec3 x = 2. * fract(p * C.www) - 1.;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - .85373472095314 * (a0 * a0 + h * h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130. * dot(m, g);
}

vec3 hsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6. + vec3(0., 4., 2.), 6.) - 3.) - 1., 0., 1.);
    rgb = rgb * rgb * (3. - 2. * rgb);
    return c.z * mix(vec3(1.), rgb, c.y);
}

vec2 pq(vec2 uv) {
    return vec2(atan(uv.x, uv.y) / TAU + .5, length(uv));
}

/** 软边宽度。参考片用 8 / iResolution.y 的像素软边，这里没有像素分辨率，取等常量。 */
#define SOFT 0.022

vec4 glorb(vec2 uv, vec2 offset, float radius, float t) {
    vec2 p = pq(uv + offset);
    float r = radius * snoise(uv + t * .2);
    float m = smoothstep(r + SOFT, r - SOFT, p.y);
    vec3 c = hsv2rgb(vec3(p.x, 1., 1.));
    return vec4(c, 1.) * m;
}

vec4 field(vec2 uv, vec2 offset, float radius, float t) {
    vec4 c0 = glorb(uv, offset, radius, t);
    vec4 c1 = glorb(uv, offset, radius * .92, t);
    return c0 - c1;
}

// ══════════════════════════════════════════════════════════════
//  六芒星轮廓
// ══════════════════════════════════════════════════════════════

/** 等边三角形 SDF（尖角朝上，外接圆半径 r）。 */
float sdEquilateralTriangle(vec2 p, float r) {
    const float k = 1.7320508;           // sqrt(3)
    p.x = abs(p.x) - r;
    p.y = p.y + r / k;
    if (p.x + k * p.y > 0.0) p = vec2(p.x - k * p.y, -k * p.x - p.y) / 2.0;
    p.x -= clamp(p.x, -2.0 * r, 0.0);
    return -length(p) * sign(p.y);
}

/** 六芒星 = 正三角 ∩ 倒三角（把点取负 = 旋转 180°）。 */
float sdHexagram(vec2 p, float r) {
    return max(sdEquilateralTriangle(p, r), sdEquilateralTriangle(-p, r));
}

void main() {
    // quad 的 0..1 → 以中心为原点的 [-1,1]²
    vec2 uv = texCoord * 2.0 - 1.0;

    float rad = length(uv);
    if (rad > 1.05) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }

    // 自转：慢速基础自转 + 濒死加速
    float spin = 0.22 + 0.55 * uAgitation;
    float ang = uRot + uTime * spin;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 p = mat2(ca, -sa, sa, ca) * uv;

    float theta = atan(p.x, p.y);

    // 一条 SDF 同时界定两个区域：
    //   |d| ≤ uBorder → 空心厚边（沿轮廓等宽）
    //   d ≤ -uBorder  → 内部（铺内容）
    float d = sdHexagram(p, uSize);

    float edge = smoothstep(uBorder, 0.0, abs(d));       // 厚边
    float core = smoothstep(uBorder * 0.22, 0.0, abs(d)); // 边框白芯
    float inside = smoothstep(0.0, -uBorder, d);         // 内部（d 越负越接近 1）

    // 内部内容：参考片的 field() 环（细亮环）+ 一层铺满星内的虹彩星云。
    // 只放细环的话星内几乎是空的（实测径向亮度 0.004，肉眼等于"看不见"），
    // 所以额外用按极角取色的 hsv2rgb + 双层噪声铺底，保证星内是"有内容的"。
    vec4 c1 = field(p, vec2(0.0), 0.85, uTime);
    vec4 c2 = field(p, vec2(0.0), 0.58, uTime);
    vec4 c3 = field(p, vec2(0.0), 0.34, uTime);
    vec4 content = c1 + c2 + c3 * 0.8;
    content *= inside * uContent;

    // 彩虹按极角取色相；噪声扰动让边框"活"起来
    float hue = theta / TAU + uTime * 0.06;
    vec3 rainbow = hsv2rgb(vec3(hue, 0.92, 1.0));
    float n = snoise(p * 2.2 + uTime * 0.20);
    float rim = 1.0 + 0.32 * n;

    // 星内铺底：粗噪声定大块明暗，细噪声加纹理，色相随极角 + 噪声偏移 → 彩色星云
    float nBig = snoise(p * 1.6 + uTime * 0.20) * 0.5 + 0.5;
    float nFine = snoise(p * 4.5 - uTime * 0.35) * 0.5 + 0.5;
    float fillMask = (0.30 + 0.70 * nBig) * (0.55 + 0.45 * nFine) * inside * uContent;
    vec3 fillRgb = hsv2rgb(vec3(fract(hue + nBig * 0.18), 0.95, 1.0)) * 0.85;

    // 空心厚边 + 星内内容
    vec3 col = rainbow * 1.45 * rim * edge
             + vec3(1.0) * core * 0.85
             + content.rgb * content.a
             + fillRgb * fillMask;

    // 亮度只乘一次：直接把 col 当成要加进去的光（json 里是加法混合）
    float coverage = edge * rim + core + content.a + fillMask;
    if (coverage <= 0.003) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }
    fragColor = vec4(col * uAlpha, 1.0);
}
