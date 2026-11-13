package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

/**
 * 使徒斩击的外层空间扭曲：复用 {@link ShockwaveRenderer} 的 {@code rendertype_shockwave} 着色器做球壳折射，
 * {@code uGlow=0} 即纯折射（不出现冲击波那圈亮带），场景拷贝与冲击波同帧共用。
 * Oculus/Iris 激活时此刻不能画（主帧缓冲还是 GBuffer），必须快照进 {@link ApostleSlashWarpQueue} 由 renderLevel TAIL 回放。
 */
final class ApostleSlashWarp {

    /** 球面细分。只用于屏幕空间环带计算，不需要高精度，取小值省顶点。 */
    private static final int LAT = 16, LON = 32;
    private static final float TWO_PI = (float) (2.0 * Math.PI);
    private static final float PI_2 = (float) (Math.PI / 2.0);

    /** 折射位移倍率：冲击波默认 1.0 是给半径 20 格波前的，斩击球只有 5~6 格，压到 0.16 才只是"轻扭"。 */
    private static final float WARP_STRENGTH = 0.16f;
    /** 湍流微扰：让扭曲不是完美圆环。 */
    private static final float SHIMMER_STRENGTH = 0.20f;
    /** 色散（红蓝偏折差异），斩击用很小值，只要一层"空气被挤压"的感觉。 */
    private static final float CHROMA_STRENGTH = 0.06f;
    /** 环带厚度系数：比冲击波默认的 0.95 薄，让扭曲集中在剑气轮廓外侧一圈。 */
    private static final float RING_WIDTH = 0.30f;

    /** 扭曲球半径系数 = 剑气半长 × outerScale × 该系数；0.35 让扭曲待在刀身中段外侧那一圈，
     *  否则刀身 32 格长（半长 16）时球壳会落到 23 格外，半个屏幕被拧过。 */
    private static final float RADIUS_PER_HALF_HEIGHT = 0.35f;
    /** 扭曲球半径下限（格）。 */
    private static final float WARP_RADIUS_MIN = 2.0f;
    /** 扭曲球半径上限（格）。 */
    private static final float WARP_RADIUS_MAX = 8.0f;

    private static final float[][][] V = new float[LAT + 1][LON + 1][3];

    static {
        for (int la = 0; la <= LAT; la++) {
            float phi = -PI_2 + (float) Math.PI * la / LAT;
            float cp = (float) Math.cos(phi);
            float sp = (float) Math.sin(phi);
            for (int lo = 0; lo <= LON; lo++) {
                float th = TWO_PI * lo / LON;
                V[la][lo][0] = cp * (float) Math.cos(th);
                V[la][lo][1] = sp;
                V[la][lo][2] = cp * (float) Math.sin(th);
            }
        }
    }

    private ApostleSlashWarp() {}

    /** 绘制一帧内所有斩击的扭曲；整批只做一次场景拷贝（逐斩击全屏 blit 会在"20 刀齐飞"时把带宽吃光）。 */
    static void renderAll(List<SwordAuraEntity> slashes, float partialTick, PoseStack poseStack) {
        if (slashes.isEmpty()) return;
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        ShaderInstance sh = SplendidingShaders.shockwaveShader;
        if (sh == null) return;

        boolean defer = HeldItemOutlineCompat.isOculusShaderPackActive();

        // 非延迟路径：这一帧的整批共用一次场景拷贝
        if (!defer) ShockwaveRenderer.doSceneCopy();

        for (SwordAuraEntity slash : slashes) {
            // 与实体层同一套插值：扭曲球必须跟着剑气实际大小走，否则"刀已经缩了、扭曲还挂着原来那么大"。
            float scale = slash.getCurrentScale(partialTick);
            float alpha = slash.getCurrentAlpha(partialTick);
            float lifeProgress = slash.getLifeProgress(partialTick);
            if (scale < 0.005f || alpha < 0.01f) continue;

            float radius = warpRadius(slash, scale);
            if (radius < 0.25f) continue;

            poseStack.pushPose();
            poseStack.translate(slash.getX(), slash.getY(), slash.getZ());
            poseStack.scale(radius, radius, radius);
            Matrix4f pose = new Matrix4f(poseStack.last().pose());
            Matrix3f normal = new Matrix3f(poseStack.last().normal());
            poseStack.popPose();

            // 球心的视图空间位置由 pose 自己算（见 drawWarpSphere），这里不需要再传世界坐标。
            if (defer) {
                ApostleSlashWarpQueue.enqueue(pose, normal, radius, alpha, lifeProgress);
            } else {
                drawWarpSphere(pose, normal, sh, radius, alpha, lifeProgress);
            }
        }
    }

    /** 延迟回放入口：由 {@link ApostleSlashWarpQueue#renderAll()} 在 renderLevel TAIL 调用。 */
    static void renderDeferred(Matrix4f pose, Matrix3f normal,
                               float radius, float alpha, float lifeProgress) {
        ShaderInstance sh = SplendidingShaders.shockwaveShader;
        if (sh == null) return;
        drawWarpSphere(pose, normal, sh, radius, alpha, lifeProgress);
    }

    /** 延迟回放前也要先做场景拷贝（那时主帧缓冲里才是合成完的画面）。 */
    static void copySceneForDeferred() {
        ShockwaveRenderer.doSceneCopy();
    }

    /** 扭曲球半径：跟剑气尺寸与当前缩放一起收缩，并夹在合理区间内。 */
    private static float warpRadius(SwordAuraEntity slash, float currentScale) {
        float halfHeight = Math.max(slash.getAuraHeight() * 0.5f, slash.getAuraRadius());
        float outer = slash.getStyle() == SwordAuraEntity.STYLE_APOSTLE ? slash.getOuterScale() : 1.0f;
        float raw = halfHeight * outer * RADIUS_PER_HALF_HEIGHT * currentScale;
        return Math.max(WARP_RADIUS_MIN * currentScale,
                Math.min(WARP_RADIUS_MAX, raw));
    }

    private static void drawWarpSphere(Matrix4f pose, Matrix3f nrm, ShaderInstance sh,
                                       float radius, float alpha, float lifeProgress) {
        Minecraft mc = Minecraft.getInstance();

        // uPixelSize 的分母必须是被采样纹理（场景拷贝）的尺寸：要跟主 RT 像素空间里的 gl_FragCoord 一致，
        // 用窗口尺寸会在 Oculus 的 resolution scale 下整体偏移。
        float w = Math.max(1, ShockwaveRenderer.sceneCopyWidth() > 0
                ? ShockwaveRenderer.sceneCopyWidth() : mc.getWindow().getWidth());
        float h = Math.max(1, ShockwaveRenderer.sceneCopyHeight() > 0
                ? ShockwaveRenderer.sceneCopyHeight() : mc.getWindow().getHeight());

        // 球心 → 视图空间：用烘焙顶点用的同一个 pose 变换局部原点（缩放不影响原点）。
        // 不走 RenderSystem.getModelViewMatrix()：那条路要求它此刻恰好等于完整视图矩阵，多一层或少一层变换都会整体偏离球体。
        Vector3f centerVS = pose.transformPosition(new Vector3f(0f, 0f, 0f));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> sh);

        setUniform(sh, "uIntensity", alpha);
        setUniform(sh, "uLifeProgress", lifeProgress);
        setUniform(sh, "uRingPosition", 0.10f);
        setUniform(sh, "uRingWidth", RING_WIDTH);
        setUniform(sh, "uWarp", WARP_STRENGTH);
        setUniform(sh, "uShimmer", SHIMMER_STRENGTH);
        setUniform(sh, "uChroma", CHROMA_STRENGTH);
        // uGlow=0：关掉冲击波那圈压缩壳亮带，白色斩击自己就是白边，再叠亮环会跟 outline 抢焦点。
        setUniform(sh, "uGlow", 0.0f);
        setUniform(sh, "uPixelSize", 1.0f / w, 1.0f / h);
        setUniform(sh, "uCenterVS", centerVS.x, centerVS.y, centerVS.z);
        setUniform(sh, "uWaveRadius", radius);

        int depthTex = ShockwaveRenderer.resolveDepthTextureFor(mc);
        boolean hasDepth = depthTex > 0;
        setUniform(sh, "uHasDepth", hasDepth ? 1.0f : 0.0f);

        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(ShockwaveRenderer.sceneCopyTextureId());
        sh.setSampler("ScreenTexture", ShockwaveRenderer.sceneCopyTextureId());
        if (hasDepth) sh.setSampler("DepthTexture", depthTex);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int la = 0; la < LAT; la++) {
            for (int lo = 0; lo < LON; lo++) {
                float[] a = V[la][lo], b = V[la][lo + 1], c = V[la + 1][lo], d = V[la + 1][lo + 1];
                vert(buf, pose, nrm, a);
                vert(buf, pose, nrm, c);
                vert(buf, pose, nrm, b);
                vert(buf, pose, nrm, b);
                vert(buf, pose, nrm, c);
                vert(buf, pose, nrm, d);
            }
        }
        tess.end();

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(prev);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void setUniform(ShaderInstance sh, String name, float v) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(v);
    }

    private static void setUniform(ShaderInstance sh, String name, float a, float b) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(a, b);
    }

    private static void setUniform(ShaderInstance sh, String name, float a, float b, float c) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(a, b, c);
    }

    private static void vert(BufferBuilder buf, Matrix4f pose, Matrix3f nrm, float[] p) {
        float nx = nrm.m00 * p[0] + nrm.m01 * p[1] + nrm.m02 * p[2];
        float ny = nrm.m10 * p[0] + nrm.m11 * p[1] + nrm.m12 * p[2];
        float nz = nrm.m20 * p[0] + nrm.m21 * p[1] + nrm.m22 * p[2];
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        buf.vertex(pose, p[0], p[1], p[2]).color(1f, 1f, 1f, 1f).normal(nx, ny, nz).endVertex();
    }
}
