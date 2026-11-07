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
 * 陨石砸中时的<b>动漫冲击帧</b>全屏屏效（{@code rendertype_meteor_screen}）。
 *
 * <h3>这里的"2bit"是风格，不是位深</h3>
 * <p>需求原话："类似动漫风格的爆炸效果，高对比、有限色阶、二值化/抖动感、
 * 强调轮廓和剪影"。所以它不是把世界压成几级色阶（那会得到一堆灰色台阶），
 * 而是在屏幕上<b>直接画一个炸开的东西</b>：全屏白闪 → 12 根尖刺的黑色剪影
 * → 剪影外一圈抖动出来的半调红边 → 速度线 / 硬白冲击环 → 淡出。
 * 整个调色板只有三个值：纯黑 / 红 / 纯白。</p>
 *
 * <h3>它怎么画</h3>
 * <p>一整屏四边形 + 临时把投影换成像素空间正交（{@code 0..1} 域），挂在
 * {@code AFTER_LEVEL} —— 世界 pass 的最末尾，地形 / 实体 / 粒子 / 云都已经
 * 画完，所以剪影与速度线盖得住整个画面；HUD 走另一趟正交投影，不受影响。</p>
 *
 * <h3>时间轴</h3>
 * <pre>
 *   0    → 160ms   全屏白闪（动漫的"曝光帧"）
 *   0    → 280ms   中心白热核
 *   0    → 450ms   尖刺剪影炸开（0.28 → 3.2 单位）
 *   450ms→ 1050ms  剪影继续扩、速度线拉长
 *   1050ms→ 1750ms 整体淡出
 * </pre>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorScreenEffect {

    private MeteorScreenEffect() {}

    private static final long DURATION_MS = 1900L;
    private static long startMillis = Long.MIN_VALUE;
    private static float seed;
    /** 爆炸点（世界坐标）：屏效的扩散中心由它投影得到。 */
    private static net.minecraft.world.phys.Vec3 impactAt = net.minecraft.world.phys.Vec3.ZERO;

    /**
     * 触发一次屏效。
     *
     * @param seed 决定射线相位，两次事件不该一模一样
     * @param at   爆炸点的世界坐标 —— 屏效从**这里**扩散，不是从屏幕正中
     */
    public static void trigger(float seed, net.minecraft.world.phys.Vec3 at) {
        MeteorScreenEffect.seed = seed;
        MeteorScreenEffect.impactAt = at;
        startMillis = System.currentTimeMillis();
    }

    /**
     * 当前强度 0..1；没在播就是 0。
     *
     * <p>这一版把淡入淡出**全部交给着色器**（白闪 / 形状扩张 / 末尾 fade 都在
     * 片元里按 {@code uTime} 算）。Java 侧只管"在不在播"：冲击帧不该有
     * Java 层的淡入，否则第一帧的白闪会被削弱。</p>
     */
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
            // 把这一帧拷下来 —— 冲击帧要**照着真实画面**重画漫画分镜，
            // 不是在上面叠图案（参考模组 composite.fsh 的做法）。
            ShockwaveRenderer.copySceneForOverlay();
            int sceneTex = ShockwaveRenderer.sceneTextureForOverlay();
            if (sceneTex <= 0) return;
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(sceneTex);
            // ⚠️ 传**纹理 id**（和 ShockwaveRenderer 里同一套用法）；传 0 会
            //    采样到 unit 0 的方块图集 —— 这正是"满屏橙黑碎块"的来源。
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


    /**
     * 世界坐标 → 屏幕 uv（0..1，y 向上）。
     *
     * <p>用相机的右/上/前三个基向量手动做视图变换，再套当前的投影矩阵 ——
     * 不依赖 pose stack 在 {@code AFTER_LEVEL} 时处于什么状态。落点跑到相机
     * 背后时，退回"画面左/右边缘"，让扩散仍然从正确的方向来。</p>
     */
    public static float[] projectToScreen(net.minecraft.world.phys.Vec3 world) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 eye = cam.getPosition();
        net.minecraft.world.phys.Vec3 rel = world.subtract(eye);

        // 世界 → 视空间：相对向量再过一遍相机四元数的共轭。
        //  直接用 Camera 的 look/up/left 在这版映射里类型是 Vector3f，
        //  和 Vec3 混用会编译不过；四元数这条路不依赖那几个 getter。
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        cam.rotation().conjugate(new Quaternionf()).transform(v);
        float vx = v.x;
        float vy = v.y;
        float vz = v.z;

        if (vz > -0.05f) {
            // 在背后：从画面的那一侧进来
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
