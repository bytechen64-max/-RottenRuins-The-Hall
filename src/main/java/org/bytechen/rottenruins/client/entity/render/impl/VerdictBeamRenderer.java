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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictBeamEntity;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDebug;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictTuning;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 天穹裁决 · 垂直光柱渲染器：沿 +Y 的圆柱侧面（不含底盖）。顶点格式 {@code POSITION_COLOR_NORMAL}：
 * {@code Color.rg} 当贴图坐标（径向 / 高度归一化），{@code Normal} 是径向方向（边缘光与背面剔除用）。
 */
public class VerdictBeamRenderer extends EntityRenderer<VerdictBeamEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final int SEGMENTS = 48;
    private static final int RINGS = 32;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    private static final float BEAM_BRIGHTNESS = 0.72f;

    private static float brightness() {
        return BEAM_BRIGHTNESS * VerdictTuning.beamBrightness();
    }

    /** 相机离中轴多近时开始放弃背面剔除：取当前（扩散后）半径的 1.6 倍。 */
    private static final float INSIDE_SOFTEN = 1.6f;

    /**
     * 背面剔除强度：0 = 全保留（看得到内壁），1 = 完全剔除。相机在柱内时所有面外法线都背对相机，
     * 硬剔会让光柱在眼前消失，而这个技能恰恰是"在你脚下立柱、你在它里面"，所以在
     * {@code d >= 1.6R → 1} 与 {@code d = 0 → 0} 之间做 smoothstep 过渡。只影响画不画，不影响判定。
     */
    private static float backCullFactor(VerdictBeamEntity entity, float radius) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - entity.getX();
        double dz = cam.z - entity.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float outer = radius * INSIDE_SOFTEN;
        float inner = radius * 0.5f;
        if (dist >= outer) return 1f;
        if (dist <= inner) return 0f;
        float t = (float) ((dist - inner) / (outer - inner));
        return t * t * (3f - 2f * t);
    }

    /** 近白（爆发）/ 天蓝（领域）：与 DomeriteLongsword 的描边色同值。 */
    private static final int CORE_RGB = 0xF0F8FF;
    private static final int EDGE_RGB = 0x87CEFA;

    public VerdictBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VerdictBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        // 光影包的阴影贴图那一趟也会调实体渲染器：让它画出去就会往阴影贴图里写几何（光柱自己投出柱状影子）。
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        float intensity = entity.intensity(partialTick);
        if (intensity <= 0.004f) return;

        // 网格按<b>起始</b>半径建一次，扩散靠两个缩放系数放大（每帧重建 9216 个顶点是纯浪费）。
        // ⚠️ 缩放只能作用于局部几何：乘到带平移的坐标上，柱子会整个漂在天上（见 vsh 的 uBeamScale）。
        float baseRadius = entity.getRadius();
        float baseLength = entity.getLength();
        if (baseRadius <= 0.01f || baseLength <= 0.05f) return;

        float scaleXZ = entity.currentRadius(partialTick) / Math.max(baseRadius, 1e-3f);
        // 纵向系数单独封顶：视觉高度是 440 格量级，跟着横向一起 ×7.7 会越过远裁剪面。
        float scaleY = entity.currentHeightScale(partialTick);
        if (scaleXZ <= 0.01f || scaleY <= 0.01f) return;

        // 着色器没加载就整个实体静默跳过：宁可看不见，也不要画出一根颜色错的光柱。
        ShaderInstance shader = SplendidingShaders.verdictBeamShader;
        if (shader == null) return;

        float time = (entity.tickCount + partialTick) * 0.05f;
        // 扫描球 0 → 1 在 70% 的生命里走完，尾段只剩静态光柱
        float scanT = Mth.clamp(entity.lifeProgress(partialTick) / 0.7f, 0f, 1f);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();

        // 深度测试 + 写入都必须显式打开：Tesselator 路线不走 RenderType 的状态设置，
        // 少了这两句的症状是光柱穿透一切 / 像一张贴在所有物体前面的发光贴纸。
        RenderSystem.depthMask(true);

        // 缩放和位移待在同一处（矩阵）才是对的：顶点着色器里乘 scale 会改掉 vLocal 语义，
        // 症状是"莫名其妙的坐标变换"且极难定位。
        poseStack.pushPose();
        poseStack.scale(scaleXZ, scaleY, scaleXZ);
        Matrix4f matrix = poseStack.last().pose();

        // 光影包激活时世界 pass 落在 GBuffer 上，自定义着色器的自发光体在里面会被当成反照率材质，
        // 所以改为入队、等 pack 合成完（renderLevel 的 TAIL）在 MAIN_TARGET 上回放。
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            // 相机位置与两个矩阵必须<b>此刻</b>快照：回放发生在更晚阶段，届时回读拿到的是下一帧的值。
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            VerdictBeamLateRenderQueue.enqueue(
                    RenderSystem.getProjectionMatrix(),
                    RenderSystem.getModelViewMatrix(),
                    entity.getX(), entity.getY(), entity.getZ(),
                    cam.x, cam.y, cam.z,
                    baseRadius, baseLength, scaleXZ, scaleY,
                    time, intensity * brightness(), scanT,
                    entity.lifeProgress(partialTick),
                    (entity.getAge() + partialTick) / 20f,
                    backCullFactor(entity, entity.currentRadius(partialTick)));
            poseStack.popPose();
            restoreGlState();
            return;
        }

        // 必须显式绑着色器：Tesselator.end() 用的是 RenderSystem 当前绑定的那个，不是 RenderType 里的。
        RenderSystem.setShader(() -> shader);

        applyUniforms(shader, baseRadius, baseLength, time, intensity * brightness(),
                scanT, entity.lifeProgress(partialTick),
                (entity.getAge() + partialTick) / 20f,
                new Matrix3f(poseStack.last().normal()),
                backCullFactor(entity, entity.currentRadius(partialTick)));

        logBeamProbe(entity, matrix, baseRadius, baseLength, scaleXZ, scaleY, shader);

        emitGeometry(shader, matrix, baseRadius, baseLength);

        // 与上面的 pushPose() 必须成对：少一次 pop 会把 LevelRenderer 共享的 PoseStack 弹空，
        // 最后在 renderLevel 收尾的 checkPoseStack() 里崩（Pose stack not empty），编译期查不出来。
        poseStack.popPose();

        restoreGlState();
    }

    private static void restoreGlState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 全部 uniform 都在这里设置：立即绘制与光影包下的延后回放共用同一份，两条路径的画法不会分叉。 */
    static void applyUniforms(ShaderInstance shader,
                              float baseRadius, float baseLength,
                              float time, float intensity, float scanT,
                              float lifeProgress, float ageSeconds,
                              Matrix3f normalToView, float backCull) {
        setUniform(shader, "uTime", time);
        setUniform(shader, "uIntensity", intensity);
        // uRadius / uHeight 是<b>网格的基准尺寸</b>（起始半径 / 长度），片元用它们做归一化，
        // 所以必须和顶点喂进去的几何一致；扩散的缩放走矩阵，不经过 uniform。
        setUniform(shader, "uRadius", baseRadius);
        setUniform(shader, "uHeight", baseLength);
        setUniform(shader, "uScanT", scanT);
        setUniform(shader, "uProgress", lifeProgress);
        // 用真实年龄而不是生命进度：三波打击的时机是 tick 级固定值，生命进度会被 maxAge 稀释。
        setUniform(shader, "uAge", ageSeconds);
        setUniformRGB(shader, "uCoreColor", CORE_RGB);
        setUniformRGB(shader, "uEdgeColor", EDGE_RGB);

        // 模型 → 视图 3x3：Tesselator 路线没有现成的法线矩阵，直接从 poseStack 取；传的是<b>当前</b>半径。
        // ⚠️ "哪个方向算朝相机"的符号约定在 fsh 里是靠实测定的，别按"相机朝 -Z"推导（会把前后壁判反）。
        setUniformMat3(shader, "uNormalToView", new Matrix3f(normalToView));
        setUniform(shader, "uBackCull", backCull);
    }

    static void emitGeometry(ShaderInstance shader, Matrix4f matrix,
                             float baseRadius, float baseLength) {
        RenderSystem.setShader(() -> shader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        emitCylinderSide(buffer, matrix, baseRadius, baseLength);
        tess.end();
    }

    /**
     * 一次性定位探针：pose 链 / uniform 注册 / 缩放系数 / 相机裁剪这四种故障在画面上长得一样，
     * 这里把每个量一次性写进日志，看一行就能排掉前三个。限频 1 秒避免刷屏。
     */
    private static void logBeamProbe(VerdictBeamEntity entity, Matrix4f matrix,
                                     float baseRadius, float baseLength,
                                     float scaleXZ, float scaleY, ShaderInstance shader) {
        long now = System.currentTimeMillis();
        if (now - lastProbeMs < 1000L) return;
        lastProbeMs = now;

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VerdictDebug.log("beam probe: entity=(%.1f,%.1f,%.1f) cam=(%.1f,%.1f,%.1f) "
                        + "poseT=(%.2f,%.2f,%.2f) [应 ≈ 实体−相机 = (%.2f,%.2f,%.2f)] "
                        + "scaleXZ=%.3f scaleY=%.3f baseR=%.2f baseLen=%.1f "
                        + "uScaleXZ=%s uScaleY=%s 柱顶世界y=%.1f",
                entity.getX(), entity.getY(), entity.getZ(), cam.x, cam.y, cam.z,
                matrix.m30(), matrix.m31(), matrix.m32(),
                entity.getX() - cam.x, entity.getY() - cam.y, entity.getZ() - cam.z,
                scaleXZ, scaleY, baseRadius, baseLength,
                shader.getUniform("uScaleXZ") != null ? "ok" : "MISSING",
                shader.getUniform("uScaleY") != null ? "ok" : "MISSING",
                entity.getY() + baseLength * scaleY);
    }

    private static long lastProbeMs;

    private static void emitCylinderSide(BufferBuilder buffer, Matrix4f matrix,
                                          float radius, float length) {
        for (int ring = 0; ring < RINGS; ring++) {
            float h0 = ring / (float) RINGS;
            float h1 = (ring + 1) / (float) RINGS;
            float y0 = length * h0;
            float y1 = length * h1;

            for (int seg = 0; seg < SEGMENTS; seg++) {
                float a0 = TWO_PI * seg / SEGMENTS;
                float a1 = TWO_PI * (seg + 1) / SEGMENTS;

                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

                float x0 = radius * c0, z0 = radius * s0;
                float x1 = radius * c1, z1 = radius * s1;

                vertex(buffer, matrix, x0, y0, z0, radius, length);
                vertex(buffer, matrix, x0, y1, z0, radius, length);
                vertex(buffer, matrix, x1, y1, z1, radius, length);

                vertex(buffer, matrix, x0, y0, z0, radius, length);
                vertex(buffer, matrix, x1, y1, z1, radius, length);
                vertex(buffer, matrix, x1, y0, z1, radius, length);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                                float x, float y, float z, float radius, float length) {
        float d = Mth.clamp(Mth.sqrt(x * x + z * z) / Math.max(radius, 1e-4f), 0f, 1f);
        float h = Mth.clamp(y / Math.max(length, 1e-4f), 0f, 1f);
        float nx = x / Math.max(radius, 1e-4f);
        float nz = z / Math.max(radius, 1e-4f);

        buffer.vertex(matrix, x, y, z)
                .color(d, h, 0f, 1f)
                .normal(nx, 0f, nz)
                .endVertex();
    }

    /**
     * 传一个 float uniform。<b>缺注册会记一条 error 而不是静默跳过</b> —— 否则该 uniform 会一直停在
     * JSON 默认值，症状和"顶点算错了"一模一样（为此查过两轮）。按名字去重，不刷屏。
     */
    private static void setUniform(ShaderInstance shader, String name, float value) {
        var u = shader.getUniform(name);
        if (u == null) { warnMissingUniform(name); return; }
        u.set(value);
    }

    private static final java.util.Set<String> REPORTED_MISSING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnMissingUniform(String name) {
        if (REPORTED_MISSING.add(name)) {
            HallMod.LOGGER.error("[VerdictBeam] uniform '{}' 未在 rendertype_verdict_beam.json 的 "
                    + "uniforms 白名单里注册 —— 它会一直停在 JSON 默认值，"
                    + "渲染结果会像\"顶点算错了\"。请检查该 json。", name);
        }
    }

    /** {@code Uniform.set(Matrix3f)} 走 JOML 的 get(FloatBuffer)，是<b>列主序</b>，与 GLSL mat3 一致，不用转置。 */
    private static void setUniformMat3(ShaderInstance shader, String name, Matrix3f value) {
        var u = shader.getUniform(name);
        if (u == null) { warnMissingUniform(name); return; }
        u.set(value);
    }

    private static void setUniformRGB(ShaderInstance shader, String name, int rgb) {
        var u = shader.getUniform(name);
        if (u == null) return;
        u.set(((rgb >> 16) & 0xFF) / 255f,
              ((rgb >> 8) & 0xFF) / 255f,
              (rgb & 0xFF) / 255f);
    }

    @Override
    public ResourceLocation getTextureLocation(VerdictBeamEntity entity) {
        return DUMMY_TEXTURE;
    }
}
