#version 150 core

// 环形合成程序：把剪影遮罩膨胀成一个等宽硬边环，再叠一层「体积光雾」。
//
//   ring = dilate(mask, radius) − mask
//
// 这个减法就是全部关键：剪影内部 mask=1，膨胀后仍是 1，相减得 0 ——
// 所以描边永远只出现在物品外侧，绝不会盖住物品表面。
// 宇宙星空层画在物品表面上，因此它跟描边连一个像素都不会重叠。
//
// 视觉风格对齐原版发光实体描边：纯色、等宽、硬边。这里没有羽化、没有外发光。
// 唯一的模糊来自 smoothstep 提供的约 1 像素抗锯齿，以及遮罩自身的双线性采样。
//
// ── 光雾（HaloStrength > 0 时启用）──────────────────────────────
// 硬环保留，另外再叠一层由**降采样模糊过的遮罩**（outline_halo_blur.fsh 生成，
// 见 ItemOutlinePipeline.buildHaloChain）构成的雾，并把它用噪声打散成团块。
// 三个要点，少一个就会坏：
//   1. 雾必须减掉自身（max(halo − self, 0)）。模糊后的遮罩在剪影**内部**也等于 1，
//      不减掉整件物品会被糊上一层光 —— 在游戏里等于把物品贴图和 cosmic 星空层一起盖掉。
//   2. 雾的噪声坐标要以「物品的屏幕中心」为原点，否则镜头一动图案就像贴纸一样滑过物品。
//   3. 雾的采样偏移要按屏幕高宽比修正，否则横向位移会被拉长。

uniform sampler2D MaskSampler;    // 剪影遮罩（全分辨率，硬环用）
uniform sampler2D HaloHalf;       // 1/2 分辨率、已模糊的遮罩（中层雾）
uniform sampler2D HaloWide;       // 1/4 分辨率、已模糊的遮罩（大范围雾）

uniform vec2 ScreenSize;

// 环向外扩展的宽度，单位是像素。等宽就是靠它：与物品大小、远近无关。
uniform float OutlineWidth;

// 整体不透明度。
uniform float Opacity;

// ── 火焰光环（fire_2）──
// 物品剪影当火源，火焰画在**轮廓外**一片比物品大数倍的透明区域里（由管线的 scissor 放大）。
uniform sampler2D FireSpread;   // 1/4 分辨率、按物品尺寸放大的模糊剪影密度
                                // 0 = 远在轮廓外，0.5 = 轮廓上，1 = 剪影内部
uniform float FireStrength;     // 0 = 关闭（默认；所有非 fire_2 条目都是 0）
uniform vec4 FireRect;          // 该物品的可见包围盒（像素，y 向上），用来锚定噪声与"上"方向
uniform vec3 FireColorA;        // 主色（热核外侧的橙）
uniform vec3 FireColorB;        // 冷尾（深红 / 暗处）

// 遮罩覆盖率的判定阈值。遮罩是二值的，双线性采样后在边缘会得到
// 0~1 的中间值，阈值决定"算不算物品的一部分"。
uniform float AlphaTest;

// ── 光雾参数 ──
uniform vec2 HaloNoiseOrigin;   // 噪声坐标原点（该物品的屏幕中心，UV 空间）
uniform float HaloStrength;     // 0 = 关闭光雾，回到纯硬环
uniform float HaloNoise;        // 云团强度：把光滑的雾打散成团块
uniform float HaloWarp;         // 采样偏移幅度（1.0 ≈ 0.04 屏高）
uniform float Time;

// ── 电弧参数（ArcStrength > 0 时启用；见文件末尾的"电弧"一节）──
uniform vec2 ArcNoiseOrigin;    // 电弧噪声坐标原点（该物品的屏幕中心，UV 空间）
uniform float ArcStrength;      // 0 = 关闭电弧
uniform float ArcScale;         // 噪声尺度：越大细丝越密越短
uniform float ArcOffset;        // 等值线基准偏移（与 ArcThreshold 一起决定取哪条等值线）
uniform float ArcThreshold;     // 阈值：越小细丝越少越粗
uniform float ArcWidth;         // 已废弃（保留以免 JSON 失配）：线宽改用 ArcLine
uniform float ArcLine;          // 电弧线宽（np 单位 = 屏幕高；4px/1600 ≈ 0.0025）
uniform float ArcGain;          // 总增益
uniform float ArcSpeed;         // 流动速度
uniform float ArcReach;         // 已废弃（保留以免 JSON 失配）：见 ArcBandExtent
uniform vec2 ArcBandExtent;     // 电弧软椭圆带的半宽/半高（np 单位 = 屏幕高）
uniform float ArcAniso;         // 各向异性：把噪声沿**径向**拉长的倍率（>1 = 触须朝外）
uniform float ArcSub;           // 距离扣减：把连续等值线打断成段，并让远场变暗
uniform float ArcTonemap;       // >0.5 = 对电弧做 ACES+sqrt（白热核心的关键）
uniform vec3 ArcColorA;         // 细丝色 A
uniform vec3 ArcColorB;         // 细丝色 B

in vec2 texCoord;

out vec4 fragColor;

/** ACES 近似（Narkowicz）：把 1/|Δ| 的爆亮部分压成"亮到发白的芯"，而不是糊成色斑。 */
vec3 acesApprox(vec3 v) {
    v = max(v, vec3(0.0)) * 0.6;
    return clamp((v * (2.51 * v + 0.03)) / (v * (2.43 * v + 0.59) + 0.14), 0.0, 1.0);
}

const int RING_DIR_COUNT = 16;

const vec2 RING_DIRS[RING_DIR_COUNT] = vec2[RING_DIR_COUNT](
    vec2( 1.00000,  0.00000),
    vec2( 0.92388,  0.38268),
    vec2( 0.70711,  0.70711),
    vec2( 0.38268,  0.92388),
    vec2( 0.00000,  1.00000),
    vec2(-0.38268,  0.92388),
    vec2(-0.70711,  0.70711),
    vec2(-0.92388,  0.38268),
    vec2(-1.00000,  0.00000),
    vec2(-0.92388, -0.38268),
    vec2(-0.70711, -0.70711),
    vec2(-0.38268, -0.92388),
    vec2( 0.00000, -1.00000),
    vec2( 0.38268, -0.92388),
    vec2( 0.70711, -0.70711),
    vec2( 0.92388, -0.38268)
);

float coverageAt(vec2 uv) {
    return texture(MaskSampler, uv).a;
}

// 与 outline_mask.fsh 里的火焰噪声同一套（那边注释更详细）。之所以在这里再写一遍而不是
// #moj_import：离屏验证工具是直接把 .fsh 丢给驱动的，不做 MC 的预处理器，重复一份最省事也最稳。
float hash21(vec2 n) {
    n = mod(n, 64.0);
    return fract(sin(dot(n, vec2(12.9898, 12.1414))) * 83758.5453);
}

float vnoise(vec2 n) {
    const vec2 d = vec2(0.0, 1.0);
    vec2 b = floor(n);
    vec2 f = fract(n);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(b), hash21(b + d.yx), f.x),
               mix(hash21(b + d.xy), hash21(b + d.yy), f.x), f.y);
}

float fbm(vec2 n) {
    return vnoise(n) * 0.60 + vnoise(n * 2.3) * 0.28 + vnoise(n * 5.1) * 0.12;
}

/**
 * 火焰光环：物品轮廓是火源，火只在轮廓**外侧**那片放大区域里烧。
 *
 * <p>坐标以物品高度为 1 个单位（与分辨率、与距离无关）；"离轮廓多远"由
 * {@code FireSpread} 的模糊剪影密度给出（|2d−1|：0 = 轮廓上，1 = 模糊半径外）。</p>
 *
 * @return rgb = 火焰颜色，a = 覆盖度（加法混合下 r*g*a 就是发光量）
 */
vec4 fireAura(vec2 uv) {
    vec2 screen = max(ScreenSize, vec2(1.0));
    vec2 box = max(FireRect.zw, vec2(1.0));
    vec2 center = FireRect.xy + box * 0.5;
    // y 向上、1 个单位 = 物品可见高度；横向按高度归一化，图案不会被拉扁
    vec2 p = (uv * screen - center) / max(box.y, 1.0);

    // 剪影内部用**全分辨率遮罩**判定（模糊场在小物品上内部到不了 1，不能当 inside 用），
    // 离轮廓多远则用放大模糊后的密度：轮廓上密度最高、往外单调降到 0。
    float inside = smoothstep(0.25, 0.60, texture(MaskSampler, uv).a);
    float density = texture(FireSpread, uv).a;
    float dist = clamp(1.0 - density / 0.28, 0.0, 1.0);   // 0 = 轮廓附近，1 = 模糊半径外
    float outward = 1.0 - inside;                          // 只在轮廓外

    // 紧贴轮廓的连续底环：**不乘噪声/团块**。镜头转动时火舌会变，但这一圈永远贴着
    // 剪影边缘 —— 否则"边缘脱离一会儿"就是这里断开的观感。
    float rim = outward * (1.0 - smoothstep(0.0, 0.12, dist));

    // 贴边锚：用**全分辨率剪影**多向采样，保证最内圈每帧都连着物品轮廓。
    // 光雾那两张模糊图是 1/4 分辨率、线性放大的，物品移动（尤其开视角摇晃时）会
    // 有一两个纹素的量化跳动 —— 表现就是"边缘脱离一会儿"。这一圈不经过模糊图。
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float anchor = 0.0;
    if (outward > 0.001 && rim < 0.5) {
        float cover = 0.0;
        for (int i = 0; i < 8; i++) {
            float ang = float(i) * 0.7853982;
            vec2 dir = vec2(cos(ang), sin(ang));
            cover = max(cover, texture(MaskSampler, uv + dir * texel * 2.0).a);
            cover = max(cover, texture(MaskSampler, uv + dir * texel * 7.0).a);
        }
        anchor = outward * smoothstep(0.30, 0.55, cover);
    }

    float t = Time;
    // 两个尺度的上升噪声。纵向压扁 → 火舌是竖着的；减 t 让图案向上卷。
    vec2 q = vec2(p.x * 2.8, p.y * 1.5) + vec2(0.0, -t * 1.25);
    float n1 = fbm(q);
    float n2 = fbm(q * 2.5 + vec2(9.1, -t * 0.7));
    float n = n1 * 0.6 + n2 * 0.4;

    // 火舌能伸多远：噪声强的地方烧得更远；比上一版更厚（0.55~1.85 个模糊半径），
    // 再乘一个"上方更长"的偏置 —— 火是往上窜的，否则会是一个对称的圆环。
    float up = smoothstep(-0.5, 0.9, p.y);
    float reach = mix(0.55, 1.85, n) * mix(0.70, 1.35, up);
    float flame = outward * (1.0 - smoothstep(0.0, reach, dist));

    // 团块：把连续的一圈打散成一条条火舌；下限从 0.10 提到 0.28，
    // 让火带更"连"（太碎会显得时有时无）。
    float clump = smoothstep(0.06, 0.62, n1 * 0.62 + n2 * 0.58);
    flame *= mix(0.28, 1.0, clump);

    // 白热核：贴在轮廓上
    float core = outward * (1.0 - smoothstep(0.0, 0.10, dist));

    // 外层扩散：火舌之外再铺一层更淡、更远的暖雾（"扩散厚度"的主要来源）
    float haze = outward * smoothstep(0.10, 0.80, dist)
            * (1.0 - smoothstep(1.35, 1.95, dist)) * 0.28;

    // 颜色：冷尾 → 主色 → 白热
    float heat = clamp(core * 1.2 + flame * (0.45 + 0.75 * n2) + rim * 0.5, 0.0, 1.4);
    vec3 color = mix(FireColorB, FireColorA, smoothstep(0.04, 0.55, heat));
    color = mix(color, vec3(1.0, 0.94, 0.80), smoothstep(0.72, 1.15, heat));

    // 烟：火舌之外、往上的那一带，用冷色压暗（加法下只是很淡的暗红）
    float smoke = smoothstep(0.30, 0.95, p.y) * (1.0 - flame) * outward * fbm(q * 1.3 + 3.1) * 0.30;

    // 火星：网格随机点，跟着火一起上升；只在离轮廓不远的地方出现（否则会撒满整片绘制区）
    vec2 sp = vec2(p.x * 6.0, p.y * 6.0 - t * 2.6);
    vec2 cell = floor(sp);
    float rnd = hash21(cell + 19.7);
    float sparkOn = step(0.80, rnd);
    float sparkDot = smoothstep(0.40, 0.0, length(fract(sp) - 0.5));
    float sparkFalloff = 1.0 - smoothstep(0.20, 1.05, dist);
    float spark = sparkOn * sparkDot * outward * sparkFalloff * (0.45 + 0.55 * sin(t * 7.0 + rnd * 43.0));
    vec3 sparkColor = vec3(1.0, 0.55, 0.16) * spark;

    float alpha = clamp(flame * 1.35 + core * 1.10 + spark * 0.80 + rim * 0.85
            + haze * 0.55 + anchor * 1.25, 0.0, 1.0) * FireStrength;
    vec3 rgb = (color * max(flame, core) + sparkColor
            + vec3(1.0, 0.90, 0.72) * rim * 0.55
            + FireColorA * anchor * 0.9
            + FireColorA * haze * 0.65) / max(alpha, 1.0e-4);
    // 烟只加一点点色偏，不额外占覆盖度（否则会变成一大片灰雾）
    rgb += FireColorB * smoke;
    return vec4(rgb * alpha, alpha);
}

void main() {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float radius = max(OutlineWidth, 0.5);

    float selfCoverage = coverageAt(texCoord);

    // 圆盘采样：16 个方向 × 3 层半径。单圈采样在大半径下方向之间会
    // 留下空洞，补上内层半径就能填实，同时保持外缘正好落在 radius 上。
    float outerCoverage = 0.0;
    vec3 outerColor = vec3(1.0);
    for (int i = 0; i < RING_DIR_COUNT; i++) {
        vec2 dir = RING_DIRS[i];
        for (int r = 0; r < 3; r++) {
            float rr = radius * (0.45 + 0.275 * float(r));
            vec4 s = texture(MaskSampler, texCoord + dir * texel * rr);
            if (s.a > outerCoverage) {
                outerCoverage = s.a;
                outerColor = s.rgb;
            }
        }
    }

    // 约 1 像素宽的过渡带，只用来抗锯齿；不做更宽的羽化。
    float lo = AlphaTest;
    float hi = min(AlphaTest + 0.30, 1.0);
    float outer = smoothstep(lo, hi, outerCoverage);
    float inner = smoothstep(lo, hi, selfCoverage);

    // 硬边环 = 膨胀结果 − 自身。
    float ring = max(outer - inner, 0.0);

    // ── 光雾 ──
    vec3 haloColor = outerColor;
    float aura = 0.0;
    if (HaloStrength > 0.0) {
        float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
        // 以物品中心为原点的各向同性噪声坐标（单位 ≈ 1 个屏幕高）
        vec2 np = (texCoord - HaloNoiseOrigin) * vec2(aspect, 1.0);
        vec2 warp = vec2(fbm(np * 6.0 + 11.3), fbm(np * 6.0 + 27.9)) - 0.5;
        float detail = fbm(np * 17.0 + vec2(-Time * 0.10, Time * 0.07));

        // 按屏幕高宽比修正，横向偏移才不会被拉伸
        vec2 warpUv = warp * vec2(1.0 / aspect, 1.0);
        vec4 wide = texture(HaloWide, texCoord + warpUv * HaloWarp * 0.040);
        vec4 mid = texture(HaloHalf, texCoord + warpUv * HaloWarp * 0.018);
        float halo = max(wide.a * 0.80, mid.a);
        // ★ 只取溢出剪影之外的那部分 —— 见文件头第 1 条
        halo = max(halo - selfCoverage, 0.0);

        // 云团：噪声强的位置阈值低 → 雾延伸得更远，于是外缘一团一团
        float wisps = smoothstep(0.05, 0.35, halo * (0.45 + 1.2 * HaloNoise * (0.4 + detail)));
        aura = max(halo * 1.9, wisps) * HaloStrength;
        haloColor = mix(mid.rgb, wide.rgb, 0.5);
    }

    // ── 电弧（electric arcs）──
    //
    // 手法来自 Shadertoy 那套"闪电"（作者 mrange 一类）：**不把形状外扩**，而是取
    // **噪声场的等值线**：
    //
    //     col += 宽度 / |偏移 + 阈值 − fbm(p, t)|
    //
    // 噪声穿过阈值的地方分母趋零 → 爆亮成一缕细丝；噪声场本身在流动、在不同尺度上
    // 卷曲，于是细丝会分叉、会抖、触须忽远忽近。这和 dilate − self（几何等距偏移）
    // 是**正交**的两条路：偏移永远做不出分叉，反过来等值线也画不出等宽硬环。
    // 所以这里不是替换硬环，而是**叠**在硬环外面 —— 原版也是"边线 + 辉光 + 电弧"三层。
    vec3 arc = vec3(0.0);
    if (ArcStrength > 0.0) {
        float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
        // 与光雾同源：以物品中心为原点的各向同性坐标，电弧才会跟着物品走而不是滑过屏幕
        vec2 np = (texCoord - ArcNoiseOrigin) * vec2(aspect, 1.0);
        float t = Time * ArcSpeed;

        // reach：**锚在剪影上**，用光雾模糊图（可见剪影模糊出来的）当距离代理。
        //
        // ★ 这里走过一次弯路，记下来：我一度改成"以物品包围盒为基准的软椭圆带"
        //   （想做更宽的完整电弧带），结果电弧铺满了包围盒周围的空白 —— 剑在盒子里是
        // ── 外边界：软椭圆带（ArcBandExtent > 0 时）──────────────────────────
        //
        // ★ 用户给了关键澄清（对照参考 shader）：参考图里的电弧**不是一个"边"**，
        //   而是**铺满整个形状区域**的混沌场（爱心内部也全是电弧），轮廓只是它的外边界。
        //   所以这里不再要求"电弧只能在剪影外"，也不再死贴轮廓：
        //     · 外边界 = 以物品包围盒为基准的**软椭圆**（不是矩形框，所以不会在盒子
        //       四角出现"空白区一团圈圈"的诡异感）；
        //     · 允许铺到物品身上（下面不再乘 (1 - inner)）。
        //   早先那版"包围盒椭圆带"之所以难看，是因为当时 reach 被写成了 0（完全不限），
        //   于是填满了整个**矩形剪裁框**；椭圆带本身是对的。
        float reach = 1.0;
        if (ArcBandExtent.x > 0.0 && ArcBandExtent.y > 0.0) {
            float r = length(np / ArcBandExtent);
            reach = 1.0 - smoothstep(0.70, 1.12, r);
        } else if (HaloStrength > 0.0 && ArcReach > 0.0) {
            // ── 跨界带：以**轮廓**为中心，内外各覆盖一小段 ──
            //
            // 模糊过的剪影天然是一个**有符号距离代理**：
            //     h = 0   远在外侧
            //     h = 0.5 **恰好落在轮廓上**
            //     h = 1   深入内部
            // 所以"到轮廓的归一化距离"就是 |2h − 1|，取补即"越靠轮廓越亮"。
            // 这样电弧是**骑在边缘上**的：外面伸出一段、里面也压进一段 ——
            // 正是"从边缘开始覆盖、只覆盖靠近边缘那一部分"。
            // （早先我先只做外侧、后来又只做内侧，两个方向都试错过。）
            float h = texture(HaloWide, texCoord).a;
            float distToEdge = abs(2.0 * h - 1.0);
            reach = 1.0 - smoothstep(0.0, ArcReach, distToEdge);
        }

        // 3 次迭代 = 3 组不同相位/流速的细丝互相叠加，避免"一层规则网格"的塑料感
        // ★ 只在**真的需要各向异性**时才走极坐标基。
        //
        //   踩过的坑：极坐标基（radial/tangential）是随位置旋转的，所以即使 ArcAniso = 1，
        //   把 np 投影到这个基上也**不是恒等变换** —— 噪声被"扭"了一下，梯度变大、
        //   等值线峰值变钝 → 整片电弧虚掉（实验台里几乎看不见）。
        //   ArcAniso≈1 时直接用 np，噪声才是各向同性的原始形态。
        vec2 stretch = np;
        if (ArcAniso > 1.05) {
            vec2 radial = normalize(np + vec2(1e-5));
            vec2 tangential = vec2(-radial.y, radial.x);
            // 拉长**切向**、径向保持 1 → 等值线变成从物品射出去的条
            //（反过来（拉长径向）得到的是同心圆环，实测如此）
            stretch = vec2(dot(np, radial), dot(np, tangential) * ArcAniso);
        }

        vec3 acc = vec3(0.0);
        for (int i = 0; i < 2; i++) {   // 2 组足够；3 组会叠成一片白
            float fi = float(i);
            // 两条不同尺度 + 不同上流速率的噪声：慢的那条负责长触须，快的那条负责抖
            float slow = fbm((stretch + vec2(0.0, -t * 0.62)) * ArcScale + fi * 7.3);
            float fast = fbm((stretch + vec2(0.0, -t * 0.34)) * (ArcScale * 0.60) + fi * 3.1);

            // ── 阈值：必须落在噪声的真实取值范围内 ──
            //
            // ★ 这就是"电弧恒为 0"的最后一块拼图。阈值原先写成
            //     thr = ArcThreshold + 0.08*(seg-0.5)   // 一个绝对值，如 0.15
            //   而 fbm 的实际取值范围是未知的：若它大致落在 [0.30, 0.70]，
            //   0.15 就**永远不可能被穿过** → |thr − n| 恒大于线宽 → `line` 恒为 0
            //   → **电弧项一点输出都没有**。这也解释了那个反常现象：两种完全不同的
            //   门配置渲染出**逐字节完全相同**的图（项本身是 0，门怎么改都看不出来）。
            //   旧写法 ArcWidth/|Δ| 之所以能看见，是因为它不截断：|Δ| 再大也留一点余辉，
            //   于是表现为"一大片糊掉的云"。改成按距离截断后，阈值就**必须**在范围内。
            //
            //   修法：阈值改用**同范围的另一个低频噪声本身**（seg）——两者一定会有
            //   相等的地方；再用 ArcThreshold 轻微牵引（25%），保留可调性而不破坏
            //   "一定能穿过"这个前提。
            float seg = fbm(stretch * 1.7 + fi * 11.0 + 3.0);
            float thr = mix(seg, ArcThreshold, 0.25);

            // ── 到等值线的**距离**，而不是 `宽度/|n−阈值|` ──
            //
            // 这是这一整轮踩的最深的坑：`ArcWidth / |n − thr|` 的**实际线宽 ≈ 2·ArcWidth/|∇n|**，
            // 而 |∇n| 正比于噪声频率 —— 于是"把 arcScale 调大让它更密"会**同时把线变细**，
            // 调到 30 时线已落进**亚像素**，屏幕上根本采不到 → 电弧整体消失（看着像"没有电弧"）。
            // 现在先算到等值线的距离 d = |n − thr| / |∇n|，再用**固定的屏幕宽度**去截：
            // 频率只决定线的**疏密**，不再决定**粗细**。|∇n| ≈ ArcScale × 0.9 是 fbm 的解析近似。
            float w1 = abs(ArcOffset + thr - slow) / max(ArcScale * 0.90, 0.001);
            float w2 = abs(ArcOffset + thr * 0.85 - fast) / max(ArcScale * 0.54, 0.001);
            // ★ edge 必须**递增**：GLSL 规范规定 smoothstep(edge0, edge1, x) 在
            //   edge0 >= edge1 时结果 **undefined** —— NVIDIA 的编译器会把它优化成恒 0，
            //   于是 `line` 恒为 0、电弧项精确为零（实测：两种完全不同的门配置渲染出
            //   逐字节相同的图，就是"项本身是 0"的铁证）。
            //   等价的标准写法：1.0 - smoothstep(0.0, 线宽, 距离)。
            float lineW = max(ArcLine, 0.0008);   // ★ 护栏：ArcLine=0 时 smoothstep(0,0,x) 是 UB（NVIDIA 上退化成 0）
            float line1 = 1.0 - smoothstep(0.0, lineW, w1);
            float line2 = 1.0 - smoothstep(0.0, lineW, w2);

            // 颜色相位：两条细丝略微错色，叠起来才是"电"而不是"发光描边"
            vec3 c = mix(ArcColorA, ArcColorB, fract(0.37 * fi + 0.13));
            acc += c * line1 * 0.55;
            acc += c * line2 * 0.20;   // 第二组只做点缀，避免叠成一团
            // 距离扣减（原版 sub 的思路，这里用乘法形式以免出现负值）：
            // 越往外越暗、越容易断 —— 远场收口，同时制造"火花够不到那么远"的层次
            acc *= 1.0 - clamp(length(np) * ArcSub * 0.34, 0.0, 0.55);
        }

        // ★ 关键的一步：电弧亮度要**随"离表面的距离"增长**。
        //
        //   原版是 `d1 = d(有符号距离) * glow / |Δ|` —— 贴着表面反而暗，等于把细丝
        //   "立"在表面之外。这是"电"而不是"一圈发光描边"的根本原因：等值线本身只是
        //   花纹，乘上距离权重之后才变成从表面射出去的触须。
        //   我们没有真 SDF，就用光雾场（**经过深度测试的剪影**模糊出来的）当距离代理：
        //   prox≈1 贴着表面 → outward≈0；prox→0 往外 → outward→1。
        //
        //   下限 0.40（早期是 0.10）是"贴着轮廓也要有电弧"的取舍：距离权重把细丝从
        //   表面推开之后，紧贴轮廓那一圈会空掉一段，看起来像"电弧飘在物品外面"。
        //   抬高下限 = 整圈都有电弧，代价是"电"的方向感弱一些。
        float outward = 1.0;
        if (HaloStrength > 0.0) {
            float prox = texture(HaloWide, texCoord).a;
            outward = mix(0.40, 1.0, smoothstep(0.0, 0.85, 1.0 - prox));
        }
        arc = acc * ArcGain * reach * outward * ArcStrength;

        // 不再乘 (1 - inner)：用户明确要求"轮廓内容也能蔓延到剑身上"，
        // 参考 shader 里电弧本来就是铺满形状内部的（爱心内部全是电弧）。
        // 唯一保留的约束是：外边界由上面的软椭圆带收口，不会糊满整个屏幕。

        // 色调映射（ACES 近似 + sqrt）：这一招才是"白热核心 + 彩色细丝"的关键 ——
        // 没有它，`1/d` 的爆亮部分会糊成一块饱和色斑，而不是亮芯细丝。
        // 注意只作用在电弧上，既有描边的颜色一个字节都不动。
        if (ArcTonemap > 0.5) {
            arc = sqrt(acesApprox(arc));
        }
    }

    float arcPeak = max(arc.r, max(arc.g, arc.b));

    // 环/雾/电弧这一路的覆盖度
    float ringAlpha = max(max(ring, aura) * Opacity, clamp(arcPeak * 3.0, 0.0, 1.0));

    // 火焰光环：轮廓外的火（FireStrength = 0 时这一整块不执行）
    float fireAlpha = 0.0;
    vec3 fireRGB = vec3(0.0);
    if (FireStrength > 0.0) {
        vec4 fire = fireAura(texCoord);
        fireAlpha = fire.a * Opacity;
        fireRGB = fire.rgb;
    }

    float alpha = max(ringAlpha, fireAlpha);
    if (alpha <= 0.002) {
        discard;
    }

    // 硬核优先、雾次之：两者都出现的地方按强度插值，避免交界处出现硬缝。
    float hardWeight = ring / max(ring + aura, 1.0e-4);
    // 电弧是加性的（自发光），所以直接加到结果上，不参与 ring/aura 的插值
    vec3 ringColor = mix(haloColor, outerColor, hardWeight) + arc;

    // 预乘加权合成：加法混合下最终贡献正好是 ringColor*ringAlpha + fireRGB*fireAlpha。
    float safeAlpha = max(alpha, 1.0e-4);
    vec3 color = ringColor * (ringAlpha / safeAlpha) + fireRGB * (fireAlpha / safeAlpha);
    fragColor = vec4(color, alpha);
}
