package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowFieldEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 巨型法阵渲染器 —— 一次绘制里画四样东西：地面法阵、中心的液态铬（片元里光线步进）、上方的方块扭曲、终末螺旋光柱。
 * <p><b>绘制顺序是有约束的，不能随便换</b>：① 场景拷贝（每帧一次）→ ② 全部扭曲柱（它是<b>替换</b>像素，必须在所有加法混合的东西之前，否则会把法阵和立方体一起盖掉）→ ③ 全部法阵 / 光柱 / 立方体（加法混合）。
 * <p>③ 内部光柱必须排在立方体<b>之前</b>：立方体会把剔除面切成 FRONT，而光柱要关剔除 —— 顺序反了光柱会共用"只画背面"的状态，症状是柱子凭空缺一半。绘制函数收 {@code Matrix4f} 而非 {@code PoseStack}，因为延后回放发生在下一帧。
 */
public class CrimsonVowFieldRenderer extends EntityRenderer<CrimsonVowFieldEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final float CORE_SIZE = CrimsonVowFieldEntity.CORE_SIZE;
    /** 立方体中心离地高度 / 半边长：核心小了一半（半边长 7.5 → 3.75）后这个系数必须跟着调大，取 1.65 让底面离地 0.65 个半边长 ≈ 2.4 格，明显悬空又仍在光柱高度区间里。 */
    private static final float CORE_LIFT = 1.65f;

    /** 核心渐变的两端颜色（需求："拥有紫色和粉色的颜色交替渐变"）：⚠️ 这是<b>染色用的乘数</b>（见 fsh 第 ⑦ 段的 {@code mix(vec3(1), grad, .55)}）而不是最终像素色，取值要比"看起来该有的颜色"更饱和一档，按最终色去取会被压成两片接近的淡紫（离屏对照过）。 */
    private static final float[] CORE_GRAD_PURPLE = { 0.54f, 0.09f, 1.00f };
    private static final float[] CORE_GRAD_PINK   = { 1.00f, 0.32f, 0.71f };
    /** 紫粉交替的额外漂移速度（周期/秒）：取 0.35 约 3 秒一个来回，比自转慢，不会和自转抢注意力。 */
    private static final float CORE_GRAD_RATE = 0.35f;

    /** 扭曲柱半径 / 法阵半径。比法阵略小：柱壁落在阵内，读起来才像"从阵里升起来的"。 */
    private static final float WARP_RADIUS_RATIO = 0.94f;
    private static final float BEAM_RADIUS_RATIO = 0.34f;

    public CrimsonVowFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** 实体渲染阶段不做任何事（见 {@link CrimsonVowFieldRenderHandler} 的类注释）。 */
    @Override
    public void render(CrimsonVowFieldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
    }

    /** 一帧里这个法阵要用到的全部标量：不可变快照让立即绘制与延后回放共用同一份绘制代码（回放时只有快照可信），参数"读一次"也避免半个画面用旧值、半个画面用新值的撕裂。 */
    static final class Params {
        final float x, groundY, z;
        final float radius, meshRadius;
        final float intensity, life, wave, finale, beamFall, impact;
        /** 触地之后经过了多少 tick（触地前恒为 0）。 */
        final float impactElapsed;
        /** 触地之后经过的<b>秒</b>；还没触地时是 -1 —— 着色器用它画地面那圈白热冲击环（⑪），并用"&lt; 0"当"还没触地"的判据（省一个布尔 uniform）。 */
        final float impactAge;
        /** 终末预警进度 0..1（预警开始前恒 0，光柱落下后钳在 1）。 */
        final float warn;
        /** 从"光柱开始下落"起算的 tick 数；预警段里是负数。 */
        final float beamElapsed;
        final float time, seed;
        final int phase;

        Params(float x, float groundY, float z,
               float radius, float meshRadius,
               float intensity, float life, float wave, float finale, float beamFall,
               float impact, float impactElapsed, float impactAge, float warn, float beamElapsed,
               float time, float seed, int phase) {
            this.x = x; this.groundY = groundY; this.z = z;
            this.radius = radius; this.meshRadius = meshRadius;
            this.intensity = intensity; this.life = life; this.wave = wave;
            this.finale = finale; this.beamFall = beamFall;
            this.impact = impact; this.impactElapsed = impactElapsed; this.impactAge = impactAge;
            this.warn = warn; this.beamElapsed = beamElapsed;
            this.time = time; this.seed = seed; this.phase = phase;
        }

        boolean warpVisible() { return intensity > 0.01f; }

        /** 预警段（天顶裂口 + 幽灵柱）是否还该画。 */
        boolean warnVisible() { return warn > 0.001f && intensity > 0.004f; }
        /**
         * 终末螺旋光柱这一帧要不要画。
         * <p><b>不能直接拿 {@link #intensity} 判</b>：它带着 22 tick 的淡出，而光柱下落到触地要走 {@code BEAM_FALL_TICKS} tick —— 光柱还没落地强度就已经是 0（症状："激光刚落下来就整根不见了"）。
         */
        boolean beamVisible() {
            return beamElapsed >= 0f && beamIntensity() > 0.004f;
        }

        /**
         * 光柱这一帧的总强度：下落期由 {@code beamFall} 淡入到 1，触地后按 {@code impactElapsed / BEAM_LINGER_TICKS} 收束到 0，再乘实体的淡入。{@code BEAM_FALL_TICKS} 只有 7 tick，淡入几乎瞬时 —— 刻意如此，它第一帧就该是满亮度。
         */
        float beamIntensity() {
            float fadeIn = Mth.clamp(intensity, 0f, 1f);
            float fall = Mth.clamp(beamFall, 0f, 1f);
            float imp = Mth.clamp(impactElapsed / BEAM_LINGER_TICKS, 0f, 1f);
            return Math.min(0.18f + 0.82f * fall, 1f - imp) * fadeIn;
        }
    }

    /** 终末光柱的余晖时长（tick）：28 tick ≈ 1.4 秒，够 Muzzle 爆闪"啪"一下再收掉，又不至于盖住地形被撕开的画面。 */
    private static final float BEAM_LINGER_TICKS = 28f;

    /**
     * 光柱三层各自的半径 / 法阵半径：软光晕 0.32（最宽最淡）、主体 0.245（电弧与下扫环）、白热核 0.075。等粗的发光管没有层次也就没有体积，三层共用一个 program 只是 uniform 不同。
     */
    private static final float BEAM_HALO_RATIO = 0.32f;
    private static final float BEAM_BODY_RATIO = 0.245f;
    private static final float BEAM_CORE_RATIO = 0.075f;

    /** 柱身锥度：顶面半径 / 底面半径。上细下粗才读得出"从上来"。 */
    private static final float BEAM_TOP_TAPER = 0.62f;

    /** 轮廓波动强度（传给 {@code uBeamWobble}），按层给：振幅 = {@code uBeamWobble * 半径}。需求是<b>边缘有波动感</b>而不是"柱子本身在扭"，所以外沿 0.26、芯 0.05（给 0.55 时侧影会变成"毛毛虫"，离屏预览里一眼可见）。 */
    private static final float BEAM_WOBBLE_HALO = 0.26f;
    private static final float BEAM_WOBBLE_BODY = 0.18f;
    private static final float BEAM_WOBBLE_CORE = 0.05f;

    /** 沿高度的分环数：波动推的是<b>顶点</b>，只有上下两条棱边的话侧影仍是直线；24 环 × 56 段 ≈ 2688 个三角形，每环约 1.5 格高。 */
    private static final int BEAM_RINGS = 24;

    /** 天顶裂口的半径 / 法阵半径：必须比法阵大一圈（2.1 倍）才像"天被捅开"，高度取 {@code BEAM_TOP + BEAM_LENGTH + 6} —— 正好在光柱起点上方，光柱像是从那个洞里抽出来的。 */
    private static final float PORTAL_RADIUS_RATIO = 2.1f;

    /** 预警段幽灵柱的前缘落点高度 / BEAM_TOP（预警结束时停在 28% 高，不触地）。 */
    private static final float GHOST_DESCEND = 0.72f;
    private static final float GHOST_SPAN = 22.0f;

    /** 由一个法阵实体算出这一帧的全部参数：所有随时间变化的量都吃 {@code partialTick}，否则 20Hz 的推进在 144fps 上会是明显的台阶。 */
    static Params paramsOf(CrimsonVowFieldEntity entity, float partialTick) {
        float x = (float) entity.getX();
        float z = (float) entity.getZ();
        float groundY = (float) entity.getY();

        float radius = entity.getRadius();
        float meshRadius = radius * CrimsonVowRenderSupport.HALO_RATIO;

        float intensity = entity.intensity(partialTick);
        float life = entity.lifeProgress(partialTick);
        float wave = entity.wavePhase(partialTick);
        float finale = entity.finaleProgress(partialTick);
        float beamFall = entity.beamFall(partialTick);
        float impactElapsed = entity.impactElapsed(partialTick);

        float impact = entity.getPhase() >= CrimsonVowFieldEntity.PHASE_IMPACT
                ? Mth.clamp(1f - impactElapsed / 10f, 0f, 1f) : 0f;

        // 触地"秒数"（-1 表示还没触地）：与 beamFall 同源推算而不读 phase —— 同步相位可能晚一 tick，会看出"已经砸到地上了、地上的环还没开始扩"。
        float beamElapsed = (entity.getAge() + Math.max(0f, partialTick))
                - entity.beamStartAge();
        float impactTicks = beamElapsed - CrimsonVowFieldEntity.BEAM_FALL_TICKS;
        float impactAge = impactTicks >= 0f ? impactTicks * 0.05f : -1f;

        float time = (entity.getAge() + partialTick) * 0.05f + (entity.getId() % 17) * 0.37f;

        return new Params(x, groundY, z, radius, meshRadius,
                intensity, life, wave, finale, beamFall, impact, impactElapsed,
                impactAge, entity.warningProgress(partialTick), beamElapsed,
                time, entity.getSeed() % 1024, entity.getPhase());
    }

    /** 立即路径：画一个法阵（{@code mat} 是已含 {@code translate(-camera)} 的视图矩阵，{@code basis} 来自 {@link CrimsonVowRenderSupport#viewBasis}）。 */
    static void drawAll(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        drawWarp(p, mat, basis, cam);
        drawBody(p, mat, basis, cam);
    }

    static void drawAllDeferred(Params p, Matrix4f viewMatrix, float[] basis, Vec3 cam) {
        drawWarp(p, viewMatrix, basis, cam);
        drawBody(p, viewMatrix, basis, cam);
    }

    /**
     * 画"法阵照耀的 y 轴区域"里那块方块扭曲：它读的是<b>场景拷贝</b>，所以调用方必须已经先做过一次 {@link ShockwaveRenderer#doSceneCopy()}（每帧一次，全帧共用）。视空间的两个量（{@code uBaseVS} / {@code uAxisVS}）由 Java 现算。
     */
    private static void drawWarp(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonWarpShader;
        if (shader == null || !p.warpVisible()) return;

        int copyTex = ShockwaveRenderer.sceneCopyTextureId();
        int copyW = ShockwaveRenderer.sceneCopyWidth();
        int copyH = ShockwaveRenderer.sceneCopyHeight();
        if (copyTex <= 0 || copyW <= 0 || copyH <= 0) return;

        float[] baseVS = new float[3];
        CrimsonVowRenderSupport.worldToView(basis, p.x, p.groundY, p.z,
                cam.x, cam.y, cam.z, baseVS);
        // R · e_y = R 的第 1 列 = (basis[1], basis[4], basis[7]) —— basis 存的是行。
        float axisX = basis[1], axisY = basis[4], axisZ = basis[7];

        int depthTex = ShockwaveRenderer.resolveDepthTextureFor(Minecraft.getInstance());
        boolean hasDepth = depthTex > 0;

        float warpRadius = Math.max(1.0f, p.radius * WARP_RADIUS_RATIO);

        // 这一趟是"替换"而不是叠加：输出就是重采样后的场景本身，所以混合用标准 alpha 混合并把 alpha 写 1。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        CrimsonVowRenderSupport.setUniform(shader, "uTime", p.time);
        CrimsonVowRenderSupport.setUniform(shader, "uIntensity", p.intensity);
        CrimsonVowRenderSupport.setUniform(shader, "uRadius", warpRadius);
        CrimsonVowRenderSupport.setUniform(shader, "uHeight", CrimsonVowFieldEntity.WARP_HEIGHT);
        CrimsonVowRenderSupport.setUniform(shader, "uWarp", 1.0f);
        CrimsonVowRenderSupport.setUniform(shader, "uChroma", 0.25f);
        CrimsonVowRenderSupport.setUniform(shader, "uFinale", p.finale);
        CrimsonVowRenderSupport.setUniform(shader, "uHasDepth", hasDepth ? 1f : 0f);
        CrimsonVowRenderSupport.setUniform2(shader, "uPixelSize", 1f / copyW, 1f / copyH);
        CrimsonVowRenderSupport.setUniform3(shader, "uBaseVS", baseVS[0], baseVS[1], baseVS[2]);
        CrimsonVowRenderSupport.setUniform3(shader, "uAxisVS", axisX, axisY, axisZ);

        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(copyTex);
        shader.setSampler("ScreenTexture", copyTex);
        if (hasDepth) shader.setSampler("DepthTexture", depthTex);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitCylinder(buffer, mat, p.x, p.groundY, p.z,
                warpRadius, warpRadius,
                p.groundY + CrimsonVowFieldEntity.WARP_HEIGHT, 1);
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(prev);

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void drawBody(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        if (p.intensity <= 0.004f) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        drawDisc(p, mat);
        drawPortal(p, mat);
        drawGhost(p, mat, basis, cam);
        // 光柱排在立方体之前：drawCube 会把剔除面切成 FRONT 又还原成 BACK，而光柱要**关掉剔除**（玩家会站在柱子里面）。
        // 顺序反了的话法阵 + 光柱会共用"只画背面"这个状态 —— 症状是柱子的一半凭空消失。
        drawBeam(p, mat, basis, cam);
        drawCube(p, mat, basis, cam);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void drawDisc(Params p, Matrix4f mat) {
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null || p.intensity <= 0.004f) return;

        RenderSystem.setShader(() -> shader);
        CrimsonVowRenderSupport.setUniform(shader, "uTime", p.time);
        CrimsonVowRenderSupport.setUniform(shader, "uIntensity", p.intensity);
        CrimsonVowRenderSupport.setUniform(shader, "uMode", 1f);
        CrimsonVowRenderSupport.setUniform(shader, "uRadius", p.radius);
        CrimsonVowRenderSupport.setUniform(shader, "uMeshRadius", p.meshRadius);
        CrimsonVowRenderSupport.setUniform(shader, "uCharge", 1f);
        CrimsonVowRenderSupport.setUniform(shader, "uPulse", p.wave);
        CrimsonVowRenderSupport.setUniform(shader, "uProgress", p.life);
        CrimsonVowRenderSupport.setUniform(shader, "uFinale", p.finale);
        CrimsonVowRenderSupport.setUniform(shader, "uSeed", p.seed);
        // 终末的两拍画在同一张圆盘上：⑩ 预警的三圈内收、⑪ 触地的白热冲击环。
        CrimsonVowRenderSupport.setUniform(shader, "uWarn", p.warn);
        CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", p.impactAge);
        // ⚠️ 轮廓波动是**顶点阶段**的，圆盘与光柱共用同一个 program：光柱那趟把 uBeamWobble / uBeamRadius 设成非零后这里必须显式清零，否则紧接着画的圆盘也会被推歪。
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamRadius", 0f);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitDisc(buffer, mat, p.x, p.groundY + 0.03f, p.z, p.meshRadius);
        BufferUploader.drawWithShader(buffer.end());
    }

    /**
     * 阵心那颗血晶（方法名沿用旧名 {@code drawCube}）：几何只是轴对齐包围盒，画面全在片元里。GL 状态刻意与其它几样<b>都不一样</b>：{@code disableDepthTest()}（盒面不是物体表面，遮挡由片元采样深度纹理完成）、{@code cullFace(GL_FRONT)} 只画背面（否则同一条视线步进两次，亮度翻倍且棱边留接缝）。
     */
    private static void drawCube(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonCubeShader;
        if (shader == null || p.intensity <= 0.004f) return;

        float deploy = Mth.clamp((p.life * CrimsonVowFieldEntity.DEFAULT_MAX_AGE)
                / CrimsonVowFieldEntity.DEPLOY_TICKS, 0f, 1f);
        float grow = 0.25f + 0.75f * (deploy * deploy * (3f - 2f * deploy));

        float lift = CORE_LIFT + p.finale * 0.55f;
        float size = Math.max(0.4f, CORE_SIZE * grow * (1f - 0.16f * p.finale));

        float cx = p.x;
        float cy = p.groundY + size * lift;
        float cz = p.z;

        int depthTex = ShockwaveRenderer.resolveDepthTextureFor(Minecraft.getInstance());
        boolean hasDepth = depthTex > 0;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        // 只画背面：RenderSystem 没有 cullFace 包装（原版不缓存剔除面），所以直接下 GL 调用，成对地在末尾恢复成 BACK。
        GL11.glCullFace(GL11.GL_FRONT);
        RenderSystem.setShader(() -> shader);

        CrimsonVowRenderSupport.setUniform(shader, "uTime", p.time);
        CrimsonVowRenderSupport.setUniform(shader, "uIntensity", p.intensity);
        CrimsonVowRenderSupport.setUniform(shader, "uProgress", p.life);
        CrimsonVowRenderSupport.setUniform(shader, "uImpact", Math.max(p.impact, p.finale * 0.35f));
        CrimsonVowRenderSupport.setUniform(shader, "uSize", size);
        CrimsonVowRenderSupport.setUniform(shader, "uGroundY", p.groundY + 0.10f);
        CrimsonVowRenderSupport.setUniform(shader, "uHasDepth", hasDepth ? 1f : 0f);
        CrimsonVowRenderSupport.setUniform3(shader, "uCenter", cx, cy, cz);
        CrimsonVowRenderSupport.setUniform3(shader, "uCamPos",
                (float) cam.x, (float) cam.y, (float) cam.z);
        CrimsonVowRenderSupport.setUniform3(shader, "uViewRight", basis[0], basis[1], basis[2]);
        CrimsonVowRenderSupport.setUniform3(shader, "uViewUp", basis[3], basis[4], basis[5]);
        CrimsonVowRenderSupport.setUniform3(shader, "uViewFwd", basis[6], basis[7], basis[8]);
        // 紫 / 粉交替渐变：自转那一半复用着色器里本来就有的 g_rot（由 uTime 驱动），Java 这边不再传角度 —— 少一个 uniform 就少一处"两边角速度对不上"（见 fsh 的 ⑦）。
        CrimsonVowRenderSupport.setUniform3(shader, "uGradA", CORE_GRAD_PURPLE[0],
                CORE_GRAD_PURPLE[1], CORE_GRAD_PURPLE[2]);
        CrimsonVowRenderSupport.setUniform3(shader, "uGradB", CORE_GRAD_PINK[0],
                CORE_GRAD_PINK[1], CORE_GRAD_PINK[2]);
        CrimsonVowRenderSupport.setUniform(shader, "uGradRate", CORE_GRAD_RATE);

        int prev = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (hasDepth) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(depthTex);
            shader.setSampler("DepthTexture", depthTex);
        }

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitBox(buffer, mat, cx, cy, cz, size);
        BufferUploader.drawWithShader(buffer.end());

        if (hasDepth) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(prev);
        }

        GL11.glCullFace(GL11.GL_BACK);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);    }

    /**
     * 自天而降的一道光（{@code rendertype_crimson_sigil}，uMode = 3）：三层同心圆柱 + 柱底一个向下的锥当枪尖。
     * <p>必须是<b>世界空间几何</b>而不是屏幕叠层：屏幕空间的光带横贯画面、与相机朝向无关，读起来像贴在屏幕上；换成柱面坐标后横向光带就成了绕柱而下的电弧。
     * <p>几何只发"这一帧可见的那一截"，且喂的是<b>绝对世界高度</b>（电弧相位与落点判定都活在"格"上，折成比例柱子下落时图案会跟着伸缩）。柱面是<b>开口</b>的、两个三角形绕序也不一致 —— 所以显式关剔除、画完还原。
     */
    private static void drawBeam(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null || !p.beamVisible()) return;

        BeamSpan span = beamSpan(p);
        float impact = Mth.clamp(1f - p.impactElapsed / 10f, 0f, 1f);

        // 不写深度（与法阵/立方体一样是自发光的加法层），但深度测试必须留着 —— 前方的方块与生物要挡得住它。
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        float shrink = 1f - 0.18f * span.collapse();
        float haloR = Math.max(0.55f, p.radius * BEAM_HALO_RATIO) * shrink;
        float bodyR = Math.max(0.38f, p.radius * BEAM_BODY_RATIO) * shrink;
        float coreR = Math.max(0.16f, p.radius * BEAM_CORE_RATIO) * shrink;

        float baseI = p.beamIntensity();
        float tipFade = 1f - span.collapse();

        // 枪尖必须**每层各来一个**、半径等于该层半径 —— 共用一个小锥的话，最外层（光晕）的开口圆筒会露出来，从下往上看就是一只"亮碗"。
        float[] radii = { haloR, bodyR, coreR };
        float[] wobbles = { BEAM_WOBBLE_HALO, BEAM_WOBBLE_BODY, BEAM_WOBBLE_CORE };
        float[] scales = { 0.55f, 1.00f, 1.00f };
        // uLayer：4 = 外光晕，0 = 主体（等离子鞘 + 细电弧 + 下扫脉冲），1 = 白热核（丝状电流）。
        float[] layers = { 4f, 0f, 1f };
        float[] tapers = { 0.93f, BEAM_TOP_TAPER, BEAM_TOP_TAPER };
        float[] tipLen = { 0.30f, 0.55f, 0.95f };
        // 外光晕层不画枪尖锥：那种浅锥从下往上看就是「灯罩 / 蘑菇伞」，它的开口改由 shader 的底部淡出收掉。
        float[] tipBright = { 0.00f, 0.70f, 1.00f };

        for (int i = 0; i < 3; i++) {
            float r = radii[i];
            float layer = layers[i];
            float hi = baseI * scales[i];
            float headLen = Math.max(0.60f, r * 0.55f) * tipLen[i];
            float shaftBottom = Math.min(span.top(), span.bottom() + headLen);

            if (span.top() - shaftBottom > 0.05f) {
                emitBeamCylinder(shader, p, mat, basis, cam, span,
                        shaftBottom, span.top(), r, r * tapers[i],
                        layer, hi, wobbles[i], BEAM_RINGS);
            }

            float ti = hi * tipFade * tipBright[i];
            if (ti > 0.004f) {
                emitBeamCylinder(shader, p, mat, basis, cam, span,
                        span.bottom(), span.bottom() + headLen, 0.02f, r,
                        layer, ti, 0f, 4);
            }
        }

        // 触地飞溅（uLayer = 5）：柱底一圈宽喇叭 + 角向火舌，只在触地后 10 tick 内存在；落地那一下该是"炸开"而不是"戳住"。
        if (impact > 0.01f) {
            float flareBase = Math.max(bodyR * 4.2f, 1.6f);
            BeamSpan flareSpan = new BeamSpan(p.groundY, p.groundY + 3.4f, 0f);
            emitBeamCylinder(shader, p, mat, basis, cam, flareSpan,
                    p.groundY, p.groundY + 3.4f, flareBase, bodyR * 1.25f,
                    5f, baseI * impact * 1.10f, 0f, 6);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
    }

    private static float portalY(Params p) {
        return p.groundY + CrimsonVowFieldEntity.BEAM_TOP
                + CrimsonVowFieldEntity.BEAM_LENGTH + 6f;
    }

    /**
     * 天顶裂口（{@code uMode = 4}）：张在 {@link #portalY} 处的一张水平圆盘 —— 光柱先"闪一下"变成"从哪里来"，靠的就是它提前裂开 1.5 秒。圆盘是水平几何：会随视角产生透视、会被山挡住，所以它"长在世界里"而不是贴在镜头上；触地后自己再炸一下再淡掉。
     */
    private static void drawPortal(Params p, Matrix4f mat) {
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null || !p.warnVisible()) return;

        float fade = p.impactAge >= 0f ? 1f - Mth.clamp(p.impactAge / 0.75f, 0f, 1f) : 1f;
        float intensity = p.intensity * 0.85f * fade;
        if (intensity <= 0.004f) return;

        float y = portalY(p);
        float r = Math.max(6f, p.radius * PORTAL_RADIUS_RATIO);

        RenderSystem.setShader(() -> shader);
        CrimsonVowRenderSupport.setUniform(shader, "uTime", p.time);
        CrimsonVowRenderSupport.setUniform(shader, "uIntensity", intensity);
        CrimsonVowRenderSupport.setUniform(shader, "uMode", 4f);
        CrimsonVowRenderSupport.setUniform(shader, "uRadius", r);
        CrimsonVowRenderSupport.setUniform(shader, "uMeshRadius", r);
        CrimsonVowRenderSupport.setUniform(shader, "uProgress", p.life);
        CrimsonVowRenderSupport.setUniform(shader, "uFinale", p.finale);
        CrimsonVowRenderSupport.setUniform(shader, "uSeed", p.seed);
        CrimsonVowRenderSupport.setUniform(shader, "uWarn", p.warn);
        CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", p.impactAge);
        // ⚠️ 圆盘不接受柱面轮廓波动，必须显式清零：同一个 program 的 uniform 是全局状态，光柱那一趟会把 uBeamWobble 设成非零。
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitDisc(buffer, mat, p.x, y, p.z, r);
        BufferUploader.drawWithShader(buffer.end());
    }

    /**
     * 幽灵柱（{@code uLayer = 2}）：从裂口一直垂到半空的一根极淡光柱，把"天上那个洞"和"地上这个阵"连起来。预警段只灌到 {@link #GHOST_DESCEND} 就停 —— 灌到底会让最后落下的那一下失去意义。
     */
    private static void drawGhost(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null || !p.warnVisible()) return;

        float portalY = portalY(p);
        // 预警段把光"灌"到哪先算出来，光柱开始下落后要用它兜底。
        // ⚠️ 不能直接切到光柱前缘：光柱从 BEAM_TOP 开始，直接切会让这根柱子**凭空跳上去 40 格**；取两者的较低者，通道才连续。
        float warnBottom = Mth.lerp(GHOST_DESCEND,
                portalY - 2f, p.groundY);
        float bottom = p.beamElapsed < 0f
                ? Mth.lerp(smoothstep01(Mth.clamp(p.warn, 0f, 1f)), portalY - 2f, warnBottom)
                : Math.min(warnBottom, beamSpan(p).bottom());
        if (portalY - bottom < 0.5f) return;

        float fade = p.impactAge >= 0f ? 1f - Mth.clamp(p.impactAge / 0.9f, 0f, 1f) : 1f;
        float intensity = p.intensity * (0.16f + 0.34f * Mth.clamp(p.warn, 0f, 1f)) * fade;
        if (intensity <= 0.004f) return;

        RenderSystem.setShader(() -> shader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // 比主体宽一倍多、比光晕窄：它表达的是"这一整条通道"；太宽会变成一块糊住画面的紫雾。
        float r = Math.max(0.9f, p.radius * BEAM_BODY_RATIO * 1.7f);
        BeamSpan shaft = new BeamSpan(bottom, portalY, 0f);
        emitBeamCylinder(shader, p, mat, basis, cam, shaft, bottom, portalY,
                r, r * 0.85f, 2f, intensity, 0.28f, 12);
    }

    /**
     * 发一层光柱：写好这一层的全部 uniform，再发一段（可带锥度的）圆柱。
     * <p>uniform 必须<b>整组</b>重写，不能只改半径：同一个 program 的 uniform 是全局状态，上一层留下的 {@code uBeamHead} / {@code uBeamSpan} / {@code uLayer} 会让下一层用别人的尺寸算图案。
     * <p>{@code geomBottom} / {@code geomTop} 与 {@code fx} 必须分开给：枪尖锥的几何从柱底开始，图案前缘仍是 {@code fx.bottom()}，否则锥会把"前缘"往下拽。
     */
    private static void emitBeamCylinder(ShaderInstance shader, Params p, Matrix4f mat,
                                         float[] basis, Vec3 cam, BeamSpan fx,
                                         float geomBottom, float geomTop,
                                         float baseRadius, float topRadius,
                                         float layer, float intensity, float wobble, int rings) {
        float bottomY = fx.bottom();
        float topY = fx.top();

        CrimsonVowRenderSupport.setUniform(shader, "uTime", p.time);
        CrimsonVowRenderSupport.setUniform(shader, "uIntensity", intensity);
        CrimsonVowRenderSupport.setUniform(shader, "uMode", 3f);
        CrimsonVowRenderSupport.setUniform(shader, "uLayer", layer);
        // uRadius / uMeshRadius 在光柱分支里是恒等变换，照给是为了让 `r = Color.r * (uMeshRadius / uRadius)` 不把上一层的光柱半径带进来。
        float layerRadius = Math.max(0.05f, Math.max(baseRadius, topRadius));
        CrimsonVowRenderSupport.setUniform(shader, "uRadius", layerRadius);
        CrimsonVowRenderSupport.setUniform(shader, "uMeshRadius", layerRadius);
        CrimsonVowRenderSupport.setUniform(shader, "uProgress", p.life);
        CrimsonVowRenderSupport.setUniform(shader, "uFinale", p.finale);
        CrimsonVowRenderSupport.setUniform(shader, "uSeed", p.seed);
        CrimsonVowRenderSupport.setUniform(shader, "uWarn", p.warn);
        CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", p.impactAge);
        // 未截断的爆闪：Params.impact 在 10 tick 处被钳成 0，那一下会硬切。
        CrimsonVowRenderSupport.setUniform(shader, "uImpact",
                Mth.clamp(1f - p.impactElapsed / 10f, 0f, 1f));
        CrimsonVowRenderSupport.setUniform(shader, "uBeamBase", p.groundY);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamHead", bottomY);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamSpan", Math.max(topY - bottomY, 0.75f));
        CrimsonVowRenderSupport.setUniform(shader, "uBeamRadius", layerRadius);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", wobble);

        // 柱轴换算到视空间给顶点着色器：顶点那边用 "视空间顶点 − 视空间柱轴" 直接得到径向 —— 这是唯一不会出错的做法，任何"从相机指向顶点"的近似都差近 90°，轮廓会逐行等宽、看起来像没生效。
        float[] axisVS = new float[3];
        CrimsonVowRenderSupport.worldToView(basis, p.x, p.groundY, p.z,
                cam.x, cam.y, cam.z, axisVS);
        CrimsonVowRenderSupport.setUniform3(shader, "uBeamAxisVS",
                axisVS[0], axisVS[1], axisVS[2]);
        CrimsonVowRenderSupport.setUniform3(shader, "uBeamAxisDir",
                basis[1], basis[4], basis[7]);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitCylinder(buffer, mat, p.x, geomBottom, p.z,
                baseRadius, topRadius, geomTop, rings);
        BufferUploader.drawWithShader(buffer.end());
    }

    private record BeamSpan(float bottom, float top, float collapse) {}

    private static float smoothstep01(float x) {
        float t = Mth.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * 算出这一帧光柱的可见段：下落用二次缓入（匀速读起来像电梯；1.45 次幂是"读得出迟疑、又仍在往下掉"的折中），触地后只抬柱底，像被天空吸回去。
     * <p>⚠️ 柱长必须独立于 fall：写成 {@code BEAM_TOP * (1 - fall)} 会让柱顶与柱底同时落地，最后几 tick 收缩成地上一个点 —— 落地那一帧是空的（离屏预览里复现过）。
     */
    private static BeamSpan beamSpan(Params p) {
        float raw = Mth.clamp(p.beamFall, 0f, 1f);
        // 1.45 次幂：前 0.2 秒"头"还悬在天上，之后越来越快。指数不能太大：纯二次（2.0）时站在 30 格外平视的玩家要等到最后 0.2 秒才看见它。
        float fall = (float) Math.pow(raw, 1.45);

        float impactTicks = Math.max(0f, p.beamElapsed - CrimsonVowFieldEntity.BEAM_FALL_TICKS);
        float collapse = Mth.clamp(impactTicks / 16f, 0f, 1f);
        collapse = collapse * collapse * (3f - 2f * collapse);

        float bottom;
        float top;
        if (p.beamFall < 1f) {
            bottom = p.groundY + CrimsonVowFieldEntity.BEAM_TOP * (1f - fall);
            top = bottom + CrimsonVowFieldEntity.BEAM_LENGTH;
        } else {
            bottom = p.groundY + collapse * (CrimsonVowFieldEntity.BEAM_LENGTH - 1.5f);
            top = p.groundY + CrimsonVowFieldEntity.BEAM_LENGTH + collapse * 1.5f;
        }
        return new BeamSpan(bottom, Math.max(top, bottom + 0.75f), collapse);
    }

    @Override
    public ResourceLocation getTextureLocation(CrimsonVowFieldEntity entity) {
        return DUMMY_TEXTURE;
    }
}
