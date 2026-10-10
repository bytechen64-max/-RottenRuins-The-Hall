package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

/**
 * 使徒斩击的<b>外层空间扭曲</b>。
 *
 * <h3>它是什么</h3>
 * 以剑气为中心画一个不可见的球，用<b>和冲击波同一个着色器</b>
 * （{@code rendertype_shockwave}，见 {@link ShockwaveRenderer}）把球壳当成一层
 * 折射介质：片元阶段做射线-球壳求交 → 光程积分 → 斯涅尔偏折，再用折射后的 UV
 * 重采样场景拷贝。落到屏幕上就是"斩击外侧的空间被拧了一下"。
 *
 * <h3>为什么直接复用冲击波的着色器，而不是再写一个</h3>
 * <ul>
 *   <li>那套折射数学（球壳求交、带宽自适应、深度遮挡、色散）已经调好且经过
 *       光影兼容验证，重写一遍只会引入新的差异；</li>
 *   <li>给着色器加了一个 {@code uGlow} 开关：冲击波置 1（保留压缩壳的亮带），
 *       斩击置 0（<b>纯折射</b>，不会多出一圈白亮边）——
 *       "外围是扭曲"和"外围是亮环"是两种完全不同的观感，这个开关就是分界；</li>
 *   <li>场景拷贝（{@link ShockwaveRenderer#doSceneCopy 全屏 blit}）两边共用，
 *       同一帧里冲击波与斩击只付一次拷贝成本。</li>
 * </ul>
 *
 * <h3>光影兼容</h3>
 * 与冲击波完全同路：Oculus/Iris 激活时不能立刻画（那时主帧缓冲里还是 GBuffer 数据），
 * 必须把参数快照进 {@link ApostleSlashWarpQueue}，等光影把最终画面合成到主帧缓冲之后
 * 再由 {@code CosmicAfterLevelMixin} 的 TAIL 回放。详见 {@code docs/shader-pack-compat.md}。
 */
final class ApostleSlashWarp {

    /** 球面细分。只用于屏幕空间环带计算，不需要高精度，取小值省顶点。 */
    private static final int LAT = 16, LON = 32;
    private static final float TWO_PI = (float) (2.0 * Math.PI);
    private static final float PI_2 = (float) (Math.PI / 2.0);

    // ── 强度（调参区） ──

    /**
     * 折射位移倍率。冲击波的默认值是 1.0，但那是给"半径 20 格、铺满半个屏幕"的
     * 波前用的；斩击的球只有 5~6 格，同样的峰值位移会夸张到把画面拧烂，
     * 所以这里压到 0.16 —— 读起来是"空间被轻轻扭了一下"而不是"镜头被砸了"。
     */
    private static final float WARP_STRENGTH = 0.16f;
    /** 湍流微扰：让扭曲不是完美圆环。 */
    private static final float SHIMMER_STRENGTH = 0.20f;
    /** 色散（红蓝偏折差异），斩击用很小值，只要一层"空气被挤压"的感觉。 */
    private static final float CHROMA_STRENGTH = 0.06f;
    /**
     * 环带厚度系数。比冲击波默认的 0.95 薄，让扭曲集中在剑气轮廓外侧一圈。
     */
    private static final float RING_WIDTH = 0.30f;

    /**
     * 扭曲球半径系数：{@code 剑气半长 × outerScale × 该系数}。
     * <p>不直接取剑气半长：刀身现在是 32 格长（半长 16），若球跟着长，球壳会落到
     * 16×1.45 ≈ 23 格外 —— 那已经不是"斩击外侧扭一下"，而是半个屏幕被拧过。
     * 取 0.35 并夹在 [{@link #WARP_RADIUS_MIN}, {@link #WARP_RADIUS_MAX}] 内，
     * 扭曲就稳定地待在刀身中段外侧那一圈。</p>
     */
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

    // ══════════════════════════════════════════════════════════════
    // 对外入口
    // ══════════════════════════════════════════════════════════════

    /**
     * 绘制一帧内所有使徒斩击的扭曲。
     *
     * <p>整批只做一次场景拷贝：扭曲是屏幕空间效果，一张拷贝能供本帧所有斩击共用；
     * 逐斩击各做一次全屏 blit 会在"20 刀齐飞"时把带宽吃光。</p>
     */
    static void renderAll(List<SwordAuraEntity> slashes, float partialTick, PoseStack poseStack) {
        if (slashes.isEmpty()) return;
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        ShaderInstance sh = SplendidingShaders.shockwaveShader;
        if (sh == null) return;

        boolean defer = HeldItemOutlineCompat.isOculusShaderPackActive();

        // 非延迟路径：这一帧的整批共用一次场景拷贝
        if (!defer) ShockwaveRenderer.doSceneCopy();

        for (SwordAuraEntity slash : slashes) {
            // 与实体层同一套插值：扭曲球必须跟着剑气的实际大小走，
            // 否则会出现"刀已经缩了、扭曲还挂着原来那么大"的脱节。
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

            // 球心的视图空间位置由 pose 自己算（见 drawWarpSphere 的说明），
            // 所以这里不需要再传世界坐标。
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

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

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

        // ── uPixelSize 的分母 = 被采样纹理（场景拷贝）的尺寸 ──
        //     gl_FragCoord 在主 RT 的像素空间里，场景拷贝也按主 RT 尺寸建立，
        //     两者必须一致；用窗口尺寸会在 Oculus 的 resolution scale 下整体偏移。
        float w = Math.max(1, ShockwaveRenderer.sceneCopyWidth() > 0
                ? ShockwaveRenderer.sceneCopyWidth() : mc.getWindow().getWidth());
        float h = Math.max(1, ShockwaveRenderer.sceneCopyHeight() > 0
                ? ShockwaveRenderer.sceneCopyHeight() : mc.getWindow().getHeight());

        // ── 球心 → 视图空间 ──
        //     用烘焙顶点用的同一个 pose 变换局部原点：pose = 视图矩阵 × T(实体) × S(半径)，
        //     缩放不影响原点，所以结果恰好等于"球心在视图空间的位置"，与顶点的空间严格一致。
        //     （不走 RenderSystem.getModelViewMatrix()：那条路要求它此刻恰好等于完整视图矩阵，
        //       多一层或少一层变换都会让畸变整体偏离球体。）
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
        // 纯折射：关掉冲击波那圈压缩壳亮带 —— 白色斩击自己就是白边，
        // 再叠一圈亮环会和 outline 抢视觉焦点。
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
