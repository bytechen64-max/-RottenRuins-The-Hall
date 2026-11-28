#version 150 core

// ═════════════════════════════════════════════════════════════
//  绯红誓约 · 法阵（片元阶段）
//
//  同一个着色器画四种东西，靠 uMode 分支 —— 它们的"图案语言"本来
//  就是同一套（环 / 刻度 / 符文 / 辐条），分成四个着色器只会让配色
//  与刻度密度出现四份互不相同的实现：
//
//    uMode = 0  蓄力法阵  玩家身下那一圈，随蓄力向外扩散
//    uMode = 1  巨型法阵  蓄满放出去的那一个（铺开 / 周期扩散波 / 终末）
//    uMode = 2  （空号）  第一版那根"会发光的圆管"光柱，实机反馈说它和参考
//                        内容毫无关系，已由 uMode = 3 取代。分支保留不占成本
//                        （uMode < 2.5 时什么都不画），但 Java 侧不再传 2。
//    uMode = 3  轰击光柱  终末时自天而降的那一根，见下面 beamSigil
//    uMode = 4  天顶裂口  终末预警里"天被捅开"的那张圆盘，见 portalSigil
//
//  ── 几何与极坐标的约定（沿用 rendertype_verdict_field）──
//    Color.r / Normal.x = 径向（圆盘）或高度（光柱）
//    Color.g / Normal.y = 角度归一化 0..1
//
//  ⚠️ 光柱这一路的侧影不是理想圆柱：顶点阶段会按 (角度, 高度, 时间) 把顶点
//  沿半径推开一点（见 rendertype_crimson_sigil.vsh 的 wobble）。所以 Java 侧
//  必须沿高度**分环**发几何，否则只有两条棱边、推不出轮廓起伏。
//
//  ⚠️ 圆盘的最后一个扇区的 a1 恰好是 1.0（不是 0.0），
//  否则 cos(TAU*a) 会在接缝处跳变，画面上是一道永远修不掉的裂缝。
//  Java 侧 emitDisc 已经把这一点写死了。
// ═════════════════════════════════════════════════════════════

uniform float uTime;
uniform float uIntensity;     // 0..1，Java 侧已含淡入淡出
uniform float uMode;          // 0 蓄力 / 1 巨型法阵 / 3 光柱 / 4 天顶裂口（2 是空号）
uniform float uRadius;        // 逻辑半径（格）；光柱模式下是**这一层的柱半径**
uniform float uMeshRadius;    // 网格半径（格）
uniform float uCharge;        // 0..1 蓄力进度（仅 uMode=0）
uniform float uPulse;         // 0..1 当前扩散波相位（仅 uMode=1）
uniform float uProgress;      // 0..1 生命周期
uniform float uFinale;        // 0..1 终末进度
uniform float uSeed;          // 每张法阵一个种子

// ── 仅 uMode=1（巨型法阵）与 uMode=4（天顶裂口）──
//  uWarn 在预警开始前恒为 0，预警结束后钳在 1。地面用它画"向内收的三圈"，
//  裂口用它决定开洞大小 —— 两处必须读同一个量，否则画面会出现"地上已经开始
//  收、天上却还没开"的错位。
uniform float uWarn;
//  触地之后经过的**秒数**；还没触地时 Java 侧传 -1。
//  用"有没有触地"这一条判据（>= 0）而不是另开一个布尔 uniform：
//  片元里少一个分支、Java 侧也少一处可能忘记同步的开关。
uniform float uImpactAge;

// ── 仅 uMode=3（光柱）──
//  Java 只给"这一帧可见的那一段"发几何，所以片元不能拿"归一化高度"当标尺 ——
//  那样图案会跟着柱子的长度一起伸缩，读起来像一张被拉扯的贴图。
//  这几个量把图案钉死在**世界尺度**上：
uniform float uBeamBase;      // 地面世界 Y（爆闪与图案的世界原点）
uniform float uBeamHead;      // 光柱前缘（柱底）世界 Y —— 下落的那个"头"
uniform float uBeamSpan;      // 可见段长度（格）
uniform float uBeamRadius;    // 这一层的柱半径（格）
// 柱轴：位置（视空间）与方向（世界 +Y 在视空间）—— 片元用它反解世界高度、
// 以及算"视线到柱轴的距离"做解析体积；顶点阶段用同一组做轮廓波动。
uniform vec3  uBeamAxisVS;
uniform vec3  uBeamAxisDir;
uniform float uImpact;        // 0..1 触地爆闪（触地那一下 = 1）
uniform float uLayer;         // 光柱分层：0 主体 / 1 白热核 / 2 幽灵柱 / 3 枪尖

uniform vec3  uColorA;        // 亮粉
uniform vec3  uColorB;        // 紫
uniform vec3  uColorC;        // 雾粉紫（近白）

in vec4 vertexColor;
in vec3 vLocal;
// 顶点的**世界 Y**（格）。见 .vsh 与 emitCylinder 上的说明：
// 它是 float 属性，所以能承受 0..100+ 格而不像 Color 那样被 8 bit 回绕。
in float vHeight;

out vec4 fragColor;

#define TAU 6.28318530718
#define PI  3.141592654

float hash11(float x) { return fract(sin(x * 127.1) * 43758.5453123); }

// 二维值噪声（单层）。光柱上只用来给亮度加"颗粒"，不需要 fBm：
// 光柱在屏幕上只占一条，采样点密度本来就低，三层 fBm 的观感提升肉眼看不出来，
// 代价却是每片元多 8 次 sin —— 这一招在混战里会和别的特效同屏。
float hash12(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

// 高斯线：w 越小线越细。所有"环 / 条"都用它，保证边缘是平滑衰减而不是硬边。
float gauss(float x, float w) {
    float v = x / max(w, 1e-5);
    return exp(-v * v);
}

// 在 count 等分处各画一条刻度：返回 1 表示落在刻度上。
float ticks(float a, float count, float halfW) {
    float s = fract(a * count);
    float d = min(s, 1.0 - s);
    return 1.0 - smoothstep(halfW, halfW + 0.20, d);
}

vec2 rot2(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

// 等边三角形的边界距离（Inigo Quilez, MIT）。
// 取 abs() 之后是"三条边"，正反两个三角形叠起来就是六芒星。
float sdTri(vec2 p, float r) {
    const float k = 1.7320508;
    p.x = abs(p.x) - r;
    p.y = p.y + r / k;
    if (p.x + k * p.y > 0.0) p = vec2(p.x - k * p.y, -k * p.x - p.y) * 0.5;
    p.x -= clamp(p.x, -2.0 * r, 0.0);
    return -length(p) * sign(p.y);
}

float hexagram(vec2 p) {
    float d0 = abs(sdTri(p, 0.60));
    float d1 = abs(sdTri(rot2(p, PI / 3.0), 0.60));
    return 1.0 - smoothstep(0.0, 0.012, min(d0, d1));
}

// ═════════════════════════════════════════════════════════════
//  地面法阵共用的"笔画零件"
//
//  与背板（rendertype_crimson_backplate.fsh）是同一套纹章语言：
//  · 线用 gauss（柔和环/光晕）与 stroke（硬一点的笔画）；
//  · 符文是"2~3 笔 + 星点"的笔画，不是方块 —— 旧版用 step() 拼矩形，
//    实机读起来是一圈随机马赛克，这是这次重做要治的第一个问题；
//  · 所有亮度最后走一次软压缩 col/(1+col*k)，加法混合下才不会糊成纯白。
// ═════════════════════════════════════════════════════════════

/** 硬一点的线（符文笔画用）。w 是半宽。 */
float stroke(float d, float w) {
    return 1.0 - smoothstep(0.0, max(w, 1.0e-5), abs(d));
}

/**
 * 一格"誓约文字"：2~3 笔 + 一颗星点。
 * 格空间：fx ∈ [-0.5, 0.5]（角向），fy ∈ [-1, 1]（径向）。
 */
float runeGlyph(float fx, float fy, float h1, float h2, float h3) {
    float g = 0.0;

    // 竖笔（必有）：位置与高度随机
    float x1 = mix(-0.26, 0.26, h1);
    float hY = mix(0.32, 0.58, h2);
    g += stroke(fx - x1, 0.070) * step(abs(fy), hY);

    // 横笔：从竖笔中途伸出
    float y1 = mix(-0.32, 0.32, h3);
    float wX = mix(0.10, 0.28, hash11(h1 * 7.3 + h2 * 3.1));
    g += stroke(fy - y1, 0.070) * step(abs(fx - x1 * 0.6), wX);

    // 斜笔：约 1/3 的格子有，方向由 h2 决定
    float dir = h2 > 0.5 ? 1.0 : -1.0;
    g += stroke((fx - x1 * 0.4) - (fy + 0.10) * dir * 0.42, 0.052)
       * step(abs(fy + 0.14), 0.22) * step(0.68, h3);

    // 星点
    g += gauss(length(vec2(fx - x1, fy - (0.42 + 0.08 * h3))), 0.085)
       * step(0.55, hash11(h3 * 5.7 + h1));

    return clamp(g, 0.0, 2.0);
}

/**
 * 从极坐标里取一条符文带，返回笔画强度（未乘颜色）。
 *
 * @param rr        归一化半径（1.0 = 逻辑边界）
 * @param angTurns  角度（0..1 圈，可带自转）
 * @param centerR   带的中心半径
 * @param halfH     带的半高
 * @param cells     格子数（24 / 36 都试过：再密就读不出笔画了）
 */
float runeBand(float rr, float angTurns, float centerR, float halfH, float cells) {
    float cf = angTurns * cells;
    float cell = floor(cf);
    float fx = fract(cf) - 0.5;
    float fy = (rr - centerR) / halfH;
    float band = 1.0 - smoothstep(0.82, 1.0, abs(fy));
    if (band < 0.001) return 0.0;
    float h1 = hash11(cell * 1.37 + 2.7);
    float h2 = hash11(cell * 2.71 + 8.1);
    float h3 = hash11(cell * 0.73 + 4.3);
    return runeGlyph(fx, fy, h1, h2, h3) * band;
}

/**
 * 放射状的"刃"：从 rIn 伸到 rOut，根部宽、尖端收。
 *
 * @param ad        到刃中心线的角度差（弧度）
 * @param baseHalfW 根部半宽（弧度）
 */
float blade(float rr, float ad, float rIn, float rOut, float baseHalfW) {
    float u = (rr - rIn) / max(rOut - rIn, 1.0e-4);
    if (u < 0.0 || u > 1.0) return 0.0;
    float halfW = baseHalfW * (1.0 - pow(u, 0.85));
    return (1.0 - smoothstep(0.0, max(halfW, 1.0e-4), ad))
         * (1.0 - smoothstep(0.72, 1.0, u));
}

// ═════════════════════════════════════════════════════════════
//  ① 蓄力法阵（uMode = 0）
//
//  "渐渐扩散运动"这条需求仍然是主角，但这一版把它做成一张**正在起誓的印**：
//    · 外双环 + 沿环奔跑的亮弧：先给出"这是一个界"；
//    · 48 枚反向旋转的刻度 + 24 格笔画符文：中层的细节层；
//    · 8 根刃状辐条：从内封印环伸向外，随蓄力变长 —— 与"外扩的环"形成对拉；
//    · 三圈相位错开的外扩环：外扩这件事本身；
//    · 中心十字 + 亮核：蓄力能量的落点（实机被玩家身体挡住一半，所以要够亮）；
//    · 余烬：外圈缓慢外漂的星点，让静止帧也有"活着"的证据。
//
//  过载（最后 15%）不再是整体乘个系数，而是让外环与刃一起高频抖动 ——
//  读起来是"这块印快要按不住了"，而不是"灯变亮了"。
// ═════════════════════════════════════════════════════════════
void chargeSigil(float r, float a, out vec3 col) {
    float t = uTime;
    float c = clamp(uCharge, 0.0, 1.0);
    float rr = clamp(r, 0.0, 1.14);
    float ang = a * TAU;
    float spin = t * (0.12 + 0.55 * c);
    float angR = ang + spin;              // 顺转层
    float angT = ang - spin * 1.5;        // 反转层
    float turns = angR / TAU;

    float lw = clamp(1.10 / max(uRadius, 0.55), 0.004, 0.080);

    // ① 外双环 + 沿环奔跑的亮弧
    float rim   = gauss(rr - 1.000, lw * 1.10);
    float rimIn = gauss(rr - 0.955, lw * 0.62);
    float sweep = pow(0.5 + 0.5 * cos(ang - t * 1.9 - 1.1), 14.0)
                + 0.55 * pow(0.5 + 0.5 * cos(ang + t * 1.3 + 2.4), 20.0);

    // 外缘短刺（16 根，与背板冠冕呼应）
    float thorns = 0.0;
    if (rr > 0.94) {
        for (int i = 0; i < 16; i++) {
            float sa = float(i) * (TAU / 16.0) + spin * 0.6;
            float ad = abs(mod(ang - sa + PI, TAU) - PI);
            thorns += blade(rr, ad, 1.000, 1.090, 0.026);
        }
    }

    // ② 刻度带：48 枚，反向转
    float tickBand = smoothstep(0.878, 0.890, rr) * (1.0 - smoothstep(0.936, 0.948, rr));
    float tickMarks = tickBand * ticks(fract(angT / TAU), 48.0, 0.16);

    // ③ 符文带：24 格笔画符文
    float glyph = runeBand(rr, turns, 0.760, 0.105, 24.0);
    float cellIdx = floor(turns * 24.0);
    float glyphLit = 0.55 + 0.45 * pow(0.5 + 0.5 * sin(
            t * (0.9 + 1.3 * hash11(cellIdx)) + hash11(cellIdx * 2.3 + 7.0) * TAU), 2.0);
    glyph *= glyphLit * (0.72 + 0.55 * c);

    // ④ 八根刃：从内圈伸向外，随蓄力变长
    float blades = 0.0;
    if (rr > 0.20 && rr < 0.95) {
        for (int i = 0; i < 8; i++) {
            float fi = float(i);
            float sa = fi * (TAU / 8.0) + t * 0.10;
            float ad = abs(mod(angR - sa + PI, TAU) - PI);
            blades += blade(rr, ad, 0.235, mix(0.58, 0.90, c), 0.075);
        }
    }

    // ⑤ 三圈相位错开的外扩环（带角向"彗尾"）
    float rings = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float ph = fract(t * (0.17 + 0.28 * c) + fi * 0.3333);
        float rw = mix(0.10, 1.00, ph);
        float head = 0.5 + 0.5 * cos(ang - fi * 2.1 - t * 0.8);
        float wid = lw * (1.5 + 1.4 * ph) + 0.010;
        rings += gauss(rr - rw, wid) * (1.0 - ph) * (1.0 - ph)
               * (0.45 + 0.55 * head * head);
    }

    // ⑥ 内封印环 + 四臂十字 + 中心核
    float seal = gauss(rr - 0.300, lw * 0.85) * (0.45 + 0.55 * c);
    float cross4 = pow(max(0.0, cos(angT * 4.0 + 1.2)), 18.0)
                 * smoothstep(0.02, 0.08, rr) * (1.0 - smoothstep(0.14, 0.235, rr));
    float core = gauss(rr, 0.070) * (0.55 + 1.70 * c * c);

    // ⑦ 余烬：外圈缓慢外漂的亮点（36 格，相位错开）
    float eCells = 36.0;
    float ecf = ang / TAU * eCells;
    float ecell = floor(ecf);
    float efx = fract(ecf) - 0.5;
    float eh1 = hash11(ecell * 1.31 + 9.0);
    float eh2 = hash11(ecell * 2.17 + 3.0);
    float drift = fract(t * 0.12 * (0.4 + eh1) + eh2);
    float eu = 0.80 + 0.20 * eh1 + 0.10 * drift;
    float embers = gauss(rr - eu, 0.010) * gauss(efx, 0.12)
                 * (1.0 - drift) * (1.0 - drift) * (0.40 + 0.60 * eh2);

    vec3 col2 = uColorA * (rim * 1.05 + rimIn * 0.35 + sweep * rim * 1.15
                         + rings * 1.10) 
              + uColorB * (tickMarks * 0.55 + glyph * 1.20
                         + seal * 0.85 + cross4 * 0.85)
              + uColorC * (embers * 1.30 + core * 1.05 + blades * 0.70
                         + thorns * 0.65);

    // 过载：外环与刃一起高频抖动，最后 15% 才出现
    float over = smoothstep(0.85, 1.0, c) * (0.5 + 0.5 * sin(t * 40.0));
    col2 *= 1.0 + 0.45 * over;
    col2 *= 1.0 - smoothstep(1.02, 1.14, rr);

    // 软过曝压缩：加法叠出来的峰值不压会全白（旧版就是这个问题）
    col = col2 / (1.0 + col2 * 0.45);
}

// ═════════════════════════════════════════════════════════════
//  ② 巨型法阵（uMode = 1）
//
//  这一版把旧版的「外圈 + 随机方块 + 六芒星 + 中心白团」换成一枚
//  **同轴的血月誓约印**，从外到内的读法是：
//      外缘双环 + 奔跑亮弧  →  96 枚刻度带  →  36 格笔画符文带
//      →  12 根誓约刃 + 轨道环  →  内封印环（框住立方体核心）  →  中心三臂慢旋
//  亮度全部走软压缩，所以外圈、符文、刃、波是四个层次，而不是一团白。
//
//  动态：符文逐字明灭、刃缓慢自转、外缘亮弧绕圈跑、余烬外漂；
//  uPulse 扩散波、uWarn 三圈内收、uImpactAge 触地白闪都保留原有时间语义。
//  中心 r<0.30 刻意留白 —— 那里坐着液态铬立方体，抢戏只会两个都看不清。
// ═════════════════════════════════════════════════════════════
void fieldSigil(float r, float a, out vec3 col) {
    float t = uTime;
    float rr = clamp(r, 0.0, 1.16);
    float ang = a * TAU;
    float seed = uSeed;
    float angR = ang + t * 0.030 + seed;      // 顺转层（符文/刃）
    float angT = ang - t * 0.045;             // 反转层（刻度/轨道）
    float turns = angR / TAU;

    float lw = clamp(1.05 / max(uRadius, 0.5), 0.004, 0.085);

    float line = 0.0;
    float glow = 0.0;

    // ① 外缘：主环 + 内侧细环 + 外光晕 + 绕圈奔跑的亮弧
    line += gauss(rr - 1.000, lw * 1.05) * 1.25;
    line += gauss(rr - 0.958, lw * 0.55) * 0.50;
    float sweep = pow(0.5 + 0.5 * cos(ang - t * 0.55 + seed * 2.0), 18.0);
    line += sweep * gauss(rr - 1.000, lw * 1.8) * 0.90;
    glow += step(1.0, rr) * exp(-(rr - 1.0) * 12.0) * 0.40;
    // 外缘之外再点一圈短刺（与背板的冠冕呼应）
    if (rr > 0.94) {
        for (int i = 0; i < 24; i++) {
            float sa = float(i) * (TAU / 24.0) - t * 0.012 + seed;
            float ad = abs(mod(ang - sa + PI, TAU) - PI);
            line += blade(rr, ad, 1.000, 1.105, 0.020) * 0.55;
        }
    }

    // ② 刻度带：96 枚细刻 + 内侧一条细环
    float tickBand = smoothstep(0.874, 0.886, rr) * (1.0 - smoothstep(0.938, 0.950, rr));
    line += tickBand * ticks(fract(angT / TAU), 96.0, 0.20) * 1.00;
    line += gauss(rr - 0.860, lw * 0.50) * 0.45;

    // ③ 符文带：36 格笔画符文（主细节层）。逐格明灭，像被逐字点亮
    float glyph = runeBand(rr, turns, 0.760, 0.105, 36.0);
    float cellIdx = floor(turns * 36.0);
    float glyphLit = 0.62 + 0.38 * pow(0.5 + 0.5 * sin(
            t * (0.25 + 0.35 * hash11(cellIdx * 1.7))
              + hash11(cellIdx * 2.3 + 7.0) * TAU), 3.0);
    line += glyph * glyphLit * 1.18;

    // ④ 十二根"誓约刃"：从内环指向外，尖头收细；刃间夹一条轨道环 + 48 枚小刻
    float blades = 0.0;
    if (rr > 0.30 && rr < 0.63) {
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            float sa = fi * (TAU / 12.0) + t * 0.020 + seed;
            float ad = abs(mod(angR - sa + PI, TAU) - PI);
            blades += blade(rr, ad, 0.315, 0.615, 0.028);
        }
    }
    line += blades * 0.85;
    line += gauss(rr - 0.620, lw * 0.55) * 0.55;
    float orbBand = smoothstep(0.470, 0.482, rr) * (1.0 - smoothstep(0.505, 0.517, rr));
    line += orbBand * ticks(fract((angT * 1.6) / TAU), 48.0, 0.16) * 0.60;

    // ⑤ 内封印环（r=0.30）：亮环 + 24 枚刻 + 8 根短刺 —— 给立方体核心搭台
    line += gauss(rr - 0.300, lw * 0.95) * 1.00;
    float innerTick = smoothstep(0.312, 0.320, rr) * (1.0 - smoothstep(0.346, 0.354, rr));
    line += innerTick * ticks(fract((angT * 1.3) / TAU), 24.0, 0.18) * 0.55;
    float innerSpikes = 0.0;
    if (rr < 0.42) {
        for (int i = 0; i < 8; i++) {
            float sa = float(i) * (TAU / 8.0) + t * 0.03;
            float ad = abs(mod(angR - sa + PI, TAU) - PI);
            innerSpikes += blade(rr, ad, 0.300, 0.400, 0.020);
        }
    }
    line += innerSpikes * 0.55;

    // ⑥ 中心：留给立方体核心，只留三臂慢旋与柔光
    float arms = pow(0.5 + 0.5 * cos(angT * 3.0 + rr * 9.0), 6.0)
               * smoothstep(0.02, 0.10, rr) * (1.0 - smoothstep(0.20, 0.30, rr));
    glow += arms * 0.32 + gauss(rr, 0.10) * 0.20;

    // ⑦ 余烬：外圈缓慢外漂的星点（64 格）
    float eCells = 64.0;
    float ecf = ang / TAU * eCells;
    float ecell = floor(ecf);
    float efx = fract(ecf) - 0.5;
    float eh1 = hash11(ecell * 1.31 + seed * 3.1);
    float eh2 = hash11(ecell * 2.17 + seed * 5.7);
    float drift = fract(t * 0.030 * (0.4 + eh1) + eh2);
    float eu = 0.72 + 0.34 * eh1 + 0.12 * drift;
    float embers = gauss(rr - eu, 0.010) * gauss(efx, 0.14)
                 * (1.0 - drift) * (1.0 - drift) * (0.35 + 0.65 * eh2);

    // ⑧ 扩散波：主环 + 尾随回声 + 波前刻度闪（与真实冲击环同一 tick）
    float px = (rr - uPulse) / 0.035;
    // 波从内封印环餐靠外一点才开始：不然 uPulse=0 时中心会亮成一块白饼（实机里正好被立方体核心盖住，但光晕会溢出来）。
    float waveGate = smoothstep(0.045, 0.130, rr);
    float wave = exp(-px * px) * (1.0 - uPulse) * waveGate;
    float echo = gauss(rr - (uPulse - 0.075), 0.020) * (1.0 - uPulse) * 0.45 * waveGate;
    float waveTicks = ticks(fract(angT / TAU), 96.0, 0.22)
                    * gauss(rr - uPulse, 0.020) * (1.0 - uPulse) * 0.90 * waveGate;

    // ⑨ 终末 / 生命末段（保留原语义：整阵收紧并过载）
    float finale = clamp(uFinale, 0.0, 1.0);
    line *= 1.0 + 1.30 * finale;
    glow *= 1.0 + 0.80 * finale;
    line *= 1.0 - 0.24 * smoothstep(0.70, 1.0, uProgress);

    vec3 c = uColorA * (line * 0.95)
           + uColorB * (glow * 0.75 + blades * 0.35)
           + uColorC * (wave * 1.30 + echo * 0.70 + waveTicks * 0.55
                        + embers * 1.35 + innerSpikes * 0.25);

    // 外围软淡出（逻辑边界 1.0 之外靠 halo 收）
    c *= 1.0 - smoothstep(1.04, 1.16, rr);

    // ⑩ 终末预警：三圈向内收（与扩散波反向），最后 1.5 秒整张阵开始"收"
    if (uWarn > 0.001) {
        float w = clamp(uWarn, 0.0, 1.0);
        for (int i = 0; i < 3; i++) {
            float ph = fract(w * 1.45 + float(i) * 0.3333);
            float rw = mix(1.02, 0.05, ph);
            c += uColorC * gauss(rr - rw, lw * (1.1 + 2.2 * ph))
               * (1.0 - ph) * (0.30 + 1.10 * w) * 0.80;
        }
        c += mix(uColorA, uColorC, 0.55) * gauss(rr, 0.09 + 0.05 * (1.0 - w))
           * (0.18 + 1.35 * w * w);
    }

    // ⑪ 触地：白热冲击环 + 阵心灼痕 + 全阵白闪（原语义，亮度略压）
    if (uImpactAge >= 0.0) {
        float it = uImpactAge;
        float shockR = 1.06 * (1.0 - exp(-it * 7.5));
        float ww = mix(0.085, 0.010, clamp(it * 2.4, 0.0, 1.0));
        float decay = exp(-it * 2.6);
        float shock = gauss(rr - shockR, ww) * decay;
        c += uColorC * shock * 4.2 + vec3(1.0, 0.94, 1.0) * shock * 2.4;
        c += mix(uColorA, vec3(1.0), 0.5) * gauss(rr, 0.16) * exp(-it * 1.4) * 1.20;
        c += vec3(1.0, 0.93, 1.0) * exp(-it * 11.0)
           * (1.0 - smoothstep(0.0, 0.92, rr)) * 1.00;
    }

    // 软过曝压缩：加法叠起来峰值高，不压会全白、层次全丢
    col = c / (1.0 + c * 0.50);
}

// ═════════════════════════════════════════════════════════════
//  ③ 终末光柱（uMode = 3）—— "血誓长矛"
//
//  【这一版先修一个真 bug：vHeight 在实机里恒等于 1.0】
//  `POSITION_COLOR_NORMAL` 的 NORMAL 是 **3 个 signed byte、归一化**。
//  Java 侧把世界 Y（36~70）写进 `Normal.x`，而 `BufferVertexConsumer.normalIntValue()`
//  第一步就是 `clamp(x, -1, 1)` —— 整根柱子的 vHeight 全部变成 1.0。
//  于是所有按高度算的图案（电弧相位、下扫环、前缘光斑、tail 淡出）都冻在
//  `hN≈0`、`wy0≈-地面高度` 上：实机看到的是一根**均匀竖条纹的塑料管**。
//
//  所以这里不再用 vHeight：柱轴在视空间的位置/方向（uBeamAxisVS / uBeamAxisDir）
//  Java 每层都传了，点积一下就能精确反解世界高度，与相机朝向无关：
//      worldY = uBeamBase + dot(vLocal - uBeamAxisVS, normalize(uBeamAxisDir))
//
//  【这一版的形状：一根"长矛"，不是一根管子】
//    · 轮廓由**解析体积**给出：算视线到柱轴的距离 d，radial = sqrt(1 - (d/r)²)。
//      中心最亮、轮廓自然溶解 —— 没有硬边，也就不再是"半透明膜/塑料管"。
//    · 主体（uLayer=0）：向下滚的等离子噪声 + 3 条细电弧丝 + 下扫脉冲 + 前缘光斑。
//      电弧丝用 pow(sin, 26)（细而亮），不再是粗条纹。
//    · 白热核（uLayer=1）：只有主体 1/3 粗的近白细柱 + 螺旋灯丝 + 强前缘。
//    · 外光晕（uLayer=4，新增）：最宽最淡的一层，只负责"这里有一大团光"。
//    · 触地飞溅（uLayer=5，新增）：柱底一圈宽喇叭 + 角向火舌，替掉原来的"铅笔尖"。
//    · 幽灵柱（uLayer=2）：预警段那根极淡的光通道，用同一套体积感。
//
//  Java 侧对应改动：三层 → 四层（halo=4/body=0/core=1），枪尖按层给不同的
//  长度/亮度，触地时多画一层 uLayer=5 的飞溅；蓄力柱补 core/halo 两层。
// ═════════════════════════════════════════════════════════════

/** 世界 Y（格）。见上面关于 Normal byte 的说明 —— 这是本模式的正确取法。 */
float beamWorldY() {
    vec3 D = uBeamAxisDir;
    float dl = length(D);
    if (dl < 1.0e-4) return vHeight;          // 兜底（圆盘模式不会走到这里）
    return uBeamBase + dot(vLocal - uBeamAxisVS, D / dl);
}

/**
 * 解析体积：相机在视空间原点，射线方向 = normalize(vLocal)。
 * 求这条射线到"柱轴直线"的距离 d，再换算成归一化弦长。
 * 返回 1 = 穿过柱心，0 = 柱轮廓之外。
 */
float beamChord(float radius) {
    vec3 u = normalize(vLocal + vec3(1.0e-5));
    vec3 D = uBeamAxisDir;
    float dl = length(D);
    if (dl < 1.0e-4) return 1.0;
    vec3 n = cross(u, D / dl);
    float nl = length(n);
    if (nl < 1.0e-4) return 1.0;
    float d = abs(dot(uBeamAxisVS, n)) / nl;
    float x = clamp(d / max(radius, 1.0e-3), 0.0, 1.0);
    return sqrt(max(0.0, 1.0 - x * x));
}

/**
 * 光柱主体 / 白热核 / 幽灵柱 / 外光晕 / 触地飞溅。
 *
 * @param a 角度 0..1
 */
void beamSigil(float a, out vec3 col) {
    float t = uTime;
    float ang = a * TAU;
    float rad = max(uBeamRadius, 1.0e-3);
    float span = max(uBeamSpan, 0.01);

    float worldY = beamWorldY();
    float rel = worldY - uBeamHead;            // 0 = 前缘，span = 尾端
    float hN = clamp(rel / span, 0.0, 1.0);
    float wy0 = worldY - uBeamBase;            // 离地高度（格）—— 图案的世界标尺
    float chord = beamChord(rad);              // 0 轮廓 .. 1 柱心

    // 向下滚的两层等离子噪声（世界尺度，柱子下落时图案不跟着伸缩）
    float n1 = vnoise(vec2(ang * 2.4 + t * 0.35, wy0 * 0.26 - t * 2.4));
    float n2 = vnoise(vec2(ang * 6.5 - t * 0.60, wy0 * 0.70 - t * 5.2));
    float plasma = 0.30 + 0.70 * (0.62 * n1 + 0.38 * n2);

    // 下扫脉冲：锐利前缘 + 一段拖尾
    float ph = fract(wy0 * 0.20 - t * 2.1);
    float pulse = pow(ph, 7.0) + 0.30 * pow(ph, 2.0);

    // 前缘光斑只活在世界尺度的一小段里（柱头）
    float head = exp(-max(rel, 0.0) / max(rad * 1.15, 0.9));
    // 尾端淡出：不能是一条被切平的边
    float tail = 1.0 - smoothstep(0.55, 1.0, hN);
    // 柱顶收口：最后 18% 整体褪掉，避免"一根被横切的柱子"
    float endFade = 1.0 - smoothstep(0.82, 1.0, hN);
    // 触地爆闪 + 地面灼痕（只在地面附近几个柱半径内）
    float gh = max(wy0, 0.0);
    float groundGlow = exp(-gh / max(rad * 0.85, 0.9)) * clamp(uImpact, 0.0, 1.0);

    if (uLayer < 0.5) {
        // ── 主体：等离子鞘 + 细电弧丝 ──
        float arcs = 0.0;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float aph = ang + wy0 * (0.52 + 0.13 * fi) + fi * 2.1
                      - t * (10.0 + 2.2 * fi) + (n1 - 0.5) * 2.2;
            arcs += pow(0.5 + 0.5 * sin(aph), 26.0);
        }
        col = uColorA * (chord * (0.24 * plasma + 0.80 * pulse) * 0.85)
            + uColorB * (chord * 0.18 * plasma)
            + uColorC * (arcs * (0.18 + 0.82 * chord) * tail * 0.85
                       + chord * head * 0.55
                       + groundGlow * (0.75 + 0.50
                           * pow(0.5 + 0.5 * sin(ang * 9.0 + t * 14.0), 6.0)));
        col *= endFade;

    } else if (uLayer < 1.5) {
        // ── 白热核：细柱上的丝状电流 + 强前缘 ──
        float flick = 0.86 + 0.14 * sin(t * 17.0 + wy0 * 0.9);
        float helix = pow(0.5 + 0.5 * sin(ang * 2.0 + wy0 * 1.3 - t * 10.0), 7.0);
        float coreChord = pow(chord, 0.55);
        col = (uColorC * coreChord * (0.75 + 0.45 * helix) * flick
            + vec3(1.0, 0.95, 1.0) * coreChord * head * 1.60
            + vec3(1.0, 0.92, 1.0) * groundGlow * 0.80 * coreChord) * endFade;

    } else if (uLayer < 2.5) {
        // ── 幽灵柱（预警）：极淡的光通道 ──
        float shimmer = 0.72 + 0.28 * sin(wy0 * 0.55 - t * 3.4 + ang * 2.0);
        float topFade = 1.0 - smoothstep(0.72, 1.0, hN);
        float bottomFade = smoothstep(0.0, 2.5, rel);
        col = uColorB * pow(chord, 1.4) * 0.40 * shimmer * topFade * bottomFade;

    } else if (uLayer < 4.5) {
        // ── 外光晕（uLayer = 4）：宽、软、没有图案，只负责体积 ──
        float halo = pow(chord, 2.6);
        float breathe = 0.75 + 0.25 * sin(t * 1.7 + wy0 * 0.12);
        float topFade = 1.0 - smoothstep(0.60, 1.0, hN);
        // 底部淡出：外光晕层不画枪尖锥，靠这一段收掉开口，否则从下往上看
        // 柱底会读成一圈亮椭圆（「灯罩」）。
        float mouthFade = smoothstep(0.5, 6.0, rel);
        col = (uColorB * halo * (0.26 * plasma + 0.18) * breathe * topFade
            + uColorA * halo * groundGlow * 0.35) * mouthFade;

    } else {
        // ── 触地飞溅（uLayer = 5）：宽喇叭口 + 角向火舌 ──
        float up = clamp(rel / span, 0.0, 1.0);
        float flare = pow(chord, 1.25) * pow(1.0 - up, 1.8);
        float tongue = pow(0.5 + 0.5 * sin(ang * 7.0 + t * 11.0 + n1 * 3.0), 4.0);
        col = mix(uColorA, uColorC, 0.45) * flare * (0.55 + 0.75 * tongue)
            * (0.35 + 0.90 * clamp(uImpact, 0.0, 1.0));
    }

    // 贴脸自动收亮度：站在光柱旁边（4~16 格）时整体压到 0.55 档，
    // 避免"一屏纯白"；远处（>16 格）保持满亮度。
    col *= mix(0.55, 1.0, smoothstep(4.0, 16.0, length(vLocal)));

    // 触地瞬间整根柱子"炸"一下，读起来是它撞碎了什么
    col *= 1.0 + 0.90 * clamp(uImpact, 0.0, 1.0);

    // 软过曝压缩：加法混合下各层叠起来的峰值会到 3~5，不压会被削平成纯白
    col = col / (1.0 + col * 0.85);
}

// ═════════════════════════════════════════════════════════════
//  ④ 天顶裂口（uMode = 4）
//
//  终末预警里"天被捅开"的那张圆盘：几圈缓慢反向转的环 + 放射裂缝 + 中心过曝。
//  它解决的是张力里最贵的一半 —— **预告**。光束本身再漂亮，如果它是凭空出现
//  的，观众只会看到"闪了一下"；先有一张裂口在头顶张开 1.5 秒，同一下砸下来
//  才有分量。
//
//  为什么画成水平圆盘：它必须长在**世界**里（会随视角产生透视、会被山挡住），
//  而不是一张贴屏的贴纸。圆盘几何与法阵共用 emitDisc，属性打包也一致。
// ═════════════════════════════════════════════════════════════
void portalSigil(float r, float a, out vec3 col) {
    float rr  = clamp(r, 0.0, 1.06);
    float ang = a * TAU;
    float t   = uTime;

    // 开洞进度：前 55% 把洞口撑开，之后保持 —— 洞开得比光束早，
    // 但不要整段预警都在扩，那样它读起来像"地上那个阵又发了一圈"。
    float open = smoothstep(0.0, 0.55, uWarn);

    // 用噪声扰动每一圈的半径：硬圆环读起来像靶心，抖一点才是"裂口"。
    float n = vnoise(vec2(ang * 2.0 + t * 0.25, rr * 4.0 - t * 0.8));

    float rings = gauss(rr - (0.34 + 0.020 * n), 0.012)
                + gauss(rr - (0.55 + 0.030 * n), 0.010)
                + gauss(rr - (0.74 + 0.020 * n), 0.014)
                + gauss(rr - 0.900, 0.008);

    // 放射裂缝：9 条，缓慢反转，外圈才有
    float spokes = pow(max(0.0, sin(ang * 9.0 + n * 2.0 - t * 0.35)), 12.0)
                 * smoothstep(0.18, 0.92, rr) * (1.0 - smoothstep(0.94, 1.02, rr));

    // 中心：越接近下落越亮，是"能量在往这里灌"的唯一恒亮物
    float coreGlow = exp(-rr * 7.0);
    float pulse = 0.90 + 0.10 * sin(t * 3.0);

    col = (uColorB * rings * 0.75
         + uColorC * spokes * 0.85
         + uColorA * coreGlow * (0.55 + 2.30 * uWarn)) * open * pulse;

    // 触地：裂口自己也跟着炸一下，然后收掉（uImpactAge 由 Java 传秒数）
    if (uImpactAge >= 0.0) {
        float flash = exp(-uImpactAge * 7.0);
        col += vec3(1.0, 0.92, 1.0) * flash * (coreGlow * 1.6 + 0.20);
    }

    col = col / (1.0 + col * 0.60);
}

void main() {
    float I = max(uIntensity, 0.0);
    if (I <= 0.003) discard;

    // ⚠️ 顶点里的 Color.r 是相对**网格**半径归一化的，而网格半径比逻辑半径大
    // 一圈（留给 halo）。所以这里必须把它换算回"相对逻辑半径"：
    //   r = 1        → 逻辑边界（判定 / 伤害 / 破坏范围就是按它算的）
    // 少了这一步，画出来的圈会比实际作用范围大 18% —— 玩家会以为
    // "我站在圈外"却照样挨打，这是最难被原谅的一类不一致。
    // （光柱 / 裂口模式下 uRadius 与 uMeshRadius 相等，这一步是恒等变换。）
    float r = vertexColor.r * (uMeshRadius / max(uRadius, 1.0e-3));
    float a = vertexColor.g;

    vec3 col;
    if (uMode < 0.5)       chargeSigil(r, a, col);
    else if (uMode < 1.5)  fieldSigil(r, a, col);
    else if (uMode < 2.5)  col = vec3(0.0);          // 空号：见文件头的 uMode 说明
    else if (uMode < 3.5)  beamSigil(a, col);
    else                   portalSigil(r, a, col);

    fragColor = vec4(col * I, 1.0);
}
