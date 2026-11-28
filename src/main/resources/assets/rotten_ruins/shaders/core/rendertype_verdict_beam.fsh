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
uniform float uProgress;       // 0..1 生命周期进度（1 = 即将消失）
uniform float uAge;            // 实体年龄（秒）—— 两波打击的扫描计时用
uniform float uBackCull;       // 0..1 背面剔除强度（相机在柱内时自动降为 0，见 main）
uniform vec3  uCoreColor;      // 近白
uniform vec3  uEdgeColor;      // 天蓝

/** 一次打击自上而下扫完整根柱子所需的秒数（0.35 秒，比 WAVE2 的 9 tick 稍长）。 */
#define SCAN_SECONDS 0.35

in vec4 vertexColor;
in vec3 vLocal;
in vec3 vRadial;
in vec3 vRadialView;

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
    //  uRadius / uHeight 是网格的基准尺寸，而扩散的缩放是在<b>矩阵</b>里做的
    //  （Java 侧 push → scale → pop），顶点喂进来的 vLocal 始终是基准尺寸的局部坐标
    //  —— 所以这两个比值在扩散过程中天然不变，剖面不会随柱子变大而失真。
    float d = vertexColor.r;
    float h = vertexColor.g;
    if (d <= 0.0 && h <= 0.0) {
        d = clamp(length(vLocal.xz) / R, 0.0, 1.0);
        h = clamp(vLocal.y / H, 0.0, 1.0);
    }

    // ── ⓪ 背面剔除：整根柱子只画朝向相机的那一层 ──
    //
    //  这是"水平看光柱出现割裂"的正解。空心圆柱的侧影处，视线在**同一像素上
    //  连续穿过两个表面**（前壁与后壁），加法混合下就是 1+1 —— 于是轮廓处出现
    //  一条比柱身更亮、且把柱子"竖着切成一半"的硬边。
    //
    //  ⚠️ 这个符号是<b>实测定下来的，不要再按"推导"改回去</b>。
    //
    //  我曾经按"视图空间相机在原点、朝 -Z 看，所以朝相机的法线 z 为负"
    //  推出应当保留 {@code z < 0}，并把条件写成了 {@code z > 0 → discard}。
    //  实机结果是<b>只看到背面那一层、柱子像被竖着切了一半</b> —— 也就是
    //  那个推导至少有一个前提是错的（可能是 uNormalToView 与顶点所在空间
    //  的符号约定，也可能是 mc 的视图矩阵朝向约定），而结果是相反的。
    //
    //  所以这里统一成一个<b>语义化的量</b>：towardCam = -nView.z，
    //  +1 表示这个面正对相机。剔除与下面的 facing 都从它出发 ——
    //  万一以后符号又要改，只需要改这一行的一个负号。
    //
    //  ⚠️ 插值之后必须重新归一化：三个顶点的单位法线插值到面片中间会短于 1。
    vec3 nView = normalize(vRadialView);
    float towardCam = -nView.z;

    //  uBackCull 由 Java 侧算：相机在柱体<b>内部</b>时它会降到 0。
    //  理由：站在柱子里面时所有面都背离相机，硬剔会让光柱整个消失。
    //  贴脸看一个本来就有体积的光束，也确实应该看到内壁的光。
    if (uBackCull > 0.5 && towardCam < 0.0) discard;

    // ── ① 核心衰减：用径向距离 d，<b>不要</b>用 facing ──
    //
    //  这里踩过一个坑，记下来免得再犯：背面剔除做完之后，我一度觉得
    //  "屏幕上不再有完整的 d 剖面"，于是把 core 改成用 (1 - |nView.z|) 来算。
    //  结果整根柱子的径向剖面<b>倒了过来</b>：
    //
    //    近壁正对相机的那一点 → nView.z ≈ +1 → facing ≈ 0 → core ≈ 0（最暗）
    //    剪影边缘             → nView.z ≈ 0 → facing ≈ 1 → core 最大（最亮）
    //
    //  也就是"中间黑、边缘亮的一圈"，读起来是一根<b>空心管子 / 一层壳</b>，
    //  而不是一根实心的光柱 —— 玩家反馈"在外面只能看见里面那一层"就是它。
    //
    //  d 一直是对的：轴心 d=0 最亮、柱面 d=1 归零。顶点色里就带着它，
    //  背面有没有被剔掉并不影响这个剖面的正确性。
    float core = pow(1.0 - smoothstep(0.0, 1.0, d), 2.2);

    // ── ② 边缘环 ──
    //  峰在 d ≈ 0.80，宽度 0.18。这是"柱体边界"的视觉锚点。
    float rimX = (d - 0.80) / 0.18;
    float rim = exp(-rimX * rimX);

    // 剪影处的一点额外提亮（只留一次、权重很低）。
    //  facing = 1 - |nView.z|：正对相机时 0、侧对（剪影）时 1。
    //  它只做"边缘的细亮线"，作为 core 的补充，不承担主亮度 ——
    //  主亮度归 core，见上面那段踩坑记录。
    float facing = clamp(1.0 - abs(nView.z), 0.0, 1.0);
    float fres = pow(facing, 3.0);

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

    // ── ⑥ 两波打击：柱体上自上而下刷过两道光 ──
    //  与 VerdictBeamEntity 的 WAVE1_TICK / WAVE2_TICK（0 tick 与 9 tick）对应，
    //  是"伤害确实分两次打出去"这件事在画面上的唯一凭据。
    //
    //  ⚠️ 必须用<b>随时间移动的窄带尖峰</b>，不能写成
    //  {@code exp(-((h - uProgress * k) / w)^2)}：那样的峰位置只随生命周期推进，
    //  于是整根柱子上会长期挂着一条明亮而静止的横带 —— 看起来像一根灯管上的花纹，
    //  而不是"一道光刷过去"。峰必须在几 tick 内扫完整根柱子，然后消失。
    //
    //  位置 = (age - 该波的触发 tick) / 扫描时长；越界就夹到外面，差值自然变负、尖峰归零。
    //  三波的触发 tick 与 VerdictBeamEntity 的 WAVE1/2/3_TICK 一致：0 / 0.5 / 1.5 秒。
    float s1 = (uAge - 0.00) / SCAN_SECONDS;
    float s2 = (uAge - 0.50) / SCAN_SECONDS;
    float s3 = (uAge - 1.50) / SCAN_SECONDS;
    float wave1 = max(0.0, 1.0 - abs(h - s1) / 0.09);
    float wave2 = max(0.0, 1.0 - abs(h - s2) / 0.12) * 0.6;
    float wave3 = max(0.0, 1.0 - abs(h - s3) / 0.16) * 0.45;

    // ── ⑦ 收束：生命末段不只是变暗，而是<b>从下往上塌陷</b> ──
    //  只做整体淡出的话，一根 44 格的柱子会像贴纸一样整片变透明（很假）。
    //  让它从底部开始缩短、同时尾部有一次短促的亮爆，读起来才像"裁决执行完毕"。
    float collapse = smoothstep(0.86, 1.0, uProgress);
    float collapseEdge = 1.0 - smoothstep(uProgress - 0.02, uProgress + 0.04, h);
    float collapsing = mix(1.0, collapseEdge, collapse);
    // 收束尾段的一次亮爆（不是延长寿命，只是在淡出曲线上加一个脉冲）
    float flash = exp(-pow((uProgress - 0.86) / 0.035, 2.0)) * 0.85;

    // shape 里的 fres 不再乘 core：core 现在本身就是 facing 的函数，
    // 再乘一次会把侧影推进过曝区（见上面的 ②）。
    float shape = (core * 0.92 + rim * 0.55 + fres * 0.5)
                * flow * topFade * botFade * collapsing;

    vec3 col = uCoreColor * shape + uEdgeColor * rim * 0.85;
    col += vec3(1.0) * scan * 1.6;
    col += uCoreColor * (wave1 * 1.15 + wave2 * 0.65 + wave3 * 0.5) * collapsing;
    col += uEdgeColor * flash;

    fragColor = vec4(col * I, 1.0);
}
