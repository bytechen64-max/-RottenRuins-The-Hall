#version 150

// ── mask 效果层（方块版）：death_core ─────────────────────────────────
//
// 效果来源：ShaderToy "Ghosts" by XorDev
//   https://www.shadertoy.com/view/3XXSDB （Tweet 版）
//
// 原作结构原样保留：随深度扭转的 3D 湍流 + 不规则 gyroid 距离场 raymarch +
// tanh 色调映射。为了把它放进方块表面的「mask 窗口」，只改了三处输入：
//
//   原作                           → 这里
//   fragCoord / iResolution        → 视空间片元位置（视线方向）
//   iTime                          → time（游戏 tick）× 0.05 = 秒
//   全屏输出                       → 只写在 mask 白层里；窗口外 discard
//
// 为什么不用屏幕 NDC 算方向（之前出现过"靠近方块转动视角时割裂"）：
//   NDC 是 clip.xy / clip.w，若把除过 w 的值作为 varying 传下来，片元着色器拿到的
//   是"透视校正插值后的 NDC"，在几何被近平面裁切 / 四边形跨相机平面时会在裁剪边
//   或三角形对角线上出现一条明显的裂缝。视空间位置 (ModelViewMat * Position) 是
//   线性变换的结果，插值在裁切前后都连续，因此不会再有这条缝。
//
// mask 语义：
//   白 = 窗口（渲染 Ghosts 内容，alpha = 1，原像素完全不透出来）
//   黑 = 透明（露出方块本体贴图）

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

// 层参数（来自 MaskLayerSpec）
uniform vec4 CoreColor;   // 主色（默认冷青白，alpha 参与整体强度）
uniform float Intensity;  // 亮度倍率
uniform float Opacity;    // 整体强度
uniform float Phase;      // 相位偏移
uniform float time;       // 游戏 tick（20/s）

// 相机参数：IViewRotMat 是 ShaderInstance 自动上传的「视空间 → 世界空间」旋转
// （RenderSystem.setInverseViewRotationMatrix，Minecraft 每帧设置）。

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;

uniform mat3 IViewRotMat;

out vec4 fragColor;

// 原作是 100 步全屏单 pass；这里是「每帧 × 每个可见方块 × 6 个面，
// 但仍然只算 mask 窗口内的像素」，所以压到 64 步，配合下面的色调映射常数。
const int RAY_STEPS = 64;
const int TURB_STEPS = 7;   // 原版 d=1; d<9; d/=.7 恰好 7 次

void main() {
    // ── mask：窗口 ──
    float mask = texture(Sampler0, texCoord0).r;
    float gate = smoothstep(0.03, 0.55, mask);
    if (gate < 0.01) discard;   // 窗口外不画、也不跑 raymarch

    // ── 视空间位置 → 视线方向 → 世界方向 ──
    // 视空间里相机在原点、朝 -Z；片元的 viewPos 就是从相机指向该点的向量。
    // 乘 IViewRotMat（视空间→世界的逆相机旋转）后得到世界方向 —— 体积效果在
    // 世界里锚定，转视角不会像屏幕贴图一样在方块上滑动。
    vec3 rd = normalize(IViewRotMat * normalize(viewPos));

    // ── "Ghosts" 主体 ──
    float t = time * 0.05 + Phase;

    float z = 0.0;
    vec4 acc = vec4(0.0);

    for (int i = 0; i < RAY_STEPS; i++) {
        // 光线采样点
        vec3 p = z * rd;

        // 随深度扭转（原 mat2(cos((z + t)*.1 + vec4(0,33,11,0)))）
        p.xy *= mat2(cos((z + t) * 0.1 + vec4(0.0, 33.0, 11.0, 0.0)));

        // 向前滚动（原 p.z -= 5.*t）
        p.z -= 5.0 * t;

        // 湍流叠加（原 for (d=1.; d<9.; d/=.7) p += cos(p.yzx*d + t)/d）
        float d = 1.0;
        for (int k = 0; k < TURB_STEPS; k++) {
            p += cos(p.yzx * d + t) / d;
            d /= 0.7;
        }

        // 到不规则 gyroid 的距离（原 z += d = .02 + abs(2. - dot(cos(p), sin(p.yzx*.6))) / 8.）
        d = 0.02 + abs(2.0 - dot(cos(p), sin(p.yzx * 0.6))) / 8.0;
        z += d;

        // 颜色累积 + 光晕衰减（原 O += vec4(z/7., 2, 3, 1) / d）
        acc += vec4(z / 7.0, 2.0, 3.0, 1.0) / d;
    }

    // tanh 色调映射（原 O = tanh(O*O/1e7)；步数从 100 降到 64，常数同步放大补亮度）
    vec3 ghost = tanh(acc.rgb * acc.rgb / 5.0e6);

    // ── 输出：窗口内不透明，窗口外透明 ──
    float strength = CoreColor.a * Opacity * Intensity;
    vec3 rgb = CoreColor.rgb * ghost * strength;

    // 自发光层：只乘顶点色 / ColorModulator，不乘光照贴图
    rgb *= vertexColor.rgb * ColorModulator.rgb;

    // 透明层用线性雾衰减，不用 linear_fog（它会朝雾色混合）
    rgb *= linear_fog_fade(vertexDistance, FogStart, FogEnd);

    fragColor = vec4(rgb, gate);
}
