#version 150 core

// 裂纹（fracture）—— 框架效果 "fracture" 的合成 pass。
//
// ── 这一支和电弧/火焰的根本差别：它是**纯几何**的 ──
//   电弧 = 噪声等值线（乱麻里找线）
//   火焰 = 有状态的平流场（形状由历史决定）
//   裂纹 = **Voronoi 单元边界**（|F2−F1| → 0 的地方就是裂缝）
// 三者用的是三种不同的数学，所以观感互不重叠 —— 这正是"换数学"而不是"换笔刷"。
//
// ── 为什么裂纹不能用描边那套做 ──
// Voronoi 边界是**分叉的直线段**（像玻璃碎裂），而描边是"形状的等距偏移"：
// 偏移可以做粗做细、做软做硬，但永远做不出**分叉**。这是结构性的差别。
//
// ── uniform 声明必须与 fracture_composite.json 的 count 一一对应 ──
// （MC 的 JSON uniforms 只创建 Uniform 对象，**不会**给 GLSL 注入声明；漏一行整个 program 编译失败）

uniform sampler2D Mask;         // 全分辨率剪影：裂纹只出现在物品身上
uniform sampler2D Edge;         // 1/8 分辨率模糊剪影：到轮廓的距离代理
                                //   h=0 远在外侧 / h=0.5 恰在轮廓上 / h=1 深入内部

uniform vec2 ScreenSize;
uniform vec2 NoiseOrigin;       // 物品屏幕中心（UV）：退化时的兜底原点
uniform vec3 CrackColor;
uniform float Time;
uniform float CrackScale;       // 单元尺寸（**物品局部单位**里每单位多少裂块）
uniform float CrackWidth;       // 裂纹线宽（np 单位 = 屏幕高）

// ── 物品局部坐标（框架级能力）────────────────────────────────────────
// 把物品模型空间的 X / Y 基向量投影到屏幕 UV，于是"屏幕 UV → 物品局部坐标"
// 就是解一个 2×2 线性方程。**图案必须求值在这个坐标里**，否则镜头一动，
// 裂纹就会从物品上滑过去（用户实测："不同视角下裂纹在物品上的位置不同"）。
// 屏幕 UV 只适合"挂在屏幕上的东西"（比如光晕），不适合"贴在物体表面上的图案"。
uniform vec2 ItemOrigin;        // 物品模型原点投影到屏幕 UV
uniform vec2 ItemBasisX;        // 模型 +X 单位向量在屏幕 UV 上的位移
uniform vec2 ItemBasisY;        // 模型 +Y 单位向量在屏幕 UV 上的位移
uniform float CrackGlow;        // 裂缝辉光强度
uniform float CrackGlowWidth;   // 辉光宽度倍率（相对线宽）
uniform float CrackReach;       // 裂纹最深能推进到多深（0=只在轮廓上，1=贯穿）
uniform float CrackProgress;    // 0..1 进度：裂纹从**边缘往内**推进到哪
uniform float CrackSeed;        // 种子（同一件物品每次碎裂图案不同）

in vec2 texCoord;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    float n = hash21(p);
    return vec2(n, hash21(p + n + 17.3));
}

/**
 * 抖动网格 Voronoi：返回 (F1, F2)。
 *
 * <p>F2 − F1 在单元**边界**上趋近 0，在单元内部是个正数 —— 所以
 * {@code 1 − smoothstep(0, 线宽, F2−F1)} 就是一条贴着边界的等宽缝，
 * 而且这些缝天然**首尾相连、彼此分叉**（碎裂的观感就来自这里）。</p>
 */
vec2 voronoi(vec2 p) {
    vec2 ip = floor(p);
    vec2 fp = fract(p);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = hash22(ip + g + CrackSeed);
            float d = length(g + o - fp);
            if (d < f1) {
                f2 = f1;
                f1 = d;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    return vec2(f1, f2);
}

void main() {
    // 只在物品**身上**（剪影内部）：裂纹不该飘在空气里
    float self = texture(Mask, texCoord).a;
    if (self < 0.5) {
        discard;
    }

    // 到轮廓的深度：0 = 轮廓上，1 = 内部深处
    float h = texture(Edge, texCoord).a;
    float depthIn = clamp((h - 0.5) * 2.0, 0.0, 1.0);

    // 进度控制裂纹推进到多深；前沿做软收尾，避免出现一条平直的"断口"
    float reach = clamp(CrackReach * CrackProgress, 0.0, 1.0);
    float front = 1.0 - smoothstep(reach - 0.10, reach + 0.02, depthIn);
    if (front <= 0.002) {
        discard;
    }

    // 以物品中心为原点的各向同性坐标（单位 ≈ 1 个屏幕高）—— 仅在拿不到局部基时兜底
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 np = (texCoord - NoiseOrigin) * vec2(aspect, 1.0);

    // ── 屏幕 UV → 物品局部坐标 ──
    // d = local.x * ItemBasisX + local.y * ItemBasisY，解这个 2×2 方程即可。
    // 退化（行列式≈0，比如物品几乎侧对镜头）时退回屏幕坐标，宁可图案滑动也不要出 NaN。
    vec2 local = np;
    float det = ItemBasisX.x * ItemBasisY.y - ItemBasisX.y * ItemBasisY.x;
    if (abs(det) > 1.0E-7) {
        vec2 d = texCoord - ItemOrigin;
        local = vec2(
                (d.x * ItemBasisY.y - d.y * ItemBasisY.x) / det,
                (-d.x * ItemBasisX.y + d.y * ItemBasisX.x) / det);
    }

    vec2 v = voronoi(local * CrackScale + CrackSeed);
    float edgeDist = v.y - v.x;                     // 单元边界处 → 0

    // ★ 量纲：edgeDist 是在**单元尺度**里量的（坐标已经乘过 CrackScale），
    //   所以"屏幕上的线宽"必须乘回 CrackScale 才对得上。
    //   漏掉这一步的实际后果：线宽只剩目标的 1/CrackScale（默认 14 倍细），
    //   表现就是"裂纹太细了根本看不清" —— 这是踩过的坑。
    float w = max(CrackWidth, 0.0004) * max(CrackScale, 0.001);
    float crack = 1.0 - smoothstep(0.0, w, edgeDist);
    float glow = 1.0 - smoothstep(0.0, w * max(CrackGlowWidth, 1.0), edgeDist);

    // 裂缝本体偏亮、外围是辉光；进度前沿的裂纹更亮一点（正在开裂的地方）
    float opening = 1.0 + front * 0.8;
    vec3 col = CrackColor * (crack * 1.7 * opening + glow * CrackGlow);

    float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    fragColor = vec4(col, a);
}
