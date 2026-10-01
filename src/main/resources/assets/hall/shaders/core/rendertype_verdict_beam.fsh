#version 150 core

// ═════════════════════════════════════════════════════════════
//  天穹裁决 · 垂直光柱（片元阶段）
//
//  这是一个纯自发光体积光束：不做屏幕空间折射（那是冲击波的活），
//  因为裁决的读法是"天上劈下来一条亮线"，而不是"空气被推开"。
//  画面目标只有三个：**看得见、立得住、有能量在流**。
//
//  ── 五层叠加，从底到顶 ──────────────────────────────────────
//
//  ① 核心衰减 core
//       1 - smoothstep(0, 1, 径向距离)，再 pow 2.2 收紧。
//       得到中心极亮、迅速衰减的柱芯。
//
//  ② 边缘环 rim
//       剪影边缘附近的一圈亮边。为什么必须有这一层：
//       在"平坦地面 / 天空"这类低频背景上，只有径向渐变的光束几乎看不出来 ——
//       背景本来就没有细节可对比。真正让远处光柱被读出来的是**亮度**，
//       而且亮在"边界"比亮在"中心"更能读出柱体的形状。
//       这与冲击波里 visBoost/边缘亮带是同一个教训。
//
//  ③ 轴向能量下行 flow
//       沿 +Y 的 fbm 细丝随时间向下滚动。方向刻意选"从上往下"：
//       裁决是从天而降的，能量流向就是玩家的心理方向。
//       用 abs 把噪声折成对称细丝，避免出现"一团团斑块"。
//
//  ④ 裁决之痕扫描球 scan
//       exp 形状的高斯，沿轴向下扫过一次。视觉上就是"裁决落下"这件事本身。
//       它与伤害判定的语义同源：伤害在光柱生成的那一瞬结算，
//       扫描球就是那一瞬在屏幕上的可见形态。
//
//  ⑤ 顶部切线淡出 topFade
//       光柱顶端在"天上"收口。不收的话会是一条硬边切在天空里，非常假。
//
//  ── 颜色 ────────────────────────────────────────────────────
//  uCoreColor = 0xFFF0F8FF（近白，爆发）／uEdgeColor = 0xFF87CEFA（天蓝，领域）
//  这两个色值直接取自 DomeriteLongsword.outlineColor()/outlineSecondaryColor()，
//  不引入第二种主色 —— 全套技能与长剑描边共用一套视觉语言。
//
//  混合模式为加法（ONE / ONE），所以本着色器输出的是**亮度**而非颜色：
//  中心叠到近白，边缘是低饱和天蓝，接到深色天空上会自然泛光。
// ═════════════════════════════════════════════════════════════

uniform float uTime;
uniform float uIntensity;      // 0..1，Java 侧已含淡入淡出
uniform float uRadius;         // 光柱半径（格）
uniform float uHeight;         // 光柱高度（格）
uniform float uScanT;          // 0..1 扫描球沿轴的位置
uniform vec3  uCoreColor;      // 近白
uniform vec3  uEdgeColor;      // 天蓝

in vec4 vertexColor;
in vec3 vLocal;
in vec3 vRadial;

out vec4 fragColor;

// ── 哈希与噪声（与 rendertype_shockwave.fsh 同一套，保证细丝质感一致）──
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
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + 17.3;
        a *= 0.5;
    }
    return s;
}

void main() {
    float I = max(uIntensity, 0.0);
    if (I <= 0.003) discard;

    float H = max(uHeight, 1e-3);
    float R = max(uRadius, 1e-3);

    // ── 归一化坐标：径向 d ∈ [0,1]，高度 h ∈ [0,1] ──
    //  优先用 Java 塞进顶点色的贴图坐标；为零时按局部位置反算（几何兜底）。
    float d = vertexColor.r;
    float h = vertexColor.g;
    if (d <= 0.0 && h <= 0.0) {
        d = clamp(length(vLocal.xz) / R, 0.0, 1.0);
        h = clamp(vLocal.y / H, 0.0, 1.0);
    }

    // ── ① 核心衰减 ──
    float core = pow(1.0 - smoothstep(0.0, 1.0, d), 2.2);

    // ── ② 边缘环 ──
    //  峰在 d ≈ 0.80，宽度 0.18。这是"柱体边界"的视觉锚点。
    float rimX = (d - 0.80) / 0.18;
    float rim = exp(-rimX * rimX);

    // 径向膨胀法线带来的内侧光：正对视线的面 |vRadial| ≈ 0，剪影边缘 ≈ 1。
    // 于是"贴着边缘的那些面"额外提亮 —— 光柱看起来是空心的玻璃管而非实心棒。
    float fres = pow(clamp(length(vRadial), 0.0, 1.0), 2.0);

    // ── ③ 轴向能量下行 ──
    //  用局部空间采样而不是视图空间：光柱是轴对齐的，用局部空间
    //  细丝才会**贴在柱体上**随柱体一起走，而不是像贴纸一样滑过屏幕。
    vec3 q = vec3(vLocal.x * 0.55, vLocal.y * 0.26 - uTime * 2.1, vLocal.z * 0.55);
    float n = fbm3(q);
    // 折成细丝：abs 让噪声在 0 附近出现清晰的"丝"，而不是一团团云。
    float flow = 0.62 + 0.38 * abs(n * 2.0 - 1.0);

    // ── ④ 裁决之痕扫描球 ──
    float su = (h - uScanT) / 0.055;
    float scan = exp(-su * su);

    // ── ⑤ 顶部切线淡出（按比例，远景/近景一致）──
    float topFade = 1.0 - smoothstep(0.82, 1.0, h);
    // 底部也给一点收口，否则光柱会在脚底下露出一个平齐的硬边
    float botFade = smoothstep(0.0, 0.035, h);

    float shape = (core * 0.92 + rim * 0.55 + fres * core * 0.45)
                * flow * topFade * botFade;

    vec3 col = uCoreColor * shape + uEdgeColor * rim * 0.85;
    col += vec3(1.0) * scan * 1.6;

    fragColor = vec4(col * I, 1.0);
}
