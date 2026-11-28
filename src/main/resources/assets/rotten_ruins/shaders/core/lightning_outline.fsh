#version 150 core

in vec4 vertexColor;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    // 视线方向：相机在原点，viewPos 是 view space 位置
    vec3 viewDir = normalize(-viewPos);
    float NdotV = abs(dot(normalize(viewNormal), viewDir));

    // 固定菲涅尔厚度（可根据需要调整，这里用 1.5 演示）
    float fresnel = pow(1.0 - NdotV, 1.5);
    float alpha = clamp(fresnel, 0.0, 1.0) * vertexColor.a;

    if (alpha < 0.02) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}