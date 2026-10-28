#version 150 core

// ─────────────────────────────────────────────────────────────
//  tooltip 底板 · 热力学流动（片元阶段）
//
//  画的是"一块被从下方加热的板"：热源在下沿，热量以羽流的形式往上走，
//  冷区是深紫黑，热区沿 暗底 → 亮粉 → 雾粉紫 的色阶升温。
//
//  三个物理动作，全部由噪声构成，没有贴图：
//    ① 对流   —— 整场随时间向下采样（等价于图案向上飘），并叠一个低频
//                 横向摆动，让羽流不是直上直下；
//    ② 域扭曲 —— 先用一层慢噪声算出 warp，再拿它扭第二层噪声，
//                 得到丝状/絮状的热羽，而不是一团糊的云；
//    ③ 热源   —— 温度里额外加一份随 texCoord.y 增大的偏置（下沿最热）。
//
//  收边：靠近四条边压暗 + 上亮下暗，让原版那 1px 边框仍然是画面里
//  最利落的一条线；alpha 也保持在高位，因为文字是画在这层<b>之上</b>的
//  （见 GuiGraphics.renderTooltipInternal 的绘制顺序），底色太透会掉可读性。
//
//  uniform 由 Java 侧每帧写入（client/tooltip/TooltipThermalRenderer）。
//  注意：Uniform.set() 只是记值，真正上传发生在 endBatch → ShaderInstance.apply()，
//  所以顺序必须是「设 uniform → 写顶点 → endBatch」，不能攒着（见 docs/mask-layers.md 第 8 节）。
// ─────────────────────────────────────────────────────────────

in vec2 texCoord;

uniform float uTime;        // 秒（游戏时间 + 帧插值），保证暂停时画面也停
uniform float uSpeed;       // 对流速度（像素无关，作用于噪声空间）
uniform float uScale;       // 热羽的特征尺寸（GUI 像素），越大越"大块"
uniform float uIntensity;   // 整体强度，0 = 只剩底色
uniform vec2  uPanelSize;   // 面板像素尺寸，用来把 uv 换回像素坐标
uniform vec4  uBaseColor;   // 冷底（深紫黑）
uniform vec4  uFlowColor;   // 主流（亮粉）
uniform vec4  uHotColor;    // 热核（雾粉紫）

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return v;
}

void main() {
    // 局部像素坐标 → 噪声空间。用像素而不是 uv，是为了让不同尺寸的
    // tooltip 里热羽的粗细一致（否则同一个效果在窄面板上会被压扁）。
    vec2 px = (texCoord - 0.5) * uPanelSize;
    vec2 p  = px / max(uScale, 1.0);

    float t = uTime * uSpeed;

    // ① 对流：向上飘 + 低频横向摆动
    p.y += t;
    p.x += 0.22 * sin(t * 0.7 + p.y * 1.6 + fbm(p * 0.8) * 1.5);

    // ② 域扭曲
    float warp = fbm(p * 1.5 + vec2(0.0, -t * 0.35));
    float heat = fbm(p * 2.4 + warp * 1.3);

    // ③ 热源在下沿（texCoord.y = 1 是下沿）
    float source = texCoord.y;
    float temp = clamp(heat * 0.80 + source * 0.40 - 0.22, 0.0, 1.0);

    // ④ 色阶：冷底 → 亮粉 → 雾粉紫热核
    float glow = smoothstep(0.18, 0.92, temp) * uIntensity;
    vec3 col = mix(uBaseColor.rgb, uFlowColor.rgb, glow);
    float core = smoothstep(0.72, 1.00, temp) * uIntensity;
    col = mix(col, uHotColor.rgb, core * 0.75);

    // ⑤ 收边：四边压暗（smoothstep 的两个边界必须递增，所以右边/下边写成 1 - ...）
    vec2 e = smoothstep(vec2(0.0), vec2(0.07), texCoord)
           * (vec2(1.0) - smoothstep(vec2(0.93), vec2(1.0), texCoord));
    col *= mix(0.45, 1.0, e.x * e.y);
    col *= mix(1.06, 0.92, texCoord.y);

    float alpha = mix(uBaseColor.a, 0.985, glow);
    fragColor = vec4(col, alpha);
}
