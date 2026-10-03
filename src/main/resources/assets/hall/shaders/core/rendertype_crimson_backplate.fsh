#version 150 core

// ─────────────────────────────────────────────────────────────────────────
//  绯红誓约背板（片元阶段）—— 纯程序化，不采样任何贴图
//
//  三层结构，全部用「距离场」画，几何本身只是一个扇形：
//
//    RingRadius.x = 0.80  之外            →  第 3 层：绕圈移动的条带 + 外发光
//    RingRadius.y = 0.60 ~ 0.80           →  第 2 层：六芒星（两个正三角形交叠）
//    中心 ~ 0.60                          →  第 1 层：正五边形 + Voronoi 方块纹
//
//  为什么用距离场而不是把顶点摆成五边形/星形：图案的边界与几何的边界解耦。
//  改形状只动这个文件，改尺寸只动 Rig 里的两个常数，两边互不影响。
//
//  线宽统一用 fwidth() 归一化（屏幕空间恒定粗细），并用 PixelWidth 托底 ——
//  极细的线上 fwidth 会趋于 0，只靠它会让线在远处整体消失。
// ─────────────────────────────────────────────────────────────────────────

in float vRadial;
in float vTheta;

uniform vec2  RingRadius;
uniform vec3  Spin;
uniform float Time;
uniform float Reveal;
uniform float Block;
uniform float Intensity;
uniform float PixelWidth;
uniform vec3  VowColor;      // 内圈的深绯红
uniform vec3  AccentColor;   // 外圈的雾粉紫

out vec4 fragColor;

const float PI = 3.14159265;

// ══════════════════════════════════════════════════════════════════════════
//  各层强度旋钮（改这里就能压掉/加强某一层，不必动结构）
// ══════════════════════════════════════════════════════════════════════════

/** 内饼里 Voronoi 方块纹的强度（D）。设 0 就退回纯暗底。 */
const float FILL_BLOCK_STRENGTH = 0.42;
/** 方块纹的格子密度（越大格子越小）。 */
const float FILL_BLOCK_FREQ     = 3.1;
/** 方块纹的流动速度：很慢，只求"活着"，不求被注意到。 */
const float FILL_BLOCK_SPIN     = 0.004;

/** 外发光的强度与厚度（E）。半径范围 1.0 → FILL_GLOW_OUTER。 */
const float GLOW_STRENGTH       = 0.30;
const float GLOW_OUTER          = 1.16;
const float GLOW_INNER          = 0.94;
/** 外发光的呼吸速度。 */
const float GLOW_PULSE_SPEED    = 0.030;

/** 六芒星内描边强度（替代掉会糊住整圈的旧填充）。 */
const float HEX_INNER_STROKE    = 0.16;

// ══════════════════════════════════════════════════════════════════════════

/**
 * 正多边形的有符号距离（内切圆半径 = apothemR）。
 * @param n        边数
 * @param rotation 整体旋转（弧度）
 */
float polygonSdf(float theta, float r, float n, float rotation, float apothemR) {
    float seg = 2.0 * PI / n;
    float a = mod(theta + rotation, seg) - seg * 0.5;   // 折到一个扇区内、以中线为 0
    return r * cos(a) - apothemR;
}

/** 抗锯齿线宽：fwidth 归一化 + 像素托底。 */
float lineAA(float sdf) {
    return fwidth(sdf) * 1.6 + PixelWidth * 260.0 + 0.004;
}

/** 稳定的 1 维哈希（与 DifficultySigilMesh 同构）。 */
float hash11(float p) {
    float v = fract(p * 0.1031);
    v *= v + 33.33;
    v *= v + v;
    return fract(v);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

/**
 * Voronoi 的**三角形度量** —— 与本体那把剑的 cosmic 图案同源
 * （见 cosmic.fsh 的 blockDist / blockCell / blockShade）。
 * 取 L∞ 而不是欧氏距离，格子才会是"方块"而不是"圆斑"。
 */
float blockDist(vec2 cellOffset) {
    vec2 v = cellOffset - 2.0 * floor(cellOffset * 0.5) - 1.0;   // → [-1, 1)
    vec2 a = abs(v);
    return max(a.x, a.y);
}

vec2 blockCell(vec2 p) {
    return mod(floor(p), 6.0);   // 绕回 6，避免大坐标上的哈希精度丢失
}

/** 径向色带（F）：内圈深绯红 → 外圈雾粉紫。 */
vec3 palette(float r, float outerRadius) {
    return mix(VowColor, AccentColor, smoothstep(0.10, outerRadius * 1.05, r));
}

// ══════════════════════════════════════════════════════════════════════════
//  第 1 层：正五边形 + Voronoi 方块纹（D）
// ══════════════════════════════════════════════════════════════════════════
vec4 layerPentagon(float theta, float r, float motion, float outerRadius) {
    float rot = -PI * 0.5 + Time * Spin.x * 0.012 * motion;
    float sdf = polygonSdf(theta, r, 5.0, rot, 0.34);

    float lw = lineAA(sdf);
    float outline = 1.0 - smoothstep(0.0, lw, abs(sdf));
    // 注意：sdf < 0 才是**内部**（cos(a) 项随半径增大而增大）。
    // 旧代码的 `inner = smoothstep(0, lw, sdf)` 其实是"外部"，
    // 与它下面那行 `inner * 0.055` 的意图正好相反 —— 一并纠正。
    float inside2 = 1.0 - smoothstep(-lw, 0.0, sdf);

    vec3 tint = palette(r, outerRadius);

    // ── D：内饼填充 Voronoi 方块纹 ──
    //    拖动前先把这一层从"大面积实底"降为"暗底 + 纹路"：
    //    旧的 fill 用 max(triUp,triDown)<0 铺了半径 0.62 的实心六边形，
    //    把整个内圈糊成一块灰白膜。这里只在内饼里加很淡的方块变化，
    //    alpha 上限压到 0.30，保证它只是"底色质感"而不是"一块板"。
    float blockSpin = Time * FILL_BLOCK_SPIN * motion;
    float cs = cos(blockSpin);
    float sn = sin(blockSpin);
    vec2 bc = vec2(cs * r * cos(theta) - sn * r * sin(theta),
                   sn * r * cos(theta) + cs * r * sin(theta));
    vec2 cell = blockCell(bc * FILL_BLOCK_FREQ);
    vec2 cellCenter = (cell + 0.5) / FILL_BLOCK_FREQ;
    float cd = blockDist((bc - cellCenter) * FILL_BLOCK_FREQ * 2.0);
    float cellTone = 0.35 + 0.65 * hash21(cell * 1.37);
    float blockFill = inside2 * (0.35 + 0.65 * cd) * cellTone;

    vec3 col = AccentColor * outline * 1.10
             + tint * (inside2 * 0.055)
             + VowColor * blockFill * FILL_BLOCK_STRENGTH;

    // 五个顶点上各点一颗亮点
    float seg = 2.0 * PI / 5.0;
    float va = mod(theta + rot + PI * 0.5, seg) - seg * 0.5;
    float vertexGlow = exp(-abs(va) * 16.0) * smoothstep(0.36, 0.10, abs(sdf));
    col += AccentColor * vertexGlow * (0.40 + 0.45 * Block);

    // 内饼的 alpha 由方块纹主导（0 ~ 0.30），不会再盖住后面的层
    float a = max(outline * 0.95, blockFill * 0.30);
    return vec4(col, a);
}

// ══════════════════════════════════════════════════════════════════════════
//  第 2 层：六芒星
// ══════════════════════════════════════════════════════════════════════════
vec4 layerHexagram(float theta, float r, float midR, float motion, float outerRadius) {
    float rot = Time * Spin.y * 0.03 * motion;
    float apothem = (midR * 2.0) * 0.5 * 0.62;
    float starR = apothem / cos(PI / 6.0);   // 星尖所在半径

    float triUp   = polygonSdf(theta, r, 3.0, rot - PI * 0.5, apothem);
    float triDown = polygonSdf(theta, r, 3.0, rot + PI * 0.5, apothem);

    float sdf = min(abs(triUp), abs(triDown));
    float lw = lineAA(sdf);
    float stroke = 1.0 - smoothstep(0.0, lw, sdf);

    // ── 修 bug：旧代码这里是 (1 - inside) * 0.10，inside = smoothstep(max(triUp,triDown))
    //    max 的负值区 = 两个三角形的**交集** = 半径 0.62 的实心六边形，
    //    比外圈过渡带还大，于是被 mix 铺满整圈 —— 那就是画面里那块灰白奶膜。
    //    现在只有最外缘一点点极淡的内描边，面积上完全跟随星形轮廓。
    float inside = smoothstep(-lw * 2.5, 0.0, max(triUp, triDown)) * step(r, starR);
    float innerStroke = inside * (1.0 - smoothstep(0.0, lw * 2.0, sdf));

    vec3 col = AccentColor * stroke * (0.95 + 0.35 * Block)
             + palette(r, outerRadius) * innerStroke * HEX_INNER_STROKE;
    return vec4(col, stroke * 0.90 + innerStroke * HEX_INNER_STROKE);
}

// ══════════════════════════════════════════════════════════════════════════
//  第 3 层：绕圈移动的条带
// ══════════════════════════════════════════════════════════════════════════
vec4 layerStrip(float theta, float r, float outerRadius, float midR, float motion) {
    float d = r - outerRadius;
    float lw = lineAA(d);
    float band = 1.0 - smoothstep(0.0, lw, abs(d));

    float phase = theta + Time * Spin.z * 0.06 * motion;
    float head = 0.5 + 0.5 * cos(phase);
    float brightness = 0.30 + 0.70 * head * head;

    float notch = smoothstep(0.55, 1.0, abs(cos(theta * 12.0)));

    vec3 col = AccentColor * (brightness * 1.25 + notch * 0.18)
             + VowColor * 0.35;

    float web = (1.0 - smoothstep(0.0, lw * 1.5, abs(mod(r * 16.0, 1.0) - 0.5) - 0.42))
              * smoothstep(midR, midR + 0.05, r) * band * 0.16;
    col += VowColor * web;

    return vec4(col, band * (0.80 + 0.20 * head) + web);
}

// ══════════════════════════════════════════════════════════════════════════
//  外发光（E）—— 条带之外的一圈柔晕
// ══════════════════════════════════════════════════════════════════════════
vec4 layerOuterGlow(float r, float outerRadius, float motion) {
    // 内外两端都做软过渡，避免出现硬边（硬边会读成"又一个圆环"而不是光晕）
    float fadeOut = 1.0 - smoothstep(outerRadius, GLOW_OUTER, r);
    float fadeIn  = smoothstep(GLOW_INNER, outerRadius, r);
    float halo = fadeOut * fadeIn;
    halo *= halo;                                   // 二次衰减，边缘更柔

    float pulse = 0.75 + 0.25 * sin(Time * GLOW_PULSE_SPEED * motion * 6.2831);
    float a = halo * GLOW_STRENGTH * pulse;
    return vec4(AccentColor * 1.15, a);
}

// ══════════════════════════════════════════════════════════════════════════

void main() {
    float r = vRadial;
    float theta = vTheta;

    float motion = (0.45 + 0.55 * clamp(Reveal, 0.0, 1.0)) * (1.0 + 0.80 * Block);

    float outerRadius = RingRadius.x;
    float midR        = RingRadius.y;

    // 三个区域用软过渡拼接，避免圆环上出现锯齿状的硬接缝
    float cross2 = smoothstep(midR - 0.02, midR + 0.02, r);           // 内 → 中
    float cross3 = smoothstep(outerRadius - 0.02, outerRadius + 0.02, r); // 中 → 外

    vec4 pent = layerPentagon(theta, r, motion, outerRadius);
    vec4 hex  = layerHexagram(theta, r, midR, motion, outerRadius);
    vec4 str  = layerStrip(theta, r, outerRadius, midR, motion);
    vec4 glow = layerOuterGlow(r, outerRadius, motion);

    // 层叠规则：外圈压中圈、中圈压内圈（先取内层，再被外层覆盖）
    vec4 col = mix(pent, hex, cross2);
    col = mix(col, str, cross3);

    // 外发光是叠加项，但它的 alpha 也要走同一条混合，所以单独加在颜色上、
    // alpha 取两者的较大值 —— 否则柔晕那一圈会因为 alpha=0 被整体丢掉。
    col.rgb += glow.rgb * glow.a;
    col.a = max(col.a, glow.a);

    // 展开时的亮度：没展开就是一块几乎看不见的暗影
    col.rgb *= Intensity * mix(0.30, 1.0, clamp(Reveal, 0.0, 1.0));

    // 超出光晕之外的角落完全丢弃 —— 几何是方框，图案是圆的，
    // 不丢的话四角会露出方形的边。
    if (r > GLOW_OUTER + 0.02 || col.a <= 0.004) discard;

    fragColor = vec4(col.rgb, clamp(col.a, 0.0, 1.0));
}
