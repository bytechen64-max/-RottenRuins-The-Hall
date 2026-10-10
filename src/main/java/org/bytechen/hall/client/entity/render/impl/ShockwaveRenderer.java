package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * 冲击波渲染器 —— 空气折射 / 波前透镜。
 *
 * <h3>渲染流程</h3>
 * <ol>
 *   <li>把主帧缓冲 blit 到 {@code copyTex}（场景拷贝）；</li>
 *   <li>绘制一个以爆心为球心、半径 = 当前波前半径的球壳；</li>
 *   <li>片元着色器把球壳当作真实存在的空气介质，做
 *       射线-球壳求交 → 光程积分 → 斯涅尔偏折，
 *       再用折射后的 UV 重采样场景拷贝。</li>
 * </ol>
 *
 * 因此畸变由真实几何驱动：正视球心时弦最长、掠射处趋近 0，
 * 边缘天然比中心畸变强；波前是一层薄而陡的压缩壳（锐利白边 +
 * 色散），其后方是宽而弱的稀疏尾（热扰动）。
 * 详见 {@code assets/hall/shaders/core/rendertype_shockwave.fsh}。
 */
public class ShockwaveRenderer extends EntityRenderer<ShockwaveEntity> {

    private static final ResourceLocation DUMMY = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 经纬细分。横向加密以获得平滑的轮廓（球体本身不参与光照）。 */
    private static final int LAT = 40, LON = 96;
    private static final float TWO_PI = (float)(2.0 * Math.PI);
    private static final float PI_2 = (float)(Math.PI / 2.0);

    // ── 效果强度（统一入口，便于后续挂配置） ──
    /**
     * 折射强度倍率。着色器内部已把峰值位移标定为
     * {@code 波前壳带宽 × 0.40}（该比例同时保证位移场斜率
     * ≈ 0.64 px/px < 1.0，不会把画面折成锯齿），本系数是全局缩放：
     * 1.0 表示用满标定值。
     * <p>带宽 = 波半径角度的 18%~86%（受 {@code uRingWidth} 调节），
     * 并有 90px 像素下限，所以远景的壳仍然是一整条厚带而不是发丝。</p>
     * <p>实测（1080p / 70° FOV，波半径 16 格）：</p>
     * <ul>
     *   <li>24 格外 → 壳厚约 283px，峰值位移约 113px，边缘亮带约 100px</li>
     *   <li>80 格外 → 约 90px 厚，约 36px 位移，亮带约 32px</li>
     *   <li>1000 格外 → 仍为约 90px 厚、约 36px 位移（像素下限托底）</li>
     *   <li>平视站在波内 → 覆盖全屏，约 162px 位移</li>
     * </ul>
     */
    private static final float WARP_STRENGTH = 1.0f;
    /** 尾流热扰动强度。 */
    private static final float SHIMMER_STRENGTH = 0.22f;
    /** 色散强度（红/蓝偏折差异比例）。 */
    private static final float CHROMA_STRENGTH = 0.12f;

    /** 读取配置里的折射强度倍率；配置未就绪时回落到 1.0。 */
    private static float refractionScale() {
        try {
            ConfigHolder<SplendidingConfig> holder = ConfigHelper.configHolder;
            if (holder == null || holder.get() == null) return 1.0f;
            return Math.max(0.0f, holder.get().shockwaveRefraction);
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    private static final float[][][] V = new float[LAT + 1][LON + 1][3];
    static {
        for (int la = 0; la <= LAT; la++) {
            float phi = -PI_2 + (float)Math.PI * la / LAT;
            float cp = (float)Math.cos(phi);
            float sp = (float)Math.sin(phi);
            for (int lo = 0; lo <= LON; lo++) {
                float th = TWO_PI * lo / LON;
                V[la][lo][0] = cp * (float)Math.cos(th);
                V[la][lo][1] = sp;
                V[la][lo][2] = cp * (float)Math.sin(th);
            }
        }
    }

    // Per-frame scene-copy texture to avoid framebuffer read-back artefacts
    private static int copyTex = -1, copyFbo = -1;
    private static int copyW = -1, copyH = -1;

    // 主帧缓冲深度纹理探测结果（-1 = 不可用）
    private static int depthTexId = -1;
    private static boolean depthTexProbeDone = false;

    public ShockwaveRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    /**
     * Entity renderer path — skip; actual rendering happens in
     * {@link ShockwaveRenderHandler#onRenderLevel} so the scene copy
     * includes all entities.
     */
    @Override
    public void render(ShockwaveEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource bufs, int light) {
        // no-op: rendering is handled after all entities via RenderLevelStageEvent
    }

    /**
     * Render a single shockwave. Called from the stage-event handler
     * after all entities have been drawn, so the framebuffer copy
     * captures the complete scene.
     *
     * <h3>Shader pack compatibility</h3>
     * When Oculus/Iris is active the scene copy + draw are deferred to
     * {@link ShockwaveLateRenderQueue} and replayed at
     * {@code renderLevel()} TAIL, after the shader pack has composed
     * the final scene to the main framebuffer.
     */
    public static void renderOne(ShockwaveEntity e, float pt, PoseStack ps) {
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        float r = Math.min((e.getAge() + pt) / 20f * e.getSpreadSpeed(), e.getMaxRadius());
        if (r < 0.05f) return;

        ShaderInstance sh = SplendidingShaders.shockwaveShader;
        if (sh == null) return;

        // ── Compute uniforms ──
        float maxR = Math.max(e.getMaxRadius(), 0.01f);
        float prog = r / maxR;
        float fi = Math.min(1.0f, prog / 0.08f);
        float fadeIn = 1.0f - (1.0f - fi) * (1.0f - fi) * (1.0f - fi);
        float fo = Math.max(0.0f, Math.min(1.0f, (prog - 0.80f) / 0.20f));
        float fadeOut = 1.0f - (fo * fo * (3.0f - 2.0f * fo));
        float fade = fadeIn * fadeOut;
        float alpha = e.getAlpha() * fade;
        if (alpha <= 0.002f) return;

        // ── Pose ──
        ps.pushPose();
        ps.translate(e.getX(), e.getY() + 0.02, e.getZ());
        ps.scale(r, r, r);
        Matrix4f pose = new Matrix4f(ps.last().pose());
        Matrix3f nrm = new Matrix3f(ps.last().normal());
        ps.popPose();

        // 爆心（世界空间）—— 从 pose 的平移列读取（此时缩放为 r、平移为爆心）
        float cx = pose.m30(), cy = pose.m31(), cz = pose.m32();

        // ── Shader pack active → defer ──
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            ShockwaveLateRenderQueue.enqueue(pose, nrm, cx, cy, cz, r, maxR,
                    alpha, e.getLifeProgress(), e.getRingPosition(), e.getRingWidth());
            return;
        }

        // ── Immediate render (no shader pack) ──
        doSceneCopy();
        drawShockwave(pose, nrm, sh, cx, cy, cz, r,
                alpha, e.getLifeProgress(), e.getRingPosition(), e.getRingWidth());
    }

    /**
     * Called from {@link ShockwaveLateRenderQueue#renderAll()} during late replay.
     * Does the scene copy first (main framebuffer now has the composed scene),
     * then draws the sphere with late-deferred GL state.
     */
    static void renderOneDeferred(Matrix4f pose, Matrix3f nrm,
                                   float cx, float cy, float cz,
                                   float radius, float maxRadius,
                                   float alpha, float lifeProgress,
                                   float ringPosition, float ringWidth) {
        ShaderInstance sh = SplendidingShaders.shockwaveShader;
        if (sh == null) return;

        doSceneCopy();
        drawShockwave(pose, nrm, sh, cx, cy, cz, radius,
                alpha, lifeProgress, ringPosition, ringWidth);
    }

    // ── internal helpers ──────────────────────────────────────

    /**
     * Blit the current main framebuffer into the scene-copy texture.
     *
     * <p>包级可见，供同一包内的 {@link ApostleSlashWarp} 复用：使徒斩击的空间扭曲
     * 需要同一张场景拷贝，两边共用一份可以保证<b>每帧只做一次全屏 blit</b>。</p>
     */
    static void doSceneCopy() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getMainRenderTarget() == null) return;

        // 尺寸必须取「主 render target」而不是「窗口」：Oculus/Iris 的 resolution scale
        // 会让主 RT 比窗口小（例如 0.75×）。以前用窗口尺寸当 srcRect 去 blit 一个更小的
        // 源 → 越界，glBlitFramebuffer 报 GL_INVALID_OPERATION，拷贝静默失败，
        // 屏幕上就是"扭曲不生效 / 卡住一张旧图"。
        int w = Math.max(1, mc.getMainRenderTarget().width);
        int h = Math.max(1, mc.getMainRenderTarget().height);
        int mainFbo = mc.getMainRenderTarget().frameBufferId;

        if (copyTex == -1 || w != copyW || h != copyH) {
            if (copyTex != -1) { GL11.glDeleteTextures(copyTex); GL30.glDeleteFramebuffers(copyFbo); }
            copyTex = GL11.glGenTextures();
            GlStateManager._bindTexture(copyTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            copyFbo = GL30.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, copyFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, copyTex, 0);
            copyW = w; copyH = h;
            depthTexProbeDone = false;   // 尺寸变化 → 渲染目标已重建，重新探测深度纹理
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        if (GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D) != copyTex) {
            GlStateManager._bindTexture(copyTex);
        }
    }

    /** Draw the shockwave sphere with the given pose and uniforms. */
    private static void drawShockwave(Matrix4f pose, Matrix3f nrm, ShaderInstance sh,
                                       float cx, float cy, float cz, float radius,
                                       float alpha, float lifeProgress,
                                       float ringPosition, float ringWidth) {
        Minecraft mc = Minecraft.getInstance();
        // uPixelSize 的分母必须是「被采样的那张纹理」的尺寸 —— 即场景拷贝的尺寸。
        // gl_FragCoord 在主 RT 的像素空间里，所以这两个值必须一致，否则整幅折射会偏移。
        float w = Math.max(1, copyW > 0 ? copyW : mc.getWindow().getWidth());
        float h = Math.max(1, copyH > 0 ? copyH : mc.getWindow().getHeight());

        // ── 爆心 → 视图空间 ──
        //  视图矩阵最后一行 (m03,m13,m23) 的相反数是相机世界位置
        //  （Mat4 是列主序，故 m30/m31/m32 即“第 4 行的前 3 列”，
        //   与 JOML Vector4f(0,0,0,1).mul(view) 的结果一致）。
        Matrix4f view = RenderSystem.getModelViewMatrix();
        float centerVSx = view.m00() * cx + view.m10() * cy + view.m20() * cz + view.m30();
        float centerVSy = view.m01() * cx + view.m11() * cy + view.m21() * cz + view.m31();
        float centerVSz = view.m02() * cx + view.m12() * cy + view.m22() * cz + view.m32();

        // ── GL state ──
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> sh);

        // ── Uniforms ──
        setUniform(sh, "uIntensity", alpha);
        setUniform(sh, "uLifeProgress", lifeProgress);
        setUniform(sh, "uRingPosition", ringPosition);
        setUniform(sh, "uRingWidth", ringWidth);
        setUniform(sh, "uWarp", WARP_STRENGTH * refractionScale());
        setUniform(sh, "uShimmer", SHIMMER_STRENGTH * refractionScale());
        setUniform(sh, "uChroma", CHROMA_STRENGTH);
        // 亮带开关：冲击波要那圈压缩壳亮带；使徒斩击的扭曲复用同一个着色器时
        // 会把它置 0（纯折射）。uniform 是全局的，所以这里必须显式写回 1.0，
        // 否则先画斩击再画冲击波时，冲击波会"继承"到 0 而丢掉亮带。
        setUniform(sh, "uGlow", 1.0f);
        setUniform(sh, "uPixelSize", 1.0f / w, 1.0f / h);
        setUniform(sh, "uCenterVS", centerVSx, centerVSy, centerVSz);
        // 球壳真实半径。必须在片元里当常数用 —— 用 length(vPos) 反推会
        // 拿到插值量，随三角形边界抖动，屏幕上表现为沿网格走的锯齿。
        setUniform(sh, "uWaveRadius", radius);

        // ── 深度纹理：让波前只作用于它前方的空气（被方块遮挡处不畸变） ──
        int depthTex = resolveDepthTexture(mc);
        boolean hasDepth = depthTex > 0;
        setUniform(sh, "uHasDepth", hasDepth ? 1.0f : 0.0f);

        // ── Bind scene-copy texture ──
        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(copyTex);
        sh.setSampler("ScreenTexture", copyTex);
        if (hasDepth) sh.setSampler("DepthTexture", depthTex);

        // ── Draw sphere ──
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int la = 0; la < LAT; la++)
            for (int lo = 0; lo < LON; lo++) {
                float[] a = V[la][lo], b = V[la][lo+1], c = V[la+1][lo], d = V[la+1][lo+1];
                vert(buf, pose, nrm, a); vert(buf, pose, nrm, c); vert(buf, pose, nrm, b);
                vert(buf, pose, nrm, b); vert(buf, pose, nrm, c); vert(buf, pose, nrm, d);
            }
        tess.end();

        // ── Restore ──
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(prev);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 取得主帧缓冲的深度纹理 id；若该纹理不可用（例如某些光影包把主
     * 目标改成 renderbuffer 深度附件），返回 -1 并让着色器跳过深度遮挡
     * 测试，而不会产生 GL 错误。
     *
     * 探测只做一次（窗口尺寸变化时重探），并在探测时刻意打开
     * GL_TEXTURE_2D —— 这正是绘制时会用到的目标，因此若纹理无效，
     * GL_INVALID_OPERATION 必然在此刻出现。
     */
    private static int resolveDepthTexture(Minecraft mc) {
        if (depthTexProbeDone) return depthTexId;
        depthTexProbeDone = true;
        depthTexId = -1;
        int id = -1;
        try {
            id = mc.getMainRenderTarget().getDepthTextureId();
        } catch (Throwable ignored) {
            return -1;
        }
        if (id <= 0) return -1;

        final int GL_INVALID_ENUM = 0x0500, GL_INVALID_OPERATION = 0x0502;
        while (GL11.glGetError() != GL11.GL_NO_ERROR) { /* 清空既有错误 */ }
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + 1);
        RenderSystem.bindTexture(id);
        int err = GL11.glGetError();
        if (err == GL11.GL_NO_ERROR || (err != GL_INVALID_ENUM && err != GL_INVALID_OPERATION)) {
            depthTexId = id;
        }
        return depthTexId;
    }

    private static void setUniform(ShaderInstance sh, String name, float v) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(v);
    }

    // ══════════════════════════════════════════════════════════════
    // 供同包内的 ApostleSlashWarp 复用（场景拷贝 + 深度纹理探测）
    // ══════════════════════════════════════════════════════════════

    /** 当前场景拷贝纹理 id（由 {@link #doSceneCopy()} 建立）。 */
    static int sceneCopyTextureId() {
        return copyTex;
    }

    /** 场景拷贝纹理宽度（-1 = 尚未建立）。片元里 {@code uPixelSize} 必须用这个尺寸。 */
    static int sceneCopyWidth() {
        return copyW;
    }

    /** 场景拷贝纹理高度。 */
    static int sceneCopyHeight() {
        return copyH;
    }

    /** 取主帧缓冲的深度纹理 id（-1 = 不可用）。 */
    static int resolveDepthTextureFor(Minecraft mc) {
        return resolveDepthTexture(mc);
    }

    private static void setUniform(ShaderInstance sh, String name, float a, float b) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(a, b);
    }

    private static void setUniform(ShaderInstance sh, String name, float a, float b, float c) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(a, b, c);
    }

    private static void vert(BufferBuilder buf, Matrix4f pose, Matrix3f nrm, float[] p) {
        float nx = nrm.m00*p[0] + nrm.m01*p[1] + nrm.m02*p[2];
        float ny = nrm.m10*p[0] + nrm.m11*p[1] + nrm.m12*p[2];
        float nz = nrm.m20*p[0] + nrm.m21*p[1] + nrm.m22*p[2];
        float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }
        buf.vertex(pose, p[0], p[1], p[2]).color(1f, 1f, 1f, 1f).normal(nx, ny, nz).endVertex();
    }

    @Override public ResourceLocation getTextureLocation(ShockwaveEntity e) { return DUMMY; }
}
