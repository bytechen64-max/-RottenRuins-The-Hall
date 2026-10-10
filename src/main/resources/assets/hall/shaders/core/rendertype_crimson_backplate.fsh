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

/** 外发光的强度与厚度（E）。半径范围 1.0 → GLOW_OUTER。 */
const float GLOW_STRENGTH       = 0.30;
const float GLOW_OUTER          = 1.16;
const float GLOW_INNER          = 0.94;
/** 外发光的呼吸速度。 */
const float GLOW_PULSE_SPEED    = 0.030;

/**
 * 中圈那两道环的半径（乘 RingRadius.y）。
 *
 * <p>之前中圈只有一圈"12 段白色刻度"，看起来像警告标；而且刻度是**等距切块**，
 * 与外圈、内圈都没有任何连接 —— 整块读起来是"三个各自独立的零件"，也就是"碎"。
 * 现在中圈改成<b>双环 + 六根辐条</b>（辐条落在六芒星的六个星尖方向上），
 * 六芒星的星尖又正好顶在中环内侧：三层被串成一张连续的图。</p> */
const float MID_RING_INNER_MUL  = 1.010;
const float MID_RING_OUTER_MUL  = 1.155;

/** 辐条条数 = 6（与六芒星尖数一致，于是"星尖 → 辐条 → 外环"是一条连续的线）。 */
const float SPOKE_COUNT         = 6.0;
/** 辐条的角向半宽（弧度）。太大就糊成一整圈，太小会读成刻度。 */
const float SPOKE_HALF_WIDTH    = 0.055;
/** 辐条在半径方向的延伸量（相对中环内侧），让它从环上一直连到外环附近。 */
const float SPOKE_SPAN          = 0.100;

/** 六芒星内部填充强度 —— 不填的话星形只是个空壳，中心那圈会读成"空的"。 */
const float HEX_FILL_STRENGTH   = 0.13;

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

/**
 * 正多边形的<b>顶点半径</b> → 内切半径。
 *
 * <p>想要"星尖刚好落在半径 R 上"，传的就是这个函数。
 * 之前直接用 {@code (midR*2)*0.5*0.62} 拍脑袋取 apothem，
 * 结果星尖只到 0.43、缩在内圈里，读不出六芒星。</p>
 */
float apothemForVertexRadius(float vertexRadius, float n) {
    return vertexRadius * cos(PI / n);
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
    // 五边形"外接"半径取 0.42 → 顶点刚好与六芒星尖（0.43）相邻，
    // 两层不是各缩各的、而是相接的。
    float vertR = 0.42;
    float rot = -PI * 0.5 + Time * Spin.x * 0.012 * motion;
    float sdf = polygonSdf(theta, r, 5.0, rot, apothemForVertexRadius(vertR, 5.0));

    float lw = lineAA(sdf);
    float outline = 1.0 - smoothstep(0.0, lw, abs(sdf));
    // 注意：sdf < 0 才是**内部**（cos(a) 项随半径增大而增大）。
    // 旧代码的 `inner = smoothstep(0, lw, sdf)` 其实是"外部"，
    // 与它下面那行 `inner * 0.055` 的意图正好相反 —— 一并纠正。
    float inside2 = 1.0 - smoothstep(-lw, 0.0, sdf);

    vec3 tint = palette(r, outerRadius);

    // ── D：内饼填充 Voronoi 方块纹 ──
    //    alpha 上限压在 0.30，保证它是"底色质感"而不是"一块板"。
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

    // ── 中心不再空 ──
    //    之前内饼只有一层极淡的方块纹（0.055 底色 + 0.30 alpha 的纹路），
    //    在夜里的亮外圈衬托下就是"一个空框"。补两样：
    //    · 一圈核心光环（把"中心"这个位置明确下来）
    //    · 抬高的底色，让内饼整体有实处
    float core = 1.0 - smoothstep(0.0, 0.018 + PixelWidth * 200.0, abs(r - 0.115));
    core *= 1.0 - 0.55 * smoothstep(vertR * 0.80, vertR, r);

    vec3 col = AccentColor * outline * 1.10
             + tint * (inside2 * 0.16)
             + VowColor * blockFill * FILL_BLOCK_STRENGTH
             + AccentColor * core * (0.65 + 0.35 * Block);

    // 五个顶点上各点一颗亮点
    float seg = 2.0 * PI / 5.0;
    float va = mod(theta + rot + PI * 0.5, seg) - seg * 0.5;
    float vertexGlow = exp(-abs(va) * 16.0) * smoothstep(0.36, 0.10, abs(sdf));
    col += AccentColor * vertexGlow * (0.40 + 0.45 * Block);

    float a = max(max(outline * 0.95, blockFill * 0.32), core * 0.80);
    return vec4(col, a);
}

// ══════════════════════════════════════════════════════════════════════════
//  第 2 层：六芒星 + 中圈双环 + 六根辐条
// ══════════════════════════════════════════════════════════════════════════
//
//  <b>这一层是"碎"的主要修复点。</b>原来中圈只有一圈 12 段等距白色刻度，
//  与外圈、内圈都没有连接，读起来像"警告标"；而且六芒星被算小了
//  （apothem 拍脑袋取 0.62×midR，星尖只到 0.43），缩在内圈里读不出星形。
//
//  现在：星尖半径直接钉在"中环内侧"上 → 星形撑满它那一环、六个尖顶到环上；
//  六根辐条落在**同样的六个角度**上，把中环与外环串起来。
//  于是"中心 → 五边形 → 星形六尖 → 辐条 → 中环 → 外环"是一条连续的路。
vec4 layerHexagram(float theta, float r, float midR, float motion, float outerRadius) {
    float rot = Time * Spin.y * 0.03 * motion;

    float ringInner = midR * MID_RING_INNER_MUL;
    float ringOuter = midR * MID_RING_OUTER_MUL;

    // 星尖落在中环内侧稍微往里一点，视觉上"顶住"环
    float starR   = ringInner * 0.995;
    float apothem = apothemForVertexRadius(starR, 3.0);

    // 星形的自转要把"外环 + 辐条"一起带上，否则星尖会与辐条错开、又变回零碎
    float triUp   = polygonSdf(theta, r, 3.0, rot - PI * 0.5, apothem);
    float triDown = polygonSdf(theta, r, 3.0, rot + PI * 0.5, apothem);

    float sdf = min(abs(triUp), abs(triDown));
    float lw = lineAA(sdf);
    float stroke = 1.0 - smoothstep(0.0, lw * 1.45, sdf);

    // 星形内部填充：不填的话中心那圈是空的（"中心空"的另一半原因）
    float bothInside = 1.0 - smoothstep(-lw * 3.0, lw * 3.0, max(triUp, triDown));
    float fill = bothInside * HEX_FILL_STRENGTH;

    // ── 中圈双环：给这一层一个明确的边界，而不是靠散落的刻度去暗示 ──
    float bandIn  = 1.0 - smoothstep(0.0, lineAA(r - ringInner), abs(r - ringInner));
    float bandOut = 1.0 - smoothstep(0.0, lineAA(r - ringOuter), abs(r - ringOuter));

    // ── 六根辐条：把星尖、中环与外环串成一条线 ──
    //    角度与星尖一致（星尖在 θ + rot ≡ ±π/2 + k·2π/3，即每 π/3 一根），
    //    半径方向从 midR 一直伸到 midR + SPOKE_SPAN —— 起点正好压在星尖上，
    //    终点搭到外环内缘。于是"星尖 → 辐条 → 外环"是连续的，不再是三段散件。
    float seg6 = PI / 3.0;
    float ra = mod(theta + rot, seg6) - seg6 * 0.5;
    float angDist = abs(ra) * max(midR, 0.001);                   // 角向距离换算成弧长
    float spokeShape = 1.0 - smoothstep(SPOKE_HALF_WIDTH * 0.45, SPOKE_HALF_WIDTH, angDist);
    float spokeRadial = smoothstep(midR - 0.012, midR + 0.004, r)
                      * (1.0 - smoothstep(midR + SPOKE_SPAN,
                                          midR + SPOKE_SPAN + 0.014, r));
    float spoke = spokeShape * spokeRadial;

    vec3 col = AccentColor * (stroke * (1.05 + 0.35 * Block) + (bandIn + bandOut) * 0.85)
             + AccentColor * spoke * (0.75 + 0.35 * Block)
             + palette(r, outerRadius) * fill;

    float a = max(max(stroke * 0.92, (bandIn + bandOut) * 0.85),
                  max(spoke * 0.80, fill));
    return vec4(col, a);
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
