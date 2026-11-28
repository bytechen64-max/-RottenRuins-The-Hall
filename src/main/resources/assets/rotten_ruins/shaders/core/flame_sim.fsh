#version 150 core

// 火焰的「平流 sim」pass：注入 → 浮力平流 → 湍流 → 冷却 → 淬火。
//
// 这是整个火焰效果的核心，也是一条**有状态**的 pass：它把上一帧的密度搬过来，
// 而不是每帧重新算一个图案。火焰之所以读成"火"而不是"描边"，就靠这一点 ——
// 特征是跨帧同一件东西，会上升、会拉伸、会断裂。
//
// 全部在 1/4 分辨率上做：雾/火是低频的，面积只剩 1/16，而且整屏一次、同屏所有
// 燃烧物品共用同一个场（多团火在同一个场里交叠合并，读感更自然）。
//
// 三个算子（缺一不可）：
//   1. 扩散（4 邻域十字平均）—— 抹平棋盘格、让舌形连续。不能省：半拉格朗日在
//      低分辨率下会留下采样锯齿。
//   2. 半拉格朗日平流 —— 无条件稳定：回溯上游一次采样即得，不会像显式对流那样炸。
//   3. 指数冷却 —— 寿命有限，于是"火焰高度"是参数的自然结果，尖端的收细也是。

uniform sampler2D Prev;      // 上一帧的场（R = 能量/温度，G = 烟密度）
uniform sampler2D Fuel;      // 燃料：只有"这件物品要烧"的剪影被画进来
uniform vec2 ScreenSize;     // 全屏尺寸（用于把屏幕高宽比拉平，噪声才不会横着拉长）
uniform vec2 AnchorUv;       // 燃料的屏幕锚点（物品 AABB 中心），用于"离源高度"
uniform vec2 UpUv;           // "世界上方"在屏幕 UV 空间的方向（已含高宽比修正）
uniform float Time;          // 秒
uniform float Dt;            // 帧间隔（已 clamp）
uniform float Rise;          // 浮力基准速度（屏幕高/秒）
uniform float Buoyancy;      // 浮力对温度的放大系数（热的地方升得更快 → 拉伸 → 断裂）
uniform float Turbulence;    // 湍流强度（curl 噪声）
uniform float Inject;        // 燃料注入强度
uniform float Cooling;       // 冷却速率（1/秒），越大火越短
uniform float SmokeYield;    // 燃料转烟的比率
uniform float VentSpeed;     // 根部"喷口"噪声的滚动速度

in vec2 texCoord;
out vec4 fragColor;

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
    return vnoise(n) * 0.55 + vnoise(n * 2.3) * 0.28 + vnoise(n * 5.1) * 0.17;
}

/** 标量势 ψ 的旋度：无散度速度场 —— 只让流体旋转，不凭空产生源汇。 */
vec2 curl(vec2 p) {
    const float e = 0.06;
    float l = fbm(p - vec2(e, 0.0));
    float r = fbm(p + vec2(e, 0.0));
    float d = fbm(p - vec2(0.0, e));
    float u = fbm(p + vec2(0.0, e));
    return vec2(u - d, -(r - l)) / (2.0 * e) * 0.06;
}

void main() {
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 np = vec2(texCoord.x * aspect, texCoord.y);

    // ── 扩散：4 邻域十字平均（采样本场自己的邻域，1/4 分辨率下一个像素就是 4 屏幕像素）──
    vec2 texel = 1.0 / max(ScreenSize * 0.25, vec2(1.0));
    float e = texture(Prev, texCoord).r;
    float avg = (texture(Prev, texCoord + vec2(texel.x, 0.0)).r
               + texture(Prev, texCoord - vec2(texel.x, 0.0)).r
               + texture(Prev, texCoord + vec2(0.0, texel.y)).r
               + texture(Prev, texCoord - vec2(0.0, texel.y)).r) * 0.25;
    e = mix(e, avg, 0.08);

    // ── 速度场：浮力（∝ 温度）+ curl 湍流 ──
    // 离源高度 h：越往上湍流越强、根部越稳（火焰"根稳尖狂"）
    float h = max(dot((texCoord - AnchorUv) * vec2(aspect, 1.0), UpUv), 0.0);
    vec2 flow = UpUv * (Rise + Buoyancy * clamp(e, 0.0, 1.5));
    flow += curl(np * 3.1 + vec2(0.0, -Time * 0.25)) * Turbulence * (0.35 + min(h * 2.5, 1.5));

    // ── 半拉格朗日逆流采样：这个像素的密度来自"上游" ──
    // UpUv 已经含高宽比修正，所以这里只需把 x 再除一次 aspect 回到 UV 空间
    vec2 src = texCoord - vec2(flow.x / aspect, flow.y) * Dt;
    vec2 prev = texture(Prev, clamp(src, vec2(0.0), vec2(1.0))).rg;

    // ── 冷却：指数衰减，无条件稳定（dt 再大也不会炸）──
    float keep = exp(-Cooling * Dt);
    e = prev.r * keep;
    float smoke = prev.g * exp(-Cooling * 0.45 * Dt);

    // ── 注入：燃料（这件物品的剪影）× 低频滚动的"喷口"噪声 ──
    // 喷口必须是低频慢滚的，不能用逐帧白噪声 —— 后者会让根部闪成噪点而不是"呼吸的喷口"，
    // 而且喷口开开关关正是火焰"一绺一绺"而不是"一堵火墙"的原因。
    float fuel = texture(Fuel, texCoord).a;
    if (fuel > 0.0) {
        float vent = 0.55 + 0.65 * fbm(np * 6.0 + vec2(0.0, -Time * VentSpeed));
        float add = fuel * vent * Inject;
        e = max(e, add);                 // max 而不是 add：保持根部锐利，防止积成均匀火墙
        smoke = max(smoke, add * SmokeYield);
    }

    // ── 淬火：只杀"深入剪影内部"的密度，绝不动边界那一圈 ──
    //
    // ★ 这里踩过一个致命的坑：燃料是在**整个剪影**上注入的，如果淬火也覆盖整个剪影，
    //   注入会当场被自己抹掉 —— 平流场永远是零，合成出来一片全黑（离屏一跑就看到了）。
    //   所以先做一次 1 燃料纹素的"腐蚀"（4 邻域取 min），只有深入内部的像素才被清：
    //   边界那一圈（火焰出生的地方）完整保留，内部则不会积成一个会漏出去的蓄水池。
    vec2 du = vec2(1.0 / max(ScreenSize.x * 0.25, 1.0), 0.0);
    vec2 dv = vec2(0.0, 1.0 / max(ScreenSize.y * 0.25, 1.0));
    float inner = min(min(texture(Fuel, texCoord + du).a, texture(Fuel, texCoord - du).a),
                      min(texture(Fuel, texCoord + dv).a, texture(Fuel, texCoord - dv).a));
    inner = min(inner, fuel);
    float quench = 1.0 - smoothstep(0.55, 0.95, inner);
    e *= quench;
    smoke *= quench;

    // ── NaN 擦洗：16F 反馈回路里一个 NaN 会随平流+扩散永久扩散下去 ──
    if (!(e >= 0.0) || e > 8.0) e = 0.0;
    if (!(smoke >= 0.0) || smoke > 8.0) smoke = 0.0;

    fragColor = vec4(e, smoke, 0.0, 1.0);
}
