#version 150 core

// ─────────────────────────────────────────────────────────────
//  坍缩使徒「背部六芒星光环」（顶点阶段）
//
//  几何是一张朝向相机的四边形（billboard），UV0 是它在自身平面内的 0..1 坐标，
//  片元里直接映射成以中心为原点的 [-1,1]²。
//
//  坐标约定与冲击波/黑洞完全一致：Java 侧用 handler 的 pose
//  （PoseStack 的相机旋转 × translate(-camera)）把世界坐标烘焙成
//  「相机相对（视图）空间」的 Position，所以这里<b>只乘 ProjMat</b>，
//  不能再乘 ModelViewMat —— 否则会多变换一次。
// ─────────────────────────────────────────────────────────────

in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    texCoord    = UV0;
}
