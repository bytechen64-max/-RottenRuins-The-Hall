#version 150 core

// ═════════════════════════════════════════════════════════════
//  绯红誓约 · 终幕「绯红之线」+ 枪口闪光（片元阶段）
//
//  【这是什么】
//  经典 shadertoy「glowing strands + muzzle」的绯红化移植：
//  六条不同频率 / 振幅 / 相位的正弦光带横贯屏幕，叠加一团贴在屏幕
//  右侧的枪口形辐射闪光。它是魔法阵终极技的**终幕**表现层，
//  配色整体收在绯红→紫罗兰→兰花的色域里（见下面的六条 Strand 颜色）。
//
//  【为什么是全屏叠加通道】
//  这一层不依附任何实体几何，是整屏铺满的滤镜，所以走全屏四边形 +
//  GUI 正交通道（json: depthtest=always / depthwrite=false / 加法混合）。
//  片元只读 gl_FragCoord，不采样任何贴图（json 的 samplers 为空），
//  因此顶点传来的 UV0 在本阶段是无意义的，刻意忽略。
//
//  【坐标约定 —— 本文件最容易搞错的一点】
//   · gl_FragCoord.xy 是**帧缓冲像素**，原点在**左下角**，y **向上**；
//   · uCenterUV 是**归一化屏幕 UV**，原点在**左上角**，y **向下**
//     （Java 侧由世界坐标投影得到的那种 UV）。
//  两者必须显式换算，见下面的 cc：
//      cc = vec2(uCenterUV.x * uResolution.x,
//                (1.0 - uCenterUV.y) * uResolution.y);
//  漏掉 (1.0 - ...) 会让汇聚中心上下镜像到屏幕的另一半。
//
//  【uniform 一览】
//   · uTime        秒。驱动正弦相位与枪口 20Hz 的阶跃闪烁。
//   · uIntensity   0..1 总强度，Java 侧已含淡入淡出。<= 0.002 时整层输出
//                  全黑（加法混合下等于没画）并提前返回，省掉全部计算。
//   · uDrop        0..1「自屏幕顶端向下揭示」的进度：0 = 只有最顶上一点点，
//                  1 = 全屏。前沿本身会画一条横扫的亮线（shock-line）。
//   · uConverge    0..1 六条线向魔法阵屏幕位置收束的程度；
//                  > 0.01 时额外点亮中心周围的径向辉光。
//   · uResolution  帧缓冲像素尺寸 (w, h)。用于把像素坐标归一化，
//                  同时是揭示前沿、辉光半径等一切尺度的基准。
//   · uCenterUV    魔法阵中心的归一化屏幕 UV（y 向下，见上）。
//   · ProjMat      由 json 声明、在 .vsh 里使用；本阶段不引用，
//                  同一 program 内不会被优化掉（Java 侧不会 NPE）。
//
//  【层次与合成顺序】
//  六条光带（受 uConverge 折叠、受 uDrop 揭示）→ 前沿亮线 → 汇聚辉光
//  → 枪口闪光 → 乘 uIntensity → 软 filmic 过曝压缩 → gamma 0.85 提亮。
//  出口 alpha 恒为 1.0，可见性完全由加法混合与 RGB 决定（黑 = 不可见）。
// ═════════════════════════════════════════════════════════════

uniform float uTime;        // 秒
uniform float uIntensity;   // 0..1，Java 侧已含淡入淡出
uniform float uDrop;        // 0..1，顶端→底端的揭示进度
uniform float uConverge;    // 0..1，向 uCenterUV 收束的程度
uniform vec2  uResolution;  // 帧缓冲像素 (w, h)
uniform vec2  uCenterUV;    // 魔法阵中心，归一化屏幕 UV，y 向下

out vec4 fragColor;

// ─────────────────────────────────────────────────────────────
//  一条正弦光带。
//  fragCoord 是 y 向上的像素坐标；曲线是 y = sin(x 的相位) 的一条正弦，
//  再用 1 - |y - 曲线| 得到「到曲线的距离」，所以线本身是硬的，
//  外圈那层 0.4 权重的 glow 才是把亮度糊开的软光晕。
// ─────────────────────────────────────────────────────────────
vec3 Strand(in vec2 fragCoord, in vec3 color, in float hoffset, in float hscale, in float vscale, in float timescale)
{
    float glow = 0.06 * uResolution.y;
    float twopi = 6.28318530718;
    float curve = 1.0 - abs(fragCoord.y - (sin(mod(fragCoord.x * hscale / 100.0 / uResolution.x * 1000.0 + uTime * timescale + hoffset, twopi)) * uResolution.y * 0.25 * vscale + uResolution.y / 2.0));
    float i = clamp(curve, 0.0, 1.0);
    i += clamp((glow + curve) / glow, 0.0, 1.0) * 0.4 ;
    return i * color;
}

// ─────────────────────────────────────────────────────────────
//  枪口形辐射闪光。
//  theta 是「屏幕右缘中点」到片元的极角，len 是当前帧该方向的射程
//  （20Hz 阶跃量化 ⇒ 每秒 20 次跳变，读起来是枪口的爆闪而不是连续呼吸）。
// ─────────────────────────────────────────────────────────────
vec3 Muzzle(in vec2 fragCoord, in float timescale)
{
    float theta = atan(uResolution.y / 2.0 - fragCoord.y, uResolution.x - fragCoord.x + 0.13 * uResolution.x);
    float len = uResolution.y * (10.0 + sin(theta * 20.0 + float(int(uTime * 20.0)) * -35.0)) / 11.0;
    float d = max(-0.6, 1.0 - (sqrt(pow(abs(uResolution.x - fragCoord.x), 2.0) + pow(abs(uResolution.y / 2.0 - ((fragCoord.y - uResolution.y / 2.0) * 4.0 + uResolution.y / 2.0)), 2.0)) / len));
    return vec3(d * (1.0 + sin(theta * 10.0 + floor(uTime * 20.0) * 10.77) * 0.5), d * (1.0 + -cos(theta * 8.0 - floor(uTime * 20.0) * 8.77) * 0.5), d * (1.0 + -sin(theta * 6.0 - floor(uTime * 20.0) * 134.77) * 0.5));
}

void main() {
    // ── 关断：强度归零 ⇒ 整层不可见 ──
    // 加法混合下「输出全黑」与「不画」等价，直接返回可以省掉后面全部计算，
    // 也避免 Java 侧把强度拉到 0 之后还残留一层雾。
    if (uIntensity <= 0.002) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // 本阶段唯一的输入：帧缓冲像素坐标（原点左下，y 向上）
    vec2 fragCoord = gl_FragCoord.xy;

    // ── uDrop：自屏幕顶端向下的揭示前沿 ──
    // uDrop = 0 → front = yRes：前沿贴在屏幕最上沿之外，屏幕基本全遮；
    // uDrop = 1 → front = 0  ：前沿落到底边，全屏揭示。
    // reveal 在 [front - 0.16h, front + 0.02h] 上做一次 smoothstep，
    // 前沿之上（y 更大的一侧）才是被揭示的部分 —— 方向不能反。
    float front  = uResolution.y * (1.0 - clamp(uDrop, 0.0, 1.0));
    float reveal = smoothstep(front - 0.16 * uResolution.y, front + 0.02 * uResolution.y, fragCoord.y);

    // ── uConverge：把光带的采样坐标向魔法阵中心折叠 ──
    // uCenterUV 是 y 向下的 UV，这里换算到 y 向上的像素空间
    vec2 cc  = vec2(uCenterUV.x * uResolution.x, (1.0 - uCenterUV.y) * uResolution.y);
    vec2 rel = fragCoord - cc;
    // 到中心的距离，以屏幕高度为单位（宽屏不会把辉光拉成椭圆）
    float d2c = length(rel / uResolution.y);
    // 越靠近中心，横向采样坐标被推得越远 ⇒ 相位推进越快 ⇒ 正弦被压得越密；
    // 纵向则向 cc.y 压扁（0.55），于是六条线在中心附近"收拢成束"。
    float pinch = mix(1.0, 1.0 + 1.6 * exp(-d2c * 4.0), uConverge);
    vec2 fc = fragCoord;
    fc.x = cc.x + rel.x * pinch;
    fc.y = cc.y + rel.y * mix(1.0, 0.55, uConverge * exp(-d2c * 3.0));

    // ── 六条光带：源片结构照搬，只把配色换成绯红誓约色域 ──
    // 颜色顺序与源片一致（亮粉 / 兰花 / 紫罗兰 / 深紫 / 淡玫瑰 / 品红），
    // 保证六条线在叠加后仍能互相分辨。
    float timescale = 4.0;
    vec3 c = vec3(0.0, 0.0, 0.0);
    c += Strand(fc, vec3(1.00, 0.20, 0.60), 0.7934  + 1.0 + sin(uTime) * 30.0, 1.0, 0.16, 10.0 * timescale);
    c += Strand(fc, vec3(1.00, 0.55, 0.85), 0.645   + 1.0 + sin(uTime) * 30.0, 1.5, 0.20, 10.3 * timescale);
    c += Strand(fc, vec3(0.72, 0.30, 1.00), 0.735   + 1.0 + sin(uTime) * 30.0, 1.3, 0.19,  8.0 * timescale);
    c += Strand(fc, vec3(0.55, 0.20, 0.95), 0.9245  + 1.0 + sin(uTime) * 30.0, 1.6, 0.14, 12.0 * timescale);
    c += Strand(fc, vec3(1.00, 0.75, 0.95), 0.7234  + 1.0 + sin(uTime) * 30.0, 1.9, 0.23, 14.0 * timescale);
    c += Strand(fc, vec3(0.90, 0.35, 0.75), 0.84525 + 1.0 + sin(uTime) * 30.0, 1.2, 0.18,  9.0 * timescale);

    // 揭示只作用在光带上：枪口闪光与下面两项都不受前沿裁剪。
    c *= reveal;

    // ── 前沿的横扫亮线：跟着 front 一起下移的高斯细线 ──
    // 两端的 step 把 uDrop = 0 与 uDrop = 1 收敛到 0，
    // 免得"还没开始"或"已经揭完"时屏幕上仍挂着一条线。
    // pow 的底数取 abs：指数虽为偶数，GLSL 对负底数的 pow 仍是未定义行为。
    c += vec3(1.0, 0.7, 1.0)
       * exp(-pow(abs((fragCoord.y - front) / (0.06 * uResolution.y)), 2.0))
       * 0.9 * step(0.0001, uDrop) * step(uDrop, 0.999);

    // ── uConverge 的径向辉光：中心的魔法阵被点亮 ──
    // 注：按需求这一项与 Muzzle 同级、不乘 reveal（保持原样）。
    //     若希望它也跟着前沿一起自上而下出现，把本行乘上 reveal 即可。
    if (uConverge > 0.01) {
        float rd = length(rel) / (0.35 * uResolution.y);
        c += vec3(1.0, 0.45, 0.9) * uConverge * 0.9 * exp(-rd * rd * 3.0);
    }

    // ── 枪口闪光：固定在屏幕右侧，与揭示 / 收束都无关 ──
    // 用 fragCoord（而非折叠后的 fc），它的形状才不会跟着汇聚变形。
    c += clamp(Muzzle(fragCoord, timescale), 0.0, 1.0);

    // ── 强度 + 过曝：先乘总强度，再过一条软 filmic 压缩曲线，
    //    最后 gamma 0.85 抬中低段 —— 读起来是"爆闪"而不是一层均匀的雾。
    //    max(c, 0.0) 同样是 pow 的负底数保护。
    c *= uIntensity;
    c  = c / (1.0 + c * 0.35);
    c  = pow(max(c, vec3(0.0)), vec3(0.85));

    fragColor = vec4(c, 1.0);
}
