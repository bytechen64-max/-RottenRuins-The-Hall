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
        // DEEP_SPACE(0) — FBM星云 + 极光
        vec3 nearC=vec3(0.04,0.015,0.10), farC=vec3(0.005,0.003,0.015);
        col = vec4(mix(farC, nearC, depthFade), 1.0);
        col.rgb += getFbmNebula(fPos, time)*0.35;
        col.rgb += getAurora(fPos, time);
        col.rgb += vec3(breathe*0.8, breathe*0.3, breathe);
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
                // DEEP_SPACE(0) — 冷白/蓝星星
                starC=vec3(fract(rand1*0.123)*0.4+0.6,
                           fract(rand2*0.456)*0.3+0.7,
                           fract(rand3*0.789)*0.3+0.7);
                starC*=vec3(1.0+mod(rand1,20.0)/500.0,1.0+mod(rand2,20.0)/500.0,1.0+mod(rand3,20.0)/500.0);
                starC*=twinkle*distFade;
                float dGlow=1.0+sin(depth*M_PI+time*0.003)*0.3;
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
