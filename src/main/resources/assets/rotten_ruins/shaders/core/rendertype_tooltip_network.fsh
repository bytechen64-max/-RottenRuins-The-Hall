#version 150 core

// tooltip 底板 · 细胞网络（片元阶段）：浮动的小点被邻近点连线，串成一层会缓慢旋转、
// 呼吸的网格，点上有脉动高光。四层不同尺度叠起来产生纵深，没有贴图。
// 移植自 update.txt 里的 Shadertoy 版：去掉 iMouse / iChannel0（音频 glow），
// 面板尺寸由 uPanelSize 换成像素坐标，时间由 uTime 驱动。
in vec2 texCoord;

uniform float uTime;        // 秒（游戏时间 + 帧插值），暂停时网络也停
uniform float uIntensity;   // 整体强度，0 = 只剩底色
uniform float uLayers;      // 叠几层尺度，越大越密也越费
uniform vec2  uPanelSize;   // 面板像素尺寸
uniform vec4  uBaseColor;   // 底色（只它的 alpha 参与）
uniform vec4  uFlowColor;   // 连线色
uniform vec4  uHotColor;    // 脉动点的高光色

out vec4 fragColor;

// Shadertoy 的 S(a,b,x) 允许 a > b（反向渐隐）。GLSL 的 smoothstep 在 edge0 >= edge1
// 时结果未定义，这里手写一份，保证各驱动一致。
float S(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float N21(vec2 p) {
    vec3 a = fract(vec3(p.xyx) * vec3(213.897, 653.453, 253.098));
    a += dot(a, a.yzx + 79.76);
    return fract((a.x + a.y) * a.z);
}

// 每个格点的振荡偏移：相位与幅度都由 id 决定，所以图形稳定不会每帧乱跳。
vec2 GetPos(vec2 id, vec2 offs, float t) {
    float n = N21(id + offs);
    float n1 = fract(n * 10.0);
    float n2 = fract(n * 100.0);
    float a = t + n;
    return offs + vec2(sin(a * n1), cos(a * n2)) * 0.4;
}

float df_line(vec2 a, vec2 b, vec2 p) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

// 一条线段：r1 是线宽，d2 是长度 —— 太短或太长都渐隐，避免格点重合时糊成一团。
float line(vec2 a, vec2 b, vec2 uv) {
    float r1 = 0.04;
    float r2 = 0.01;

    float d = df_line(a, b, uv);
    float d2 = length(a - b);
    float fade = S(1.5, 0.5, d2);
    fade += S(0.05, 0.02, abs(d2 - 0.75));
    return S(r1, r2, d) * fade;
}

// 一层网络：3x3 邻域 + 四条斜线，外加点上的脉动高光。
float NetLayer(vec2 st, float n, float t) {
    vec2 id = floor(st) + n;
    st = fract(st) - 0.5;

    vec2 p[9];
    int i = 0;
    for (float y = -1.0; y <= 1.0; y++) {
        for (float x = -1.0; x <= 1.0; x++) {
            p[i++] = GetPos(id, vec2(x, y), t);
        }
    }

    float m = 0.0;
    float sparkle = 0.0;
    for (int k = 0; k < 9; k++) {
        m += line(p[4], p[k], st);

        float d = length(st - p[k]);
        float s = 0.005 / max(d * d, 1e-5);
        s *= S(1.0, 0.7, d);
        float pulse = sin((fract(p[k].x) + fract(p[k].y) + t) * 5.0) * 0.4 + 0.6;
        pulse = pow(pulse, 20.0);
        sparkle += s * pulse;
    }

    m += line(p[1], p[3], st);
    m += line(p[1], p[5], st);
    m += line(p[7], p[5], st);
    m += line(p[7], p[3], st);

    float sPhase = (sin(t + n) + sin(t * 0.1)) * 0.25 + 0.5;
    sPhase += pow(sin(t * 0.1) * 0.5 + 0.5, 50.0) * 5.0;
    m += sparkle * sPhase;
    return m;
}

void main() {
    vec2 res = max(uPanelSize, vec2(1.0));
    vec2 uv = (texCoord * res - res * 0.5) / res.y;

    float t = uTime * 0.1;
    float s = sin(t);
    float c = cos(t);
    mat2 rot = mat2(c, -s, s, c);
    vec2 st = uv * rot;

    float m = 0.0;
    float layers = max(uLayers, 1.0);
    for (float i = 0.0; i < 1.0; i += 1.0 / layers) {
        float z = fract(t + i);
        float size = mix(15.0, 1.0, z);
        float fade = S(0.0, 0.6, z) * S(1.0, 0.8, z);
        m += fade * NetLayer(st * size, i, uTime);
    }

    // 与 Shadertoy 版一致：四周自然衰减，避免连线在面板边缘被硬切。
    m *= 1.0 - clamp(dot(uv, uv), 0.0, 0.85);
    m = clamp(m, 0.0, 1.6);

    float glow = clamp(m * 0.85, 0.0, 1.0) * uIntensity;
    vec3 col = mix(uBaseColor.rgb, uFlowColor.rgb, glow);
    col = mix(col, uHotColor.rgb, clamp(m * m * 0.55, 0.0, 1.0) * uIntensity);

    // 收边压暗 + 底部略沉，让原版那 1px 边框仍是画面里最利落的一条线。
    vec2 e = smoothstep(vec2(0.0), vec2(0.06), texCoord)
           * (vec2(1.0) - smoothstep(vec2(0.94), vec2(1.0), texCoord));
    col *= mix(0.55, 1.0, e.x * e.y);
    col *= mix(1.04, 0.92, texCoord.y);

    float alpha = mix(uBaseColor.a, 0.985, clamp(m, 0.0, 1.0));
    fragColor = vec4(col, alpha);
}
