#version 150 core

// 剪影遮罩程序。
//
// 这个 pass 的唯一职责：把"这件物品在屏幕上占据哪些像素"以及"这些像素应该
// 用什么颜色描边"写成一张离屏遮罩贴图。它不画任何最终画面。
//
//   rgb   = 该物品本帧的描边颜色（颜色模式在这里就烘进去）
//   a     = 覆盖率，目前是二值的（0 或 1），靠 discard 保证只写不透明像素
//
// 之所以把颜色烘进遮罩，是因为环形合成 pass 是全屏一次性的：颜色随遮罩逐像素
// 携带，同一帧里不同物品就能有各自的描边颜色，而不需要按颜色分批重画。
// 同时这也修掉了旧实现的一个隐患 —— 旧代码把颜色设成共享 ShaderInstance 的
// uniform，同一批次里多个物品会互相覆盖，只有最后一个的颜色生效。

uniform sampler2D Sampler0;

// 主色 / 副色
uniform vec4 MaskColor;
uniform vec4 SecondaryColor;

// 0 = 纯色, 1 = 双色滚动, 2 = 彩虹, 3 = 色板滚动
uniform float ColorMode;

uniform float ColorScrollSpeed;
uniform float Time;

// 渐变的空间频率（模型空间单位）。默认 18/12 是给"手持/世界"用的：
// 物品在屏幕上占得大，所以那个波长看着是柔和的宽带。
// 物品栏图标只有 16px，同样的频率意味着一圈描边里过 3~4 次黑白 ——
// 于是管线在 GUI 上下文里把它调小（见 SplendidingConfig.guiOutlineGradientScale）。
uniform vec2 ColorFreq;

// 流动速度（弧度/秒）。GUI 会按 guiOutlineSpeedScale 放慢。
uniform float ColorSpeed;

// 暗端下限：0 = 纯黑；GUI 会抬到 0.35 左右。
// 纯黑遇上模型自带的黑描边会糊成一整块，抬高一点两端才都看得见。
uniform float ColorFloor;

uniform float PaletteSize;
uniform vec4 PaletteColor0;
uniform vec4 PaletteColor1;
uniform vec4 PaletteColor2;
uniform vec4 PaletteColor3;
uniform vec4 PaletteColor4;
uniform vec4 PaletteColor5;
uniform vec4 PaletteColor6;
uniform vec4 PaletteColor7;

// 低于这个 alpha 的纹素不算物品的一部分。用来贴合物品真实形状，
// 否则模型四边形的矩形边界（含大片透明区）都会被算进剪影。
uniform float AlphaCutoff;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 modelPos;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec4 paletteAt(int index) {
    if (index <= 0) return PaletteColor0;
    if (index == 1) return PaletteColor1;
    if (index == 2) return PaletteColor2;
    if (index == 3) return PaletteColor3;
    if (index == 4) return PaletteColor4;
    if (index == 5) return PaletteColor5;
    if (index == 6) return PaletteColor6;
    return PaletteColor7;
}

vec3 paletteScrollColor(vec2 uv) {
    int count = int(clamp(PaletteSize, 1.0, 8.0));
    float flow = fract(uv.x * 0.85 + uv.y * 0.55 - Time * 0.03 * ColorSpeed * ColorScrollSpeed) * float(count);
    int i0 = int(floor(flow)) % count;
    int i1 = (i0 + 1) % count;
    float blend = fract(flow);
    return mix(paletteAt(i0).rgb, paletteAt(i1).rgb, blend);
}

vec3 resolveColor(vec2 uv) {
    if (ColorMode < 0.5) {
        return MaskColor.rgb;
    }
    float flow = uv.x * ColorFreq.x + uv.y * ColorFreq.y - Time * ColorSpeed * ColorScrollSpeed;
    if (ColorMode < 1.5) {
        float m = 0.5 + 0.5 * sin(flow);
        m = ColorFloor + (1.0 - ColorFloor) * m;      // 抬高暗端：别让黑半段吃掉整圈
        return mix(MaskColor.rgb, SecondaryColor.rgb, m);
    }
    if (ColorMode < 2.5) {
        float hue = fract(uv.x * 0.22 + uv.y * 0.14 - Time * 0.02 * ColorSpeed * ColorScrollSpeed);
        return hsv2rgb(vec3(hue, 0.85, 1.0));
    }
    return paletteScrollColor(uv);
}

void main() {
    float texAlpha = texture(Sampler0, texCoord0).a;
    if (texAlpha < AlphaCutoff) {
        discard;
    }

    // 遮挡判定不在这里做。
    //
    // 管线在画剪影之前，把"这件物品当时画进的那个 framebuffer 的深度附件"
    // 临时挂到遮罩 FBO 上，用**硬件深度测试**（LEQUAL + 不写深度，见
    // OutlineMaskRenderType）来剔除被挡住的部分。这样完全不需要知道那份深度
    // 是什么格式/什么约定 —— 光影包的 GBuffer 深度是纹理还是 renderbuffer、
    // 是不是 reversed-Z、far plane 有没有被改，全都无所谓。

    // 保持不受全局 shaderColor / ColorModulator 影响：原版发光描边是
    // 纯色不带光照的，这样在洞穴里也不会变暗。
    vec3 color = resolveColor(modelPos.xy);
    fragColor = vec4(color, 1.0);
}
