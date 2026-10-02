#version 150 core

// ═════════════════════════════════════════════════════════════
//  冲击波 —— 空气折射 / 波前透镜
//
//  物理模型
//  ────────
//  爆炸在空气中扫出一个球对称的压力波。波前是一层被压缩的空气
//  （ρ↑、n↑），其后方是抽空的稀疏尾（ρ↓、n↓）。当视线与波前球面
//  相交时，光线沿整条光路累积偏折：
//
//      球对称介质下斯涅尔定律有精确一次积分
//          dφ = (1/n)·(dn/dr)·(ds/p)
//      沿光路积分 → Δφ(p) ∝ Δn / p    （p = 视线到波心的垂距）
//
//  因此：
//    · 视线越贴近波前球面（p → R），穿过的压缩空气越多，偏折越强；
//    · 相机与波心等高时（平视），视线与波前近乎相切，整个屏幕都
//      落在“掠射带”里 —— 这正是平视最容易看出来的角度；
//    · 视线掠射出球面（切线）时引入的介质量 → 0，
//      偏折平滑归零、与场景无缝。
//
//  本实现把上式整理成**屏幕空间**形式（这是关键）：
//
//    1. 命中判定     → 该像素视线是否打到波前球面；没打到就原样输出。
//    2. 归一化带坐标  → u = (θ − θ_R)/θ_band
//         θ    = acos(dot(L, rd))   该视线到波心的角距
//         θ_R  = asin(R/D)          波前球面的角半径
//         u = 0 正好落在波前环上，|u| = 1 是带宽边缘。
//       用**角度**而不是世界坐标做坐标轴，带宽就不会随视角塌缩成
//       亚像素，也不会在平视（相机与波心等高、视线与球面相切）时
//       整片消失。
//    3. 偏折剖面     → P(u)：u = 0（波前）处最陡，向两侧衰减，
//       |u| ≥ 1 处恒为 0。位移场处处连续 —— 没有台阶，没有裂纹。
//    4. 梯度安全     → 位移梯度超过约 1 px/px 会把图像折成锯齿状裂纹。
//    4. 壳厚与梯度 → 壳厚（band）= θ_R × (0.18 + 0.34·uRingWidth)，
//       并钳到 ≥ 90 px 像素下限。两个作用：
//         · 远景时波半径很小，像素下限保证屏幕上仍是一条厚带，
//           而不是退化成看不见的发丝；
//         · 位移恒为带宽的 0.40 倍，因此位移场斜率恒为
//           1.603 × 0.40 = 0.64 px/px < 1.0，任何视角都不会折叠。
//    5. 色散         → 红/绿/蓝用不同偏折量分别采样 → 真实彩边。
//    6. 深度遮挡     → 读取主 RT 深度，被方块挡住的片元不产生畸变。
// ═════════════════════════════════════════════════════════════

uniform sampler2D ScreenTexture;
uniform sampler2D DepthTexture;
uniform mat4  ProjMat;

uniform float uIntensity;      // Java 侧已含淡入淡出（0 → 无效果）
uniform float uLifeProgress;   // 0..1
uniform float uRingPosition;   // 波前位置（信息性）
uniform float uRingWidth;      // 波前厚度系数（0.05..2）
uniform float uWarp;           // 折射强度（像素级缩放）
uniform float uShimmer;        // 尾流热扰动强度
uniform float uChroma;         // 色散强度
uniform float uHasDepth;       // 1 = 深度纹理可用
uniform vec2  uPixelSize;      // (1/width, 1/height)
// 亮带总开关：1 = 冲击波（压缩壳的前向散射亮带），0 = 纯折射。
// 使徒斩击外层的"空间扭曲"复用的就是这个着色器：把 uGlow 置 0 之后
// 只剩下折射位移与色散，画出来就是纯粹的空间扭曲，不会多出一圈亮边。
uniform float uGlow;

in vec4 vertexColor;
in vec3 vNormal;
in vec3 vPos;
in vec3 vCenterVS;
in float vWaveRadius;

out vec4 fragColor;

#define WAVE_DECAY   0.55      // 后期随扩张的额外能量衰减
// 带宽（= 可见的“波前壳厚度”）由两部分决定：
//   band = θ_R × (BAND_BASE + BAND_PER_RING · uRingWidth)，再钳到 MIN_BAND_PX
// 位移场斜率 = 剖面最大斜率(1.603) × PEAK_PER_BAND = 0.64 < 1.0，
// 因此加厚/加强都不会把画面折成锯齿。
#define MIN_BAND_PX   110.0    // 带宽绝对像素下限（越大远景越厚实）
// 带宽还要至少占“波形屏幕直径”的这个比例 —— 保证位移峰值始终在波
// 自己的可见范围内，远处不会把背景甩到波外面去。
#define MIN_BAND_WAVE_FRAC 0.62
// 壳厚 = 波半径 × (BAND_BASE + BAND_PER_RING · uRingWidth)
// 0.30 ~ 1.16 倍波半径：整体偏厚，平视时波前是一整圈厚玻璃而不是发丝。
#define BAND_BASE     0.30
#define BAND_PER_RING 0.45
#define PEAK_PER_BAND 0.40     // 峰值位移 ≤ 带宽 × 该系数（斜率安全）
#define PEAK_OF_WAVE  0.55     // 峰值位移 ≤ 波形屏幕半径 × 该系数

// ── 工具 ────────────────────────────────────────────────────

float hash31(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + 17.3;
        a *= 0.5;
    }
    return s;
}

// 偏折剖面 P(u)：u = 0 落在波前环上，|u| >= 1 归零。
// “峰 + 缓肩”的混合：环上最陡（锐利的冲击前缘），
// 内侧是宽而缓的透镜体（把整个画面推开）。最大斜率 ≈ 0.72。
float wavefrontProfile(float u) {
    float b = clamp(abs(u), 0.0, 1.0);
    float e = 1.0 - b * b;
    float e2 = e * e;
    float spike = e2 * sqrt(sqrt(e));      // 峰：u = 0 处为 1，边缘平滑
    float shoulder = e2 * e;               // 肩：更宽更缓
    return spike * 0.72 + shoulder * 0.28;
}

void main() {
    vec2 baseUV = clamp(gl_FragCoord.xy * uPixelSize, vec2(0.002), vec2(0.998));
    vec3 scene = texture(ScreenTexture, baseUV).rgb;

    float I0 = max(uIntensity, 0.0);
    if (I0 <= 0.002) { fragColor = vec4(scene, 1.0); return; }

    // ── 相机 / 波前几何（全部在视图空间：相机位于原点） ──
    vec3 rd = normalize(vPos);
    vec3 C  = vCenterVS;

    float camDist = length(C);                                     // D
    if (camDist < 0.35) { fragColor = vec4(scene, 1.0); return; }  // 相机在爆心内
    vec3 L = C / camDist;                                          // 单位：指向波心

    // 球壳半径 R —— 必须来自 uniform（vWaveRadius）。
    // 绝不要用 length(vPos) 反推：vPos 是插值量，在球面网格上会随
    // 三角形边界抖动约 1.4%，而 R 直接决定波前环的位置与带宽，
    // 于是屏幕上会出现沿网格走向的锯齿 —— 平视（相机贴近波面、
    // 波在屏幕上铺得最开）时最明显。
    float waveR = vWaveRadius;
    if (!(waveR == waveR) || waveR < 0.2) { fragColor = vec4(scene, 1.0); return; }

    // 相机是否被整个波包住（半径大于相机到爆心的距离）。两种几何下
    // “波前在屏幕上的角半径”算法完全不同，必须分开处理：
    //   · 观察者在波外：波前是球面的**切线**，θ_R = asin(R/D)；
    //   · 观察者在波内（平视站在爆点里）：整个可视角锥都被波覆盖，
    //     球面不再产生切线，其可见边界是 θ_R = acos(D/R)。
    //     旧写法沿用 asin 会算出 ≈90°，使屏幕中心落不到波带上，
    //     于是平视时正前方什么都看不见。
    bool enclosing = (waveR > camDist * 1.0005);

    float thetaR;
    if (enclosing) {
        thetaR = acos(clamp(camDist / max(waveR, 1e-3), 0.0, 1.0));
    } else {
        thetaR = asin(clamp(waveR / camDist, 0.0, 0.9995));
    }
    if (thetaR < 1.0e-4) { fragColor = vec4(scene, 1.0); return; }

    // 该像素的角距 θ
    float theta = acos(clamp(dot(L, rd), -1.0, 1.0));

    // ── 带宽（角），并钳到下限像素 ──
    //    · 观察者在波外：带宽按波半径的比例（壳厚 = 波半径的 18%~86%）；
    //    · 观察者在波内：整个可视角锥都在波里，带宽必须 ≥ θ_R 才能覆盖全屏，
    //      否则屏幕中心（u = −θ_R/带宽）会掉出带外、正前方一片死区；
    //    · 像素下限分两级：绝对下限 MIN_BAND_PX，以及“至少占波形屏幕
    //      直径的三分之一”的相对下限 —— 后者保证位移峰值永远落在波
    //      自己的可见范围之内（否则远处位移会甩到波外面去）。
    float fpx = 0.5 / max(uPixelSize.y, 1e-6);                     // 焦距（像素）
    float waveDiamPx = 2.0 * fpx * tan(min(thetaR, 1.45));
    float bandPxFloor = max(MIN_BAND_PX, waveDiamPx * MIN_BAND_WAVE_FRAC) / max(fpx, 1.0);
    float ringAmt = clamp(uRingWidth, 0.05, 2.0);
    float band;
    if (enclosing) {
        band = max(thetaR * (1.0 + BAND_PER_RING * ringAmt), bandPxFloor);
    } else {
        band = max(thetaR * (BAND_BASE + BAND_PER_RING * ringAmt), bandPxFloor);
    }

    // 远离波前环的区域直接跳过
    float u = (theta - thetaR) / band;
    if (abs(u) >= 1.0) { fragColor = vec4(scene, 1.0); return; }

    // ── 强度与可见性 ──
    float prof = wavefrontProfile(u);
    // 带宽两端平滑收口，避免在 |u| → 1 处留下硬边
    prof *= 1.0 - smoothstep(0.86, 1.0, abs(u));

    // 掠射出球面时引入的介质量 → 0。相机在波内时全部可视角锥都被波覆盖，
    // 没有“掠出”这回事，故不加方向性收口（否则会把最强的中心抹平）。
    float graze = enclosing ? 1.0
                            : (1.0 - smoothstep(thetaR * 0.93, thetaR * 1.02, theta));
    float decay = mix(1.0, 1.0 - WAVE_DECAY * uLifeProgress, 0.45);

    float vis = 1.0;
    if (uHasDepth > 0.5) {
        // 波前表面在该像素上的真实深度：解 |rd·t − C|² = R²（近端交点）。
        // 直接用几何交点而不是 gl_FragCoord.z —— 相机在波内时球面顶点
        // 会被近平面裁掉，gl_FragCoord.z 失去意义，只有几何解是可靠的。
        float along = dot(C, rd);
        float disc  = along * along - (camDist * camDist - waveR * waveR);
        float tHit  = along - sqrt(max(disc, 0.0));
        if (tHit <= 0.0) { fragColor = vec4(scene, 1.0); return; }   // 波前在相机后面
        vec3 hitVS  = rd * tHit;
        // 视空间 z 与深度缓冲的关系（reciprocal 约定的投影矩阵）：
        //   ndcZ = ProjMat[2][2] + ProjMat[3][2] / z
        // 于是世界 z 可由深度缓冲线性还原，再与波前的 |z| 直接比较。
        float zFront = abs(hitVS.z);
        float dScene = texture(DepthTexture, baseUV).r;
        float zScene = abs((ProjMat[3][2] - ProjMat[2][2]) / (2.0 * dScene - 1.0 - ProjMat[2][2]));
        // 波前只有落在场景深度之前（即它前面的空气里没有阻挡物）才生效
        vis = smoothstep(0.994, 1.006, zFront / max(zScene, 1e-3));
    }

    float I = I0 * vis * decay * graze;
    if (I <= 0.002) { fragColor = vec4(scene, 1.0); return; }

    // ── 峰值位移 ──
    //    三个上限同时成立，取最紧的一个：
    //      A = bandPx · PEAK_PER_BAND
    //          剖面最大斜率 ≈1.603，故位移场斜率 ≈ 1.603·0.40 = 0.64 px/px，
    //          低于 1.0 —— 任何视角都不会折成锯齿裂纹；
    //      B = 波形屏幕半径 · PEAK_OF_WAVE
    //          位移峰值必须落在波自己的可见范围内，否则远处的背景会被
    //          甩到波外面去，看起来不像“波在推画面”而像画面被整体拖走；
    //      C = 视口 30% —— 兜底。
    //    位移与带宽成正比 ⇒ 拉伸比与距离无关，远处不会缩成看不见。
    float waveRadPx = thetaR * fpx;
    float bandPx = band * fpx;
    float peakPx = uWarp * (fpx / 740.0) * bandPx * PEAK_PER_BAND;
    peakPx = min(peakPx, bandPx * PEAK_PER_BAND);
    peakPx = min(peakPx, waveRadPx * PEAK_OF_WAVE);
    peakPx = min(peakPx, 0.30 * min(gl_FragCoord.x, gl_FragCoord.y));

    // ── 屏幕径向方向（由波心指向外侧） ──
    vec3 radial = C - rd * dot(C, rd);
    vec3 rdir = (length(radial) > 1e-5) ? normalize(radial) : vec3(1.0, 0.0, 0.0);
    rdir = rdir - rd * dot(rd, rdir);
    float rlen = length(rdir);
    rdir = (rlen > 1e-5) ? rdir / rlen : vec3(1.0, 0.0, 0.0);

    // ── 尾流热扰动（沿正交切向） ──
    vec3 tdir = cross(rd, rdir);
    float tlen = length(tdir);
    tdir = (tlen > 1e-5) ? tdir / tlen : vec3(0.0, 1.0, 0.0);

    vec3 wq = (vPos - C) * 0.7 + vec3(0.0, 0.0, uLifeProgress * 40.0);
    float shimmer = fbm3(wq + rd * 2.5) - 0.5;
    float shAmp = uShimmer * I * prof * peakPx * 0.35;

    float mag = peakPx * prof * I;
    vec2 dispMid = rdir.xy * mag + tdir.xy * shimmer * shAmp;

    // 色散：红偏折最大、蓝最小 → 波前上出现彩边
    vec2 dispR = dispMid * (1.0 + uChroma);
    vec2 dispB = dispMid * (1.0 - uChroma);

    vec3 col = vec3(
        texture(ScreenTexture, clamp(baseUV + dispR  * uPixelSize, vec2(0.002), vec2(0.998))).r,
        texture(ScreenTexture, clamp(baseUV + dispMid * uPixelSize, vec2(0.002), vec2(0.998))).g,
        texture(ScreenTexture, clamp(baseUV + dispB  * uPixelSize, vec2(0.002), vec2(0.998))).b);

    // ── 致密空气的前向散射：波前上的冷白亮带 ──
    //    位移纹理在“平坦地面/天空”这类低频背景上几乎看不出来 ——
    //    背景本来就没有细节可推。真正让远处波前被看见的是**亮度**：
    //    压缩壳的前向散射。所以亮度带独立成型，并且随波在屏幕上的
    //    尺寸变小而增强（远景可见性补偿）：波越小，同一层压缩空气
    //    在屏幕上越集中，叠加亮度自然越高。
    //    以“波形屏幕直径 260px”为基准，最多补偿 3 倍。
    float visBoost = clamp(260.0 / max(waveDiamPx, 1.0), 1.0, 3.0);

    // ① 波前亮带：pow(b01, 2) 铺开到约 1/4 带宽 —— 远景也有一条厚亮线
    float b01 = 1.0 - clamp(abs(u), 0.0, 1.0);
    float edgeGlow = pow(b01, 2.0) * 0.75 + pow(b01, 0.7) * 0.25;
    col += vec3(0.90, 0.95, 1.0) * edgeGlow * 0.30 * visBoost * I * uGlow;

    // ② 整个壳层的宽柔光：让“边缘厚度”在整条带上都读得出来，
    //    远景时就是这一层把波读成“一圈厚玻璃”而不是一根发丝
    col += vec3(0.74, 0.82, 0.92) * prof * 0.13 * visBoost * I * uGlow;

    fragColor = vec4(col, 1.0);
}
