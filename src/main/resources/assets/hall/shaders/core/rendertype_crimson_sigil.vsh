#version 150 core

// ═════════════════════════════════════════════════════════════
//  绯红誓约 · 法阵（顶点阶段）
//
//  几何有三种，共用同一套属性打包：
//    · 水平圆盘（蓄力 / 巨型法阵 / 天顶裂口）—— 三角扇，中心 + 若干同心环
//    · 竖直圆柱（终末光柱，uMode = 3）—— 侧面四边形带，沿高度分环
//    · 竖直锥（光柱底部的枪尖）—— 同一套环带，顶半径 = 0
//
//  Java 侧已把 ModelViewMat 折进了顶点（position[i] = ModelViewMat · worldPos），
//  所以这里 Position 就是视图空间坐标，只需要再过一道 ProjMat。
//
//  ── 三种属性分别装什么 ────────────────────────────────────────
//    Color.r / Color.g   圆盘：径向 / 角度（都归一化 0..1）
//                        圆柱：0 / 角度（高度**不在这里**，见下）
//    Normal.x            <b>顶点的世界 Y（格）</b>。这是光柱图案的唯一标尺。
//
//  ⚠️ 高度必须走 Normal.x（3 个 float），不能走 Color.r（4 个 ubyte）。
//  第一版把"柱长 = 34 格"写进 Color.r，存进去时 34*255 = 8670 被截成低 8 位
//  222 → 读回来 0.8706，整根柱子所有顶点同一个值 —— 六条螺旋只剩一条、
//  柱头光斑与枪口爆闪全灭，画面上是一根均匀发亮的塑料管。
//  改为 float 之后，0..100+ 格的高度都是精确的，片元里的 hN 才真的表示
//  "这个片元在柱子上的第几格"。
//
//  ── 边缘波动为什么放在顶点阶段，而不是片元里做 alpha 抖动 ────────────
//  需求是"激光的边缘有波动感"，也就是<b>轮廓本身</b>要起伏。片元里做 alpha
//  抖动只能得到一条毛边，轮廓依旧是一根笔直的圆柱；要让轮廓动，就必须真的
//  把顶点推开。所以：
//    · Java 侧把圆柱沿高度分成若干环（见 emitCylinder 的 rings 参数），
//      侧影才有折点可推；
//    · 这里用 wobble() 按 (角度, 高度, 时间) 把顶点沿半径方向推开/收进。
//
//  ⚠️ 只有**顶点位置**被推开，属性里那份 (高度, 角度) 坐标<b>不动</b>。
//  片元里的螺旋图案因此仍然绑在"理想圆柱"上，不会被这层波动搅乱 ——
//  两者一个改轮廓、一个管图案，互不干扰。
// ═════════════════════════════════════════════════════════════

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ProjMat;
uniform float uBeamWobble;   // 0..1 轮廓波动强度（0 = 理想圆柱；圆盘模式恒 0）
uniform float uBeamRadius;   // 柱半径（格）—— 波动振幅的基准，见下
uniform float uBeamHead;     // 柱底（前缘）世界 Y
uniform float uBeamBase;      // 地面世界 Y（反解世界高度用）
uniform float uBeamSpan;     // 可见段长度（格）
uniform float uTime;         // "秒"，驱动波动随时间起伏
// 柱轴（世界 +Y 那条线）在**视空间**的位置。波动要沿"离柱轴的水平方向"推顶点，
// 而 Position 已经是视空间坐标 —— 拿它直接减柱轴就是正确的径向，
// 不需要任何基变换。取值 = Java 侧 worldToView(basis, x, groundY, z)。
uniform vec3  uBeamAxisVS;
/** 柱轴方向（世界 +Y）在视空间下的单位向量 —— 用来把 rel 水平化。 */
uniform vec3  uBeamAxisDir;

out vec4 vertexColor;
out vec3 vLocal;
/** 顶点世界 Y（格）。片元靠它把图案钉在世界尺度上。 */
out float vHeight;

#define TAU 6.28318530718

/**
 * 轮廓波动量：沿半径方向推开多少格（可为负 = 收进）。
 *
 * <p>四个不同频率的正弦叠加，读起来才像"被气流吹皱的焰柱"而不是一个规整的
 * 波浪；纯正弦会立刻暴露成"一根麻花"。</p>
 *
 * <p>竖直项带 {@code -uTime * 2.3}：高频细节<b>向上跑</b>。向下跑的话会和
 * 光柱整体下落的运动打架，读起来像静止的；向上舔正好是火焰 / 电浆柱的观感。</p>
 *
 * @param a  角度 0..1（= Color.g，逐顶点线性插值，扇区内单调）
 * @param hn 高度归一化 0..1（0 = 柱底前缘，1 = 柱顶）
 */
float wobble(float a, float hn, float amp) {
    float th = a * TAU;
    return amp * (0.55 * sin(th * 3.0 + hn * 5.0 - uTime * 2.3)
                + 0.28 * sin(th * 5.0 - hn * 8.0 + uTime * 1.7)
                + 0.14 * sin(th * 8.0 + hn * 13.0 + uTime * 3.1)
                + 0.09 * sin(th * 2.0 - hn * 3.0 - uTime * 1.1));
}

void main() {
    vec3 p = Position;

    // ⚠ 世界 Y 不能信 Normal.x：POSITION_COLOR_NORMAL 的 NORMAL 是 3 个
    // signed byte（归一化），写 36~70 会被 normalIntValue 先 clamp 成 1.0。
    // 柱轴在视空间的位置/方向由 Java 每层传好，点积反解精确且与相机无关。
    float wy = Normal.x;
    float axisLen = length(uBeamAxisDir);
    if (axisLen > 1.0e-4) {
        wy = uBeamBase + dot(p - uBeamAxisVS, uBeamAxisDir / axisLen);
    }

    // ── 柱面轮廓波动 ──
    //  ⚠️ 振幅的硬上限是**柱半径**。四个正弦叠起来的最大系数是 1.06，
    //  所以"轴向位移 ≤ 半径"这条约束等价于 dBudget ≤ 0.94 * 半径。
    //  这里取 0.85，也就是位移最多 ±0.9 半径。
    //
    //  这条约束不是审美问题，是正确性问题：一旦位移超过半径，相邻扇区的顶点
    //  会互相穿过、法线翻转，侧影被自己折成一团 —— 离屏预览里表现为
    //  "柱子在某些机位整根消失、某些机位只剩一条细缝"，而且随距离时好时坏
    //  （看起来像深度测试的锅，其实是几何自交）。定位花了很久，记在这里。
    //
    //  ⚠️ 径向必须是"片元 − **柱轴**"，不能是"片元 − 相机"。
    //  第一版拿 normalize(vec3(p.x, 0, p.z)) 当径向，那是"从视空间原点到顶点"
    //  的方向（视空间相机就在原点），跟真正的径向差了将近 90° —— 顶点被推到
    //  切向上去，轮廓逐行等宽、看起来<b>完全没生效</b>，而且不报任何错。
    //  拿 uBeamAxisVS（柱轴在视空间的位置）一减就对了，也省掉所有基变换。
    if (uBeamWobble > 0.0001 && wy > 0.0001) {
        //  hn 现在从**世界高度**算：旧版拿 Color.r / 自己 得到恒等于 1，
        //  波动只剩"绕柱一圈"的分量，沿高度完全没有变化，侧影因此是直的。
        float hn = clamp((wy - uBeamHead) / max(uBeamSpan, 0.01), 0.0, 1.0);
        // 前缘（hn≈0）不推 —— smoothstep 把振幅从 0 拉到满。
        // 命中点必须稳稳落在那一点上：柱底被推歪的话，
        // 最亮的那一圈会陷进地里或者飘在半空。
        float dBudget = 0.85 * max(uBeamRadius, 0.0);
        float amp = min(uBeamWobble * uBeamRadius, dBudget) * smoothstep(0.0, 0.35, hn);
        float d = wobble(Color.g, hn, amp);

        // 视空间里"顶点 − 柱轴"就是径向；水平化（去掉沿柱轴的那一维）用
        // "减去 (radial·axis) * axis" 来做。
        vec3 rel = p - uBeamAxisVS;
        vec3 axisVS = normalize(uBeamAxisDir);
        vec3 radialVS = rel - axisVS * dot(rel, axisVS);
        float rl = length(radialVS);
        if (rl > 1.0e-4) {
            p += (radialVS / rl) * d;
        }
    }

    gl_Position = ProjMat * vec4(p, 1.0);
    vLocal      = p;
    vHeight     = wy;
    vertexColor = Color;
}
