#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Dissolve;
uniform float Intensity;

in vec4 vertexColor;
in vec2 texCoord;
in float glyphDissolve;
out vec4 fragColor;

// Font render types bind atlases with NEAREST filtering. Reconstruct coverage locally;
// never alter the shared atlas's filtering state, which is also used by other UI text.
float linearCoverage(vec2 uv) {
    ivec2 size = textureSize(Sampler0, 0);
    vec2 p = uv * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(p));
    vec2 f = fract(p);
    float a = texelFetch(Sampler0, clamp(base, ivec2(0), size - 1), 0).r;
    float b = texelFetch(Sampler0, clamp(base + ivec2(1, 0), ivec2(0), size - 1), 0).r;
    float c = texelFetch(Sampler0, clamp(base + ivec2(0, 1), ivec2(0), size - 1), 0).r;
    float d = texelFetch(Sampler0, clamp(base + ivec2(1, 1), ivec2(0), size - 1), 0).r;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    vec4 sampleColor = texture(Sampler0, texCoord);
    if (Intensity > 0.5) {
        // Four coverage samples over the screen pixel reduce shimmer on slanted world-space panels.
        vec2 dx = dFdx(texCoord) * 0.25;
        vec2 dy = dFdy(texCoord) * 0.25;
        float coverage = 0.25 * (linearCoverage(texCoord - dx - dy) + linearCoverage(texCoord + dx - dy)
                              + linearCoverage(texCoord - dx + dy) + linearCoverage(texCoord + dx + dy));
        // Coverage belongs in alpha only. Multiplying RGB as well darkens antialiased edges twice.
        sampleColor = vec4(1.0, 1.0, 1.0, coverage);
    }
    vec4 color = sampleColor * vertexColor * ColorModulator;
    float dissolve = max(Dissolve, glyphDissolve);
    if (color.a < 0.01 || dissolve >= 1.0) discard;
    if (dissolve > 0.0) {
        // Atlas coordinates keep the erosion attached to the glyphs when the camera turns.
        vec2 p = texCoord * vec2(textureSize(Sampler0, 0));
        float n = 0.7 * noise(p * 0.55) + 0.3 * noise(p * 1.7);
        float threshold = mix(-0.12, 1.12, dissolve);
        float coverage = 1.0 - smoothstep(n - 0.09, n + 0.09, threshold);
        float edge = (1.0 - smoothstep(0.0, 0.14, n - threshold)) * coverage;
        color.rgb = mix(color.rgb, vec3(1.0, 0.76, 0.40), edge * 0.6);
        color.a *= coverage * (1.0 - smoothstep(0.65, 1.0, dissolve));
    }
    if (color.a < 0.005) discard;
    fragColor = color;
}
