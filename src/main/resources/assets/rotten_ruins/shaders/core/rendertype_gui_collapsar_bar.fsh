#version 150 core

// ─────────────────────────────────────────────────────────────
//  坍缩使徒 boss 血条的「血量内容」着色器
//
//  目标观感：**纯黑底 + 彩色星尘**（与物品上的 cosmic 星空同一套宇宙语言），
//  但这里的星尘是程序化生成的，不采样星空图集 —— 血条是 GUI 元素，
//  没有物品 UV，也不该依赖方块图集。
//
//  三件事：
//   1. mask 贴图的**红通道**决定"哪里是血条内部"（与 void_sword_mask 同一套约定：
//      白 = 显示，黑 = 不显示）。所以美术只要改 mask，条形的形状/粗细就跟着变，
//      代码里没有任何硬编码的像素几何。
//   2. 背景恒为纯黑（vec3(0.0)），星尘叠在黑底上；条外 alpha=0，完全透明，
//      把 frame（outline）之外的天空让出来。
//   3. uFill = 血量比例：星尘只在填充区出现，条内的黑底仍然铺满整条，
//      于是"空血"部分读起来是那条黑色空槽，而不是一片透明。
// ─────────────────────────────────────────────────────────────

uniform sampler2D MaskTexture;

uniform float uTime;          // 秒（Java 侧用毫秒取模，暂停时也继续流动）
uniform float uFill;          // 血量比例 0..1
uniform float uAlpha;         // 整体不透明度
uniform float uStarStrength;  // 星尘亮度倍数
uniform float uStarDensity;   // 星尘密度倍数
/**
 * 低血颤抖强度（0~1，由 Java 按血量算好）。
 * <p>整条的位移由 Java 侧负责（槽/内容/框架一起挪）；这里只管"内容躁动"：
 * 给星尘采样坐标加高频抖动 + 叠一层心跳式脉动亮度。
 * 两层星用同一个抖动量，否则不同尺度会互相错开成两团。</p>
 */
uniform float uShake;
uniform float uBarAspect;     // 条形宽高比（182/32），用来把星点画成圆的而不是椭圆
/**
 * 几何兜底带（UV 空间，y 方向）与开关。
 * <p>血条内部本来完全由 mask 的红通道决定。但一旦 mask 采样失败（纹理没绑上、图被改坏），
 * {@code band} 会整条为 0，观感就是"框架在、里面全空" —— 这是排查成本极高的一种静默失败。
 * 所以 Java 侧会<b>预先读一遍 mask PNG</b>，算出亮像素所占的行范围传进来；
 * 真的读不到 / 整张全黑（{@code uUseGeomBand = 1}）时，就退回按这个几何带画，
 * 宁可位置是估的，也不要屏幕上什么都没有。</p>
 */
uniform float uBandMin;
uniform float uBandMax;
uniform float uUseGeomBand;

in vec2 texCoord;
out vec4 fragColor;

#define TWO_PI 6.28318530718

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    return vec2(hash21(p), hash21(p + 19.19));
}

/**
 * 一层星尘。
 *
 * <p>做法是标准的"格子撒点"：把坐标按 scale 分格，每格用哈希决定
 * 是否放星（density）、放在格子里的哪个位置、什么色相、什么闪烁相位。
 * 邻域遍历 3×3 是为了让靠近格边的星点也能画出完整的圆，不会在格线上被切掉。</p>
 *
 * <p><b>星点尺寸必须按"格换算成像素"来定，这是第一版踩过的坑</b>：
 * 格子的物理边长 = {@code 32 / scale} 个 GUI 像素（条高 32 GUI 像素），
 * 而下面 core/glow 的半径单位是"格"。第一版 scale=34 ⇒ 1 格 ≈ 0.94 像素，
 * 再配上 0.085 格的核半径 ⇒ 星芯只有 <b>0.08 像素</b>：
 * 只有片段中心恰好落进星心 0.08px 才亮，整条血条只剩几个零星亮点，
 * 观感就是"血条是空的"。现在只用 scale = 4.5 / 9 两档（1 格 ≈ 7.1 / 3.6 像素），
 * 核半径 0.20 格 ⇒ 直径 1.4~2.8 像素，是实打实看得见的圆点。
 * 以后要加层，请先算 {@code 0.2 * 32 / scale} 是否 ≥ 0.7 像素。</p>
 */
vec3 starLayer(vec2 uv, float t, float scale, float density, float drift) {
    // 乘宽高比让格子是正方形：否则 182×32 的条会让星点被横向拉长 5.7 倍
    vec2 p = vec2(uv.x * uBarAspect, uv.y) * scale;
    p.x -= t * drift;

    vec2 id = floor(p);
    vec2 gv = fract(p) - 0.5;

    vec3 acc = vec3(0.0);
    for (int i = 0; i < 9; i++) {
        vec2 o = vec2(float(i % 3) - 1.0, float(i / 3) - 1.0);
        vec2 cid = id + o;
        float on = hash21(cid);
        if (on > density) continue;

        vec2 sp = (hash22(cid + 3.7) - 0.5) * 0.62;     // 星点在格内的随机位置
        float d = length(gv - o - sp);

        // 闪烁：每颗星自己的频率与相位。用 0.5+0.5*sin 保证恒为非负 ——
        // 0.45+0.55*sin 会掉到 -0.1，负亮度虽然会被帧缓冲夹掉，
        // 但会让一部分星点整段时间完全不亮，观感是"有些位置永远没星"。
        float phase = hash21(cid + 8.1) * TWO_PI;
        float twinkle = 0.5 + 0.5 * sin(t * (1.2 + 4.0 * hash21(cid + 4.4)) + phase);

        float core = smoothstep(0.360, 0.150, d);        // 芯：亮盘半径约 0.36 格 ≈ 1.3px，实心部分 0.15 格
        // 晕：半径约 0.50 格。密度拉高后 3×3 邻域的晕会互相重叠叠加，
        // 系数从 0.35 降到 0.18，否则整条会被"糊亮"成灰带而不是一颗颗彩点。
        float glow = smoothstep(0.500, 0.000, d) * 0.18;

        // 彩色：色相由哈希决定，转成 RGB（保证每颗星颜色不同，而不是统一白）
        vec3 col = 0.55 + 0.45 * cos(TWO_PI * (vec3(0.0, 0.33, 0.67) + hash21(cid + 1.3)));

        acc += col * (core + glow) * twinkle;
    }
    return acc;
}

void main() {
    // ① 血条内部：mask 红通道（白=内部、黑=外部）。采样不到就退回几何带。
    float maskR = texture(MaskTexture, texCoord).r;
    float maskBand = smoothstep(0.35, 0.62, maskR);

    float geomBand = smoothstep(uBandMin - 0.015, uBandMin + 0.015, texCoord.y)
                   * (1.0 - smoothstep(uBandMax - 0.015, uBandMax + 0.015, texCoord.y));
    float band = max(maskBand, geomBand * uUseGeomBand);
    if (band <= 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    // ② 填充裁切：1 像素级软边，避免移动时锯齿
    float filled = smoothstep(uFill + 0.006, uFill - 0.006, texCoord.x);

    // ③ 星尘：两层不同尺度/速度，大颗慢、小颗密
    //    scale 决定格子大小：格子边长 = 32/scale 个 GUI 像素
    //      scale=9  → 格 ≈ 3.6px，星点直径 ≈ 1.4px（密集小星）
    //      scale=4.5 → 格 ≈ 7.1px，星点直径 ≈ 2.8px（稀疏大星）
    //    最后一个参数是"有多少格子里有星"的基准密度（0~1，1 = 每格都有），
    //    uStarDensity 是它的整体倍数 —— 想更浓就调基值或 uStarDensity。
    //    当前基值 0.80 / 0.70：条带上大约 68 颗小星 + 15 颗大星，是"浓密星云"的稠度。
    // 低血颤抖：星尘采样坐标的高频抖动（0.006 UV ≈ 1.1px 横向、0.02 ≈ 0.64px 纵向），
    // 幅度乘 uShake —— 满血时为 0，完全不抖。
    vec2 shakeUv = texCoord
                 + vec2(sin(uTime * 47.0), sin(uTime * 61.0 + 1.3)) * vec2(0.006, 0.020) * uShake;

    vec3 stars = starLayer(shakeUv, uTime, 9.0, 0.80 * uStarDensity,  0.55)
               + starLayer(shakeUv, uTime, 4.5, 0.70 * uStarDensity, -0.22) * 1.25;

    // 条带纵向中段更亮：黑底上一条"实心的能量带"，边沿自然变薄
    float vertical = 1.0 - 0.55 * abs(texCoord.y - 0.5) * 2.0;
    // 注意：这里只乘亮度（uStarStrength），不再乘 uStarDensity ——
    // 密度已经在上面进 density 参数了，乘两遍会让"密度"这个旋钮同时改亮度。
    stars *= filled * uStarStrength * vertical;
    // 低血脉动：约 1.4Hz 的心跳式闪烁，越危险跳得越明显
    stars *= 1.0 + 0.55 * uShake * (0.5 + 0.5 * sin(uTime * 9.0));

    // ④ 填充前缘的一点冷白亮边，让"血条边界"读得出来
    float edge = (1.0 - smoothstep(0.0, 0.010, abs(texCoord.x - uFill))) * filled * 0.35;

    // 纯黑底 + 彩色星尘（条外透明，交给 frame 与天空）
    vec3 col = vec3(0.0) + stars + vec3(0.85, 0.92, 1.0) * edge;
    fragColor = vec4(col, band * uAlpha);
}
