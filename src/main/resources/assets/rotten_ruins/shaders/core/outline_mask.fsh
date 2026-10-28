#version 150 core

// 剪影遮罩程序。
//
// 这个 pass 的唯一职责：把"这件物品在屏幕上占据哪些像素"以及"这些像素应该
// 用什么颜色描边"写成一张离屏遮罩贴图。它不画任何最终画面。
//
//   rgb   = 该物品本帧的描边颜色（颜色模式在这里就烘进去）
//   a     = 覆盖率，目前是二值的（0 或 1），靠 discard 保证只写不透明像素
//
// 之所以把颜色烘进遮罩，是因为环形合成 pass 是全屏一次性的：颜色随遮罩逐像素
// 携带，同一帧里不同物品就能有各自的描边颜色，而不需要按颜色分批重画。
// 同时这也修掉了旧实现的一个隐患 —— 旧代码把颜色设成共享 ShaderInstance 的
// uniform，同一批次里多个物品会互相覆盖，只有最后一个的颜色生效。

uniform sampler2D Sampler0;

// 主色 / 副色
uniform vec4 MaskColor;
uniform vec4 SecondaryColor;

// 0 = 纯色, 1 = 双色滚动, 2 = 彩虹, 3 = 色板滚动, 4 = 火焰(旧), 5 = fire_2(参考 shader)
uniform float ColorMode;

uniform float ColorScrollSpeed;
uniform float Time;

// 渐变的空间频率（模型空间单位）。默认 18/12 是给"手持/世界"用的：
// 物品在屏幕上占得大，所以那个波长看着是柔和的宽带。
// 物品栏图标只有 16px，同样的频率意味着一圈描边里过 3~4 次黑白 ——
// 于是管线在 GUI 上下文里把它调小（见 SplendidingConfig.guiOutlineGradientScale）。
uniform vec2 ColorFreq;

// 流动速度（弧度/秒）。GUI 会按 guiOutlineSpeedScale 放慢。
uniform float ColorSpeed;

// 暗端下限：0 = 纯黑；GUI 会抬到 0.35 左右。
// 纯黑遇上模型自带的黑描边会糊成一整块，抬高一点两端才都看得见。
// 火焰模式（4）里它同时是"火焰尾部有多亮"—— 0 = 只有几缕白热丝，0.35 = 整圈都泛着底光。
uniform float ColorFloor;

uniform float PaletteSize;
uniform vec4 PaletteColor0;
uniform vec4 PaletteColor1;
uniform vec4 PaletteColor2;
uniform vec4 PaletteColor3;
uniform vec4 PaletteColor4;
uniform vec4 PaletteColor5;
uniform vec4 PaletteColor6;
uniform vec4 PaletteColor7;

// 低于这个 alpha 的纹素不算物品的一部分。用来贴合物品真实形状，
// 否则模型四边形的矩形边界（含大片透明区）都会被算进剪影。
uniform float AlphaCutoff;

// 该物品在屏幕上的包围盒（像素，GL 约定：原点左下，y 向上）。
// 只有 ColorMode 5（fire_2）用它 —— 参考 shader 是按"整块画布"取坐标的，
// 描边这边要按物品自己的尺寸归一化，否则 16px 的板和 600px 的手持物观感完全不同。
uniform vec4 ItemRect;

// 贴图里"不透明内容"在方片上的归一化包围盒 {u0, v0, 宽, 高}（v 向上）。
// 物品方片通常带透明留白（法力板的贴图就是中间一条金属板），火焰必须从**可见内容**
// 的底边烧起来；用方片包围盒的话火会从一片空气里开始，整圈只剩灰烟。
uniform vec4 ItemOpaque;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 modelPos;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec4 paletteAt(int index) {
    if (index <= 0) return PaletteColor0;
    if (index == 1) return PaletteColor1;
    if (index == 2) return PaletteColor2;
    if (index == 3) return PaletteColor3;
    if (index == 4) return PaletteColor4;
    if (index == 5) return PaletteColor5;
    if (index == 6) return PaletteColor6;
    return PaletteColor7;
}

vec3 paletteScrollColor(vec2 uv) {
    int count = int(clamp(PaletteSize, 1.0, 8.0));
    float flow = fract(uv.x * 0.85 + uv.y * 0.55 - Time * 0.03 * ColorSpeed * ColorScrollSpeed) * float(count);
    int i0 = int(floor(flow)) % count;
    int i1 = (i0 + 1) % count;
    float blend = fract(flow);
    return mix(paletteAt(i0).rgb, paletteAt(i1).rgb, blend);
}

// ──────────────────────────────────────────────────────────────
//  ColorMode 4：火焰（内描边风格的噪声火）
//
//  移植自 "Inner outline shader for Match2 game cell"
//  （@remonvv，shadertoy.com/view/MdjfRK）。骨架全部保留：value noise、三层 fBm、
//  域扭曲、1/t 的过曝 ramp、以及末尾那次 tone mapping —— 出来的观感是
//  "白热的核心拖着一条深色的尾"。
//
//  两处必要改动：
//
//   1. 参考用 abs(uv.y - shift) 把空间折到"贴着格子边框的一条线"上，再拿
//      pow(2·r.y·离线距离 + .1, 4) 当作 ramp 的输入 —— 那条"线"是它自己造出来的。
//      我们的"线"由环形合成给出（ring = dilate − self，那条 alpha 衰减就是垂直于
//      线的衰减），所以这里直接把噪声强度当 ramp 输入：噪声弱 → 白热，噪声强 → 压暗。
//      配合 ICustomOutline 默认的加法混合，压暗的部分几乎不贡献亮度，剩下的就是
//      "深色底上爬着几缕白热火焰"——正是参考图的样子。
//
//   2. 噪声坐标用模型空间（与 gradient / 彩虹一致），不随相机漂移。直接把屏幕坐标
//      搬过来的话，它看起来会像贴在屏幕上的贴纸，镜头一转就"滑"过物品。
//
//  性能：每个片元约 36 次 sin 哈希（参考实现是它的 4 倍），而剪影遮罩只覆盖物品自身
//  的像素，所以代价与物品占屏面积成正比。嫌贵就删掉 fireField 的第三层八度。
// ──────────────────────────────────────────────────────────────

// 噪声晶格在这个周期上完全重复。有了它，hash 的输入永远被折回一个小范围 ——
// 否则 Time 一路涨上去（gameTime 封顶 240000 tick → Time 可到 12000）会把 sin 的参数
// 推到 1e6，哈希先变块状、再彻底冻住。64 格对 5~20 格大小的火焰来说完全看不出来。
const float FIRE_LATTICE = 64.0;

float hash21(vec2 n) {
    n = mod(n, FIRE_LATTICE);
    return fract(sin(dot(n, vec2(12.9898, 12.1414))) * 83758.5453);
}

float valueNoise(vec2 n) {
    const vec2 d = vec2(0.0, 1.0);
    vec2 b = floor(n);
    vec2 f = fract(n);
    // 参考用线性插值；这里换 smoothstep —— 线性插值在 16px 的物品栏图标上
    // 会露出晶格网格纹，smoothstep 的块状感轻得多。
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(b), hash21(b + d.yx), f.x),
               mix(hash21(b + d.xy), hash21(b + d.yy), f.x), f.y);
}

float fireField(vec2 n) {
    return valueNoise(n) + valueNoise(n * 2.1) * 0.6 + valueNoise(n * 5.4) * 0.42;
}

vec3 fireColor(vec2 p) {
    float t = Time * ColorSpeed * ColorScrollSpeed;

    // 噪声频率沿用 ColorFreq.x（世界/手持下恒为 18 → 系数 1）。GUI 那边
    // guiOutlineGradientScale 会把它一起压小，于是 16px 图标上的火焰自动变粗，
    // 不会糊成一团噪点。
    float freq = max(ColorFreq.x / 18.0, 0.05);
    // 参考里的 uv.x += 23.0（上下半格还各有一个方向相反的错位）。这里只留一个固定错位：
    // 那个 ±23/-11 的分支是为了让格子的上下两条火线不同相，会在 uv.y = 0.5 处留下一道硬缝；
    // 我们的"火线"是物品的整圈剪影，那道缝正好落在物品腰线上，会看出接缝。
    vec2 uv = p * 5.0 * freq + vec2(23.0 + t * 0.35, 0.0);

    float q = fireField(uv - t * 0.013) * 0.5;                                // 域扭曲
    // 参考里算出 r.x 却从来没用过，这里直接省掉一路 fireField（省 1/3 的哈希）。
    float n = fireField(uv + q - t);

    // ★ 与参考最大的不同在这里。
    //
    // 参考的亮度来自它自造的那条线：grad = pow(2·n·离线距离 + .1, 4)，在线上的
    // "离线距离"≈0，于是 grad ≈ 1e-4，被 ramp 的 1/t 推成过曝白热；离线越远越暗。
    // 而我们的"离线距离"是由环形合成的 alpha 承担的（ring = dilate − self），
    // 所以颜色这一路必须整体落在"热"的一侧 —— 直接把噪声丢进 1/t 的 ramp 的话，
    // 结果是"一片近乎全黑的底上零星几个亮斑"（离屏渲染验证过），描边会看不见。
    //
    // 于是把 ramp 重新锚定在噪声上：噪声驱动 heat 在 冷尾 ↔ 白热核 之间摆动，
    // 得到"一条着火的边，火苗沿着边走、偶尔断开"的观感。
    float heat = clamp((1.05 - n) * 2.2 + 0.30, 0.0, 1.0);

    vec3 core = MaskColor.rgb * 1.15 + 0.28;               // 白热核：主色提亮
    vec3 hot = mix(core, vec3(1.0), 0.55 * heat * heat);   // 越热越白，别只有纯色
    vec3 color = mix(SecondaryColor.rgb, hot, heat);
    color /= (1.0 + 0.25 * color);                         // 参考末尾那次 tone mapping 的轻量版
    return max(color, vec3(ColorFloor));
}

// ──────────────────────────────────────────────────────────────
//  ColorMode 5：fire_2 —— 参考 shader 版的「燃烧描边」
//
//  移植自 Shadertoy「fire」参考实现：Ashima/webgl-noise 的 3D simplex、
//  上升平流（timing/flow）、域扭曲（displace）、火焰 ramps（1-t^3）^8、
//  烟（smoke）与火星（sparks）。**噪声与配色照搬**，只换坐标系与两处工程适配。
//
//  两处与参考的偏差（都是为了"这是一条描边，不是一张火焰贴图"）：
//
//   1. 坐标从"整块画布"改成"该物品的屏幕包围盒"（uniform ItemRect）。
//      参考用 fragCoord/resolution，火是画满屏幕的；描边的可见像素只有物品轮廓
//      外那 2~3 像素的带子，所以必须让图案按物品尺寸归一化 —— 否则 16px 的板
//      和 600px 的手持物会得到完全不同的密度。
//   2. 补一层极暗的余烬底色（MaskColor × fuel）。参考里的"黑"是背景色，而描边带
//      上的黑在加法混合下等于"没有描边"，整条边会随火焰纹路一闪一灭。
//
//  代价：每个片元约 10~12 次 3D simplex（参考实现本身就是重活）。剪影遮罩只覆盖
//  物品自身像素，所以与物品占屏面积成正比；嫌贵就把 noiseStack 的 3 阶降到 2 阶。
// ──────────────────────────────────────────────────────────────

// ── Ashima/webgl-noise 3D simplex ──
// Copyright (C) 2011 Ashima Arts / Ian McEwan, MIT License
// https://github.com/ashima/webgl-noise
vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 mod289(vec4 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 permute(vec4 x) {
    return mod289(((x * 34.0) + 1.0) * x);
}

float snoise(vec3 v) {
    const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy;
    vec3 x3 = x0 - D.yyy;

    // Permutations
    i = mod289(i);
    vec4 p = permute(permute(permute(
                i.z + vec4(0.0, i1.z, i2.z, 1.0))
            + i.y + vec4(0.0, i1.y, i2.y, 1.0))
            + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    // Gradients: 7x7 points over a square, mapped onto an octahedron.
    float n_ = 0.142857142857;   // 1.0/7.0
    vec3 ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);   // mod(p, 7*7)

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);                // mod(j, N)

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    // Normalise gradients
    vec4 norm = inversesqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

// PRNG（from https://www.shadertoy.com/view/4djSRW）
float prng(in vec2 seed) {
    seed = fract(seed * vec2(5.3983, 5.4427));
    seed += dot(seed.yx, seed.xy + vec2(21.5351, 14.3137));
    return fract(seed.x * seed.y * 95.4337);
}

const float PI = 3.1415926535897932384626433832795;

float noiseStack(vec3 pos, int octaves, float falloff) {
    float noise = snoise(pos);
    float off = 1.0;
    if (octaves > 1) {
        pos *= 2.0;
        off *= falloff;
        noise = (1.0 - off) * noise + off * snoise(pos);
    }
    if (octaves > 2) {
        pos *= 2.0;
        off *= falloff;
        noise = (1.0 - off) * noise + off * snoise(pos);
    }
    if (octaves > 3) {
        pos *= 2.0;
        off *= falloff;
        noise = (1.0 - off) * noise + off * snoise(pos);
    }
    return (1.0 + noise) / 2.0;
}

// diff 在参考实现里也没用到，保留参数只为与参考逐行对照。
vec2 noiseStackUV(vec3 pos, int octaves, float falloff, float diff) {
    float displaceA = noiseStack(pos, octaves, falloff);
    float displaceB = noiseStack(pos + vec3(3984.293, 423.21, 5235.19), octaves, falloff);
    return vec2(displaceA, displaceB);
}

/**
 * @param fragPx 当前片元的窗口像素坐标（gl_FragCoord.xy）。
 *               ItemRect 是管线在提交时算好的该物品屏幕包围盒（像素，y 向上）。
 */
vec3 fire2Color(vec2 fragPx) {
    float time = Time * ColorScrollSpeed;
    float realTime = 0.5 * time;   // 参考的 timeSpeed = 0.5

    vec2 quadSize = max(ItemRect.zw, vec2(1.0));
    vec2 box = clamp(ItemOpaque.xy, vec2(0.0), vec2(1.0));
    vec2 boxSize = clamp(ItemOpaque.zw, vec2(1.0e-4), vec2(1.0));
    vec2 size = max(quadSize * boxSize, vec2(1.0));
    vec2 local = fragPx - ItemRect.xy - box * quadSize;

    float xpart = clamp(local.x / size.x, 0.0, 1.0);
    float ypart = clamp(local.y / size.y, 0.0, 1.0);

    // 燃料区 = 物品的可见高度（fill 之后整块剪影都在烧，不再只是"底部一条火线"）。
    const float FUEL_HEIGHT = 1.0;
    float fuel = ypart / FUEL_HEIGHT;
    float fuelClip = min(fuel, 1.0);                   // 参考的 ypartClipped
    float fuelClipInv = 1.0 - fuelClip;                // 参考的 ypartClippedn
    float fuelFalloff = clamp(2.0 - fuel, 0.0, 1.0);   // 参考的 ypartClippedFalloff

    // 参考写的是 1.0-|2x-1|（注释里也试过 pow(...,0.5)）。这里用 0.5 次幂把两侧
    // 抬起来 —— 直接压到 0 的话，描边带在物品左右两端会整段没有火。
    float xfuel = pow(max(1.0 - abs(2.0 * xpart - 1.0), 0.0), 0.5);

    // 噪声坐标：以物品高度为 1 个单位（与分辨率无关），并做宽高比修正。
    vec2 p = vec2(local.x / size.y, local.y / size.y);
    vec3 position = vec3(p * 2.0, 0.0) + vec3(1223.0, 6434.0, 8425.0);

    vec3 flow = vec3(4.1 * (0.5 - xpart) * pow(fuelClipInv, 4.0),
                     -2.0 * xfuel * pow(fuelClipInv, 64.0),
                     0.0);
    vec3 timing = realTime * vec3(0.0, -1.7, 1.1) + flow;

    vec3 displacePos = vec3(1.0, 0.5, 1.0) * 2.4 * position + realTime * vec3(0.01, -0.7, 1.3);
    vec3 displace3 = vec3(noiseStackUV(displacePos, 2, 0.4, 0.1), 0.0);

    vec3 noiseCoord = (vec3(2.0, 1.0, 1.0) * position + timing + 0.4 * displace3);
    float noise = noiseStack(noiseCoord, 3, 0.4);

    // 参考的 ramps。pow 的指数在 xfuel=0 时是 0 —— GLSL 里 pow(0,0) 未定义，
    // 所以底数/指数都加了护栏（视觉上仍然是"两侧没有火苗"）。
    float exponent = max(0.3 * xfuel, 1.0e-3);
    float flames = pow(max(fuelClip, 0.0), exponent) * pow(max(noise, 1.0e-4), exponent);
    // 参考是 ^8：火被压成很细的亮丝（在 1080p 的整块画布上没问题）。
    // 这里描边/填充的可见面积只有物品那么大，^8 会让 90% 的剪影接近全黑，
    // 因此放松到 ^4 并整体提亮 —— 结构（白热底 → 橙 → 深红、噪声驱动的火舌）不变。
    float f = fuelFalloff * pow(max(1.0 - flames * flames * flames, 0.0), 4.0);
    float fff = f * f * f;
    vec3 fire = 2.2 * vec3(f, fff, fff * fff);

    // 烟
    //   参考里烟是中性灰、用于在黑色背景上"压暗"；描边这边是加法混合，灰会变成白光。
    //   所以保留参考的强度与形状，但用物品主色染色 —— 观感是暖烟，光雾也才会是橙色。
    float smokeNoise = 0.5 + snoise(0.4 * position + timing * vec3(1.0, 1.0, 0.2)) / 2.0;
    vec3 smoke = MaskColor.rgb
            * (0.3 * pow(xfuel, 3.0) * pow(ypart, 2.0) * (smokeNoise + 0.4 * (1.0 - noise)));

    // 火星
    float sparkGridSize = max(4.0, size.y * 0.143);       // 参考 30px / 210px 火高
    float sparkNoiseScale = 2.1 / size.y;                 // 参考 0.01 * 210px
    vec2 sparkCoord = local - vec2(0.0, size.y * 0.9 * realTime);
    sparkCoord -= size.y * 0.143 * noiseStackUV(vec3(sparkCoord * sparkNoiseScale, 30.0 * time), 1, 0.4, 0.1);
    sparkCoord += size.y * 0.95 * flow.xy;

    if (mod(sparkCoord.y / sparkGridSize, 2.0) < 1.0) {
        sparkCoord.x += 0.5 * sparkGridSize;
    }
    vec2 sparkGridIndex = floor(sparkCoord / sparkGridSize);
    float sparkRandom = prng(sparkGridIndex);
    float sparkLife = min(10.0 * (1.0 - min(
            (sparkGridIndex.y + size.y * 0.9 * realTime / sparkGridSize) / (24.0 - 20.0 * sparkRandom),
            1.0)), 1.0);

    vec3 sparks = vec3(0.0);
    if (sparkLife > 0.0) {
        float sparkSize = xfuel * xfuel * sparkRandom * 0.08;
        float sparkRadians = 999.0 * sparkRandom * 2.0 * PI + 2.0 * time;
        vec2 sparkCircular = vec2(sin(sparkRadians), cos(sparkRadians));
        vec2 sparkOffset = (0.5 - sparkSize) * sparkGridSize * sparkCircular;
        vec2 sparkModulus = mod(sparkCoord + sparkOffset, vec2(sparkGridSize)) - 0.5 * vec2(sparkGridSize);
        float sparkLength = length(sparkModulus);
        float sparkRadius = max(sparkSize * sparkGridSize, 1.0e-3);   // 参考没有护栏，这里补一个
        float sparksGray = max(0.0, 1.0 - sparkLength / sparkRadius);
        sparks = sparkLife * sparksGray * vec3(1.0, 0.3, 0.0);
    }

    vec3 color = max(fire, sparks) + smoke;

    // 余烬底色（描边版的必要偏差，见本节开头第 2 点）：
    //   参考的 ramps 是 ^8 的高对比，只留很细的亮丝；而描边的可见部分只有轮廓外
    //   2~3 像素的环，整圈全靠亮丝撑的话会"一闪一灭、看不出在烧"。
    //   所以沿整条轮廓垫一层随噪声起伏的余烬，火苗只是在它上面更亮的部分。
    //   底边（fuelFalloff=1）最亮、顶部收暗，保留参考的"从下往上烧"的层次。
    vec3 ember = MaskColor.rgb
            * (0.10 + 0.12 * xfuel)
            * mix(0.50, 1.0, fuelFalloff)
            * (0.80 + 0.50 * noise);
    return max(color, ember);
}

vec3 resolveColor(vec2 uv, vec2 fragPx) {
    if (ColorMode < 0.5) {
        return MaskColor.rgb;
    }
    float flow = uv.x * ColorFreq.x + uv.y * ColorFreq.y - Time * ColorSpeed * ColorScrollSpeed;
    if (ColorMode < 1.5) {
        float m = 0.5 + 0.5 * sin(flow);
        m = ColorFloor + (1.0 - ColorFloor) * m;      // 抬高暗端：别让黑半段吃掉整圈
        return mix(MaskColor.rgb, SecondaryColor.rgb, m);
    }
    if (ColorMode < 2.5) {
        float hue = fract(uv.x * 0.22 + uv.y * 0.14 - Time * 0.02 * ColorSpeed * ColorScrollSpeed);
        return hsv2rgb(vec3(hue, 0.85, 1.0));
    }
    if (ColorMode < 3.5) {
        return paletteScrollColor(uv);
    }
    if (ColorMode < 4.5) {
        return fireColor(uv);
    }
    return fire2Color(fragPx);
}

void main() {
    float texAlpha = texture(Sampler0, texCoord0).a;
    if (texAlpha < AlphaCutoff) {
        discard;
    }

    // 遮挡判定不在这里做。
    //
    // 管线在画剪影之前，把"这件物品当时画进的那个 framebuffer 的深度附件"
    // 临时挂到遮罩 FBO 上，用**硬件深度测试**（LEQUAL + 不写深度，见
    // OutlineMaskRenderType）来剔除被挡住的部分。这样完全不需要知道那份深度
    // 是什么格式/什么约定 —— 光影包的 GBuffer 深度是纹理还是 renderbuffer、
    // 是不是 reversed-Z、far plane 有没有被改，全都无所谓。

    // 保持不受全局 shaderColor / ColorModulator 影响：原版发光描边是
    // 纯色不带光照的，这样在洞穴里也不会变暗。
    vec3 color = resolveColor(modelPos.xy, gl_FragCoord.xy);
    fragColor = vec4(color, 1.0);
}
