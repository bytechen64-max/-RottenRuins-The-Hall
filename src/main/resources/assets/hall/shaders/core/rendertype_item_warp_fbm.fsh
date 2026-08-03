#version 150 core

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;
uniform vec4 TintColor;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// ── colormap ─────────────────────────────────────────────

float colormap_red(float x) {
    if (x < 0.0) return 54.0 / 255.0;
    if (x < 20049.0 / 82979.0) return (829.79 * x + 54.51) / 255.0;
    return 1.0;
}

float colormap_green(float x) {
    if (x < 20049.0 / 82979.0) return 0.0;
    if (x < 327013.0 / 810990.0) return (8546482679670.0 / 10875673217.0 * x - 2064961390770.0 / 10875673217.0) / 255.0;
    if (x <= 1.0) return (103806720.0 / 483977.0 * x + 19607415.0 / 483977.0) / 255.0;
    return 1.0;
}

float colormap_blue(float x) {
    if (x < 0.0) return 54.0 / 255.0;
    if (x < 7249.0 / 82979.0) return (829.79 * x + 54.51) / 255.0;
    if (x < 20049.0 / 82979.0) return 127.0 / 255.0;
    if (x < 327013.0 / 810990.0) return (792.0224934136139 * x - 64.36479073560233) / 255.0;
    return 1.0;
}

vec3 colormap(float x) {
    return vec3(colormap_red(x), colormap_green(x), colormap_blue(x));
}

// ── noise ────────────────────────────────────────────────

float rand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 ip = floor(p);
    vec2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float res = mix(
        mix(rand(ip), rand(ip + vec2(1.0, 0.0)), u.x),
        mix(rand(ip + vec2(0.0, 1.0)), rand(ip + vec2(1.0, 1.0)), u.x), u.y);
    return res * res;
}

const mat2 mtx = mat2(0.80, 0.60, -0.60, 0.80);

float fbm(vec2 p) {
    float f  = 0.500000 * noise(p + Time);
    p = mtx * p * 2.02;
    f += 0.031250 * noise(p);
    p = mtx * p * 2.01;
    f += 0.250000 * noise(p);
    p = mtx * p * 2.03;
    f += 0.125000 * noise(p);
    p = mtx * p * 2.01;
    f += 0.062500 * noise(p);
    p = mtx * p * 2.04;
    f += 0.015625 * noise(p + sin(Time));
    return f / 0.96875;
}

float pattern(vec2 p) {
    return fbm(p + fbm(p + fbm(p)));
}

// ── main ─────────────────────────────────────────────────

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) discard;

    vec2 uv = texCoord0 * 80.0;
    float shade = pattern(uv);
    vec3 col = colormap(shade);

    // Tint the colormap — default white = no tint (original pink)
    col = mix(col, col * TintColor.rgb, TintColor.a);

    float alpha = 0.85 * vertexColor.a;
    fragColor = vec4(col * alpha, alpha) * ColorModulator;
}
