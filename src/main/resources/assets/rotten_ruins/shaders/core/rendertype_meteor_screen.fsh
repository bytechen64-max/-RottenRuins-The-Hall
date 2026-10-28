#version 150 core

// ═════════════════════════════════════════════════════════════
//  陨石事件 · 动漫冲击帧（片元阶段）—— 整帧后处理
//
//  ── 为什么重写 ──
//  之前几版都是"在世界之上叠一个形状"，观感像贴纸，冲击力远不如参考模组
//  （the-shooting-star-demo 的 composite.fsh）。参考的实现是
//  **把真实画面重画成漫画分镜**：
//
//    ① 边缘墨线  —— 对场景亮度做一次 2 采样差分，梯度大的地方画黑线，
//                    于是地形 / 怪物 / 十字全都被描了边；
//    ② 局部二值  —— 不跟固定的 0.5 比，而是跟**周围 8 个方向的平均亮度**
//                    比（自适应阈值）。这样就算白闪把整帧推亮了，
//                    形状也不会糊成一片；
//    ③ 网点      —— 中间调用旋转 45° 的点阵，暗的地方点大、亮的地方点小
//                    （漫画的网屏）；
//    ④ 高光      —— 比周围平均亮 0.18 以上直接给纯白。
//
//  这四样叠起来才是"动漫"，不是画几个星星。冲击力来自**整个画面变样**。
//
//  ── 扩散中心 ──
//  参考的 Impact 是 vec4(strength, mode, centre uv)：本文件用 uCenter +
//  从它算出的 d 来放冲击环，所以扩散从爆炸点开始，不是屏幕正中。
// ═════════════════════════════════════════════════════════════

uniform sampler2D SceneTexture;   // 场景拷贝（AFTER_LEVEL 那一帧）

uniform float uTime;
uniform float uIntensity;     // 0..1：0 = 原画面，1 = 完全漫画化
uniform float uSeed;
uniform vec2  uCenter;        // 爆炸点的屏幕 uv
uniform float uCenterValid;   // 0 = 在相机背后，退回屏幕中心
uniform vec2  uTexel;         // 1/宽, 1/高（场景拷贝的像素尺寸）

in vec2 vUV;
in vec4 vColor;

out vec4 fragColor;

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 px(vec2 uv) {
    return texture(SceneTexture, clamp(uv, 0.001, 0.999)).rgb;
}

void main() {
    vec2 uv = vUV;
    float aspect = uTexel.y / uTexel.x;      // 宽 / 高
    float t = uTime;

    // 爆炸点的屏幕坐标（背后 → 屏幕中心）
    vec2 c = mix(vec2(0.5), uCenter, uCenterValid);
    vec2 d = (uv - c) * vec2(aspect, 1.0);
    float r = length(d);

    // ── ① 边缘墨线 ──
    vec2 st = uTexel * 1.5;
    float gx = lum(px(uv + vec2(st.x, 0.0))) - lum(px(uv - vec2(st.x, 0.0)));
    float gy = lum(px(uv + vec2(0.0, st.y))) - lum(px(uv - vec2(0.0, st.y)));
    // 阈值压到 0.02~0.09：夜里的地形边缘梯度很小，用参考模组那套
    // 0.06~0.18 会一条线都描不出来（整帧变成一张纯色纸）。
    float ink = smoothstep(0.02, 0.09, length(vec2(gx, gy)));

    // ── 平滑亮度 ──
    //  直接拿单像素亮度去比会把"噪声"当结构：夜空只有 0.02~0.05，
    //  MC 的天空本身还带一层抖动噪点（防色带用的），于是阈值切在噪声上，
    //  整片天空被量化成一块块橙/黑 —— 实机截图里那些"奇怪的像素"就是它。
    //  先做 5 点平均，再给二值加一个**绝对亮度门限**：暗部一律当纸。
    float lRaw = lum(px(uv));
    float l = (lRaw * 2.0
             + lum(px(uv + vec2(st.x * 5.0, 0.0)))
             + lum(px(uv - vec2(st.x * 5.0, 0.0)))
             + lum(px(uv + vec2(0.0, st.y * 5.0)))
             + lum(px(uv - vec2(0.0, st.y * 5.0)))) / 6.0;

    // ── ② 局部自适应阈值（8 方向平均，半径放大到 0.06）──
    float avg = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        avg += lum(px(uv + vec2(cos(a) / aspect, sin(a)) * 0.06));
    }
    avg /= 8.0;
    // 近邻的窄平均：和上面的宽平均（0.06）做"差分之差"，
    //  两边都是平滑量，噪声再大也不会被切成碎块。
    float near = 0.0;
    for (int i = 0; i < 4; i++) {
        float a = float(i) * 1.570796;
        near += lum(px(uv + vec2(cos(a) / aspect, sin(a)) * 0.012));
    }
    near *= 0.25;

    // two = 1 表示"比周围亮" → 亮部。
    //  ⚠️ 用**相对对比度**而不是绝对差值：夜里的绝对差只有 0.01 量级，
    //     `step(0.008, l-avg)` 会切在天空抖动噪点上，整片天空被量化成
    //     橙/黑碎块（实机截图里那些"奇怪的像素"就是它）。
    //     相对量把噪声（<5%）压住、把边缘（>25%）留下，而且夜里的地形
    //     轮廓依然能翻出来。
    //  阈值保持很小（0.006），这样**一大片亮区**整体会被判成"亮"（负片里变黑块）；
    //  真正压噪声的是后面那道绝对亮度门：比 8% 还暗的表面一律当纸，
    //  夜空于是永远是干净的一片纸，不会碎成噪点。
    float two = step(0.006, near - avg) * smoothstep(0.03, 0.08, near);

    // ── ③ 网点（旋转 45° 的点阵，暗的地方点大）──
    vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (uv / uTexel) / 5.0;
    tp += uSeed * 0.37;
    float tone = clamp((l - avg) * 6.0 + 0.5, 0.0, 1.0);
    float dots = step(length(fract(tp) - 0.5), sqrt(clamp(1.0 - tone, 0.0, 1.0)) * 0.62);
    // ⚠️ 中间调必须按**绝对亮度**判定，不能用 tone（那只是"比周围亮多少"）。
    //    否则一片纯黑的夜空 l≈avg ⇒ tone=0.5 ⇒ 整屏被网点铺满，
    //    画面变成一张红白点阵壁纸（第一版就是这样）。
    float mid = smoothstep(0.10, 0.26, l) * (1.0 - smoothstep(0.55, 0.78, l));

    // ── ④ 高光 ──
    float core = step(avg + 0.18, l);

    // ── 负片二值 + 快速反转 ──
    //  参考模组冲击帧的观感是**整帧负片**：夜空的暗部变成纸色、
    //  被火光照亮的部分变成黑，再叠墨线与网点。
    //  极性千万别搞反：`mix(paper, ink, two)` 才是"暗 → 纸、亮 → 墨"；
    //  之前写成 `1.0 - two`，夜空直接变纯黑，等于什么都没发生。
    //
    //  每 ~80ms 换一次调色板（白纸 / 橙纸 / 黑纸），并且每帧把极性也翻一次，
    //  于是观感是"大量的颜色反转"而不是一张静态分镜。
    float beat  = floor(uTime * 12.0);
    float style = mod(beat + floor(uSeed), 3.0);
    vec3 paper = style < 0.5 ? vec3(0.97)
               : style < 1.5 ? vec3(0.98, 0.32, 0.04)
                             : vec3(0.03);
    vec3 inkC  = style < 0.5 ? vec3(0.02)
               : style < 1.5 ? vec3(0.02)
                             : vec3(0.95);
    float flip = mod(beat, 2.0);           // 0 负片 / 1 正片
    float lit  = mix(two, 1.0 - two, flip);

    vec3 frame = mix(paper, inkC, lit);                 // 二值
    frame = mix(frame, inkC, ink);                      // 墨线
    frame = mix(frame, mix(inkC, paper, dots), mid * 0.8); // 网点
    frame = mix(frame, paper, core * 0.8);              // 高光

    // 爆心附近整体推向纸色（不是画圆，只是亮度调制）：
    //  靠近爆炸点的画面被"烧穿"，远离的保持二值。r 从 uCenter 来。
    float heat = 1.0 - smoothstep(0.05, 0.34, r);
    frame = mix(frame, paper, heat * 0.15);

    // ── 起手 60ms 的全屏纯白（曝光帧）──
    float flash = 1.0 - smoothstep(0.0, 0.06, t);
    frame = mix(frame, vec3(1.0), flash);

    // ── 和原画面混合：0 = 原样，1 = 完全漫画化 ──
    vec3 col = px(uv);
    col = mix(col, frame, clamp(uIntensity, 0.0, 1.0));

    fragColor = vec4(col, 1.0);
}
