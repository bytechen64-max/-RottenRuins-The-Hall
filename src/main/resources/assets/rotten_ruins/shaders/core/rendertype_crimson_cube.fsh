#version 150 core

// ══════════════════════════════════════════════════════════════════════════
//  绯红晶核 · 世界空间光线步进（片元阶段）
//
//  形状是一团"液态铬"：在 球 → 环 → 圆角方块 → 八面体 之间熔融，
//  周围还有四颗 smin 融合的卫星球；表面是镜面铬，反射一片只存在于
//  反射里的霓虹 studio（条纹太阳 / 灯条 / 环形灯 / 滚动网格地板）。
//  结构（AABB 背面 + 全光线步进 + 深度遮挡 + ACES）移植自 Shadertoy
//  "More cubes for the cube lovers"（CC0, mrange）；形状与铬材质移植自
//  另一篇 CC0 的 "Liquid Chrome Morph"。旧的方块球 / 缠绕环 / 血晶
//  三支都保留在文件里，用顶部的宏切换。
//
//  ── 为什么是"世界空间体积"而不是一块贴图 ──────────────────────────────
//  Java 画的是一个世界空间轴对齐包围盒（AABB = uCenter ± uSize），并且
//  只画它的背面、关深度测试。于是每个被盒子覆盖的像素得到**恰好一个**
//  片元；这个片元要做的不是"给盒子表面着色"，而是"沿着这条视线把整个
//  盒子内部重新渲染一遍"。
//
//  片元阶段因此要自己完成四件事：
//    ① 从视空间片元位置反解出**世界空间**的视线方向；
//    ② 把这条世界光线变换到 Shadertoy 的 DF 空间；
//    ③ 在 DF 空间里步进 SDF，命中就着色；
//    ④ 用深度纹理做遮挡，被方块挡住的像素 discard。
//
//  ── ① 视线方向为什么能用"视基"三个 vec3 求出来 ────────────────────────
//  相机在**视空间的原点**，所以视空间里 normalize(vPos) 就是方向 —— 但
//  它是"视空间方向"，而 SDF 定义在世界空间，两者差一个相机旋转。
//
//  记 View 矩阵（世界→视）的旋转部分为 R。View 是刚体变换，因此它的逆
//  旋转等于它的转置：
//
//      rd_world = normalize(Rᵀ · rd_view)
//
//  Rᵀ 的三列恰好是"视空间三个轴在世界空间里的方向"，Java 侧把它们当作
//  uViewRight / uViewUp / uViewFwd 传进来（就是从 RenderLevelStageEvent 的
//  poseStack 旋转部分取出来的三个基向量）。于是：
//
//      rd_world = normalize(uViewRight * rd.x + uViewUp * rd.y + uViewFwd * rd.z)
//
//  ⚠️ 这里**刻意不用 ModelViewMat**。ModelViewMat 由 ShaderInstance.apply()
//  从 RenderSystem.getModelViewMatrix() 取，而那个字段只在
//  applyModelViewMatrix() 里被更新 —— 原版在整条世界渲染路径上（地形 /
//  实体 / 我们所在的 AFTER_TRIPWIRE_BLOCKS 阶段）都没有更新它，它保留的是
//  上一帧 GUI 那趟留下的近单位阵。用它做旋转会在部分机器上"看起来能跑"、
//  在另一些机器上整块不可见，且无法自检。显式传基向量则与
//  "顶点已经被 poseStack 变换成视空间" 这条契约**同源**，不可能对不上。
//
//  为了让"方向"和"深度遮挡"两个计算共用同一套约定，本文件把
//  toView(pW) = (dot(uViewRight,d), dot(uViewUp,d), dot(uViewFwd,d)) 这
//  一个函数同时用于反解世界方向（它的逆变换）与求命中点的视空间深度。
//  两边只要用的是同一套基，就不会出现"方向对了、深度错一格"的错位。
//
//  ── ② 世界空间 ↔ DF 空间 ──────────────────────────────────────────────
//  Shadertoy 原版把整个场景建在 sz = 20 的尺度上。这里取
//
//      k = 20.0 / uSize
//      toDF(pW) = (pW - uCenter) * k
//
//  于是盒子的半边长 uSize（方块）正好映射到 DF 空间的 ±20，SDF 的
//  所有常数（20、12、10、5…）都不用改。
//
//  距离同样按 k 缩放：DF 空间里步进 d 个 DF 单位，等于世界空间里前进了
//  d / k 个方块。所以步进的"总预算"是 (tEnd - tStart) * k，命中点换算回
//  世界距离是 tHit = tStart + tDF / k。
//
//  ⚠️ 因此步进那一行必须是 `p += rdW * d` —— p 本身就是 DF 坐标，直接走 d
//  个 DF 单位即可（同时把 d 累加进 tDF）。写成 `rdW * (d / k)` 是把"世界位移"
//  加到了 DF 坐标上，每一步只走 d/k 个 DF 单位、整整少走 k 倍：tHit 会被报成
//  k 倍远，maxDF 也会被 k 倍速烧完。这条踩过，详见 main() 里步进处的注释。
//
//  ── ③ 与地面的裁剪 ────────────────────────────────────────────────────
//  原版用 `d = max(d1, -d0)` 把 SDF 裁在"路面"之下。这里换成一条由
//  uGroundY 决定的世界水平面：在 DF 空间里它的高度是
//  groundDF = (uGroundY - uCenter.y) * k，步进中一旦 p.y 低于它就把距离
//  顶住，光线便停在平面上。
//
//  但"停在平面上"和"命中 SDF 表面"必须区分开 —— 否则地面会被当成物体
//  着色（画出一个平的光斑）。判据不是看 d 有多小，而是看**这次迭代是
//  不是被地面顶停的**：正常收敛时 d 是被 p.y - groundDF 减小的，而顶停
//  时 d 就等于 p.y - groundDF。用一个 g_groundHit 标记直接记下来最稳，
//  不依赖浮点巧合。
//
//  ── ④ 调色 ────────────────────────────────────────────────────────────
//  不复刻原版的霓虹天空与公路背景（那是 Shadertoy 场景的一部分，搬进
//  世界空间只会和真实地形打架）。改用"绯红誓约"配色：紫罗兰辉光 +
//  亮粉主光。原有的 render1 结构（菲涅尔、AO、太阳项、辉光项）照旧，
//  只把其中"反射场景"的 render0(...) 换成一段程序化兜底。
//
//  最后照原版过一遍 aces_approx + sqrt —— 这两步才是 Shadertoy 那种
//  "高光过曝、暗部通透"观感的真正来源，去掉的话颜色会发灰。
//
//  ── ⑤ 紫 / 粉交替渐变（需求新增） ──────────────────────────────────────
//  在既有光照之上再乘一层"紫 ↔ 粉交替"的染色，并且让它跟着方块一起自转
//  （复用本文件本来就有的 g_rot）。这是**乘性**的一层，不是加性的：
//  加会把结构性暗底顶亮、把体积感抹掉。细节见 main() 里 ⑦ 那一大段。
// ══════════════════════════════════════════════════════════════════════════

// ── 自动绑定（Minecraft 每次 draw 经 ShaderInstance.apply() 写入） ──
uniform mat4 ProjMat;

uniform sampler2D DepthTexture;

uniform float uTime;       // 秒
uniform float uIntensity;  // 总强度（Java 侧已含淡入淡出；≤0.003 直接 discard）
uniform float uProgress;   // 0..1 生命周期进度（当前仅用于记录，不参与着色）
uniform float uImpact;     // 0..1 撞击爆闪 → 高光溢出 + 额外粉紫辉光
uniform float uSize;       // 包围盒半边长（方块）
uniform float uGroundY;    // 地面世界高度 Y
uniform float uHasDepth;   // 1 = 深度纹理可用
uniform vec3  uCenter;     // 包围盒中心（世界空间）
uniform vec3  uCamPos;     // 相机世界位置
// 视空间三个轴在**世界空间**里的方向（= 世界→视旋转矩阵的转置的三列）。
// 由 Java 从 RenderLevelStageEvent 的 poseStack 旋转部分取出，见文件头 ①。
uniform vec3  uViewRight;
uniform vec3  uViewUp;
uniform vec3  uViewFwd;

// ── 紫 / 粉交替渐变（需求："拥有紫色和粉色的颜色交替渐变和旋转"）──
uniform vec3  uGradA;      // 紫端
uniform vec3  uGradB;      // 粉端
uniform float uGradRate;   // 渐变整体漂移速度（周期/秒）

in vec4 vertexColor;
in vec3 vPos;

out vec4 fragColor;

// ── 形状变体 ──────────────────────────────────────────────────────────
//  ⚠️ 必须定义在 MAX_STEPS 之前：不同变体的步进预算不一样（见下）。
//    DF_CHROME  = 液态铬（默认）：球 / 环 / 圆角方块 / 八面体之间熔融，
//                 表面是镜面铬，反射一个"只存在于反射里"的霓虹 studio；
//    DF_CRYSTAL = 血晶（上一版）：八棱柱身 + 上下尖 + 三块环绕碎晶；
//    DF0 / DF2  = 更早的两支（方块球 / 缠绕环），只作对照。
#define DF_CHROME
//#define DF_CRYSTAL
//#define DF0
//#define DF2

// ── 步进预算 ──────────────────────────────────────────────────────────
//  铬那支的 SDF 是"morph + 四颗 smin 融合球"，距离场比多面体软，
//  同样的预算能走的路更短 —— 给它 96 步；晶体那支是精确多面体，64 步够。
#if defined(DF_CHROME)
#define MAX_STEPS  96
#else
#define MAX_STEPS  64      // 主步进上限（原版同量级）
#endif
#define TOL        0.002   // DF 空间收敛容差
// 物体半径 / 包围盒半边长。
// ⚠️ 必须 < 1：把包围盒做得比物体大一圈，光线才有"从外面走近"的行程。
// 取 1.0（物体正好顶满包围盒）的话，光线一进盒子就已经贴在表面上，
// 步进一次就命中 —— 结果是：法线几乎不变化、晶格辉光完全攒不起来，
// 画面上只剩一块扁平的淡紫色色斑。这一条是实测出来的（见 CubePreview）。
#define OBJ_FRACTION 0.66

// ── 调色板（绯红誓约） ────────────────────────────────────────────────
const vec3 GLOW_COL1 = vec3(0.75, 0.28, 1.00);   // 紫罗兰辉光
const vec3 SUN_COL1  = vec3(1.00, 0.35, 0.78);   // 亮粉主光
const vec3 DIFF_COL  = vec3(1.00, 0.62, 0.95);   // 漫反射底色
const vec3 FRE1      = vec3(0.80, 0.50, 1.00);   // 菲涅尔边缘色
const vec3 SUN_DIR1  = vec3(0.36514837, 0.36514837, -0.85194556);  // = normalize(vec3(3,3,-7))

// ══════════════════════════════════════════════════════════════════════════
//  DF2 "cubes" 的有符号距离场
//  ── 以下函数按 Shadertoy 原文逐字移植（含命名），仅补充 vec3 重载 ──
// ══════════════════════════════════════════════════════════════════════════

#define PI 3.141592654

mat3 g_rot;   // 全局旋转，在 main() 顶部由 uTime 设定（GLSL 150 允许非 const 全局）

// 多项式平滑 min/max（原版的 pmin/pmax）：k 越大越接近普通 min/max
float pmin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float pmax(float a, float b, float k) { return -pmin(-a, -b, k); }

// vec3 版 pmin —— 原版 pabs 依赖它做"把三个分量各自折到 |x|"的平滑版本
vec3 pmin(vec3 a, vec3 b, float k) {
    vec3 h = clamp(0.5 + 0.5 * (b - a) / k, vec3(0.0), vec3(1.0));
    return mix(b, a, h) - k * h * (1.0 - h);
}

// 平滑绝对值。k → 0 时退化为普通 abs —— 这就是"方块被磨圆"的来源
vec3 pabs(vec3 a, float k) { return -pmin(a, -a, k); }

// 八次幂球：|p| 的高次范数，形状是"超椭球"，越靠边越接近立方体
float sphere8(vec3 p, float r) {
    p *= p;
    p *= p;
    float q = dot(p, p);
    // 原版写 pow(dot(p,p), 0.125)。这里 q 由平方累乘而来必然 ≥ 0，
    // 但 pow 在负底数上是未定义的，显式 max 一次把这条隐患堵死。
    return pow(max(q, 0.0), 0.125) - r;
}

float torus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

// 4 次范数的球（比 sphere8 圆得多）
float sphere4(vec3 p, float r) {
    p *= p;
    return pow(max(dot(p, p), 0.0), 0.25) - r;
}

// 轴对齐盒子
float box(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

// 圆角盒子
float boxf(vec3 p, vec3 b, float e) {
    p = abs(p) - b;
    vec3 q = abs(p + e) - e;
    return min(min(
        length(max(vec3(p.x, q.y, q.z), 0.0)) + min(max(p.x, max(q.y, q.z)), 0.0),
        length(max(vec3(q.x, p.y, q.z), 0.0)) + min(max(q.x, max(p.y, q.z)), 0.0)),
        length(max(vec3(q.x, q.y, p.z), 0.0)) + min(max(q.x, max(q.y, p.z)), 0.0));
}

// License: MIT OR CC-BY-NC-4.0, author: mercury, found: https://mercury.sexy/hg_sdf/
// 把空间按 size 切成格子（就地取模），返回格子坐标。
vec3 mod3(inout vec3 p, vec3 size) {
    vec3 c = floor((p + size * 0.5) / size);
    p = mod(p + size * 0.5, size) - size * 0.5;
    return c;
}

// ══════════════════════════════════════════════════════════════════════
//  变体开关：DF0 = "方块球"，DF2 = "缠绕环"
//
//  两者都来自同一份 CC0 作品（mrange，"More cubes for the cube lovers"），
//  原作者在文件里就是这么建议的："Try different approximations of 'cubes'
//  by changing between DF0-DF7"。
//
//  为什么这里默认开 DF0 而不是需求里给的 DF2：
//  在**世界空间的包围盒里**渲染时，两者的可读性差得很远。
//  DF2 是一个 8 次范数的超椭球（几乎就是个圆角方块）被一个细环绕着挖了一刀，
//  正脸几乎是平面 —— 法线不变 ⇒ 漫反射是常数；那条环又大多落在背面/侧面上，
//  于是画面上只剩一块均匀的色斑，完全读不出"立体"。
//  （这不是猜的：离屏把 uHasDepth、法线长度、环面距离都打出来看过。）
//
//  ── 默认形状：血晶（六棱双锥 + 三块环绕碎晶） ──────────────────────────
//
//  旧版是 DF0「方块球」：一颗球被切成 7×7×7 个圆角方块。它在离屏图里
//  挺清楚，但世界尺度上（半边长 3.75 格、离地 6 格、玩家在 20~40 格外）
//  只占屏幕上几十像素 —— 圆角 + 7 格密度糊成一片，实机里读出来是一团
//  发涨的软糖：没有棱、没有方向，也没有"这里有个东西"的剪影。
//
//  晶体针对的正是这三点：
//    · 剪影：上下收尖的八棱双锥，远远一眼就是"一颗竖着的晶体"；
//    · 明暗：平面切面 + 逐面法线，外加一条"棱线发光"（见 gemEdgeDist）——
//      宝石感全在这里，圆角方块永远给不出来；
//    · 内部：芯（一个小球，**不参与形状**）从里面把朝向它的面照亮，
//      配深色底，晶体于是"透"，而不是一块实心粉团。
//
//  16 个半空间求交 = 精确凸多面体 SDF，步进不会穿模；棱的 cos/sin
//  全在常量里，循环会被编译器展开折叠。
//
//  想看回旧版：变体开关在文件顶部的 MAX_STEPS 之前（DF_CHROME / DF_CRYSTAL / DF0 / DF2）。
// ══════════════════════════════════════════════════════════════════════

#if defined(DF_CHROME)
// ── 液态铬 ──────────────────────────────────────────────────────────────
//
//  形状与材质移植自一篇 CC0 的 Shadertoy（"Liquid Chrome Morph"）：
//  一颗镜面铬的圆球，在 球 → 环 → 圆角方块 → 八面体 之间熔融，
//  周围还有四颗 smin 融合的小球，整体缓慢翻滚。
//
//  【为什么能直接用】
//  这支 SDF 在我们这里跑的是同一条管线：世界空间 AABB + 逐像素步进 +
//  深度纹理遮挡。它的 `map()` 就是本文件的 `dfeffect()`，唯一要动的是
//  输入（iTime → uTime）与尺度。
//
//  【尺度：为什么是"折回去再乘回来"】
//  原版所有常数（球半径 1.0、环 0.85/0.38、smin 的 k=0.75、表面波纹 0.025）
//  都是按"物体半径 ≈ 1"标定的。我们的 DF 空间以格为单位（包围盒 ±30 左右），
//  直接搬会得到一颗 1 格大的珠子。所以：
//        pDF → pShape = pDF / CHROME_SCALE    （回到原版尺度）
//        dDF = map(pShape) * CHROME_SCALE      （距离再乘回来）
//  这是**精确**的 SDF 缩放，所有原版常数一个都不用改。
//  取 9：核心半径 1 → 9 DF ≈ 1.1 格；四颗卫星轨道 1.7 → 15.3 DF ≈ 1.9 格，
//  加半径后约 2.3 格，正好落在包围盒里侧、又留出步进行程。
#define CHROME_SCALE 10.5
//  一轮 morph 的周期（秒）。原版是 mod(t*0.16, 4) → 25 秒走完四个形状。
//  这一招持续 20 秒，正好能看完 3 次变形。
#define CHROME_CYCLE 25.0

mat2 rot2(float a) { float s = sin(a), c = cos(a); return mat2(c, -s, s, c); }

// 二次平滑 min（原版 smin）：k 越大融合越"黏"
float sminq(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sdBoxRound(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}
float sdOcta(vec3 p, float s) {
    p = abs(p);
    return (p.x + p.y + p.z - s) * 0.57735027;
}

/**
 * 球 → 环 → 方块 → 八面体，一个融向下一个。
 *
 * <p>相位在 0.2~0.8 之间做过渡、其余时间保持：球形要"停一下"才读得出
 * 是四个不同的形状；全程都在变的话只会看到一团不断抽搐的金属。</p>
 */
float chromeMorph(vec3 p, float t) {
    float ph = mod(t * (4.0 / CHROME_CYCLE), 4.0);
    float i = floor(ph);
    float f = smoothstep(0.0, 1.0, smoothstep(0.2, 0.8, fract(ph)));
    float d0 = length(p) - 1.0;
    float d1 = torus(p, vec2(0.85, 0.38));
    float d2 = sdBoxRound(p, vec3(0.78), 0.22);
    float d3 = sdOcta(p, 1.25) - 0.08;
    float a = d0, b = d1;
    if (i > 0.5 && i < 1.5) { a = d1; b = d2; }
    else if (i > 1.5 && i < 2.5) { a = d2; b = d3; }
    else if (i > 2.5) { a = d3; b = d0; }
    return mix(a, b, f);
}

/** 物体自己的坐标系里求距离：先翻滚，再 morph，再融合四颗卫星球，最后加波纹。 */
float chromeMap(vec3 p, float t) {
    vec3 q = p;
    q.xz *= rot2(t * 0.35);
    q.xy *= rot2(t * 0.22);
    float d = chromeMorph(q, t);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec3 c = vec3(sin(t * 0.6 + fi * 1.9) * 1.7,
                      sin(t * 0.8 + fi * 2.3) * 0.7,
                      cos(t * 0.5 + fi * 1.3) * 1.7);
        d = sminq(d, length(p - c) - (0.30 + 0.08 * sin(t * 1.3 + fi * 2.0)), 0.75);
    }
    // 表面波纹：让它读起来是"液体"而不是抛光金属球
    d += 0.025 * sin(p.x * 6.0 + t * 1.4) * sin(p.y * 6.0 - t * 1.1) * sin(p.z * 6.0 + t * 0.9);
    return d;
}

float dfeffect(vec3 p, out float ogd) {
    // p 是 DF 坐标（格 × k）→ 折回形状尺度求距离，再乘回来（精确缩放）
    float d = chromeMap(p / CHROME_SCALE, uTime) * CHROME_SCALE;
    // 铬面不需要"内部辉光"通道；给个大值让 acc 那一项自然为 0
    ogd = 1.0e3;
    return d;
}
#elif defined(DF_CRYSTAL)
// ── 血晶 ────────────────────────────────────────────────────────────────
//
//  八棱柱身 + 上下尖（石英那种），共 24 个半空间求交：
//    柱身：|rad| ≤ a、高度 ±hb —— 八条竖直棱；
//    尖  ：从 (rad=a, y=±hb) 收到 (0, y=±H) —— 八个斜面。
//  每个面都过"腰上一条八边形边"与"对应的尖"：
//        k·(p·d) ± a·y - a·H = 0            （k = H - hb，d 是该边的外法向）
//  所以 24 个平面距离直接写成解析式，max() 出来就是精确的凸多面体距离。
//
//  ⚠️ 形状从"双锥"改成"柱身 + 尖"、棱数从 6 改成 8，都是离屏试出来的：
//  双锥在低机位（玩家站着往上看阵心）时，正对镜头的那一面又大又平，整个
//  剪影是一块斜放的平行四边形 —— 比原来的方块球更像"一块板"。
//  柱身给出竖直的侧棱、尖给出方向，八棱则保证任何方位都能露出 2~3 个切面。
#define CRYSTAL_SIDES 8
#define CRYSTAL_A0    0.39269908   // 第一条棱的方位 = π/8
#define CRYSTAL_DA    0.78539816   // 间隔 = π/4
#define CRYSTAL_APOTHEM 0.92387953 // 内切圆 / 外接圆 = cos(π/8)

float sdCrystal(vec3 p, float R, float hb, float H) {
    float a = R * CRYSTAL_APOTHEM;
    float k = max(H - hb, 1.0e-3);
    float inv = inversesqrt(k * k + a * a);
    float d = -1.0e9;
    for (int i = 0; i < CRYSTAL_SIDES; i++) {
        float ang = CRYSTAL_A0 + float(i) * CRYSTAL_DA;
        vec2  dir = vec2(cos(ang), sin(ang));
        float rad = dot(p.xz, dir);
        d = max(d, rad - a);                             // 柱身侧面
        d = max(d, (k * rad + a * p.y - a * H) * inv);   // 上尖斜面
        d = max(d, (k * rad - a * p.y - a * H) * inv);   // 下尖斜面
    }
    return d;
}

/**
 * 到"最近一条棱"的距离（越大越靠棱）。
 *
 * <p>做法：把 24 个平面距离取**前两大**，第二个就是"贴着最近的另一面有多近"。
 * 在切面中间它是个很大的负值，在棱上两条面同时收敛到 0 —— 取负号之后正好
 * 正比于离棱的距离。只用来给"棱线发光"，只在命中点算一次。</p>
 */
float gemEdgeDist(vec3 p, float R, float hb, float H) {
    float a = R * CRYSTAL_APOTHEM;
    float k = max(H - hb, 1.0e-3);
    float inv = inversesqrt(k * k + a * a);
    float m1 = -1.0e9;
    float m2 = -1.0e9;
    for (int i = 0; i < CRYSTAL_SIDES; i++) {
        float ang = CRYSTAL_A0 + float(i) * CRYSTAL_DA;
        vec2  dir = vec2(cos(ang), sin(ang));
        float rad = dot(p.xz, dir);
        float ds[3];
        ds[0] = rad - a;
        ds[1] = (k * rad + a * p.y - a * H) * inv;
        ds[2] = (k * rad - a * p.y - a * H) * inv;
        for (int j = 0; j < 3; j++) {
            if (ds[j] > m1) { m2 = m1; m1 = ds[j]; }
            else if (ds[j] > m2) { m2 = ds[j]; }
        }
    }
    return -m2;
}

// 环绕碎晶的轨道参数（DF 单位：包围盒半边长 = 20）
#define SHARD_COUNT 3
#define SHARD_ORBIT 17.0
#define SHARD_R      2.4
#define SHARD_HB     1.6
#define SHARD_H      6.0

// 主晶体的尺寸（DF 单位）。
//   CRYSTAL_R  = 外接圆半径（11）→ 世界宽度约 2.7 格
//   CRYSTAL_HB = 柱身半高（12）   → 竖直棱长 24 个单位
//   CRYSTAL_H  = 尖到底高（22）   → 距盒壁 8 个单位，光线有行程
//   R/H = 11/22 = 0.5：竖长的晶体，剪影一眼读得出方向。
#define CRYSTAL_R   11.0
#define CRYSTAL_HB  12.0
#define CRYSTAL_H   22.0

// 晶体的自转：**只绕世界 Y 轴**。
// ⚠️ 这里不能用 g_rot —— 那是给缠绕环准备的全 3D 旋转（rotX·rotZ·rotY），
// 套到一根竖着的晶体上会让它整个躺平：远看是一块横放的宝石，"晶体"的
// 竖轴、方向感全没了（离屏里就是这样，一度误判成 SDF 写错）。
vec3 crystalSpin(vec3 p) {
    float a = uTime * 0.22;
    float c = cos(a), s = sin(a);
    return vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
}

float dfeffect(vec3 p, out float ogd) {
    // 只自转、不翻滚（理由见 crystalSpin）
    vec3 q = crystalSpin(p);

    // 主晶体
    float d = sdCrystal(q, CRYSTAL_R, CRYSTAL_HB, CRYSTAL_H);

    // 三块碎晶绕主晶体公转，各自还上下起伏。
    // ⚠️ 早退：只有当点落在轨道环附近（|半径 − 轨道| < 5）时才求它们。
    //    不加这条，盒子内部每一步都要多算 3 个 24 面体 —— 而盒子里
    //    绝大多数采样点离碎晶十万八千里（离屏实测：去掉早退后 SDF
    //    开销约 ×2.4，画面几乎无差别）。
    float rxz = length(q.xz);
    if (abs(rxz - SHARD_ORBIT) < 4.0) {
        for (int i = 0; i < SHARD_COUNT; i++) {
            float fi = float(i);
            float a  = uTime * 0.55 + fi * 2.0944;          // 公转角（120° 一档）
            vec3  c  = vec3(cos(a), 0.0, sin(a)) * SHARD_ORBIT;
            c.y = sin(uTime * 0.9 + fi * 2.1) * 4.5;        // 上下起伏
            vec3  s  = q - c;
            // 每块绕自己的竖轴翻一个不同角度，三块的朝向才不会像复制粘贴
            float ca = cos(a * 1.7 + fi), sa = sin(a * 1.7 + fi);
            s.xz = vec2(ca * s.x - sa * s.z, sa * s.x + ca * s.z);
            d = min(d, sdCrystal(s, SHARD_R, SHARD_HB, SHARD_H));
        }
    }

    // 芯：**不参与形状**，只是"内部光源"到采样点的距离。
    //  `ogd` 是这条管线里给辉光用的通道（见第 ① 段），这里让它指向芯。
    ogd = length(p - vec3(0.0, -1.0, 0.0)) - 4.0;
    return d;
}
#elif defined(DF0)
// ── DF0：方块球 ────────────────────────────────────────────────────────
//  球体的外框（sphere8 ∩ sphere4）里塞一组 mod3 切出来的方块。
//
//  ⚠️ 格子密度是这一支唯一的、也是最要紧的观感参数：
//  原版 FLAIR 取 bsz = 2*sz/(3-1) = 20，也就是**整个球里只切出 2×2×2 格**。
//  那在 Shadertoy 里（物体只占屏幕一小块、距离 76 单位）看着没问题，
//  但搬到世界空间、玩家走到 5~10 格外时，看到的就是"中间一个大方块 +
//  六个方向各伸出一截"的十字形色块 —— 实机截图确认过。
//  GRID 取 7：球被切成 7×7×7，世界尺度下每个小方块约 1.4 格，
//  近距离一眼就是"一堆方块组成的球"，也正是这个着色器名字里的东西。
#define GRID 7.0

float dfeffect(vec3 p, out float ogd) {
    const float sz = 20.0;
    float d0 = sphere8(p, sz);

    float bsz = 2.0 * sz / (GRID - 1.0);
    vec3 p2 = p;
    mod3(p2, vec3(bsz));
    // 每个格子里的方块留 12% 的缝 —— 缝就是"一格一格"读得出来的原因
    float d2 = box(p2, vec3(0.88 * bsz * 0.5));

    float d4 = sphere4(p, sz - 0.005);

    float d = d2;
    d = max(d, d0);
    d = min(d, d4);

    ogd = d4;
    return d;
}
#else
// ── DF2：缠绕环（需求原文给的那一支） ──────────────────────────────────
//
// ogd（out）= 环面本身的距离。它不是形状的一部分，而是**辉光通道**：
// 环面被从大球里挖掉之后，它的"残影"正好是格子的内边框。
float dfeffect(vec3 p, out float ogd) {
    const float sz = 20.0;
    vec3 p0 = p;
    vec3 p1 = p;
    p1 *= g_rot;          // 注意：这是 p1 = p1 * g_rot（行向量右乘），
    p1 = pabs(p1, 10.0);  // 等价于按 g_rot 的转置旋转 —— 原版即如此，照抄。
    p1 -= 12.0;
    p1 *= g_rot;
    float d0 = sphere8(p0, 20.0);
    float d1 = torus(p1, 10.0 * vec2(1.0, 0.0125));
    float d = d0;
    d = pmax(d, -(d1 - 2.0), 5.0);   // 从球里"减去"一圈环带（平滑差集）
    d = min(d, d1);                  // 再把环带本身并回来 → 形成格栅
    ogd = d1;
    return d;
}
#endif

// ── 旋转矩阵：按原版逐字移植 ──────────────────────────────────────────
//  GLSL 的 mat3(...) 是**列主序**：列0=(c0x,c0y,c0z)、列1=(c1x,c1y,c1z)、
//  列2=(c2x,c2y,c2z)。原版 rotX 把元素按行写出来，在 GLSL 里正好解释成
//  上面这个列主序 —— 也就是说这些函数本身就是合法 GLSL，不需要"修正"。
mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 rotZ(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0);
}

// ══════════════════════════════════════════════════════════════════════════
//  ACES 近似色调映射 —— 原版逐字移植（这一步决定成片的"摄影感"）
// ══════════════════════════════════════════════════════════════════════════
vec3 aces_approx(vec3 v) {
    v *= 0.6;
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((v * (a * v + b)) / (v * (c * v + d) + e), 0.0, 1.0);
}

// ══════════════════════════════════════════════════════════════════════════
//  世界空间工具
// ══════════════════════════════════════════════════════════════════════════

// 世界点 → 视空间点。uCamPos 是相机世界位置，减掉之后剩下的就是一个
// 世界空间向量，再与三个视基向量点乘即得视空间坐标（= R · d）。
vec3 toView(vec3 pw) {
    vec3 d = pw - uCamPos;
    return vec3(dot(uViewRight, d), dot(uViewUp, d), dot(uViewFwd, d));
}

// 视空间点 → 世界方向。刚体变换的逆旋转 = 转置，即用视基做线性组合。
vec3 toWorldDir(vec3 pv) {
    return normalize(uViewRight * pv.x + uViewUp * pv.y + uViewFwd * pv.z);
}

#if defined(DF_CHROME)
/**
 * 铬面反射里看到的"霓虹 studio"。
 *
 * <h3>它只存在于反射里，不画到天上</h3>
 * <p>原版那支 Shadertoy 会把这一整套环境画满全屏（条纹太阳、环形灯、
 * 旋转灯条）。搬进 Minecraft 就必须删掉"画"的部分 —— 真实的天空是天空、
 * 地形是地形，凭空多一个太阳会和世界打架。但<b>反射</b>是另一回事：
 * 铬面上映出一片并不存在的霓虹棚，观众不会去核对它的位置，只会读成
 * "这颗东西很亮、很贵"。</p>
 *
 * <p>方向是世界方向（不是屏幕方向），所以转动视角时反射会跟着动 ——
 * 这正是"它在世界里"而不是"贴了一层贴纸"的证据。</p>
 *
 * @param rd     世界空间方向
 * @param lights 0 = 反射里那个"远处"的版本（暗一点），1 = 满亮
 */
vec3 chromeEnv(vec3 rd, float lights) {
    float el = rd.y;
    vec3 hor = vec3(1.0, 0.22, 0.48);
    vec3 mid = vec3(0.34, 0.05, 0.62);
    vec3 top = vec3(0.01, 0.006, 0.09);
    vec3 c = mix(hor, mid, smoothstep(0.0, 0.32, el));
    c = mix(c, top, smoothstep(0.22, 0.85, el));
    // 地平线以下压暗（用 1-smoothstep 而不是反着传 edge0/edge1：
    //  GLSL 规定 edge0 >= edge1 时 smoothstep 的结果未定义）
    vec3 below = mix(hor * 0.45, vec3(0.015, 0.0, 0.05), 1.0 - smoothstep(-0.35, 0.0, el));
    c = mix(c, below, 1.0 - smoothstep(-0.02, 0.0, el));

    // ── 遮光旗：镜子必须同时映出"黑"和"白" ──
    //  离屏实测：只有一片平滑渐变时，整个球面是一块 #722868 的紫 ——
    //  读起来是彩色塑料，而不是铬。真实影棚靠的是大块遮光旗 + 柔光箱，
    //  铬面上于是有硬边的明暗块，那才是"金属"。
    //  三面旗围一圈、随方位缓慢转动，任何视角都能碰到一两面。
    float az = atan(rd.x, rd.z) + uTime * 0.3;
    float flags = smoothstep(0.50, 0.92, cos(az * 3.0 + 0.7));
    c *= 1.0 - 0.90 * flags;

    // ── 条纹太阳：在 -Z 方向，只从反射里看得见 ──
    if (rd.z < -0.05) {
        vec2 u = rd.xy / -rd.z;
        vec2 sc = vec2(0.0, 0.07);
        float r = 0.14;
        float d = length(u - sc);
        float y = u.y - sc.y;
        float gap = y < 0.05 ? step(fract(y * 36.0), (1.0 - smoothstep(-r, 0.04, y)) * 0.85) : 0.0;
        float disc = smoothstep(r, r - 0.006, d) * (1.0 - gap) * step(0.0, u.y);
        c += vec3(1.0, 0.3, 0.4) * exp(-d * 4.0) * 0.30;
        c = mix(c, mix(vec3(1.0, 0.1, 0.5), vec3(1.0, 0.8, 0.2), smoothstep(-r, r, y)) * 1.1, disc);
    }

    // ── 旋转的霓虹灯条 + 一道环形灯 ──
    float strip = smoothstep(0.985, 0.998, cos(az * 3.0))
                * smoothstep(-0.05, 0.05, el)
                * (1.0 - smoothstep(0.55, 0.75, el));
    vec3 stripCol = cos(az * 1.5) > 0.0 ? vec3(0.1, 0.9, 1.0) : vec3(1.0, 0.15, 0.8);
    c += stripCol * strip * 2.0 * lights;

    float ring = exp(-pow((el - 0.34) / 0.012, 2.0)) * (0.5 + 0.5 * cos(az * 2.0));
    c += vec3(0.2, 0.8, 1.0) * ring * 1.8 * lights;

    c += mix(vec3(1.0, 0.25, 0.85), vec3(0.2, 0.8, 1.0), 0.5 + 0.5 * sin(az))
       * smoothstep(0.72, 0.9, el) * 1.5 * lights;

    // ── 影棚柔光箱（这一层是给铬面"捏"出形体用的）──
    //  镜子必须映出**有边界的亮块**才读得出是镜子；只映一片平滑渐变，
    //  它就会变成一块彩色塑料（离屏第一版就是这样）。三块不同大小的
    //  硬边面板围着 +Z（相机那一侧）排开，于是不管你转到哪个方位，
    //  球面上总有一两块高光。
    vec3 P1 = normalize(vec3(-0.55, 0.55, 0.85));
    vec3 P2 = normalize(vec3( 0.75, 0.25, 0.72));
    vec3 P3 = normalize(vec3( 0.00, 0.95, 0.35));
    c += vec3(1.00, 0.95, 1.00) * smoothstep(0.86, 0.93, dot(rd, P1)) * 3.2 * lights;
    c += vec3(0.55, 0.90, 1.00) * smoothstep(0.90, 0.96, dot(rd, P2)) * 2.4 * lights;
    c += vec3(1.00, 0.45, 0.85) * smoothstep(0.94, 0.985, dot(rd, P3)) * 2.8 * lights;
    return c;
}
#endif

// 射线 × AABB 的 slab 求交。返回 false = 这条光线与盒子无交（盒子整个在
// 背后、或盒子架在光线射程之前）。命中时 t0/t1 是进出参数，t0 可能为负
// （相机在盒内），调用方负责钳到 0。
bool slab(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax,
          out float t0, out float t1) {
    vec3 inv = 1.0 / rd;                      // rd 的分量不为 0（方向已归一化）
    vec3 ta = (bmin - ro) * inv;
    vec3 tb = (bmax - ro) * inv;
    vec3 tsmall = min(ta, tb);
    vec3 tbig   = max(ta, tb);
    t0 = max(max(tsmall.x, tsmall.y), tsmall.z);
    t1 = min(min(tbig.x, tbig.y), tbig.z);
    return t1 >= max(t0, 0.0);
}

void main() {
    // ── 0. 强度闸门 ────────────────────────────────────────────────────
    float I = max(uIntensity, 0.0);
    if (I <= 0.003) discard;

    // ── 1. 旋转状态（原版 g_rot） ──────────────────────────────────────
    float tm = uTime * 0.5;
    g_rot = rotX(0.333 * tm) * rotZ(0.5 * tm) * rotY(0.23 * tm);

    // ── 2. 世界空间光线 ────────────────────────────────────────────────
    vec3 ro = uCamPos;
    vec3 rdW = toWorldDir(normalize(vPos));   // 视空间方向 → 世界方向

    // ── 3. 与包围盒求交 ────────────────────────────────────────────────
    float size = max(uSize, 0.001);
    vec3 bmin = uCenter - vec3(size);
    vec3 bmax = uCenter + vec3(size);
    float t0, t1;
    if (!slab(ro, rdW, bmin, bmax, t0, t1)) discard;   // 光线完全不碰盒子

    float tStart = max(t0, 0.0);   // 相机在盒内时 t0 < 0 → 从 0 起
    float tEnd   = t1;
    if (tEnd <= tStart) discard;

    // ── 4. 世界 → DF 空间 ──────────────────────────────────────────────
    //  物体的世界半径 = size * OBJ_FRACTION，它映射到 DF 空间的 20（原版的 sz）。
    //  于是包围盒（半边长 size）在 DF 空间里是 ±(20 / OBJ_FRACTION) —— 刻意比
    //  物体大一圈，光线才有从外面逼近的行程（见文件头 ③ 与 OBJ_FRACTION 的注释）。
    float k = 20.0 / max(size * OBJ_FRACTION, 0.05);
    float groundDF = (uGroundY - uCenter.y) * k;   // 地面在 DF 空间的高度

    vec3 dfStart = (ro + rdW * tStart - uCenter) * k;
    float maxDF  = (tEnd - tStart) * k;            // DF 空间里的步进预算

    // ── 4b. 相机在物体内部：直接不画 ────────────────────────────────────
    //  包围盒很大（半边长 uSize ≈ 7 格），玩家走进阵心时就站在盒子里；
    //  如果这时相机又落在**实体内部**（DF < 0），整条光线的起点就已经在
    //  "已经命中"的状态里，步进跑不动、法线退化成 0，画面上是一整块
    //  糊满屏幕的死色。这一条是实机截图暴露出来的。
    {
        float probe = 0.0;
        if (dfeffect(dfStart, probe) < 0.0) discard;
    }

    // ── 5. 步进 ────────────────────────────────────────────────────────
    //  g_gd 记录整条光线上"辉光距离"的最小值 —— 与主距离不同，
    //  它算的是环面 ogd 而不是最终 SDF。
    //  acc 是沿光线累积的"晶格辉光"：每一步把 torus 距离换算成一条细亮线。
    //  只取全光线 min 的话，光靠一次擦身而过就定生死，缠绕组件的厚度出不来；
    //  累积之后那圈霓虹才有参考里那种"绕着物体缠了几圈"的实感。
    vec3  p       = dfStart;
    float tDF     = 0.0;      // 已走过的 DF 空间距离
    float g_gd    = 1e3;      // 全光线的环面距离最小值（体积辉光用）
    float ogdHit  = 1e3;      // **命中点**的环面距离（表面图案用）
    float acc     = 0.0;
    bool  hit     = false;
    bool  hitGnd  = false;
    int   steps   = 0;

    for (int i = 0; i < MAX_STEPS; i++) {
        steps = i + 1;

        float ogd = 0.0;
        float d = dfeffect(p, ogd);
        g_gd = min(g_gd, ogd);           // 原版：g_gd 取的是 ogd，不是 d
        acc += exp(-9.0 * max(ogd, 0.0)) * min(d, 2.0);

        // 地面裁剪：低于地平面就把距离顶住，光线停在平面上。
        // 同时用 hitGnd 记下"这次是地面顶停的"，好在后面把它与真正的
        // SDF 命中区分开（否则地面会被当成物体着色）。
        if (p.y < groundDF) {
            d = max(d, groundDF - p.y);
            hitGnd = true;
        }

        if (d < TOL) { ogdHit = ogd; hit = !hitGnd; break; }

        tDF += d;
        if (tDF > maxDF) break;          // 步进预算用尽 → 体积里是空的
        // ⚠️ p 是 **DF 空间**的点，所以要沿光线走 d 个 DF 单位 —— 直接加
        //    rdW * d 即可（DF 空间 = 世界空间整体乘 k，方向不变）。
        //    这里曾经写成 rdW * (d / k)：那是"世界位移"，可 p 不是世界坐标，
        //    于是每一步只走了 d/k 个 DF 单位，整整少走 k ≈ 4 倍。后果有两个，
        //    而且都随"光线在盒子里要走多远"线性放大：
        //      ① tDF 仍按 d 累加 ⇒ tHit = tStart + tDF/k 把命中点报到 k 倍远处，
        //         深度遮挡拿这个虚高的点去比 ⇒ 明明是前方的东西却被判成遮挡；
        //      ② 预算 maxDF 被 k 倍速烧完 ⇒ 只有离物体最近的那几条光线来得及
        //         命中，外围全部 break 出去 discard。
        //    离屏实测（build/shader-preview/CubePreview）：20 格外实测 tHit≈24，
        //    而真实命中点在 15.8 格、需要 4 步；改成 rdW * d 后实测 4 步、15.8 格。
        p += rdW * d;
    }

    // 归一化累积辉光：箱子越大走得越久，不除掉的话亮度会随尺寸漂。
    acc /= max(maxDF * 0.12, 1.0);

    if (!hit) discard;                   // 未命中：不产生任何辉光

    // 命中点的世界距离：DF 空间里走了 tDF，折算回世界空间是 tDF / k
    float tHit = tStart + tDF / k;

    // ── 6. 法线（DF 空间中心差分，eps = 0.005） ────────────────────────
    const float EPS = 0.005;
    float dummy = 0.0;
    vec3 n = vec3(
        dfeffect(p + vec3(EPS, 0.0, 0.0), dummy) - dfeffect(p - vec3(EPS, 0.0, 0.0), dummy),
        dfeffect(p + vec3(0.0, EPS, 0.0), dummy) - dfeffect(p - vec3(0.0, EPS, 0.0), dummy),
        dfeffect(p + vec3(0.0, 0.0, EPS), dummy) - dfeffect(p - vec3(0.0, 0.0, EPS), dummy)
    );
    n = normalize(n);

    // ── 7. 着色（原版 render1 的结构：菲涅尔 + AO + 太阳项 + 辉光） ────
    //  材质刻意压暗：参考里那块"立体"之所以好看，是因为本体几乎是暗的，
    //  亮度全部来自缠绕的霓虹与边缘的菲涅尔。本体一亮，整体就糊成一块
    //  淡紫色色斑（这是第一版实机 + 离屏实测的结论）。
    vec3 m = DIFF_COL * 0.42;                             // 表面材质色（压暗）
    float ao = 1.0 - float(steps) / float(MAX_STEPS);     // 步数越少越"露在光里"
    float fo = mix(0.2, 1.0, ao);

    // 原版这里反射整个场景（render0）。
    // 在世界空间里再步进一整套 SDF 代价太高，而且反射到的"场景"本来就
    // 不存在（真实地形不在 SDF 里），所以换成一段程序化兜底。
    float rgd = 1e3;
    vec3 rcol = clamp(GLOW_COL1 * inversesqrt(max(rgd, 0.00025)), 0.0, 4.0)
              + DIFF_COL * 0.2;

    float fre = 1.0 + dot(rdW, n);
    fre = fre * fre;                                      // 原版：平方项
    float rf = mix(0.33, 1.0, fre) * fo * 0.75;           // 反射权重
    float dif = dot(SUN_DIR1, n);

    vec3 col = m * (1.0 - m);                             // 原版的结构性暗底
    col += m * SUN_COL1 * dif * dif * DIFF_COL * fo;      // 主光
    col += rf * rcol * fre * FRE1;                        // 菲涅尔反射（带色）

    // ① 沿光线累积的晶格辉光 —— 参考里那圈缠绕霓虹的主体
    col += GLOW_COL1 * acc * 1.2;

#if defined(DF_CHROME)
    // ── 镜面铬：把上面那套"漫反射 + 主光"整个盖掉 ──
    //  铬不散射，它的颜色几乎全部来自反射；留着漫反射项只会让它变成
    //  一块灰色的塑料。所以这里不是 `+=`，是 `col =`。
    //
    //  p / n 都是 DF 空间的量，但 DF 变换只有平移 + 等比缩放 ——
    //  方向不变，所以 n 可以直接当世界法线、rdW 当世界视线用。
    vec3 r = reflect(rdW, n);
    float ndv = max(dot(n, -rdW), 0.0);
    // ⚠️ 不能叫 fre：上面"漫反射 + 主光"那段已经有一个同名变量（同作用域）
    float chromeFre = pow(1.0 - ndv, 4.0);
    // 虹彩：金属表面的那层淡淡色散（原版的 tint）
    vec3 tint = 0.86 + 0.14 * cos(vec3(0.0, 2.0, 4.0) + dot(n, rdW) * 5.0 + uTime * 0.3);

    vec3 refl;
    // 命中点折回"形状尺度"：地板 / 网格这些常数都活在这个尺度上
    vec3 ps = p / CHROME_SCALE;
    if (r.y < -0.001) {
        // ── 朝下的反射：映一片**只存在于反射里**的霓虹网格地板 ──
        //  不画这块地板（A 档不碰真实场景），只算它在铬面上的像。
        //  上一版用"脚下法阵的光"顶替，结果整个下半球是一片暗紫，
        //  铬读起来像彩色塑料 —— 镜子必须映出**有边界的高对比亮块**。
        //  地板高度取形状尺度的 -1.45（与参考一致）：在物体中心下方约
        //  1.6 格，不会和真实地面打架，反正它只在反射里。
        float tf = (-1.45 - ps.y) / r.y;
        vec3 fp = ps + r * tf;
        // 网格缓慢滚动：铬面里的反射条纹因此一直在爬，读起来是"液体"
        vec2 g = abs(fract((fp.xz - vec2(uTime * 0.35, 0.0)) * 0.5) - 0.5);
        float line = 1.0 - smoothstep(0.0, 0.045, min(g.x, g.y));
        vec3 gridCol = mix(vec3(1.0, 0.1, 0.7), vec3(0.1, 0.9, 1.0),
                           0.5 + 0.5 * sin(fp.x * 0.3));
        // 地板底子要"暗 + 有结构"：玩家站在阵边、从下往上看这颗核心，
        // 看到的主要是**下半球**，也就是全在反射这块地板。
        //  - 底子压暗：亮底会让整个下半球变成一块匀色（上一版 #722868 的紫）；
        //  - 光池收紧：只在物体正下方一团亮，反射到曲面上就是一个**光斑**，
        //    而不是一层平铺的紫雾；
        //  - 网格线提亮加粗：细线在曲面上会变成弧形的霓虹条纹，
        //    那正是"铬"的辨识度来源。
        vec3 poolCol = mix(vec3(0.65, 0.12, 0.85), vec3(1.0, 0.25, 0.72),
                           0.5 + 0.5 * sin(fp.x * 0.45 + uTime * 0.4));
        float pool = exp(-dot(fp.xz, fp.xz) * 0.9);
        refl = vec3(0.030, 0.005, 0.080)
             + poolCol * pool * 1.10 * exp(-tf * 0.12)
             + gridCol * line * 2.60 * exp(-tf * 0.18);
        refl = mix(refl, chromeEnv(vec3(r.x, 0.001, r.z), 1.0) * 0.35, 0.30);
    } else {
        refl = chromeEnv(r, 1.0);
    }
    col = refl * tint * (1.0 + 0.5 * chromeFre);

#elif defined(DF_CRYSTAL)
    // ── 芯光：晶体"透"的全部来源 ──
    //  真实折射要再走一轮 march，太贵。对一颗多面体来说，"面朝向芯"的
    //  解析补光已经足够读出"里面有个亮的东西"：朝芯的面亮、背芯的面暗，
    //  明暗差本身就是"厚度"的替身。
    //  p 是命中点的 DF 坐标，芯与 dfeffect 里那处保持一致。
    vec3  nucC = vec3(0.0, -1.0, 0.0);
    float nucD = length(p - nucC);
    vec3  toNuc = (nucC - p) / max(nucD, 1.0e-4);
    float facingCore = max(0.0, -dot(n, toNuc));     // n 朝外、toNuc 朝内
    float atten = exp(-max(nucD - 4.0, 0.0) * 0.075);
    float core = pow(facingCore, 1.6) * atten;
    vec3  coreCol = mix(uGradB, vec3(1.0, 0.86, 1.0), 0.62);
    col += coreCol * core * 1.15;

    // 腰线：晶体最宽处那一圈。宝石的 girdle 是"被切开过"的读法来源；
    // 没有它，上下两个锥会糊成一个纺锤（离屏第一版就是这样）。
    // p.y 是 DF 空间的高度（0 = 腰），1.9 倍压缩让线宽约 0.5 格。
    float girdle = exp(-pow(p.y * 1.9, 2.0));
    col += mix(uGradA, vec3(1.0), 0.30) * girdle * 0.60;

    // ── 棱线：全篇最像"宝石"的一笔 ──
    //  切面本身是平的，法线只变一次，菲涅尔在面上是常数 —— 所以光亮必须
    //  来自**面与面的交界**。gemEdgeDist 给出"离最近一条棱多远"，取指数
    //  就是一根贴着棱的细亮线；再乘一次掠射项，正对镜头的棱只留一点、
    //  侧过去的棱最亮（和真实宝石的高光方向一致）。
    float ed = gemEdgeDist(crystalSpin(p), CRYSTAL_R, CRYSTAL_HB, CRYSTAL_H);
    float edge = exp(-max(ed, 0.0) * 2.6);
    float graze = 0.35 + 0.65 * pow(1.0 - clamp(abs(dot(rdW, n)), 0.0, 1.0), 1.5);
    col += mix(uGradA, vec3(1.0), 0.55) * edge * graze * 4.6;

    // 每个切面自己的明暗差：主光按面法线走，功率调得比"方块球"更硬，
    // 相邻切面才会一个亮一个暗（软糖感的来源就是所有面都差不多亮）。
    col += m * SUN_COL1 * pow(max(dif, 0.0), 1.6) * 1.8;

    // 步进途中擦过芯的那点余晖（acc 本来就为这种"从内部透出来"的辉光准备）
    col += GLOW_COL1 * acc * 0.35;

#elif defined(DF0)
    // DF0（方块球）的 ogd 是**外球壳**的距离，在表面上恒为 0 ——
    // 所以原来那两项（1/sqrt(g_gd)、exp(-|ogdHit|)）在这里都会退化成
    // 一个处处相同、还被 clamp 到上限的常数，把整块面拍成一块死白。
    // 实测：实机截图里那块"发白的十字色块"就是这两项造成的。
    //
    // 而 DF0 本身已经有大量几何细节（一格一格的方块面 + 缝），
    // 光照 / 移动补光 / 菲涅尔 / AO 足够把结构读出来，不需要那根拐杖。
    // 这里只补一层很淡的体辉光，让它在暗处也仍然像个发光体。
    col += FRE1 * 0.10;
#else
    // ② 局部缠绕光（原版那一层，但**必须用命中点的 ogd，不能用全光线最小值**）。
    //    实测过：DF2 的可见表面上"全光线最小环面距离"几乎是 0
    //    （光线只要从环附近经过一次就被记下来），拿它算 1/sqrt 会得到
    //    一个处处相同、还被 clamp 到上限的常数项 —— 整块面被这一个常数
    //    拍平，看起来就是一块均匀的粉色色斑。
    vec3 ggcol = GLOW_COL1 * inversesqrt(max(g_gd, 0.02));
    col += clamp(m * ggcol, 0.0, 4.0) * 0.35;

    // ③ 缠绕霓虹：用**命中点**到环面的距离当发光图案。
    //    这才是那条绕在方块上的亮线 —— 它是 DF2 那支立体上唯一的"结构"。
    float wire = exp(-1.6 * abs(ogdHit));
    col += GLOW_COL1 * wire * 2.4;
    col += FRE1 * pow(wire, 4.0) * 1.6;
#endif

    // ④ 移动补光：同上，正脸太平，需要一个会动、且"离得近"的光源
    //    才能读出起伏（固定的方向光在平面上同样是常数）。
    vec3 lp = uCenter + vec3(cos(uTime * 1.1), 0.85, sin(uTime * 1.1)) * size * 2.2;
    vec3 ld = normalize(lp - (ro + rdW * tHit));
    float dif2 = max(dot(ld, n), 0.0);
    col += m * SUN_COL1 * dif2 * dif2 * 1.0;

    // ⑤ 表面图案：即使法线不变，位置还是在变的。
    //    用世界坐标切一层缓慢滚动的三维格子，近看才不至于是一整块死色。
    //  ⚠️ 晶体分支跳过这一层：那层格子是给"方块球"补细节的，套到平整的
    //    晶体切面上只会变成一层脏噪点 —— 切面本身的明暗差已经够了。
    vec3 pw = ro + rdW * tHit;
    float pat = 0.5 + 0.5 * sin(pw.x * 2.1 + uTime * 0.9)
                          * sin(pw.y * 2.1 - uTime * 0.7)
                          * sin(pw.z * 2.1 + uTime * 1.1);
#if !defined(DF_CRYSTAL)
    col *= 0.55 + 0.80 * pat;
#endif

    // ⑥ 边缘菲涅尔：让"这块东西有体积"最省事的一招。
    float rim = pow(1.0 - clamp(abs(dot(rdW, n)), 0.0, 1.0), 3.0);
    col += FRE1 * rim * 1.0;
    col += GLOW_COL1 * pow(1.0 - clamp(abs(dot(rdW, n)), 0.0, 1.0), 8.0) * 1.2;

    // ── ⑦ 紫 / 粉交替渐变 + 旋转（本次新增的一层） ──────────────────────
    //  需求："悬浮的立方体……拥有紫色和粉色的颜色交替渐变和旋转"。
    //
    //  【为什么在 DF 空间里按 p 采样，而不是按世界坐标 pw】
    //  p 是**物体自己**的坐标（以 uCenter 为心、已按 k 归一）。用它有两个好处：
    //    · 条纹与物体严格绑定，相机怎么转、玩家走到哪，图案都长在方块上；
    //    · 条纹间距不随 uSize 变 —— 换成世界坐标的话，把核心改小一半
    //      （7.5 → 3.75）会让条纹密度跟着翻倍，观感全变。
    //
    //  【旋转是怎么来的】
    //  本文件本来就有 g_rot（DF2 那支用它把自己的 SDF 转起来，见 main 顶部
    //  g_rot = rotX·rotZ·rotY，角速度随 uTime 线性增长）。这里用**同一个**
    //  g_rot 去转采样点 —— 于是"方块在转"和"渐变在转"是同一个旋转：
    //  条纹跟着方块的面一起走，读起来才是"这块东西在自转、颜色一圈紫一圈粉"，
    //  而不是"一块静止的方块外面套了一层流动的滤镜"。
    //  另外再叠 uGradRate * uTime 的相位漂移，让紫粉交替本身也在换，
    //  旋转停下来的瞬间画面也不会定死。
    //
    //  ⚠️ 相位要在**归一化**之后再看：acos(cos(x)) 得到的是 0..PI 的折叠值，
    //  乘 2/PI 折成 0..1 的一个"来回"。不折的话 cos 的正负半周会把条纹压成
    //  同一段的重复，读起来是纯色块而不是渐变。
    //  ── 频率：必须以 DF 空间的 20 为基准，不能用世界尺寸 ──
    //  ⚠️ 这里踩过一次，值得写清楚：p 是 DF 坐标（物体整体乘过 k），
    //  所以"在物体上跨多少个条纹"只由 DF 空间里的行程决定 —— 物体被改小
    //  一半（7.5 → 3.75）时 k 翻倍、p 的行程<b>不变</b>。
    //  第一版把频率写成"世界尺寸 / 常量"，结果在减半后的核心上相位总变化
    //  只有 0.19 个周期，band 被压在 0.85~1.0 —— 整块是纯粉色，
    //  "紫粉交替"完全看不出来（离屏把 band 当灰度画出来确认的）。
    //  取 6：物体可见面上的相位总变化约 3 弧度 ≈ 0.48 个周期 ——
    //  一头紫、一头粉，中间是过渡，读起来是"渐变"而不是"条形码"。
    //  8 的时候条纹偏密、像码；4 的时候整块只剩一个色（两个方向都试过）。
    const float GRAD_K = 6.0;
    vec3 pg = p * g_rot;                     // p 是 DF 坐标，g_rot 是物体自转
    float phase = dot(pg, vec3(0.79, 0.51, 0.34)) / 20.0 * GRAD_K
                - uTime * uGradRate;
    //  0..1 的"来回"。⚠️ smoothstep 那一步是把三角波压成余弦状的关键：
    //  acos 出来的是线性三角波，边界处有一道可见的折线；压过之后
    //  两端平台变宽、中间过渡更陡，才像一次真正的颜色渐变。
    float band = acos(clamp(cos(phase * PI), -1.0, 1.0)) * (2.0 / PI);
    band = smoothstep(0.0, 1.0, band);
    vec3 grad = mix(uGradA, uGradB, band);

    // ── 诊断通道（CUBE_GRAD_PROBE 打开时）──
    // 直接把 band 当灰度画出来：整块一个常数就说明相位在物体上根本没变化，
    // 有条纹就说明渐变本身没问题、问题在别处。比"看着像粉色"可靠得多。
    // 写成 if/else 而不是提前 return：return 之后整条着色链都成了死代码，
    // 链接器会把 uTime / uCamPos / uGradA 这些全剔掉，复现台那边
    // getUniformLocation 直接返回 -1，什么都测不出来。
#ifdef CUBE_GRAD_PROBE
    col = vec3(band);
#else

    // 乘上去而不是加：加会把暗部也顶亮，方块正面那块"结构性暗底"（col 的
    // 第一项 m*(1-m)）正是靠它才读得出体积。乘法的暗端压暗、亮端提亮，
    // 紫粉两色交替的同时对比度还在。
    // 0.70 是染色力度：取 1.0 会把漫反射 / 菲涅尔 / 移动补光全部染成一个色，
    // 只剩下一块没有立体感的紫粉色斑；取 0.55 时两色差异又太弱、
    // 看上去仍是一块淡紫（两个极端都在离屏对照过）。
#if defined(DF_CHROME)
    //  铬面几乎不能染色：它本身就是一面镜子，把颜色乘上去只会变成
    //  "粉色的镜子"。0.16 只留一点点品牌色偏，剩下交给反射。
    col *= mix(vec3(1.0), grad, 0.16);
#elif defined(DF_CRYSTAL)
    //  晶体只要 0.48：切面本身的明暗差才是主角，染色一重就回到"一块淡粉"。
    col *= mix(vec3(1.0), grad, 0.48);
#else
    col *= mix(vec3(1.0), grad, 0.70);
#endif

    // ── 8. 撞击爆闪 ────────────────────────────────────────────────────
    //  uImpact > 0 时叠一层粉紫溢光，并整体拉高亮度。
    if (uImpact > 0.0) {
        col += vec3(1.0, 0.45, 0.85) * uImpact * 0.6;
    }
    col *= I * (1.0 + 2.5 * uImpact);

    // uProgress 的相位调制。写成"小于阈值就整体跳过"而不是直接除以它：
    // Java 侧若还没接上这个 uniform，默认值 0 会让它恒为 1.0（中性），
    // 既不改变观感，也避免 0 做除数。它必须真的被用到 —— 只声明不使用
    // 会被 GLSL 链接器优化掉，而 Forge 仍按 .json 注册它，Java 一侧 set()
    // 就会 NPE（见 rendertype_shockwave.fsh 里 uRingPosition 的同类事故）。
    if (uProgress > 0.001) {
        col *= 1.0 + 0.15 * sqrt(max(uProgress, 0.0));
    }

    // ── 9. 色调映射（原版配方，顺序不能换） ────────────────────────────
    //  曝光按变体分开给：
    //   · DF0 的自发光项都是"有结构的"（缝是暗的），不需要压太多，
    //     压过头会让这颗核心在夜里像块石头；
    //   · DF2 那支有几项会退化成常数（见上面 DF0/DF2 分支的注释），
    //     不压到 0.34 就会被 aces 推到饱和、把所有结构拍平。
    //  （这一条也是离屏实测出来的：不压曝光时不同时刻的两帧像素统计完全一样。）
#if defined(DF_CHROME)
    //  铬：反射本身已经是"物理量级"（环境 0.1~2.0），压太多会变成一块灰镜子。
    //  1.0 之后交给 aces；sqrt 那一步会再把中间调提亮一档。
    col *= 1.0;
#elif defined(DF_CRYSTAL)
    //  晶体：切面本身有明确明暗，但芯光是加性的，压太少会在 aces 之前
    //  就把棱线冲平；0.62 是"亮而仍有切面"的那一档（离屏对照过 0.6/0.85/1.1）。
    col *= 0.62;
#elif defined(DF0)
    col *= 0.95;
#else
    col *= 0.34;
#endif
    col = aces_approx(col);
    col = sqrt(col);

    // ── 10. 深度遮挡 ───────────────────────────────────────────────────
    //  命中点 → 视空间 → 用**投影矩阵正推**出它本该写进深度缓冲的值，
    //  再与深度纹理里那个值直接比较。
    //
    //  ⚠️ 这里刻意<b>不</b>做"从深度缓冲反解视空间 z"的线性化。
    //  那个写法（本仓库 rendertype_shockwave.fsh 里那一版）分子用的是
    //  ProjMat[3][2] - ProjMat[2][2]，而标准 GL 透视矩阵满足的是
    //      ndcZ = -(ProjMat[2][2] + ProjMat[3][2] / z_view)
    //  两式差一个 |(B-A)/B| ≈ 2·far 量级的系数 —— 结果是 zScene 被放大
    //  近一个数量级，比较永远不成立、整块效果被 discard 干净。
    //  离屏实测（build/shader-preview/CubePreview）：uHasDepth=1 时
    //  非黑像素 0/172800，正是这个原因。
    //
    //  正推只需要一次乘加，既没有反解的约定风险，也不依赖 far 的量级。
    if (uHasDepth > 0.5) {
        vec3 hitVS = toView(ro + rdW * tHit);
        // 相机前方 ⇒ hitVS.z < 0
        float ndcZ = -(ProjMat[2][2] + ProjMat[3][2] / min(hitVS.z, -1.0e-4));
        float dHit = ndcZ * 0.5 + 0.5;
        float dScene = texture(DepthTexture, gl_FragCoord.xy / vec2(textureSize(DepthTexture, 0))).r;
        // 命中点比场景更远 ⇒ 它在某个东西后面 ⇒ 不画。
        // 1e-5 的余量留给 SDF 渐近收敛与深度精度（否则会自遮挡闪烁）。
        if (dHit > dScene + 1.0e-5) discard;
    }

#endif  // CUBE_GRAD_PROBE

    fragColor = vec4(col, 1.0);
}
