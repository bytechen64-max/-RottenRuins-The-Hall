#version 150 core

// ─────────────────────────────────────────────────────────────────────────
//  绯红誓约背板（片元阶段）—— 血月 · 誓约之冠
//
//  设计目标（用户反馈：上一版"形状莫名其妙"）：
//  上一版把正五边形、六芒星、外圈条带三种彼此无关的图形叠在一起，而
//  线宽又被 PixelWidth 托底成 0.13~0.2 UV（比图案本身还粗），实际画面
//  只剩一团粉白甜甜圈。这一版改成**一个连贯的纹章**，所有元素共用同轴
//  同心结构，且每一层都为下一层让位：
//
//        u = 0.00 ~ 0.22   誓约之心（被玩家头部挡住，只留柔光）
//        u = 0.33          内封印环（正好框住玩家头颈的一圈光环）
//        u = 0.37 ~ 0.41   刻度环（24 枚细刻，与外环反向自转）
//        u = 0.34 ~ 0.50   八根封印辐条（把内环与符文带连起来，避免中间空）
//        u = 0.50 ~ 0.66   符文带（20 格"誓约文字"，主细节层）
//        u = 0.70          轨环（72 枚细刻）
//        u = 0.78          日蚀边缘（一条亮环 = 血月的轮廓）
//        u = 0.78 ~ 1.00   冠冕：56 条细射线 + 头顶五瓣冠 + 七根长刺 + 余烬
//
//  为什么是日蚀：背板在第三人称下会被玩家剪影挡住中下部约 1/3，
//  "完整的圆"永远看不清。日蚀的读法天然成立 —— 玩家正好站在暗盘中心，
//  露出来的是**亮边 + 头顶的冠冕**，被挡住的部分本来就不承载信息。
//  头顶的冠是整块板唯一的非对称元素，把"誓约之冠"点在玩家头上。
//
//  两遍绘制（Java 侧每帧画两次，靠 GlowPass 分支）：
//    GlowPass = 0  底遍：普通 alpha 混合 + 写深度。只画血月盘体（暗红、
//                  带月海与血纹的暗面）和符文/环的蚀刻暗纹。它负责
//                  "挡住身后世界"的实体感与对比度。
//    GlowPass = 1  亮遍：加法混合、不写深度。只输出自发光：日蚀边、封印环、
//                  符文、冠冕、余烬、心誓脉冲波。加法才有"在发亮"。
//
//  心誓（心跳）：Time 是 tick（20 tick/s）。每 mix(19, 11.5, Block) tick 一个
//  脉冲 —— 平时约 0.95 Hz 的缓慢心跳，举剑格挡时加快加重。脉冲驱动边缘、
//  冠冕、符文、内环与一圈从中心向外推的誓约波。
//
//  ⚠ 线宽不要再写 PixelWidth * 常数。屏幕恒定线宽用 fwidth() 就够：
//    SDF 的 fwidth 天然等于一个像素在图案空间里的跨度，线再细它也不会归零
//    （上一版把这个前提搞反了，才加了那个致命的 *260 托底）。
// ─────────────────────────────────────────────────────────────────────────

in vec2 vLocal;   // 单位平面局部坐标（见 .vsh 说明）

uniform vec3  Spin;
uniform float Time;
uniform float Reveal;
uniform float Block;
uniform float Intensity;
uniform vec3  VowColor;      // 深绯红（誓约）
uniform vec3  AccentColor;   // 雾粉紫（高光）
/** 0 = 底遍（暗部结构），1 = 亮遍（自发光）。Java 侧两遍之间会 flush。 */
uniform float GlowPass;

out vec4 fragColor;

#define TAU 6.28318530718
#define PI  3.141592654

// ── 半径（归一化：1.0 = 整块背板的可见外缘）──
const float R_OUT      = 0.86;   // 与 CrimsonVowBackplateRig.SHADER_VISIBLE_RADIUS 同步
const float U_LIMB     = 0.780;  // 日蚀边缘
const float U_ORBIT    = 0.700;  // 轨环
const float U_RUNE_C   = 0.580;  // 符文带中心
const float U_RUNE_H   = 0.085;  // 符文带半高
const float U_SEAL     = 0.330;  // 内封印环
const float U_TICK_IN  = 0.372;  // 刻度环内缘
const float U_TICK_OUT = 0.406;  // 刻度环外缘

const float RUNE_CELLS = 20.0;
const float TICK_CELLS = 24.0;
const float ORBIT_TICKS = 72.0;
const float RAY_CELLS  = 56.0;

// ══════════════════════════════════════════════════════════════════════════
//  工具
// ══════════════════════════════════════════════════════════════════════════

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

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

/** 两层 fBm：月海只需要低频起伏，三层以上在屏幕上看不出差别。 */
float fbm2(vec2 p) {
    return 0.64 * vnoise(p) + 0.36 * vnoise(p * 2.07 + vec2(11.3, 7.7));
}

/** 高斯线：w 越小越细。所有环/线统一用它，边缘是平滑衰减而不是硬台阶。 */
float gauss(float d, float w) {
    float v = d / max(w, 1.0e-5);
    return exp(-v * v);
}

/** 硬一点的线（符文笔画用）。w 也是半宽。 */
float line(float d, float w) {
    return 1.0 - smoothstep(0.0, max(w, 1.0e-5), abs(d));
}

/** 把角度折回 [-PI, PI]，用于算到某个方向的角度差。 */
float angDelta(float a, float center) {
    return abs(mod(a - center + PI, TAU) - PI);
}

// ══════════════════════════════════════════════════════════════════════════
//  "誓约文字"：一格里的程序化符文
//
//  一格 = 3 笔 + 一点。竖笔一定存在，其余笔画由 hash 决定长短/位置/方向。
//  只求"像字"：有笔画穿插、有疏密，远看是一条符文带，近看每一格都不同。
//  笔画宽度是"格单位"的常量（0.07）而不是屏幕空间 —— 格宽本身已经按半径
//  固定，再乘屏幕比例会让远看糊成一团（上一版的教训）。
// ══════════════════════════════════════════════════════════════════════════
float runeGlyph(float fx, float fy, float h1, float h2, float h3) {
    float g = 0.0;

    // ① 竖笔：位置与高度随机
    float x1 = mix(-0.26, 0.26, h1);
    float hY = mix(0.30, 0.56, h2);
    g += line(fx - x1, 0.070) * step(abs(fy), hY);

    // ② 横笔：从竖笔中途伸出
    float y1 = mix(-0.36, 0.36, h3);
    float wX = mix(0.10, 0.30, hash11(h1 * 7.3 + h2 * 3.1));
    g += line(fy - y1, 0.070) * step(abs(fx - x1 * 0.6), wX);

    // ③ 底笔：一半的格子有，一条短横压在下缘
    g += line(fy + 0.46, 0.070) * step(abs(fx - x1 * 0.3), 0.22) * step(0.5, h1);

    // ④ 斜笔：四分之一的格子有，方向由 h2 决定
    float dir = h2 > 0.5 ? 1.0 : -1.0;
    g += line((fx - x1 * 0.4) - (fy + 0.06) * dir * 0.40, 0.055)
       * step(abs(fy + 0.12), 0.22) * step(0.72, h3);

    // ⑤ 星点：约 1/3 的格子顶点一颗
    float dot = gauss(length(vec2(fx - x1, fy - (0.44 + 0.08 * h3))), 0.085);
    g += dot * step(0.62, hash11(h3 * 5.7 + h1));

    return clamp(g, 0.0, 2.0);
}

// ══════════════════════════════════════════════════════════════════════════
//  主函数
// ══════════════════════════════════════════════════════════════════════════
void main() {
    float reveal = clamp(Reveal, 0.0, 1.0);
    float block  = clamp(Block, 0.0, 1.0);
    float t      = Time;

    // ── 展开：整块纹章从 0.55 倍长到 1 倍，亮度同时点亮 ──
    float ease = reveal * reveal * (3.0 - 2.0 * reveal);
    float grow = mix(0.55, 1.0, ease);
    float dim  = mix(0.0, 1.0, reveal);

    // 归一化半径：u = 1 就是这一帧的可见外缘。
    // 角度必须在这里现算：扇形几何的顶点 θ 线性插值不是真角度（见 .vsh 的警告）。
    float r  = length(vLocal);
    float th = atan(vLocal.y, vLocal.x);
    float u = r / (R_OUT * grow);
    if (u > 1.02) discard;

    // ── 心誓脉冲 ──
    //  周期在 19 tick（~0.95 Hz）→ 11.5 tick（~1.74 Hz）之间随格挡变化。
    float period = mix(19.0, 11.5, block);
    float bp     = t / period;
    float beat   = pow(0.5 + 0.5 * sin(bp * TAU), 7.0);   // 尖脉冲
    float beatAmp = 0.30 + 0.90 * block;
    float pulse  = 1.0 + beat * beatAmp;

    // 三层各自的自转（Spin 由 Java 每帧给 (0.55, 1.0, 2.4)）
    float thRune  = th + t * 0.0022 * Spin.x * (1.0 + 0.9 * block);
    float thTick  = th - t * 0.0060 * Spin.y * (1.0 + 0.9 * block);
    float thOrbit = th + t * 0.0028 * Spin.z;

    // 屏幕恒定线宽：一个像素在 u 空间里的跨度
    float lw = max(fwidth(u) * 1.35, 0.0016);

    // 单位盘坐标（y 向上：theta = PI/2 是头顶方向）
    vec2 p = vec2(cos(th), sin(th)) * u;

    // ══════════════════════════════════════════════════════════════════
    //  共用的形状量
    // ══════════════════════════════════════════════════════════════════

    // ── 日蚀边缘：轮廓略有心跳般的起伏，不是一条数学圆 ──
    float limbD = u - U_LIMB - 0.0045 * sin(th * 5.0 + t * 0.02)
                              - 0.0018 * sin(th * 13.0 - t * 0.05);
    float limb     = gauss(limbD, lw * 1.15);
    float limbHalo = gauss(limbD, 0.030);
    // 绕圈奔跑的一段亮弧：像有能量沿边缘走
    float sweep = pow(0.5 + 0.5 * cos(th - t * 0.030), 14.0);

    // ── 内封印环：玩家头颈后方那一圈光环 ──
    float sealD = u - U_SEAL - 0.0022 * sin(th * 6.0 - t * 0.03);
    float seal  = gauss(sealD, lw * 1.25);

    // ── 刻度环：24 枚细刻 ──
    float tickBand = smoothstep(U_TICK_IN, U_TICK_IN + 0.010, u)
                   * (1.0 - smoothstep(U_TICK_OUT - 0.010, U_TICK_OUT, u));
    float tickCell = fract(thTick * TICK_CELLS / TAU);
    float tickShape = 1.0 - smoothstep(0.10, 0.19, abs(tickCell - 0.5) * 2.0);
    float ticks = tickBand * tickShape;

    // ── 内轨环 + 40 枚细刻：填住"内环 → 符文带"之间那一圈 ──
    //    这一段原来试过 8 根粗辐条，读起来像钟表指针、把同心结构打断了；
    //    改成又一条细轨环 + 密刻，与 u=0.70 的轨环是同一套语言。
    float innerOrbitD = u - 0.462;
    float innerOrbit  = gauss(innerOrbitD, lw * 0.90);
    float innerTickBand = smoothstep(0.470, 0.476, u)
                        * (1.0 - smoothstep(0.492, 0.498, u));
    float innerCell = fract((th - t * 0.0038) * 40.0 / TAU);
    float innerTicks = innerTickBand
                     * (1.0 - smoothstep(0.13, 0.22, abs(innerCell - 0.5) * 2.0));

    // ── 轨环 + 72 枚向外的小刻 ──
    float orbitD = u - U_ORBIT;
    float orbit  = gauss(orbitD, lw * 0.95);
    float orbitTickBand = smoothstep(U_ORBIT + 0.004, U_ORBIT + 0.010, u)
                        * (1.0 - smoothstep(U_ORBIT + 0.026, U_ORBIT + 0.032, u));
    float orbitCell = fract(thOrbit * ORBIT_TICKS / TAU);
    float orbitTicks = orbitTickBand
                     * (1.0 - smoothstep(0.14, 0.24, abs(orbitCell - 0.5) * 2.0));

    // ── 符文带 ──
    float cf   = thRune * RUNE_CELLS / TAU;
    float cell = floor(cf);
    float fx   = fract(cf) - 0.5;
    float fy   = (u - U_RUNE_C) / U_RUNE_H;
    float bandSoft = 1.0 - smoothstep(0.84, 1.0, abs(fy));
    float h1 = hash11(cell * 1.37 + 2.7);
    float h2 = hash11(cell * 2.71 + 8.1);
    float h3 = hash11(cell * 0.73 + 4.3);
    float glyph = 0.0;
    if (bandSoft > 0.001) {
        glyph = runeGlyph(fx, fy, h1, h2, h3) * bandSoft;
    }
    // 每格亮度有差异 + 极慢的明灭，读起来像"被逐字点亮"
    float glyphFlicker = 0.58 + 0.42 * pow(0.5 + 0.5 * sin(t * (0.30 + 0.45 * h1) + h2 * TAU), 3.0);
    glyph *= glyphFlicker;

    // ── 盘体：血月表面 ──
    float discMask = 1.0 - smoothstep(U_LIMB - 0.006, U_LIMB + 0.006, u);
    float maria = fbm2(p * 3.2 + vec2(t * 0.0020, -t * 0.0013));
    float mariaFine = fbm2(p * 9.0 - vec2(t * 0.0035, 0.0));
    // 血纹：ridged fBm（1-|2f-1| 的尖脊）→ 细长的脉络，给暗盘一点"活着"的质感
    float ridge = 1.0 - abs(2.0 * fbm2(p * 2.4 + vec2(-t * 0.0015, t * 0.0022)) - 1.0);
    float veins = pow(ridge, 7.0) * discMask;
    float innerLift = smoothstep(0.12, U_LIMB, u);
    // 中心柔光：被玩家头挡住大半，只从头顶溢出一点 —— 让"誓约之心"有个位置
    float coreGlow = gauss(u, 0.205) * (0.30 + 0.85 * beat * beatAmp);

    // ── 心誓波：每一拍从中心向外推一圈，被盘体压住 ──
    float wavePhase = fract(bp);
    float waveR = 0.14 + (U_LIMB - 0.14) * wavePhase;
    float wave = gauss(u - waveR, 0.020) * (1.0 - wavePhase) * (1.0 - wavePhase)
               * (0.55 + 0.90 * block);

    // ══════════════════════════════════════════════════════════════════
    //  底遍：暗部结构（普通 alpha 混合 + 写深度）
    // ══════════════════════════════════════════════════════════════════
    if (GlowPass < 0.5) {
        // 盘体本身：深绯红到近黑，靠月海/血纹的明暗给出"有质量的暗"
        vec3 discCol = VowColor * (0.12 + 0.24 * maria)
                     + VowColor * (0.05 * mariaFine)
                     + VowColor * (0.30 * veins)
                     + vec3(0.022, 0.003, 0.009);
        // 靠近边缘的一圈暗红抬升 + 心跳时整盘微微透出血色
        discCol += VowColor * (0.16 + 0.40 * beat * beatAmp) * innerLift * innerLift;
        // 蚀刻暗纹：符文与环在底遍里以暗红蚀刻出现，保证不发光时也有结构
        discCol += VowColor * glyph * 0.85;
        discCol += VowColor * (ticks * 0.55 + innerTicks * 0.40
                             + innerOrbit * 0.35
                             + orbitTicks * 0.45 + orbit * 0.35);
        discCol += VowColor * seal * 0.55;
        discCol += VowColor * coreGlow * 0.55;

        float alpha = discMask * (0.76 + 0.10 * (1.0 - u / U_LIMB));
        alpha *= dim;
        if (alpha <= 0.004) discard;
        fragColor = vec4(discCol * Intensity, clamp(alpha, 0.0, 1.0));
        return;
    }

    // ══════════════════════════════════════════════════════════════════
    //  亮遍：自发光（加法混合、不写深度）
    // ══════════════════════════════════════════════════════════════════
    vec3 hot  = mix(AccentColor, vec3(1.0, 0.90, 0.95), 0.40);
    vec3 pale = mix(VowColor, vec3(1.0, 0.82, 0.90), 0.72);
    vec3 crownCol = mix(pale, AccentColor, 0.30);   // 冠偏粉紫，别烤成纯白

    float corona = 0.0;
    float crown  = 0.0;
    float ember  = 0.0;

    if (u > U_LIMB - 0.02) {
        float du = max(u - U_LIMB, 0.0);

        // ① 紧贴边缘的柔光晕：整块纹章在外圈的"亮度底座"，
        //    没有它时射线只是几根细线、远看会散掉。
        corona += exp(-du * 20.0) * 0.58 * pulse;

        // ② 56 条细射线：长度/亮度每格不同，头顶方向自动更长
        float rf = th * RAY_CELLS / TAU;
        float rcell = floor(rf);
        float rfx = fract(rf) - 0.5;
        float rh1 = hash11(rcell * 1.91 + 5.0);
        float rh2 = hash11(rcell * 2.73 + 1.0);
        float up = max(sin(th), 0.0);
        float rlen = (0.035 + 0.155 * rh1) * (1.0 + 1.75 * pow(up, 5.0));
        float rt = du / max(rlen, 1.0e-3);
        float rprof = (1.0 - smoothstep(0.0, 1.0, rt)) * smoothstep(-0.004, 0.020, du);
        float rwidth = 0.17 + 0.13 * rh2;
        float rshape = 1.0 - smoothstep(0.0, rwidth, abs(rfx));
        float rflick = 0.68 + 0.32 * sin(t * (0.55 + 0.9 * rh1) + rh2 * TAU);
        corona += rshape * rprof * rflick * (0.55 + 0.85 * rh2) * pulse;

        // ③ 头顶五瓣冠：宽底、尖顶的火舌状长瓣（主体）。
        //    中间那瓣最高、且钉死在正上方，左右两瓣对称收短 —— 这样"冠"
        //    在剪影里读得出对称的皇冠轮廓，而不是一撮乱刺。
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            float ang = PI * 0.5 + (fi - 2.0) * 0.250;
            float ad = angDelta(th, ang);
            float len = (0.140 + 0.170 * (1.0 - abs(fi - 2.0) / 2.4))
                      * (0.85 + 0.26 * pulse);
            float tt = du / max(len, 1.0e-3);
            if (tt < 1.0) {
                // 底部宽、尖端收成一点；根部压在日蚀边上
                float halfW = mix(0.078, 0.0016, pow(tt, 0.70));
                float prof = (1.0 - smoothstep(0.34, 1.0, tt)) * smoothstep(-0.01, 0.03, du);
                crown += (1.0 - smoothstep(0.0, max(halfW, 1.0e-4), ad)) * prof;
            }
        }

        // ④ 七根细长刺：穿过五瓣冠之间的缝隙，拉出"冠"的剪影
        for (int i = 0; i < 7; i++) {
            float fi = float(i);
            float ang = PI * 0.5 + (fi - 3.0) * 0.185;
            float ad = angDelta(th, ang);
            float len = (0.100 + 0.170 * (1.0 - abs(fi - 3.0) / 3.5))
                      * (0.85 + 0.30 * pulse);
            float tt = du / max(len, 1.0e-3);
            if (tt < 1.0) {
                float prof = 1.0 - smoothstep(0.35, 1.0, tt);
                float halfW = mix(0.018, 0.0012, tt);
                crown += (1.0 - smoothstep(0.0, max(halfW, 1.0e-4), ad)) * prof * 0.52;
            }
        }

        // ⑤ 余烬：一圈缓慢外漂的星点（一格里一颗，相位错开）
        float ef = th * 48.0 / TAU;
        float ecell = floor(ef);
        float efx = fract(ef) - 0.5;
        float eh1 = hash11(ecell * 1.31 + 9.0);
        float eh2 = hash11(ecell * 2.17 + 3.0);
        float drift = fract(t * 0.010 * (0.5 + eh1) + eh2);
        float eu = U_LIMB + 0.012 + 0.16 * eh1 + 0.05 * drift;
        ember = gauss(u - eu, 0.0125) * gauss(efx, 0.13)
              * (1.0 - drift) * (1.0 - drift) * (0.40 + 0.60 * eh2);
    }

    // ── 合成发射 ──
    vec3 col = vec3(0.0);
    col += pale * limb * (1.55 + 0.55 * sweep);                    // 日蚀边（最亮的连续线）
    col += hot  * limbHalo * (0.28 + 0.28 * beat * beatAmp);       // 边的外晕
    col += hot  * seal * (1.00 + 0.55 * beat * beatAmp);           // 内封印环
    col += hot  * (ticks * 0.90 + innerTicks * 0.60 + innerOrbit * 0.50
                 + orbitTicks * 0.70 + orbit * 0.50);
    col += mix(AccentColor, hot, 0.45) * glyph * 1.25;             // 符文
    col += VowColor * veins * 0.55;                                // 血纹（暗亮）
    col += mix(AccentColor, hot, 0.20) * corona * 1.00;            // 冠冕射线
    col += crownCol * crown * 1.30;                                // 冠
    col += hot  * ember * 1.90;                                    // 余烬
    col += VowColor * wave * 1.60;                                 // 心誓波
    col += VowColor * coreGlow * 0.95;                             // 誓约之心

    col *= pulse * 0.30 + 0.70;   // 全体呼吸（幅度小，只求"活着"）
    col *= dim * Intensity;

    // 加法遍用 alpha = 1：颜色自己就是强度（SRC_ALPHA/ONE 下等于直接相加）。
    // 亮度低于可见阈值的像素直接丢，省填充也避免把远处糊成一片。
    if (max(col.r, max(col.g, col.b)) < 0.003) discard;
    fragColor = vec4(col, 1.0);
}
