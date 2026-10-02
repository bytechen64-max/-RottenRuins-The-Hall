package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * 黑洞渲染器 —— 真实 3D 球体 + 光线追踪引力透镜着色器。
 *
 * <p>与之前的 billboard（始终面向相机的片）不同，这里绘制一个半径为
 * {@code 6·Rs} 的 UV 球网格。球体自带真实深度：前方方块正确遮挡、
 * 后半球被前半球深度剔除，看起来是一个实体球而非平面片。</p>
 *
 * <h3>光影兼容</h3>
 * 与 {@link ShockwaveRenderer} 相同的两路径策略：Oculus/Iris 激活时把
 * 视图空间参数快照入队，延迟到 {@code GameRenderer.renderLevel()} TAIL
 * 回放（见 {@link BlackHoleLateRenderQueue}），此时主帧缓冲已由光影
 * composite 完成，场景拷贝捕获的是合成后的画面；回放经
 * {@code LateOutlineRenderState} 绑定主 RT 直写，绕过 GBuffer。
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {

    private static final ResourceLocation DUMMY = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /**
     * 球体半径（以 Rs 计）—— 决定**透镜的可见范围**。
     * <p>黑洞盘面本身恒定是 2·Rs（被视界捕获的光），球体放大只放大
     * 外围的折射扭曲区，因此“很小的黑洞本体 + 很大的折射范围”是可以
     * 同时成立的：Rs=0.65 时盘面直径 1.3 格，而透镜直径 13 格。</p>
     */
    private static final float SPHERE_RADIUS = 10.0f;

    // Per-frame scene-copy texture（与 ShockwaveRenderer 相同的 blit 方案）
    private static int copyTex = -1, copyFbo = -1;
    private static int copyW = -1, copyH = -1;

    // ── 单位 UV 球网格（48×64） ──
    private static final int SPHERE_LAT = 48, SPHERE_LON = 64;
    private static final float TWO_PI = (float) (2.0 * Math.PI);
    private static final float PI_2 = (float) (Math.PI / 2.0);
    private static final float[][][] SPHERE_V = new float[SPHERE_LAT + 1][SPHERE_LON + 1][3];
    static {
        for (int la = 0; la <= SPHERE_LAT; la++) {
            float phi = -PI_2 + (float) Math.PI * la / SPHERE_LAT;
            float cp = (float) Math.cos(phi), sp = (float) Math.sin(phi);
            for (int lo = 0; lo <= SPHERE_LON; lo++) {
                float th = TWO_PI * lo / SPHERE_LON;
                SPHERE_V[la][lo][0] = cp * (float) Math.cos(th);
                SPHERE_V[la][lo][1] = sp;
                SPHERE_V[la][lo][2] = cp * (float) Math.sin(th);
            }
        }
    }

    public BlackHoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // 纯视觉特效 —— 不渲染影子（MC 1.20.1 的阴影半径是受保护字段）
        this.shadowRadius = 0.0F;
    }

    /**
     * Entity renderer path — skip; actual rendering happens in
     * {@link BlackHoleRenderHandler#onRenderLevel}.
     */
    @Override
    public void render(BlackHoleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource bufs, int light) {
        // no-op
    }

    /**
     * 渲染单个黑洞。由 {@link BlackHoleRenderHandler} 在所有实体绘制后调用，
     * 此时主帧缓冲已包含完整场景，场景拷贝才能捕获全部内容。
     */
    public static void renderOne(BlackHoleEntity e, float pt, PoseStack ps) {
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        float Rs = Math.max(e.getRadius(), 0.05f);
        float bend = Math.max(e.getBend(), 0.0f);

        // ── 生长 / 消退动画 ──
        // 黑洞不会“啪”地出现：开场约 1 秒从小长到略大于 1，再回落稳定；
        // 寿命最后 1.5 秒缩回 0。缩放直接乘进 modelView，同时据此算出
        // 球体半径传给着色器（透镜包络要跟着一起缩放，否则缩小时会变硬边）。
        float scale = lifeScale(e);
        if (scale <= 0.0005f) return;                        // 完全消失
        float sphereR = SPHERE_RADIUS * Rs * scale;

        // ps 在 handler 已含相机旋转并平移到世界原点，这里平移至实体位置 →
        // pose 即“视图空间模型矩阵”（含相机旋转，不含缩放）
        ps.pushPose();
        ps.translate(e.getX(), e.getY(), e.getZ());
        Matrix4f pose = new Matrix4f(ps.last().pose());
        ps.popPose();

        Vector3f bhView = new Vector3f();
        pose.transformPosition(bhView);   // 黑洞中心视图空间位置
        float camDist = bhView.length();

        // 相机在黑洞正后方、且未被球体积包裹 → 黑洞确实不在视野内，跳过。
        // 若相机已进入球体积（camDist < sphereR），球体包围相机：近处半球被
        // 近平面裁剪，远处半球仍在视野内，必须继续渲染，否则靠近时会整体消失。
        if (bhView.z >= -0.5f && camDist >= sphereR) return;

        // 完整 modelView = 视图 * 平移 * 缩放（单位球 → 半径 sphereR 的球）
        Matrix4f modelView = new Matrix4f(pose).scale(sphereR);

        // ── 光影激活 → 延迟入队 ──
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            BlackHoleLateRenderQueue.enqueue(bhView, modelView, Rs, bend, sphereR);
            return;
        }

        // ── 即时渲染（无光影） ──
        doSceneCopy();
        drawBlackHole(bhView, modelView, Rs, bend, sphereR);
    }

    /**
     * 生命周期缩放：淡入（长出）→ 稳定 → 淡出（缩回）。
     * <p>{@code age / lifetime} 为进度：</p>
     * <ul>
     *   <li>0 → 0.05：0 长到 1.35（带一点过冲，出现时更有冲击力）</li>
     *   <li>0.05 → 0.12：回落到 1.0</li>
     *   <li>0.12 → 0.85：稳定在 1.0</li>
     *   <li>0.85 → 1.0：缩回 0</li>
     * </ul>
     */
    private static float lifeScale(BlackHoleEntity e) {
        int life = Math.max(e.getLifetime(), 1);
        float t = Math.min(1.0f, e.getAge() / (float) life);
        final float overshoot = 1.35f;
        if (t < 0.05f) {
            return easeOutCubic(t / 0.05f) * overshoot;
        }
        if (t < 0.12f) {
            float k = (t - 0.05f) / 0.07f;
            return overshoot + (1.0f - overshoot) * easeInOut(k);
        }
        if (t < 0.85f) {
            return 1.0f;
        }
        float k = (t - 0.85f) / 0.15f;
        return 1.0f - easeInOut(k);
    }

    private static float easeOutCubic(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float easeInOut(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * 延迟回放路径（{@link BlackHoleLateRenderQueue#renderAll()}），
     * 先拷贝合成后的主帧，再绘制球体。
     */
    static void renderOneDeferred(Vector3f bhView, Matrix4f modelView, float Rs, float bend,
                                   float sphereR) {
        ShaderInstance sh = SplendidingShaders.blackHoleShader;
        if (sh == null) return;
        doSceneCopy();
        drawBlackHole(bhView, modelView, Rs, bend, sphereR);
    }

    // ── internal helpers ──────────────────────────────────────

    /** Blit 当前主帧缓冲到场景拷贝纹理。 */
    private static void doSceneCopy() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        int mainFbo = mc.getMainRenderTarget().frameBufferId;

        if (copyTex == -1 || w != copyW || h != copyH) {
            if (copyTex != -1) { GL11.glDeleteTextures(copyTex); GL30.glDeleteFramebuffers(copyFbo); }
            copyTex = GL11.glGenTextures();
            GlStateManager._bindTexture(copyTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            copyFbo = GL30.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, copyFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, copyTex, 0);
            copyW = w; copyH = h;
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
    }

    /**
     * 绘制黑洞球体。顶点先经 modelView 变换为视图空间坐标（vPos 即球面
     * 表面点），vsh 仅投影，片元以 vPos 重建视线方向做透镜光线追踪。
     *
     * <p>深度：depthMask(true) + LEQUAL，前半球写入深度后自动遮住后半球，
     * 且与场景深度正确比较 —— 前方方块可部分遮挡黑洞。</p>
     */
    private static void drawBlackHole(Vector3f bhView, Matrix4f modelView, float Rs, float bend,
                                       float sphereR) {
        ShaderInstance sh = SplendidingShaders.blackHoleShader;
        if (sh == null) return;

        // ── GL state ──
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(() -> sh);

        // ── Uniforms ──
        sh.safeGetUniform("uBlackHolePos").set(bhView.x, bhView.y, bhView.z);
        sh.safeGetUniform("uRadius").set(Rs);
        sh.safeGetUniform("uGrav").set(bend * Rs * Rs);
        // 球体半径（含动画缩放）——着色器据此决定透镜包络的覆盖范围，
        // 保证缩小时也铺满球面、边缘平滑归零。
        if (sh.getUniform("uSphereRadius") != null) {
            sh.safeGetUniform("uSphereRadius").set(sphereR);
        }
        sh.safeGetUniform("ScreenTexture").set(0);

        // ── 绑定场景拷贝纹理 ──
        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(copyTex);

        // ── 绘制球体网格 ──
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int la = 0; la < SPHERE_LAT; la++)
            for (int lo = 0; lo < SPHERE_LON; lo++) {
                float[] a = SPHERE_V[la][lo], b = SPHERE_V[la][lo + 1];
                float[] c = SPHERE_V[la + 1][lo], d = SPHERE_V[la + 1][lo + 1];
                vert(buf, modelView, a); vert(buf, modelView, c); vert(buf, modelView, b);
                vert(buf, modelView, b); vert(buf, modelView, c); vert(buf, modelView, d);
            }
        tess.end();

        // ── 恢复 ──
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(prev);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vert(BufferBuilder buf, Matrix4f modelView, float[] p) {
        buf.vertex(modelView, p[0], p[1], p[2]).color(1f, 1f, 1f, 1f).normal(0f, 1f, 0f).endVertex();
    }

    @Override public ResourceLocation getTextureLocation(BlackHoleEntity e) { return DUMMY; }
}
