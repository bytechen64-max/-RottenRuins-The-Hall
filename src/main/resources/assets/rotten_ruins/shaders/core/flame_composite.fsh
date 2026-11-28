#version 150 core

// 火焰的「合成」pass：把平流场映射成光，并严格裁在物品外侧。
//
// 三个要点：
//   1. 颜色 = 黑体色带(温度)，不是"给噪声上色"。低密度暗红、中密度橙、高密度白热 ——
//      于是火舌有根部和尖端之分（等宽描边只有一种颜色，没有"尖端"这个概念）。
//   2. 覆盖率用 Beer 式 1−exp(−k·E)：天然软轮廓，不需要硬阈值。
//   3. 硬裁用**全分辨率**剪影：1/4 上采样会向内渗 1~2 像素，会盖住物品表面
//      与 star 层。这一条是硬约束，不能靠调参。
//
// 烟（G 通道）在这里只做"轻微压暗 + 微光"，真正的吸收是后续 S4 的 DARKEN 混合，
// 但先把烟画出来，火才有"烧完剩下东西"的感觉。

uniform sampler2D Sim;       // 1/4 分辨率的平流场（R = 温度，G = 烟）
uniform sampler2D Mask;      // 全分辨率剪影遮罩（.a = 覆盖）—— 硬裁用
uniform sampler2D Visibility;// 1/4 分辨率、由**经过深度测试的剪影**模糊出来的可见区域
uniform float VisibilityGate;// >0 时启用可见包围；0 = 关闭（退化成不做遮挡）
uniform float DebugMode;     // 0=正常合成；1=直接显示平流场密度；2=直接显示可见性包络
// ★ 这两行曾经漏掉，代价是"火焰凭空消失"整整几轮：
//   MC 的 ShaderInstance 只按 JSON 里的 uniforms 去 glGetUniformLocation，
//   **它不会给 GLSL 注入任何声明**。JSON 里写了、GLSL 里没写 = 编译失败 =
//   整个 fragment program 不可用 → 合成一个像素都不画（而且只在日志里留一行 ERROR）。
uniform float FlameFreeAboveV;   // 物品上沿 UV.y：以上一律放行
uniform float FlameScissorTopV;  // 裁剪框上沿：之前软收尾
uniform vec2 ScreenSize;
uniform float Gain;          // 亮度倍率
uniform float TempScale;     // 温度→色带的映射尺度（>1 更容易白热）
uniform float SmokeDarken;   // 烟对视线的压暗强度
uniform float Sparkle;       // 余烬闪点强度（高温处的孤立亮斑）
uniform vec3 FlameTint;      // 火焰色：冷尾/主体取它，核心推向白 —— 物品色即火色

in vec2 texCoord;
out vec4 fragColor;

float hash21(vec2 n) {
    n = mod(n, 64.0);
    return fract(sin(dot(n, vec2(12.9898, 12.1414))) * 83758.5453);
}

/**
 * 带染色的黑体色带：暗尾 = 染色×0.10 → 主体 = 染色×0.85 → 亮 = 推向白 72% → 核心 = 近白。
 *
 * <p>与"固定橙红黑体"的区别：色相由物品自己决定。绯红誓约传进它的亮粉，
 * 于是冷尾是深紫、主体是品红、核心是粉白 —— 而不是一把粉剑烧出橙色的火。</p>
 */
vec3 flameRamp(float t, vec3 tint) {
    t = clamp(t, 0.0, 1.0);
    vec3 dark = tint * 0.10;
    vec3 body = tint * 0.85;
    vec3 hot = mix(tint, vec3(1.0), 0.72);
    vec3 core = mix(tint, vec3(1.0, 0.97, 1.0), 0.90);
    vec3 c = mix(dark, body, smoothstep(0.00, 0.32, t));
    c = mix(c, hot, smoothstep(0.32, 0.68, t));
    c = mix(c, core, smoothstep(0.68, 1.00, t));
    return c;
}

void main() {
    // ── 调试视图 ──
    // 把"平流场密度"或"可见性包络"直接画在屏幕上（灰度、不参与色调映射/硬裁/包络）。
    // 这一层是给"火焰看起来不对"这类问题用的：一眼就能分清是
    //   ① 场里根本没有密度（注入/平流/dp 的问题）
    //   ② 场里有密度，但被某个 gate 抹掉了（包络/硬裁/裁剪框）
    if (DebugMode > 0.5) {
        float v = DebugMode < 1.5 ? texture(Sim, texCoord).r : texture(Visibility, texCoord).a;
        fragColor = vec4(vec3(v), 1.0);
        return;
    }

    vec2 density = texture(Sim, texCoord).rg;
    float self = texture(Mask, texCoord).a;

    // 硬裁：剪影内部一丝不留（火焰只长在物品外侧）
    float outside = 1.0 - smoothstep(0.35, 0.65, self);

    float temp = clamp(density.r * TempScale, 0.0, 1.0);
    float cover = 1.0 - exp(-3.2 * density.r);          // Beer 式覆盖率
    float smoke = 1.0 - exp(-1.6 * density.g);

    vec3 fire = flameRamp(temp, FlameTint) * cover * Gain;

    // 余烬闪点：只挑最热的地方，高频抖动 —— 纯合成期技巧，不需要粒子系统
    if (Sparkle > 0.0) {
        float spark = smoothstep(0.55, 0.95, temp)
                * smoothstep(0.86, 0.99, hash21(floor(texCoord * ScreenSize * 0.35)));
        fire += mix(vec3(1.0, 0.9, 0.75), FlameTint, 0.4) * spark * Sparkle * cover;
    }

    // 烟：在火的外圈压一点暗，然后给一点极弱的自发光（不完全黑）
    float smokeOnly = smoke * (1.0 - cover);
    vec3 color = fire + vec3(0.045, 0.035, 0.035) * smokeOnly;
    float alpha = clamp(cover + smokeOnly * 0.85, 0.0, 1.0);

    // 烟要压暗背景：加法混合做不到"减"，所以这里让烟区的颜色略低于纯加性预期 ——
    // 配合 1.0 的 alpha，视觉上就是"薄烟挡了一点光"。真正的吸收留给 S4 的 DARKEN。
    color *= 1.0 - SmokeDarken * smokeOnly * 0.5;

    // ── 遮挡：只能用"物品可见区域"当包络 ──
    //
    // 为什么不能直接开硬件深度测试：火焰在 renderLevel 的 TAIL 合成，而 vanilla 在
    // 画手之前就把世界深度清掉了 —— 那一步已经没有可比的深度。所以改用可见性包络：
    // Visibility 是**深度测试过的剪影**（被墙挡住的物品根本进不了遮罩）模糊出来的，
    // 于是"物品看不见的地方，火也不许亮"。半透明的遮挡（部分被墙挡）也跟着正确。
    float visibility = 1.0;
    if (VisibilityGate > 0.0) {
        float v = texture(Visibility, texCoord).a;
        // 门槛取得很低：模糊图在物品外还有一段软尾巴，门槛高一点就会把这个范围内的
        // 羽流整段切掉 —— 小物品上表现为"火几乎没扩散"（实测踩过）。
        // 代价是包络会往遮挡物后面渗一点，换来的是火能正常长高。
        visibility = smoothstep(0.0, 0.08, v);
        // ★ 但光靠放宽门槛不够：模糊核的触达距离只有约 52px（AURA_REACH_PX），
        //   火羽只要高过它就被**整段**切掉 —— 这就是"怎么调高度都没有拖尾"的真正原因。
        //   所以物品上沿以上一律放行（火本来就该往上升），物品旁边仍然受包络约束：
        //   于是"物品被墙挡住 → 火也消失"依然成立，而羽流能自由长高。
        visibility = max(visibility, smoothstep(FlameFreeAboveV - 0.03, FlameFreeAboveV + 0.03, texCoord.y));
        // ★ 放行之后又踩了一个：羽流会一路填到 scissor 上沿，被**平直地切掉**，
        //   屏幕上就是一条横贯物品上方的硬边（外行看是"裁剪边缘"）。
        //   裁剪框现在按实际羽流高度动态放大，这里再做一次软收尾，双保险。
        visibility *= 1.0 - smoothstep(FlameScissorTopV - 0.12, FlameScissorTopV, texCoord.y);
    }

    fragColor = vec4(color, alpha) * outside * visibility;
}
