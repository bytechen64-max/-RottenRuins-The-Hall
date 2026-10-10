#version 150 core

// ─────────────────────────────────────────────────────────────
// 黑洞 —— 纯引力透镜（3D 球体，光线追踪）
//
// 参考实现：
//   Shadertoy 黑洞 raymarch（步进弯曲光线 + 史瓦西半径捕获判定）
//   项目 docs/shader-pack-compat.md（Oculus/Iris 光影兼容：延迟回放写主 RT）
//
// 几何：实体渲染路径绘制一个真实 3D 球（半径 6·Rs），顶点经 Java 侧
// modelView 变换为视图空间坐标（vPos = 球面表面点）。球体自带真实深度，
// 前方方块正确遮挡、球体后半球被前半球深度剔除 —— 看起来是实体而非片。
//
// 片元着色器：相机位于视图空间原点，rd = normalize(vPos) 为视线方向。
// 对光线做 1/r² 引力弯曲积分；逃逸光线按弯曲方向的屏幕位移差采样场景
// 拷贝 → 真实透镜；被视界（Rs）捕获 → 纯黑光子环。
//
// 透镜强度包络基于碰撞参数 impact：球面几何保证 impact ≤ 球半径(6·Rs)，
// 在 4.5→5.5·Rs 淡出到零，球面边缘处输出与场景完全一致 → 无缝。
// ─────────────────────────────────────────────────────────────

// 步数。透镜球体半径是 10·Rs，但每步长度由 dt 钳制在 [0.06, 1.4]·Rs，
// 所以步长随靠近黑洞自动变细（外侧粗、内侧细），80 步足够覆盖
// 半径 20·Rs 的积分区间；这里再留一点余量给收缩动画时的小半径。
#define STEPS 96

uniform sampler2D ScreenTexture;
uniform mat4 ProjMat;
uniform vec3 uBlackHolePos;
uniform float uRadius;        // 史瓦西半径 Rs（方块）
uniform float uGrav;          // 弯曲强度（bend * Rs * Rs）
uniform float uSphereRadius;  // 渲染球体半径（方块）= SPHERE_RADIUS * Rs * 动画缩放

in vec4 vertexColor;
in vec3 vNormal;
in vec3 vPos;
out vec4 fragColor;

// 视图空间方向 → 屏幕 UV（方向取消失点投影）
vec2 dirToUV(vec3 d) {
    vec4 clip = ProjMat * vec4(d, 0.0);
    vec2 ndc = clip.xy / clip.w;
    return clamp(ndc * 0.5 + 0.5, vec2(0.001), vec2(0.999));
}

void main() {
    float Rs = max(uRadius, 0.05);
    vec3 ro = vec3(0.0);
    vec3 rd = normalize(vPos);

    // 片元真实屏幕 UV（锚点）
    vec4 clipPos = ProjMat * vec4(vPos, 1.0);
    vec2 baseUV = clamp((clipPos.xy / clipPos.w) * 0.5 + 0.5, vec2(0.001), vec2(0.999));

    vec3 toBH = uBlackHolePos;
    float along = dot(toBH, rd);
    vec3 closest = rd * along;
    float impact = length(toBH - closest);   // 视线到黑洞中心的垂距

    // 透镜强度包络。
    // 渲染器画的是一个半径 uSphereRadius 的球，光线在球面处与黑洞
    // 相切 —— 这决定了透镜的**最大可见范围**。要让折射铺满整个球面，
    // 淡出必须一直延伸到球面半径，而不是提前收掉。
    // 旧写法把淡出收在 5.5·Rs（球半径只有 6·Rs），于是球面外侧那一圈
    // impact ∈ [5.5Rs, 6Rs] 完全没有透镜，观感上就只剩边缘一点点。
    // 现在淡出落在 [0.62, 1.0]·球半径，既铺满整个球面，
    // 又在球面边缘严格归零 → 不会出现矩形/硬边。
    float sphereR = max(uSphereRadius, 0.05);
    float falloff = 1.0 - smoothstep(0.62 * sphereR, 1.0 * sphereR, impact);

    // 光线步进的积分区间。必须覆盖到球面之外：偏折最明显的部分是
    // 大碰撞参数的引力偏折，若只在 ±12·Rs 内积分，球面外围那圈
    // 就既没有透镜、也没有偏折。取 ±20·Rs 与渲染球体半径(20·Rs)对齐。
    float startDist = max(0.0, along - 20.0 * Rs);
    float stopDist  = along + 20.0 * Rs;
    vec3 pos = ro + rd * startDist;
    vec3 dir = rd;
    float traveled = startDist;
    bool captured = false;

    for (int i = 0; i < STEPS; i++) {
        vec3 rel = uBlackHolePos - pos;
        float m = length(rel);
        if (m < Rs * 0.999) { captured = true; break; }

        vec3 g = rel / max(m, 1e-4);
        float grav = uGrav * falloff / (m * m + Rs * Rs * 0.25);
        float dt = clamp(m - Rs, 0.06 * Rs, 1.4 * Rs);
        dir = normalize(dir + g * grav * dt);
        pos += dir * dt;
        traveled += dt;
        if (traveled > stopDist) break;
    }

    vec3 col;
    if (captured) {
        col = vec3(0.0);                     // 视界内 —— 纯黑
    } else {
        vec2 uvHere = dirToUV(rd);
        vec2 uvBent = dirToUV(dir);
        vec2 uvLensed = clamp(baseUV + (uvBent - uvHere), vec2(0.001), vec2(0.999));
        col = texture(ScreenTexture, uvLensed).rgb;
    }

    fragColor = vec4(col, 1.0);
}
