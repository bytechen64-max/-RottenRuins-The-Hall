#version 150

#define M_PI 3.1415926535897932384626433832795

#moj_import <fog.glsl>

const int cosmiccount = 12;
const int cosmicoutof = 101;
uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;
uniform float yaw;
uniform float pitch;
uniform float externalScale;

uniform float opacity;
uniform int useType;

uniform mat2 cosmicuvs[cosmiccount];

// ── Blocks 图案（CRIMSON_VOW / useType == 17）专用 ──
uniform float blockPatternScale;   // 每 1.0 UV 放多少个 blocks 单元
uniform float blockTimeScale;      // 动画速度
uniform float blockTimeOffset;     // 额外时间偏移（相位微调）

// ── 水面湍流（SILENT_DAYLIGHT / useType == 18）专用 ──
uniform float waveTimeScale;       // 动画速度（乘在游戏 tick 上，见 waterTurbulence 注释）
uniform float wavePatternScale;    // 1.0 UV 上重复几格图案（越大水纹越密）

// ── 遮罩 sprite 在图集里的 UV 矩形（每条 cosmic 绘制都会写一遍，见 CosmicShaders#setMaskSlice）──
// texCoord0 是图集坐标而不是 sprite 内的 0..1，凡是按"物品自身坐标"排图案的 style
// 都得先用这两个 uniform 折回去，否则图案会随 sprite 在图集里的位置漂移。
uniform vec2 maskUvMin;
uniform vec2 maskUvSize;

// ── DEEP_SPACE(0) 专用 ──
uniform float flowMixStrength;     // 流动混色的强度（0 = 关掉，退回原来的纯紫虚空）
uniform float irisSpeed;           // 七彩琉璃星的色相流转速度

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos;

out vec4 fragColor;

// ── 工具函数 ──────────────────────────────────────────────────

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle), c = cos(angle), oc = 1.0 - c;
    return mat4(oc*axis.x*axis.x+c, oc*axis.x*axis.y-axis.z*s, oc*axis.z*axis.x+axis.y*s, 0.0,
                oc*axis.x*axis.y+axis.z*s, oc*axis.y*axis.y+c, oc*axis.y*axis.z-axis.x*s, 0.0,
                oc*axis.z*axis.x-axis.y*s, oc*axis.y*axis.z+axis.x*s, oc*axis.z*axis.z+c, 0.0,
                0.0, 0.0, 0.0, 1.0);
}

float hash31(vec3 p) { p=fract(p*0.3183099+vec3(0.1,0.2,0.3)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }

float noise3D(vec3 x) {
    vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash31(i+vec3(0,0,0)),hash31(i+vec3(1,0,0)),f.x),mix(hash31(i+vec3(0,1,0)),hash31(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash31(i+vec3(0,0,1)),hash31(i+vec3(1,0,1)),f.x),mix(hash31(i+vec3(0,1,1)),hash31(i+vec3(1,1,1)),f.x),f.y),f.z);
}

float fbm6(vec3 p) {
    float f=0.0, amp=0.5, freq=1.0;
    for(int j=0;j<6;j++){ f+=amp*noise3D(p*freq); freq*=2.03; amp*=0.48; }
    return f;
}

vec3 hsv2rgb(vec3 c){ vec4 K=vec4(1.0,2.0/3.0,1.0/3.0,3.0); vec3 p=abs(fract(c.xxx+K.xyz)*6.0-K.www); return c.z*mix(K.xxx,clamp(p-K.xxx,0.0,1.0),c.y); }

float hash2D(vec2 p){ return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453); }

// ── Blocks（useType == 17 / CRIMSON_VOW）───────────────────────
//
// 移植 shadertoy「Fast, Minimal Animated Blocks」。原理是 Voronoi 三角形度量
// 的廉价等价物：在可平铺的 cell 里放两个点、量距离，再把坐标旋转 120° 重复
// 一次，取四个距离的最小值 —— 没有随机点、没有循环，图案却足够"随机"。
//
//     作者原话保留在下面的 s()/m() 注释里，配色按装备需求改成粉色系。
//
// 为什么换成它：上一版用的 1/abs(sin) 等离子场是"暗底 + 极窄亮丝"，
// 量下来剑身亮部只占 4%，且在 7x24 像素的剑身上天然低于 Nyquist，
// 满屏摩尔纹。Blocks 是结构化图案，自带边缘高光与曲率明暗，
// 同样面积下能读出"块"的形状。实测 7x24 剑身上 blockPatternScale≈1.4
// 时每块约 5~6 像素，结构清晰。

// 距离度量。这里用的是略微圆化的三角形 —— 也就是"方块感"的来源。
// 作者注释里列了其它度量（正三角形 / 点积 / 曼哈顿 …），换掉就能出别的图案。
float blockDist(vec2 p) {
    p = fract(p) - 0.5;
    // return max(abs(p.x)*.866 + p.y*.5, -p.y);          // 正三角形
    return (dot(p, p) * 2.0 + 0.5) * max(abs(p.x) * 0.866 + p.y * 0.5, -p.y);
}

// 极廉价的可平铺蜂窝格。两个可平铺的点 + 一次 120° 旋转 = 足够随机的图案。
float blockCell(vec2 p, float t) {
    // 动画偏移：原作 iDate.w；这里接游戏时间（连续时间，由 Java 侧提供）
    vec2 o = sin(vec2(1.93, 0.0) + t) * 0.166;

    // 两个可平铺、在动的点的距离
    float a = blockDist(p + vec2(o.x, 0.0));
    float b = blockDist(p + vec2(0.0, 0.5 + o.y));

    // 把这一层（坐标）旋转 120°
    p = -mat2(0.5, -0.866, 0.866, 0.5) * (p + 0.5);

    // 另外两个可平铺、在动的点的距离
    float c = blockDist(p + vec2(o.x, 0.0));
    float d = blockDist(p + vec2(0.0, 0.5 + o.y));

    return min(min(a, b), min(c, d)) * 2.0;
}

// 完整着色。返回粉色系 rgb，明度结构照搬原作：
//   o   = 方块主体（pow 分级）
//   b   = 边缘高光（沿坐标轴差分）
//   curv= 曲率明暗（四邻域采样）
//
// ★ 移植时踩到的坑（务必留意，否则整把剑会变成一坨纯粉色）：
//   原作里 b 是「单独叠加」的项：o = pow(...) + b*b*(.5+b*b)。
//   b = max(o - m(p+.01), 0)/.05 的量级能到几十，单独相加只是让边缘过曝白，
//   但如果像第一版那样把 0.9*b*b*(0.5+b*b) 折进「分级用的明度」里，几乎
//   所有边缘像素都会把明度顶到上限 —— 实测 24.5% 的像素撞顶后被压成同一个
//   近白粉，剑身看起来就是纯色。现在把 b 的缩放压到 0.15，撞顶率降到 0.9%。
//   另一半原因是渐变端点：亮粉的 B=0.63 会让 t1 在 lum≈0.55 处就钳到 1，
//   超过一半像素提前锁死在同一个颜色。现在端点改成 B=0.52 并改用
//   log 软膝分级，把整条色路摊到真实 lum 范围上。
vec3 blockShade(vec2 p, float t, float maskVal) {
    float o = blockCell(p, t);

    // 廉价高光：o - m(p + eps)，横向 / 纵向各一次
    vec2 eps = vec2(0.032, 0.032);
    float bx = max(o - blockCell(p + vec2(eps.x, 0.0), t), 0.0) / 0.05;
    float by = max(o - blockCell(p + vec2(0.0, eps.y), t), 0.0) / 0.05;
    // 0.15 是量出来的：再大就开始撞顶变纯色（见上面的注释）
    float hilite = (bx + by) * 0.15;

    // 原作 o = pow(vec4(1.5,1,1,0)*o, vec4(1,3.5,16,0))：
    // 这里把三级 pow 的合成分量当作「明度」用，颜色另按粉色梯度重映射。
    float lum = o
              + pow(clamp(o, 0.0, 1.0), 3.5) * 0.5
              + pow(clamp(o, 0.0, 1.0), 16.0) * 0.7;
    lum += hilite * hilite * (0.5 + hilite * hilite);

    // 曲率 → 明暗，让块面有起伏（原作: o *= curv*1.5 + .5，亮线版）
    float e = 0.016;
    float curv = (o * 4.0
                  - blockCell(p - vec2(e, 0.0), t)
                  - blockCell(p + vec2(e, 0.0), t)
                  - blockCell(p - vec2(0.0, e), t)
                  - blockCell(p + vec2(0.0, e), t)) / e / 2.0 + 0.5;
    curv = clamp(curv, 0.0, 1.0);
    lum *= curv * 0.9 + 0.55;

    // log 软膝：真实 lum 集中在 0.0~1.6，直接用会把大半像素压在端点附近
    float k = clamp(log(1.0 + lum * 1.1) / log(3.4), 0.0, 1.0);

    // ── 粉色分级：深洋红 → 亮粉 → 近白高光 ──
    vec3 deepMagenta = vec3(0.30, 0.02, 0.21);
    vec3 hotPink     = vec3(1.00, 0.16, 0.52);
    vec3 lightPink   = vec3(1.00, 0.86, 0.95);

    vec3 col = (k < 0.5) ? mix(deepMagenta, hotPink, k * 2.0)
                         : mix(hotPink, lightPink, (k - 0.5) * 2.0);

    // 原作最后 sqrt 做粗糙 gamma；这里改 pow(0.85) 免得把粉色冲淡
    col = pow(max(col, 0.0), vec3(0.85));

    // 遮罩越亮（物品实心处）越实，遮罩外只剩极淡一层
    return col * mix(0.20, 1.0, clamp(maskVal, 0.0, 1.0));
}

// ── 水面湍流（useType == 18 / SILENT_DAYLIGHT）──────────────────
//
// 移植 shadertoy「water turbulence」：
//
//   // Found this on GLSL sandbox. I really liked it, changed a few things and made it tileable.
//   // :)  by David Hoskins.  Original water turbulence effect by joltz0r
//
// 原作者的结构与常数**一个都没动**（MAX_ITER、inten、1.17/pow(c,1.4)/pow(abs(c),8.0)、
// 最后叠的 vec3(0.0,0.35,0.5) 冷色偏移都照抄），只改了三处"输入接口"：
//
//   原作                                → 这里
//   iTime                               → time（游戏 tick）× waveTimeScale
//   fragCoord.xy / iResolution.xy       → 遮罩 sprite 内的 0..1 UV（maskUvMin/maskUvSize）
//   fragColor = vec4(colour, 1.0)       → 乘遮罩与 opacity 后交给 cosmic 的合成末尾
//
// ★ 为什么 UV 必须自己折回 sprite（这是移植里唯一容易搞错的地方）：
//   quad 的 texCoord0 是**图集坐标**。48px 的遮罩在方块图集里只占 0.047 UV ——
//   直接拿它当 uv 用，整把剑落在同一格图案里（只会看到一块几乎均匀的渐变），
//   而且相位会随 sprite 在图集里的排布变化（换资源包 / 加贴图就变样）。
//   折回 0..1 之后，"一格图案 = 整个物品平面"才是原作里"一格 = 一屏"的对应关系。
//
// ★ 为什么 time 要乘 waveTimeScale：
//   cosmic 的 time uniform 是**游戏 tick**（20 tick = 1 秒），而原作 iTime 是秒。
//   默认 0.025 正好把原作的 0.5 倍速换算过来（0.5 / 20），流速接近原作观感；
//   直接喂 tick 会快 20 倍 —— 那不是水，是电风扇。
//
// ★ 原作里的 #ifdef SHOW_TILING 调试分支没有移植（它会在屏幕边缘画黄线标出平铺边界，
//   是给"看平铺对不对"用的，物品上只会变成一道脏边）。
//
// 迭代三角反馈：p 只跨 2π（一格），细节全靠 i 在层与层之间自反馈放大出来。
// MAX_ITER 保持 5：再少结构糊、再多在 48px 的剑身上已经看不出来，只白烧 GPU。
#define WATER_MAX_ITER 5

const float WATER_TAU = 6.28318530718;

vec3 waterTurbulence(vec2 uv, float wt) {
    // 原作: float time = iTime * .5 + 23.0;
    // 0.5 折进了 waveTimeScale，23.0 是原作的相位偏置（去掉它图案会停在一个
    // 结构不明显的区域），照留。
    float wtm = wt + 23.0;

    // 原作: vec2 p = mod(uv*TAU, TAU) - 250.0;
    // 250.0 把坐标推到三角函数的大参数区，[-250, -243.72] 恰好跨一个整周期 ——
    // "一格 = 一屏"就是这么来的；mod 保证平铺处接得上（原作者补的 tileable 改动）。
    vec2 p = mod(uv * WATER_TAU, WATER_TAU) - 250.0;
    vec2 i = vec2(p);

    float c = 1.0;
    const float inten = 0.005;   // 原作 inten，别动：它同时是 c 的量纲

    for (int n = 0; n < WATER_MAX_ITER; n++) {
        float tn = wtm * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(tn - i.x) + sin(tn + i.y), sin(tn - i.y) + cos(tn + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + tn) / inten), p.y / (cos(i.y + tn) / inten)));
    }

    c /= float(WATER_MAX_ITER);
    c = 1.17 - pow(c, 1.4);

    // 8 次幂把 c 压成"暗底 + 极窄亮丝"，再整体抬向青蓝（0.35 绿 / 0.5 蓝）——
    // 于是水纹读起来是冷光而不是灰阶噪声，这也是原作那一行 vec3(0.0,0.35,0.5) 的作用。
    vec3 colour = vec3(pow(abs(c), 8.0));
    return clamp(colour + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
}

// ── 背景函数 ──────────────────────────────────────────────────

vec3 getFbmNebula(vec3 pos, float t) {
    float n1=fbm6(pos*0.8+vec3(t*0.03,0.0,t*0.02));
    float n2=fbm6(pos*1.1+vec3(33.7,-17.2,t*0.04));
    float d=smoothstep(0.1,0.85,n1*0.6+n2*0.25);
    vec3 c0=vec3(0.03,0.01,0.08), c1=vec3(0.08,0.02,0.18), c2=vec3(0.12,0.03,0.28), c3=vec3(0.18,0.06,0.35);
    vec3 nb=(d<0.3)?mix(c0,c1,d/0.3):((d<0.65)?mix(c1,c2,(d-0.3)/0.35):mix(c2,c3,(d-0.65)/0.35));
    return nb*0.7+hsv2rgb(vec3(0.78+n2*0.06-0.03,0.5+n1*0.3,d*0.35))*0.18;
}

vec3 getAurora(vec3 pos, float t) {
    vec2 uv=pos.xy/(length(pos)+0.5);
    float band=smoothstep(0.2,0.7,sin(uv.x*4.0+uv.y*3.0+t*0.06)*cos(uv.y*2.5+uv.x*1.7+t*0.04)*0.5+0.5)*0.12;
    return mix(vec3(0.12,0.03,0.30),vec3(0.10,0.04,0.25),sin(t*0.03)*0.5+0.5)*band;
}

// ── 流动混色（DEEP_SPACE 专用）──────────────────────────────────
//
// 目标：让紫色虚空"缓慢换色"，而不是整片同时呼吸。
// 做法：拿一个低频噪声场当流场，它决定每个区域此刻停在色环的哪个位置；
//       色相里再叠一个极慢的时间项，于是色带整体缓缓漂移过去。
//
// 为什么不用 getFbmNebula 里那种 hsv2rgb(0.78 + n2*0.06 - 0.03, ...)：
//   那个色相幅度只有 ±0.03，肉眼读不出"混色"，只能算同色系微调。
//   这里幅度给到 0.62（绕色环大半圈），再用 strength 锁在"混进紫底"的程度上。
//
// 为什么流场用两层单倍频 noise3D 而不是 fbm6：
//   色相场要的是"大块的、平滑的"分布，高频细节只会让它碎成噪点；
//   而且 getFbmNebula 每帧已经跑两次 fbm6（各 6 个倍频），这里再上一个不划算。
vec3 getFlowingMix(vec3 pos, float t, float strength) {
    vec3 flowPos = pos*0.30 + vec3(t*0.010, -t*0.007, t*0.013);
    float flow = noise3D(flowPos)*0.65 + noise3D(flowPos*2.7)*0.35;
    float hue  = fract(0.74 + flow*0.62 + t*0.0045);   // 0.74 = 紫，从这里出发绕色环
    vec3 tint  = 0.5 + 0.5*cos(6.28318*(hue + vec3(0.0,0.33,0.67)));
    tint = mix(tint, vec3(1.0), 0.18);                 // 压一点饱和，免得读成"彩虹塑料"
    return mix(vec3(1.0), tint, clamp(strength, 0.0, 1.0));
}

vec3 getRichNebula(vec3 pos, float t) {
    float n=noise3D(pos*0.6+vec3(t*0.02,-t*0.015,t*0.025))*noise3D(pos*0.9+vec3(55.0,33.0,-t*0.018));
    float d=smoothstep(0.2,0.8,n);
    vec3 c0=vec3(0.04,0.01,0.10), c1=vec3(0.10,0.02,0.22), c2=vec3(0.16,0.04,0.30);
    return mix(mix(c0,c1,d),c2,d*d)*0.5;
}

vec3 getCrystalBg(vec3 pos, float t) {
    float n=fbm6(pos*0.7+vec3(0.0,t*0.02,t*0.01));
    vec3 c0=vec3(0.02,0.01,0.08), c1=vec3(0.03,0.03,0.12);
    return mix(c0,c1,n)*0.8;
}

// ── 星光颜色函数（每种样式不同） ──────────────────────────────────

vec3 starColorRainbow(float ru, float i, float t) {
    float hue=fract((t*0.2+ru*3.0+i*0.3)*0.3);
    return mix(vec3(1.0),0.5+0.5*cos(6.28318*(hue+vec3(0.0,0.33,0.67))),0.6);
}

vec3 starColorCrystal(float ru, float t, float rand1) {
    float hue=fract((t*0.3+ru*5.0)*0.15);
    vec3 cry=0.7+0.3*cos(6.28318*(hue+vec3(0.0,0.33,0.67)));
    cry*=sin(t*3.0+rand1*10.0)*0.3+0.7;
    return cry;
}

void main(void) {
    vec4 mask = texture(Sampler0, texCoord0.xy);
    float oneOverExternalScale = 1.0/externalScale;
    float depth = length(fPos)/10.0;

    // ── 背景（根据 useType 切换） ──
    vec4 col;
    float depthFade = clamp(1.0-depth*0.35, 0.0, 1.0);
    float pulse = mod(time, 400)/400.0;
    float breathe = sin(pulse*M_PI*2.0)*0.015+0.025;

    if (useType == 1) {
        // RAINBOW_FLOW — 彩虹渐变背景
        float bgPhase = pulse + depth*0.1;
        vec3 rainbow = hsv2rgb(vec3(fract(bgPhase+time*0.0008), 0.6, 0.06));
        col = vec4(mix(vec3(0.02,0.01,0.04), rainbow, 0.5), 1.0);
    } else if (useType == 2) {
        // PURE_DARK — 极暗背景
        col = vec4(0.005, 0.003, 0.01, 1.0);
        col.rgb += vec3(breathe*0.6, breathe*0.4, breathe*0.8);
    } else if (useType == 3) {
        // CRYSTAL_DREAM — 深蓝紫晶体
        col = vec4(mix(vec3(0.015,0.008,0.06), vec3(0.025,0.015,0.10), depthFade), 1.0);
        col.rgb += getCrystalBg(fPos, time);
    } else if (useType == 4) {
        // NEBULA_RICH — 丰富星云层
        col = vec4(mix(vec3(0.01,0.005,0.03), vec3(0.03,0.012,0.08), depthFade), 1.0);
        col.rgb += getRichNebula(fPos, time);
        col.rgb += getAurora(fPos, time)*0.5;
    } else if (useType == 16) {
        // PINK_BLUE_DUAL — 灰黑底
        col = vec4(0.035, 0.028, 0.04, 1.0);
        float bgBlend = sin(time*0.05)*0.5+0.5;
        col.rgb = mix(vec3(0.035,0.025,0.04), vec3(0.04,0.02,0.055), bgBlend);
    } else {
        // DEEP_SPACE(0) — FBM星云 + 极光 + 流动混色
        vec3 nearC=vec3(0.04,0.015,0.10), farC=vec3(0.005,0.003,0.015);
        // 流动的混色：低频流场决定每个区域此刻的色相，整体缓慢漂移。
        // 紫色底子刻意保留 —— nearC/farC 只染 35%，混色主要作用在星云那一层，
        // 于是"还是这片紫黑虚空，但它在缓慢换色"，而不是变成一整片彩虹。
        vec3 flow = getFlowingMix(fPos, time, flowMixStrength);
        col = vec4(mix(farC, nearC, depthFade) * mix(vec3(1.0), flow, 0.35), 1.0);
        // 1.35 用来补回"按色相染色"损失的能量：乘性染色必然压暗非主色通道，
        // 不补的话开了混色整体会明显变暗（看起来像调低了亮度而不是换了颜色）。
        col.rgb += getFbmNebula(fPos, time)*0.35*flow*1.35;
        col.rgb += getAurora(fPos, time)*flow;
        col.rgb += vec3(breathe*0.8, breathe*0.3, breathe);
    }

    // ── Blocks 图案：完全独立的片元路径，不出星星 ──
    // 放在星空循环之前提前返回，省掉 16 次星空采样和整个恒星色分支。
    if (useType == 17) {
        vec2 centered = texCoord0.xy - 0.5;   // 挪到物品中心
        // 原作 p /= iResolution.y/3.（像素空间）→ 这里 1.0 UV = blockPatternScale 个 cell
        vec2 bp = centered * blockPatternScale;
        float bt = time * blockTimeScale + blockTimeOffset;
        col = vec4(blockShade(bp, bt, mask.r), 1.0);
        col.a *= mask.r * opacity;
        fragColor = clamp(col * ColorModulator, 0.0, 1.0);
        return;
    }

    // ── 水面湍流：同样是一条独立片元路径，不出星星 ──
    // 与 17 的区别只在 UV：这里不用"图集坐标直接乘系数"那种近似，而是先折回
    // 遮罩 sprite 自己的 0..1（原因见 waterTurbulence 上面的注释）。
    if (useType == 18) {
        vec2 uv = (texCoord0 - maskUvMin) / max(maskUvSize, vec2(1.0e-6));
        col = vec4(waterTurbulence(uv * wavePatternScale, time * waveTimeScale), 1.0);
        // 遮罩就是这道水的"水位线"：剑刃（白）在水下，剑柄（黑）在外面。
        col.a *= mask.r * opacity;
        fragColor = clamp(col * ColorModulator, 0.0, 1.0);
        return;
    }

    // ── 视角旋转 ──
    vec4 dir = normalize(vec4(-fPos, 0));
    float sb=sin(pitch), cb=cos(pitch);
    dir=normalize(vec4(dir.x, dir.y*cb-dir.z*sb, dir.y*sb+dir.z*cb, 0));
    float sa=sin(-yaw), ca=cos(-yaw);
    dir=normalize(vec4(dir.z*sa+dir.x*ca, dir.y, dir.z*ca-dir.x*sa, 0));
    vec4 ray;

    // ── 星星粒子 ──
    int uvtiles = 16;
    for (int i=0; i<16; i++) {
        int mult=16-i, j=i+7;
        float rand1=(j*j*4321+j*8)*2.0;
        int k=j+1;
        float rand2=(k*k*k*239+k*37)*3.6;
        float rand3=rand1*347.4+rand2*63.4;

        vec3 axis=normalize(vec3(sin(rand1),sin(rand2),cos(rand3)));
        ray=dir*rotationMatrix(axis, mod(rand3, 2*M_PI));

        float rawu=0.5+(atan(ray.z,ray.x)/(2*M_PI));
        float rawv=0.5+(asin(ray.y)/M_PI);

        float scale=mult*0.5+2.75;
        float u=rawu*scale*externalScale;
        float v=(rawv+time*0.0002*oneOverExternalScale)*scale*0.6*externalScale;

        int tu=int(mod(floor(u*uvtiles), uvtiles));
        int tv=int(mod(floor(v*uvtiles), uvtiles));
        int position=((171*tu)+(489*tv)+(303*(i+31))+17209)^10;
        int symbol=int(mod(position, cosmicoutof));
        int rotation=int(mod(pow(tu,float(tv))+tu+3+tv*i, 8));
        bool flip=false;
        if(rotation>=4){ rotation-=4; flip=true; }

        if(symbol>=0 && symbol<cosmiccount){
            float ru=clamp(mod(u,1.0)*uvtiles-tu,0.0,1.0);
            float rv=clamp(mod(v,1.0)*uvtiles-tv,0.0,1.0);
            if(flip) ru=1.0-ru;
            float oru=ru, orv=rv;
            if(rotation==1){ oru=1.0-rv; orv=ru; }
            else if(rotation==2){ oru=1.0-ru; orv=1.0-rv; }
            else if(rotation==3){ oru=rv; orv=1.0-ru; }

            vec2 cosmictex;
            float umin=cosmicuvs[symbol][0][0], umax=cosmicuvs[symbol][1][0];
            float vmin=cosmicuvs[symbol][0][1], vmax=cosmicuvs[symbol][1][1];
            cosmictex.x=umin*(1.0-oru)+umax*oru;
            cosmictex.y=vmin*(1.0-orv)+vmax*orv;

            vec4 tcol=texture(Sampler0, cosmictex);
            float a=tcol.r*(0.5+(1.0/mult)*1.0)*(1.0-smoothstep(0.15,0.48,abs(rawv-0.5)));

            // 星星颜色（按 useType 区分）
            vec3 starC;
            float twinkle=sin(time*0.006+rand1*0.1)*sin(time*0.009+rand2*0.15)*0.4+0.6;
            float distFade=1.0-float(i)/20.0;

            if (useType == 1) {
                // RAINBOW — 彩虹闪烁星
                starC=starColorRainbow(rawu, float(i), time);
                starC*=twinkle*distFade*1.3;
            } else if (useType == 2) {
                // PURE_DARK — 金/白高对比
                float cc=fract(rand1*0.3);
                starC=(cc<0.5)?vec3(1.0,0.85,0.5):vec3(1.0,1.0,1.0);
                starC*=(twinkle*0.7+0.3)*distFade*1.5;
            } else if (useType == 3) {
                // CRYSTAL — 水晶闪烁蓝辉光
                starC=starColorCrystal(rawu, time, rand1);
                starC*=twinkle*distFade*1.2;
                starC+=vec3(0.2,0.3,0.6)*smoothstep(0.7,1.0,tcol.r)*0.3;
            } else if (useType == 4) {
                // NEBULA_RICH — 紫白拖尾
                starC=vec3(0.7+rand2*0.3, 0.5+rand1*0.3, 0.8+rand3*0.2);
                starC*=twinkle*(0.8+distFade*0.4)*1.1;
                starC+=vec3(0.1,0.04,0.2)*(1.0-distFade)*0.25;
            } else if (useType == 16) {
                // PINK_BLUE — 粉蓝双色
                float ch=fract(rand1*0.37+rand2*0.53);
                starC=(ch<0.5)?vec3(1.0,0.0,0.541):vec3(0.361,0.725,0.988);
                float blink=sin(time*1.5+rand1*8.0)*0.15+0.85;
                starC*=blink*distFade*1.2;
                starC+=vec3(1.0,0.85,0.95)*smoothstep(0.7,1.0,tcol.r)*0.3;
            } else {
                // DEEP_SPACE(0) — 七彩琉璃星
                //
                // 色相 = 星号(rand1/rand2) + 深度 + 时间：
                //   星号 → 同一颗星永远保持同一个颜色，不会每帧闪成乱码
                //   深度 → 前后层次拉开，像琉璃里裹了不同的矿物
                //   时间 → 整体缓慢流转（irisSpeed=1 时约 70 秒绕色环一圈）。
                //          要的是"变色"不是"闪烁"，所以系数比下面的 twinkle 小两个量级。
                float irisHue = fract(rand1*0.021 + rand2*0.013 + depth*0.06
                                      + time*0.0012*irisSpeed);
                vec3 iris = 0.5 + 0.5*cos(6.28318*(irisHue + vec3(0.0,0.33,0.67)));
                // 琉璃 = "彩而透亮"：色相先往白里拉 15%，亮核再压到近白。
                // 纯饱和的彩虹色会读成塑料，少了"琉璃"里那层透光感。
                iris = mix(iris, vec3(1.0), 0.15);
                float hotCore = smoothstep(0.55, 1.0, tcol.r);
                starC = mix(iris, vec3(1.0), hotCore*0.5);

                starC*=twinkle*distFade;
                float dGlow=1.0+sin(depth*M_PI+time*0.003)*0.3;
                // 紫色底噪保留：它负责让这些彩星仍然属于"同一片宇宙"，
                // 而不是像贴了一堆彩色亮片上去。
                starC=starC*dGlow+vec3(0.15,0.08,0.35)*0.15;
                starC+=vec3(0.12,0.06,0.30)*(1.0-distFade)*0.3;
            }

            col+=vec4(starC,1.0)*a;
        }
    }

    // ── 光照：始终保持最大亮度，不受环境光照影响 ──
    const float brightness = 1.35;
    col.rgb *= brightness;
    // 遮罩 + 不透明度
    col.a *= mask.r * opacity;

    float finalTint=time*0.002;
    col.rgb*=vec3(1.0+sin(finalTint)*0.03, 1.0+sin(finalTint+M_PI*0.33)*0.03, 1.0+sin(finalTint+M_PI*0.66)*0.03);
    col=clamp(col,0.0,1.0);
    fragColor=linear_fog(col*ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
