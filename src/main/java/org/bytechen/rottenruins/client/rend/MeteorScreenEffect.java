package org.bytechen.rottenruins.client.rend;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.joml.Matrix4f;
import org.bytechen.rottenruins.client.entity.render.impl.ShockwaveRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 陨石砸中时的动漫冲击帧全屏屏效（rendertype_meteor_screen）：一整屏四边形，
 * 临时把投影换成 0..1 像素空间正交，挂在 AFTER_LEVEL 末尾绘制。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorScreenEffect {

    private MeteorScreenEffect() {}

    private static final long DURATION_MS = 1900L;
    private static long startMillis = Long.MIN_VALUE;
    private static float seed;
    /** 爆炸点（世界坐标）：屏效的扩散中心由它投影得到。 */
    private static net.minecraft.world.phys.Vec3 impactAt = net.minecraft.world.phys.Vec3.ZERO;

    /** seed 决定射线相位；at 是世界坐标爆炸点 —— 屏效从那里扩散，不是屏幕正中。 */
    public static void trigger(float seed, net.minecraft.world.phys.Vec3 at) {
        MeteorScreenEffect.seed = seed;
        MeteorScreenEffect.impactAt = at;
        startMillis = System.currentTimeMillis();
    }

    /** 当前强度 0..1；淡入淡出全在着色器按 uTime 算，Java 侧做淡入会削弱首帧白闪。 */
    public static float intensity() {
        if (startMillis == Long.MIN_VALUE) return 0f;
        long age = System.currentTimeMillis() - startMillis;
        if (age < 0 || age > DURATION_MS) {
            startMillis = Long.MIN_VALUE;
            return 0f;
        }
        float t = age / 1000f;
        // 起手 50ms 拉满（冲击帧不该有淡入感），保持到 0.45s，1.0s 收完
        float in = Mth.clamp(t / 0.05f, 0f, 1f);
        float out = 1f - Mth.clamp((t - 0.45f) / 0.55f, 0f, 1f);
        return in * out;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        float i = intensity();
        if (i <= 0.004f) return;

        ShaderInstance sh = SplendidingShaders.meteorScreenShader;
        if (sh == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        int prevTexture = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            // 冲击帧要照着真实画面重画漫画分镜，不是在上面叠图案。
            ShockwaveRenderer.copySceneForOverlay();
            int sceneTex = ShockwaveRenderer.sceneTextureForOverlay();
            if (sceneTex <= 0) return;
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(sceneTex);
            // ⚠️ 传纹理 id（同 ShockwaveRenderer）；传 0 会采样到 unit 0 的方块图集，
            //    这正是"满屏橙黑碎块"的来源。
            sh.setSampler("SceneTexture", sceneTex);
            // 像素空间正交：四边形直接给 (0,0)..(1,1)，与屏幕分辨率无关
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0f, 1f, 0f, 1f, -1f, 1f),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setShader(() -> sh);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            // 输出 alpha = 1（整帧替换），所以关掉混合
            RenderSystem.disableBlend();

            float t = (System.currentTimeMillis() - startMillis) / 1000f;
            setUniform(sh, "uTime", t);
            setUniform(sh, "uIntensity", i);
            setUniform(sh, "uSeed", seed);
            // 场景拷贝的像素尺寸（网点 / 墨线都按真实像素算）
            var rt = mc.getMainRenderTarget();
            setUniform2(sh, "uTexel", 1f / Math.max(1, rt.width), 1f / Math.max(1, rt.height));

            // 把爆炸点投影成屏幕 uv —— 形状、射线、白闪全部以它为原点扩散
            float[] cuv = projectToScreen(impactAt);
            setUniform2(sh, "uCenter", cuv[0], cuv[1]);
            setUniform(sh, "uCenterValid", 1f);

            Matrix4f identity = new Matrix4f();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(identity, 0f, 0f, 0f).color(1f, 1f, 1f, 1f).endVertex();
            buffer.vertex(identity, 0f, 1f, 0f).color(1f, 1f, 1f, 1f).endVertex();
            buffer.vertex(identity, 1f, 1f, 0f).color(1f, 1f, 1f, 1f).endVertex();
            buffer.vertex(identity, 1f, 0f, 0f).color(1f, 1f, 1f, 1f).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Meteor] 2bit 屏效绘制失败", t);
        } finally {
            GlStateManager._bindTexture(prevTexture);
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 世界坐标 → 屏幕 uv（0..1，y 向上）：相机基向量手动视图变换再套投影；落点在背后时退回左右边缘。 */
    public static float[] projectToScreen(net.minecraft.world.phys.Vec3 world) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 eye = cam.getPosition();
        net.minecraft.world.phys.Vec3 rel = world.subtract(eye);

        // 世界 → 视空间：相对向量过一遍相机四元数的共轭。Camera 的 look/up/left 映射里
        // 类型是 Vector3f，与 Vec3 混用编译不过；四元数这条路不依赖那几个 getter。
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        cam.rotation().conjugate(new Quaternionf()).transform(v);
        float vx = v.x;
        float vy = v.y;
        float vz = v.z;

        if (vz > -0.05f) {
            return new float[] { vx >= 0f ? 1.05f : -0.05f, 0.5f };
        }
        Vector4f p = new Vector4f(vx, vy, vz, 1f);
        RenderSystem.getProjectionMatrix().transform(p);
        if (p.w <= 1.0e-5f) return new float[] { 0.5f, 0.5f };
        float nx = p.x / p.w;
        float ny = p.y / p.w;
        return new float[] { nx * 0.5f + 0.5f, ny * 0.5f + 0.5f };
    }

    private static void setUniform2(ShaderInstance sh, String name, float a, float b) {
        var u = sh.getUniform(name);
        if (u != null) u.set(a, b);
    }

    private static void setUniform(ShaderInstance sh, String name, float v) {
        var u = sh.getUniform(name);
        if (u != null) u.set(v);
    }
}
