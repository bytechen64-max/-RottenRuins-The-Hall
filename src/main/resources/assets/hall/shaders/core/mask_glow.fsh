#version 150

// ── mask 效果层：贴图泛光（glow）─────────────────────────────────────
//
// 语义（v2）：
//   光源 = **本体贴图**（layer0）。它自己的颜色和亮度决定泛光是什么颜色、多亮。
//   范围 = **泛光遮罩**（MaskMin/MaskSize 指的那张图）的红通道。白 = 发光，黑 = 不发。
//
// 也就是说这两张图分工明确：
//   crimson_vow.png                → 光源（剑柄自身是暗红金属，泛光就是暗红色）
//   crimson_vow_mask_glow.png      → 只把剑柄标成白色，于是剑柄发光、剑刃不发光
//                                     （剑刃归 cosmic 星空层管）
//
// 几何来自本体贴图而不是遮罩，这一条是刻意的：ItemModelGenerator 按 alpha 裁几何，
// 本体贴图的外面是透明的 → quad 精确等于武器轮廓；而遮罩图通常是"整张不透明的
// 黑白图"（为了不漏掉外圈像素），拿它烘几何会得到一整块矩形。
//
// 位置换算：
//   local  = (UV0 - BaseMin) / BaseSize      UV0 是图集坐标，先回到本体贴图的 0..1
//   maskUV = MaskMin + local * MaskSize      再映射到泛光遮罩的对应位置
// 两张图必须是同一张画布（同样的尺寸、同样的对齐），这是美术侧的约定。
//
// 混合方式由 json 里的 "blend" 节点决定 —— 这一条踩过坑，值得写清楚：
// ShaderInstance 在构造时解析 "blend"，并在 apply() 里调用 BlendMode.apply()
// （1.20.1 的 ShaderInstance.java:330），而 apply() 发生在 RenderType 的
// setupRenderState() **之后**，所以**着色器 json 的混合会盖掉 RenderType 的
// TransparencyStateShard**。
// 而 "blend" 缺失时的默认值是 new BlendMode() = 混合被**关闭**（REPLACE），
// 不是"加法"、也不是"alpha"：于是遮罩的黑区（rgb 恰好为 0）会把整个物品平面
// 原样写进帧缓冲 —— 症状就是"物品外一个黑框、表面那层被整片吞掉"。
// 所以这里必须显式声明 one/one（加法，与 MaskLayerRenderType 的 ADDITIVE 对齐）：
// 加法下「黑 = 加 0」，遮罩外天然不可见，不需要靠 alpha 去剪。

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

// ── 本体贴图（光源）在图集里的位置，由 MaskUniforms.setBaseSlice 设置 ──
uniform vec2 BaseMin;
uniform vec2 BaseSize;

// ── 泛光遮罩在图集里的位置，由 MaskUniforms.setSlice 设置 ──
uniform vec2 MaskMin;
uniform vec2 MaskSize;
uniform vec2 MaskPixels;

// ── 层参数（来自 MaskLayerSpec）──
uniform vec4 GlowColor;    // 着色（默认白 = 完全用本体贴图自己的颜色）
uniform float Intensity;   // 强度倍率
uniform float GlowWidth;   // 光晕外扩半径，单位是遮罩纹理像素
uniform float GlowSpeed;   // 呼吸速度（0 = 静止）
uniform float Opacity;     // 整体强度
uniform float Phase;       // 相位偏移
uniform float time;        // 游戏 tick，与 cosmic 同一个时间源

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const int RING_DIRECTIONS = 12;
const int RING_COUNT = 3;

/**
 * 采样泛光遮罩红通道。夹在遮罩自己的区域内，一是绝不跨 sprite 采到图集里别人家的
 * 贴图，二是让遮罩边缘向外延展 —— 这正是膨胀（dilate）的行为。
 */
float maskAt(vec2 maskUV) {
    return texture(Sampler0, clamp(maskUV, MaskMin, MaskMin + MaskSize)).r;
}

void main() {
    // ── 光源：本体贴图自己的颜色 ──
    vec4 base = texture(Sampler0, texCoord0);

    // 只在贴图**不透明**的地方发光。
    // 少了这一步，透明背景的 RGB（PNG 里常是非零的残值）也会被加法混合点亮，
    // 于是整块物品平面一起发亮 —— 症状是"整把武器糊成一片、星空层被挤掉"。
    // 这不是"锦上添花"的软化，而是这套加法叠加层的安全边界。
    float coverage = smoothstep(0.0, 0.15, base.a);

    vec2 local = (texCoord0 - BaseMin) / max(BaseSize, vec2(1.0e-6));
    vec2 maskUV = MaskMin + local * MaskSize;

    // 该像素要不要发光（白 = 发光）
    float gate = smoothstep(0.35, 0.62, maskAt(maskUV)) * coverage;

    // 光源亮度：取三个通道的最大值。暗部几乎不发光、亮部强烈发光，
    // 于是泛光的强度也"跟着贴图走"，而不是一刀切。
    float lum = max(max(base.r, base.g), base.b);

    // 1 个遮罩纹理像素在遮罩 UV 空间里的步长
    vec2 pixelStep = GlowWidth / max(MaskPixels, vec2(1.0));

    float haloSum = 0.0;
    float haloWeight = 0.0;
    for (int ring = 1; ring <= RING_COUNT; ring++) {
        float radius = float(ring) / float(RING_COUNT);
        float w = 1.0 - radius * 0.55;
        for (int d = 0; d < RING_DIRECTIONS; d++) {
            float ang = float(d) * 6.2831853 / float(RING_DIRECTIONS) + Phase * 0.15;
            vec2 offset = vec2(cos(ang), sin(ang)) * radius * pixelStep;
            haloSum += maskAt(maskUV + offset) * w;
            haloWeight += w;
        }
    }
    float halo = haloWeight > 0.0 ? haloSum / haloWeight : 0.0;

    // dilate − self：遮罩内部相减归零，只剩溢到遮罩外的那一圈。
    // 与描边那套 ring = dilate(mask, r) − mask 是同一个减法。
    // 外圈同样乘 coverage：贴图透明的地方没有图形，不该被点亮。
    float spill = max(halo - gate, 0.0) * coverage;

    float pulse = 0.80 + 0.20 * sin(time * 0.05 * GlowSpeed + Phase);

    // 加法混合下 alpha 不参与配色（blendFunc 就是 ONE/ONE），所以强度必须自己乘进 rgb。
    float strength = GlowColor.a * Opacity;

    // 光源色 = 本体贴图的颜色 × 自身亮度；GlowColor 只当作着色（默认白 = 不着色）
    vec3 srcCol = base.rgb * lum;
    vec3 rgb = GlowColor.rgb * srcCol * (gate * 1.15 + spill * 1.9) * Intensity * strength * pulse;
    float alpha = clamp(gate * 0.85 + spill * 1.35, 0.0, 1.0) * strength;

    vec4 col = vec4(vertexColor.rgb * rgb * ColorModulator.rgb, vertexColor.a * alpha);

    // 雾：这里**不能**用 linear_fog。它把颜色朝 FogColor 混合，而我们是加法混合，
    // 于是远处那圈"不可见的黑"会变成"加上雾色"—— 整块物品平面会亮成一个雾色方框。
    // 加法层要的是衰减到 0，所以用 linear_fog_fade 当乘数。
    col.rgb *= linear_fog_fade(vertexDistance, FogStart, FogEnd);

    fragColor = col;
}
