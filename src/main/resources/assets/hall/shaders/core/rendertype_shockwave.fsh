#version 150 core

uniform sampler2D ScreenTexture;
uniform float uIntensity, uLifeProgress, uRingPosition, uRingWidth;
uniform mat4 ProjMat;

in vec4 vertexColor;
in vec3 vNormal;
in vec3 vPos;
out vec4 fragColor;

vec2 viewToScreenUV(vec3 vp) {
    vec4 clip = ProjMat * vec4(vp, 1.0);
    vec2 ndc = clip.xy / clip.w;
    return clamp(ndc * 0.5 + 0.5, vec2(0.001), vec2(0.999));
}

void main() {
    vec3 N = normalize(vNormal), V = normalize(-vPos);
    vec2 baseUV = viewToScreenUV(vPos);
    vec3 pt = texture(ScreenTexture, baseUV).rgb;

    float I = uIntensity * smoothstep(0.0, 0.05, uLifeProgress);
    if (I <= 0.0) { fragColor = vec4(pt, 1.0); return; }

    float ef = 1.0 - abs(dot(N, V));
    float rs = uRingPosition, re = min(rs + uRingWidth, 0.99), rm = (rs + re) * 0.5;
    float ring = smoothstep(rs, rm, ef) * (1.0 - smoothstep(rm, re, ef));

    float strength = ring * 0.22 * I;
    vec4 cn = ProjMat * vec4(vPos + N, 1.0);
    vec2 sn = normalize((cn.xy / cn.w * 0.5 + 0.5) - baseUV);
    vec2 uv = clamp(baseUV + sn * strength, vec2(0.001), vec2(0.999));

    vec3 col = texture(ScreenTexture, uv).rgb + vec3(ring * 0.18 * I);
    fragColor = vec4(mix(pt, col, I), 1.0);
}
