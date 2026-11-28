#version 150 core

// 物品的「体积火焰」：在**视图空间**里围绕物品的固定尺寸盒子里做 ray marching。
//
// 为什么用视图空间而不是世界空间：
//   * 描边管线本来就在视图空间重放（pose × modelView），盒子用同一套坐标就**一定是围着物品的**，
//     不会因为手部渲染/实体渲染的变换差异而漂到屏幕别处去；
//   * 盒子的世界尺寸不变（视图空间单位 = 格），所以"火大小固定"仍然成立；
//   * 噪声仍然按世界坐标求值（用相机基向量把采样点搬过去），火永远沿世界上方窜。
//
// 遮挡：管子在捕获点把场景深度 blit 到一张纹理，这里按深度截断射线 —— 全程不采样
// "当前正被画进去的那个 FBO" 的附件。

uniform sampler2D DepthSampler;

// 剪影的模糊图（与光雾同一套 1/4 分辨率场）：用来把火限制在"贴着轮廓"的一圈，
// 而不是填满整个盒子（盒子必然是个方的）。
uniform sampler2D MaskBlur;
uniform mat4 Proj;
uniform float UseMask;

uniform vec2 ScreenSize;
uniform mat4 InvProj;

// 视图空间：火焰盒与物品包围盒
uniform vec3 BoxMin;
uniform vec3 BoxMax;
uniform vec3 ItemMin;
uniform vec3 ItemMax;

// 相机（世界空间）：只用来把采样点搬到世界空间求噪声
uniform vec3 CamRight;
uniform vec3 CamUp;
uniform vec3 CamFwd;
uniform vec3 CamPos;

// 物品底部的世界 y（火焰从这条高度往上长）
uniform float ItemBottomY;

uniform float Time;
uniform float Opacity;
uniform float UseDepth;   // 0 = 没有可用的深度快照，只按盒子边界截断

uniform vec3 ColorHot;
uniform vec3 ColorCool;

out vec4 fragColor;

// ── 3D value noise ──────────────────────────────────────────────
float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(
            mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
            mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
            f.z);
}

float fbm3(vec3 p) {
    return vnoise(p) * 0.58 + vnoise(p * 2.13 + 7.7) * 0.28 + vnoise(p * 4.37 + 19.1) * 0.14;
}

float sdBox(vec3 p, vec3 halfSize) {
    vec3 q = abs(p) - halfSize;
    return length(max(q, vec3(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0);
}

void main() {
    vec2 uv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec2 ndc = uv * 2.0 - 1.0;

    // ── 视线方向（视图空间，相机在原点）──
    vec4 farPoint = InvProj * vec4(ndc, 1.0, 1.0);
    if (abs(farPoint.w) < 1.0e-6) discard;
    vec3 dir = normalize(farPoint.xyz / farPoint.w);
    // 轴向分量恰好为 0 → 1/0 = Inf → NaN。用有限大值代替（等价于该轴永不相交）。
    vec3 invDir = vec3(
            abs(dir.x) < 1.0e-5 ? 1.0e6 : 1.0 / dir.x,
            abs(dir.y) < 1.0e-5 ? 1.0e6 : 1.0 / dir.y,
            abs(dir.z) < 1.0e-5 ? 1.0e6 : 1.0 / dir.z);

    // ── 视图空间 ray-box ──
    vec3 t0 = BoxMin * invDir;
    vec3 t1 = BoxMax * invDir;
    vec3 tMin = min(t0, t1);
    vec3 tMax = max(t0, t1);
    float tEnter = max(max(tMin.x, tMin.y), max(tMin.z, 0.0));
    float tExit = min(min(tMax.x, tMax.y), tMax.z);
    tEnter = max(tEnter, 0.0);
    if (!(tExit > tEnter) || !(tExit < 1.0e4)) discard;
    tExit = min(tExit, 1.0e4);

    // ── 深度快照截断 ──
    if (UseDepth > 0.5) {
        float depth = texture(DepthSampler, uv).r;
        if (depth > 0.0 && depth < 1.0) {
            vec4 viewPos = InvProj * vec4(ndc, depth * 2.0 - 1.0, 1.0);
            if (abs(viewPos.w) > 1.0e-6) {
                float sceneT = length(viewPos.xyz / viewPos.w);
                if (sceneT > 0.0) {
                    tExit = min(tExit, sceneT);
                }
            }
        }
    }
    if (!(tExit > tEnter)) discard;

    // ── 步进 ──
    const int STEPS = 30;
    float dt = (tExit - tEnter) / float(STEPS);

    vec3 itemCenter = (ItemMin + ItemMax) * 0.5;
    vec3 itemHalf = max((ItemMax - ItemMin) * 0.5, vec3(0.01));

    vec3 acc = vec3(0.0);
    float transmittance = 1.0;
    float t = Time;

    for (int i = 0; i < STEPS; i++) {
        vec3 pos = dir * (tEnter + (float(i) + 0.5) * dt);

        // 采样点到世界（只用于噪声与"高度"）
        vec3 worldPos = CamPos + CamRight * pos.x + CamUp * pos.y + CamFwd * pos.z;
        float height = worldPos.y - ItemBottomY;

        // 火源形状 = 贴物品外壳的一层 + 物品上方一根细火柱（半径被噪声啃边）
        float dItem = sdBox(pos - itemCenter, itemHalf);
        float shell = exp(-max(dItem, 0.0) * 15.0) * smoothstep(-0.015, 0.03, dItem);

        vec2 toAxis = pos.xz - itemCenter.xz;
        float radial = length(toAxis);

        // 上升平流 + 域扭曲
        vec3 q = worldPos * 9.0 + vec3(0.0, -t * 1.6, 0.0);
        float warp = vnoise(q * 0.6 + vec3(0.0, -t * 0.4, 0.0));
        float n = fbm3(q + warp * 1.1);

        float plumeRadius = 0.26 + 0.10 * (n - 0.5);
        float plume = smoothstep(plumeRadius + 0.10, 0.02, radial)
                * smoothstep(-0.03, 0.18, height)
                * (1.0 - smoothstep(0.42, 0.92, height));
        float shape = clamp(shell * 1.05 + plume * 1.10, 0.0, 1.0);

        // 盒面淡出：不要出现立方体硬边
        vec3 dFaces = min(pos - BoxMin, BoxMax - pos);
        float boxFade = smoothstep(0.0, 0.12, min(min(dFaces.x, dFaces.z), dFaces.y));

        float density = shape * boxFade * smoothstep(0.32, 0.82, n);

        // 只保留贴着剪影轮廓的一圈：把采样点投到屏幕，查模糊剪影的密度。
        //   prox = |2h-1|：0 = 轮廓上，1 = 模糊半径外
        //   轮廓内部（h 接近 1）也排除掉 —— 火只长在外侧。
        if (UseMask > 0.5 && density > 0.001) {
            vec4 clip = Proj * vec4(pos, 1.0);
            if (clip.w > 1.0e-4) {
                vec2 suv = (clip.xy / clip.w) * 0.5 + 0.5;
                float h = texture(MaskBlur, suv).a;
                float prox = abs(2.0 * h - 1.0);
                float nearEdge = 1.0 - smoothstep(0.15, 0.95, prox);
                float outside = 1.0 - smoothstep(0.55, 0.80, h);
                density *= nearEdge * outside;
            } else {
                density = 0.0;
            }
        }

        if (density > 0.001) {
            float heat = clamp(density * 1.6 + shell * 0.30
                    + (1.0 - smoothstep(0.0, 0.40, height)) * 0.28, 0.0, 1.0);
            vec3 col = mix(ColorCool, ColorHot, smoothstep(0.05, 0.55, heat));
            col = mix(col, vec3(1.0, 0.94, 0.82), smoothstep(0.78, 1.08, heat));

            float stepAlpha = 1.0 - exp(-density * 11.0 * dt);
            acc += transmittance * col * stepAlpha;
            transmittance *= 1.0 - stepAlpha;
            if (transmittance < 0.02) break;
        }
    }

    // NaN/Inf 绝对不许进帧缓冲（加法混合会污染整屏）
    if (any(isnan(acc)) || any(isinf(acc)) || !(transmittance == transmittance)) discard;

    float alpha = (1.0 - transmittance) * Opacity;
    if (alpha < 0.004 || any(isnan(vec3(alpha)))) discard;

    fragColor = vec4(acc * Opacity, alpha);
}
