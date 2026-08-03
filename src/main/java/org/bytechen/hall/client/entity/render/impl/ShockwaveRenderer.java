package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class ShockwaveRenderer extends EntityRenderer<ShockwaveEntity> {

    private static final ResourceLocation DUMMY = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final int LAT = 32, LON = 64;
    private static final float TWO_PI = (float)(2.0 * Math.PI);
    private static final float PI_2 = (float)(Math.PI / 2.0);

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

        // ── Pose ──
        ps.pushPose();
        ps.translate(e.getX(), e.getY() + 0.02, e.getZ());
        ps.scale(r, r, r);
        Matrix4f pose = new Matrix4f(ps.last().pose());
        Matrix3f nrm = new Matrix3f(ps.last().normal());
        ps.popPose();

        // ── Shader pack active → defer ──
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            ShockwaveLateRenderQueue.enqueue(pose, nrm, r, maxR,
                    alpha, e.getLifeProgress(), e.getRingPosition(), e.getRingWidth());
            return;
        }

        // ── Immediate render (no shader pack) ──
        doSceneCopy();
        drawShockwave(pose, nrm, sh, alpha, e.getLifeProgress(), e.getRingPosition(), e.getRingWidth());
    }

    /**
     * Called from {@link ShockwaveLateRenderQueue#renderAll()} during late replay.
     * Does the scene copy first (main framebuffer now has the composed scene),
     * then draws the sphere with late-deferred GL state.
     */
    static void renderOneDeferred(Matrix4f pose, Matrix3f nrm,
                                   float radius, float maxRadius,
                                   float alpha, float lifeProgress,
                                   float ringPosition, float ringWidth) {
        ShaderInstance sh = SplendidingShaders.shockwaveShader;
        if (sh == null) return;

        doSceneCopy();
        drawShockwave(pose, nrm, sh, alpha, lifeProgress, ringPosition, ringWidth);
    }

    // ── internal helpers ──────────────────────────────────────

    /** Blit the current main framebuffer into the scene-copy texture. */
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

    /** Draw the shockwave sphere with the given pose and uniforms. */
    private static void drawShockwave(Matrix4f pose, Matrix3f nrm, ShaderInstance sh,
                                       float alpha, float lifeProgress,
                                       float ringPosition, float ringWidth) {
        // ── GL state ──
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> sh);

        // ── Uniforms ──
        sh.safeGetUniform("uIntensity").set(alpha);
        sh.safeGetUniform("uLifeProgress").set(lifeProgress);
        sh.safeGetUniform("uRingPosition").set(ringPosition);
        sh.safeGetUniform("uRingWidth").set(ringWidth);
        sh.safeGetUniform("ScreenTexture").set(0);

        // ── Bind scene copy texture ──
        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(copyTex);

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
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(prev);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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
