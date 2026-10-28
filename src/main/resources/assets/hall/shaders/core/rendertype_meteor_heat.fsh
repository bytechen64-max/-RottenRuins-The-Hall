#version 150 core

// ═════════════════════════════════════════════════════════════
//  陨石热浪折射（片元阶段）—— **只在陨石周围的一块四边形上**
//
//  第一版是一趟整屏后处理：它把整个画面替换成"再采样一次场景"的结果，
//  于是还没落地就整屏糊掉（实机截图里满屏黑白噪点）。
//
//  现在改成"围绕陨石的一层着色层"：Java 侧沿尾迹放一串正对镜头的四边形，
//  每个四边形只在自己的范围内（局部 uv 的圆）偏移采样离屏场景，
//  边缘 alpha 归零，所以**四边形以外一个像素都不碰**。
//
//  采样源是离屏的 SceneTexture（绘制前刚拷好的那一帧），
//  偏移带色散，看起来就是热空气在抖。
// ═════════════════════════════════════════════════════════════

uniform sampler2D SceneTexture;   // 离屏场景拷贝

uniform float uTime;
uniform float uStrength;      // 0..1：随下落进度淡入
uniform vec2  uScreenSize;    // 屏幕像素尺寸（gl_FragCoord → uv）

in vec4 vColor;

out vec4 fragColor;

void main() {
    // 局部 uv：四边形的 Color.rg
    vec2 luv = vColor.rg * 2.0 - 1.0;
    float r = length(luv);

    // 只在圆内生效，边缘渐隐 —— 这是"四边形外不碰"的关键
    float mask = (1.0 - smoothstep(0.30, 1.0, r)) * uStrength;
    if (mask <= 0.002) discard;

    vec2 suv = gl_FragCoord.xy / uScreenSize;

    // 热浪方向：沿局部径向抖，幅度随时间/位置变化
    vec2 dir = normalize(luv + vec2(1.0e-5));
    float n1 = sin(suv.y * 150.0 + uTime * 14.0)
             * sin(suv.x * 110.0 - uTime * 10.0);
    float n2 = sin(suv.y * 53.0 - uTime * 6.3 + 1.7);
    vec2 off = dir * (n1 * 0.011 + n2 * 0.004) * mask;
    off.y += n2 * 0.003 * mask;

    // 色散：三个通道给一点不同的偏移，热浪边缘会有彩边
    vec3 col;
    col.r = texture(SceneTexture, clamp(suv + off * 1.15, 0.001, 0.999)).r;
    col.g = texture(SceneTexture, clamp(suv + off, 0.001, 0.999)).g;
    col.b = texture(SceneTexture, clamp(suv + off * 0.85, 0.001, 0.999)).b;

    // alpha = mask：四边形外 alpha 为 0，不参与混合
    fragColor = vec4(col, mask * 0.90);
}
