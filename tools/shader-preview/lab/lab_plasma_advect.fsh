#version 150 core

// 实验台 · 候选方案「经向平流的反馈缓冲」（Eulerian flame）的 **平流 pass**。
//
// 每一帧：把上一帧的密度沿流场做半拉格朗日逆流采样（= 让密度"飘"过去），
// 再注入来自剪影遮罩的燃料，最后乘耗散。反复迭代就会自己长出上升、卷曲、
// 直到脱离母体的火舌 —— 这正是等宽描边数学上做不到的东西。
//
// 与描边那套的根本差别：
//   * 描边是每帧独立求值的静态图案（无时间相关性）；
//   * 这里是"上一帧的密度被搬运过来" —— 记忆 + 平流 = 火。
//
// 流场 = 浮力（向上）+ 两层低频噪声（模拟浮力湍流/卷曲）。

uniform sampler2D Prev;      // 上一帧的密度（1/2 分辨率）
uniform sampler2D Fuel;      // 燃料：剪影遮罩（全分辨率；.a = 覆盖，.rgb = 颜色）
uniform vec2 ScreenSize;
uniform float Time;
uniform float Dt;
uniform float Buoyancy;      // 上升速度（屏幕高/秒）
uniform float Dissipation;   // 每步保留比例（< 1，防止无限累积）
uniform float Inject;        // 燃料注入强度
uniform float Turbulence;    // 湍流强度

in vec2 texCoord;
out vec4 fragColor;

const float TAU_F = 6.2831853;

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

void main() {
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 np = vec2(texCoord.x * aspect, texCoord.y);

    // ── 流场：浮力 + 两层噪声（低频给大卷曲，高频给抖动） ──
    vec2 flow = vec2(0.0, Buoyancy);
    flow += vec2(fbm(np * 2.6 + vec2(Time * 0.13, 0.0)),
                 fbm(np * 2.6 + vec2(0.0, -Time * 0.10) + 17.0)) * Turbulence
          - vec2(Turbulence * 0.5);
    // 越往上越散（火焰顶部扩散）
    flow.x *= 0.6 + 0.9 * texCoord.y;

    // 半拉格朗日逆流采样：这个像素的密度来自"上游"。
    // ★ 密度存在 .r —— 读 .a 的话会读到恒为 1 的 alpha，密度就被恒定喂成 1.0，
    //   整屏饱和成一片淡黄（我在这里连踩两次，都是通道读错）。
    vec2 src = texCoord - vec2(flow.x / aspect, flow.y) * Dt;
    float carried = texture(Prev, clamp(src, vec2(0.0), vec2(1.0))).r;

    // ── 燃料注入：剪影自身，根部再加一点噪声结构 ──
    // 只在剪影附近注入；离得越远注入越弱（火焰是"贴着东西烧"的）
    float fuelMask = texture(Fuel, texCoord).a;
    float flicker = 0.55 + 0.45 * fbm(np * 8.0 + vec2(0.0, -Time * 0.8));
    float fuel = fuelMask * flicker * Inject;

    float density = carried * Dissipation + fuel;
    density = clamp(density, 0.0, 4.0);

    fragColor = vec4(density, density, density, 1.0);
}
