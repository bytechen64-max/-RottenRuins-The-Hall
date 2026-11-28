#version 150 core

// ═════════════════════════════════════════════════════════════
//  裁决领域 · 地面光纹（片元阶段）
//
//  五层叠加：
//
//  ① 六分对称长辐条 spoke
//       把角度归一化放大 6 倍，取"到最近整数的距离" → 六条穿过圆心的辐射线。
//       6 这个数字不是随便挑的：它是 README 里那个"完美符合几何学的二十面体"
//       的投影对称数，也是悬浮棱片（VerdictFieldRenderer 的 icosahedron）的对称数 ——
//       地面光纹与空中棱片用同一套对称性，两者才会被读成同一个东西。
//
//  ② 向心流动条 inflow
//       一组随时间向内移动的同心条。方向刻意选"从外向内"：
//       领域是"往里收"的力量（剑阵把敌人框在里面），
//       向外的流动会读成"驱散"，与设计意图相反。
//
//  ③ 边界环 boundary
//       在 r ≈ 1 处的一圈硬边亮环。这是**范围的可读性**：
//       玩家必须能一眼看出"我这条线画到哪儿为止"，
//       否则领域半径这个"吃高度"的属性就完全不可感知。
//
//  ④ 脉冲出剑环 pulseRing
//       按出剑序号取模展开的扩散环，视觉上与实体真正发剑气的那一下同步。
//
//  ⑤ 外圈溢出 halo
//       在 r > 1 的一段（最多到 uHalo）里画一道很淡的扩张光晕。
//       它的作用是"让边界之外还有一点东西"，避免圆盘边缘一刀切。
//       这也正是 Java 侧把网格半径给得比逻辑半径大的原因。
// ═════════════════════════════════════════════════════════════

uniform float uTime;
uniform float uIntensity;      // 0..1，Java 侧已含淡入淡出
uniform float uRadius;         // 逻辑半径（格）
uniform float uMeshRadius;     // 网格半径（格）—— 比 uRadius 大，留出 halo 的余量
uniform float uHalo;           // halo 外沿 / 逻辑半径，建议 1.28
uniform float uPulse;          // 0..1 当前脉冲进度
uniform float uProgress;       // 0..1 领域生命周期
uniform float uOwnerPresent;   // 1 = 施法者在场；0 = 已离场（阵法熄火）
uniform vec3  uFieldColor;     // 天蓝
uniform vec3  uCoreColor;      // 近白

in vec4 vertexColor;
in vec3 vLocal;
in vec3 vRadial;

out vec4 fragColor;

#define SPOKES      6.0
#define INFLOW_FREQ 7.0

void main() {
    float I = max(uIntensity, 0.0);
    if (I <= 0.003) discard;

    // ── 归一化极坐标 ──
    float r = vertexColor.r * (uMeshRadius / max(uRadius, 1e-3));
    float a = vertexColor.g;

    // 逻辑边界之外：只留 halo，其余全部丢弃
    if (r > uHalo) discard;

    // ── ① 六分对称辐条 ──
    float sa = a * SPOKES;
    float spokeDist = abs(fract(sa) - 0.5);
    float spoke = 1.0 - smoothstep(0.0, 0.05, spokeDist);
    // 辐条在圆心处收束（除以 r 会让中心爆掉，所以反过来乘一个中心衰减）
    spoke *= smoothstep(0.04, 0.35, r);
    // 在逻辑边界处收掉，避免辐条戳出范围外
    spoke *= 1.0 - smoothstep(0.94, 1.02, r);

    // ── ② 向心流动条 ──
    float inflow = fract(r * INFLOW_FREQ - uTime * 1.1);
    float bars = smoothstep(0.55, 0.95, inflow) * smoothstep(0.06, 0.30, r);
    bars *= 1.0 - smoothstep(0.90, 1.0, r);

    // ── ③ 边界环 ──
    float bx = (r - 1.0) / 0.035;
    float boundary = exp(-bx * bx);

    // ── ④ 出剑脉冲环 ──
    float px = (r - uPulse) / 0.10;
    float pulseRing = exp(-px * px) * (1.0 - uPulse);

    // ── ⑤ 外圈溢出光晕 ──
    float halo = (1.0 - smoothstep(1.0, uHalo, r)) * smoothstep(1.0, 0.99, r);

    float shape = spoke * 0.55 + bars * 0.30 + boundary * 1.15
                + pulseRing * 0.90 + halo * 0.16;

    // ── ⑥ 整片呼吸：出剑的那一下，整块场子跟着一起亮 ──
    //  原本只有一道向外扩散的脉冲环 —— 它标出了"出剑了"，但场子本身纹丝不动，
    //  于是读起来像"地上有个动画在循环"而不是"这片地是活的"。
    //  叠一层全局呼吸之后，阵法才有"在运转"的感觉，而且它几乎不花钱。
    //
    //  ⚠️ 用 <b>三角波尖峰</b>而不是 {@code exp(-uPulse * k)}：
    //  uPulse 是"已经过了一个出剑间隔的百分之几"，出剑的瞬间它刚好是 <b>0</b>。
    //  指数的最大值就在 0 处，于是每两次出剑之间它都从 1 开始——整片场子
    //  会长期停在最亮档，呼吸变成"永远亮着"。用三角波才能得到一个
    //  真正收得回去的尖峰（峰在 pulse=0，到 pulse=0.35 归零）。
    float beat = max(0.0, 1.0 - uPulse / 0.35);
    float idle = 0.5 + 0.5 * sin(uTime * 1.4);             // 静止时的低频起伏
    float breath = 0.09 * idle + 0.30 * beat * beat;       // 平方让尖峰更"脆"

    // ── ⑦ 施法者离场：阵法熄火 ──
    //  Java 侧已经在离场时<b>停止出剑</b>了（见 VerdictFieldEntity.tick），
    //  这里必须同步降下来，否则画面还在"运转"、伤害却已经没了 ——
    //  那正是玩家最难判断的一类不一致。
    //
    //  分两档压低：整体先降到 34%，其中"辐条 + 流动条"再降到 25% ——
    //  留下边界环（玩家判断范围的唯一依据）和一点底光，
    //  熄灭的是"运转"的部件，而不是整个阵法。
    float alive = mix(0.34, 1.0, uOwnerPresent);
    float structure = mix(0.25, 1.0, uOwnerPresent);
    shape = shape * alive + (spoke * 0.55 + bars * 0.30) * (structure - alive);

    shape += breath * (uOwnerPresent * 0.9 + 0.1);

    // 收尾：让领域在整个生命周期末尾整体收束，而不是"啪"地消失
    float life = 1.0 - smoothstep(0.86, 1.0, uProgress);

    vec3 col = uFieldColor * shape + uCoreColor * (boundary * 0.55 + pulseRing * 0.75);

    fragColor = vec4(col * I * life, 1.0);
}
