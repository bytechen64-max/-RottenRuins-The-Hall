package org.bytechen.rottenruins.client.entity.render.impl;

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
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.BlackHoleEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * 黑洞渲染器：半径 {@code SPHERE_RADIUS·Rs} 的 UV 球网格 + 光线追踪引力透镜着色器，
 * 球体自带真实深度（前方方块可遮挡、后半球被前半球剔除）；
 * 光影激活时改走 {@link BlackHoleLateRenderQueue} 帧末回放，直写主 RT 绕过 GBuffer。
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {

    private static final ResourceLocation DUMMY = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 球体半径（以 Rs 计），只决定透镜可见范围：盘面恒为 2·Rs，放大球体只放大外围折射扭曲区。 */
    private static final float SPHERE_RADIUS = 10.0f;

    // Per-frame scene-copy texture（与 ShockwaveRenderer 相同的 blit 方案）
    private static int copyTex = -1, copyFbo = -1;
    private static int copyW = -1, copyH = -1;

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

    @Override
    public void render(BlackHoleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource bufs, int light) {
    }

    /**
     * 渲染单个黑洞。由 {@link BlackHoleRenderHandler} 在所有实体绘制后调用，此时主帧缓冲已含完整场景。
     */
    public static void renderOne(BlackHoleEntity e, float pt, PoseStack ps) {
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        float Rs = Math.max(e.getRadius(), 0.05f);
        float bend = Math.max(e.getBend(), 0.0f);

        // 生长/消退：开场约 1 秒从 0 长到略大于 1 再回落，寿命最后 1.5 秒缩回 0；
        // 缩放同时乘进 modelView 并作为球体半径传给着色器，透镜包络才会跟着一起缩放。
        float scale = lifeScale(e);
        if (scale <= 0.0005f) return;
        float sphereR = SPHERE_RADIUS * Rs * scale;

        // ps 已含相机旋转并平移到世界原点，这里平移到实体位置 → pose 即视图空间模型矩阵。
        ps.pushPose();
        ps.translate(e.getX(), e.getY(), e.getZ());
        Matrix4f pose = new Matrix4f(ps.last().pose());
        ps.popPose();

        Vector3f bhView = new Vector3f();
        pose.transformPosition(bhView);
        float camDist = bhView.length();

        // 相机在黑洞正后方且未被球体积包裹才可跳过；camDist < sphereR 时球体包围相机，
        // 近半球被近平面裁掉但远半球仍在视野内，必须继续渲染，否则靠近时会整体消失。
        if (bhView.z >= -0.5f && camDist >= sphereR) return;

        Matrix4f modelView = new Matrix4f(pose).scale(sphereR);

        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            BlackHoleLateRenderQueue.enqueue(bhView, modelView, Rs, bend, sphereR);
            return;
        }

        doSceneCopy();
        drawBlackHole(bhView, modelView, Rs, bend, sphereR);
    }

    /**
     * 生命周期缩放（t = age/lifetime）：t&lt;0.05 长到 1.35（过冲），0.05→0.12 回落 1.0，
     * 0.12→0.85 稳定 1.0，0.85→1.0 缩回 0。
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

    static void renderOneDeferred(Vector3f bhView, Matrix4f modelView, float Rs, float bend,
                                   float sphereR) {
        ShaderInstance sh = SplendidingShaders.blackHoleShader;
        if (sh == null) return;
        doSceneCopy();
        drawBlackHole(bhView, modelView, Rs, bend, sphereR);
    }

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
     * 绘制黑洞球体：vPos 是视图空间球面点，vsh 只做投影，片元用 vPos 重建视线做透镜追踪。
     * depthMask(true)+LEQUAL：前半球写深度遮住后半球，并与场景深度比较，前方方块可部分遮挡。
     */
    private static void drawBlackHole(Vector3f bhView, Matrix4f modelView, float Rs, float bend,
                                       float sphereR) {
        ShaderInstance sh = SplendidingShaders.blackHoleShader;
        if (sh == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(() -> sh);

        sh.safeGetUniform("uBlackHolePos").set(bhView.x, bhView.y, bhView.z);
        sh.safeGetUniform("uRadius").set(Rs);
        sh.safeGetUniform("uGrav").set(bend * Rs * Rs);
        // 球体半径（含动画缩放）：着色器据此决定透镜包络覆盖范围，缩小时才不会边缘变硬。
        if (sh.getUniform("uSphereRadius") != null) {
            sh.safeGetUniform("uSphereRadius").set(sphereR);
        }
        sh.safeGetUniform("ScreenTexture").set(0);

        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(copyTex);

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
