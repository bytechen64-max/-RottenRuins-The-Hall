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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictBeamEntity;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDebug;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictTuning;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 天穹裁决 · 垂直光柱渲染器。
 *
 * <h3>几何</h3>
 * <p>一个沿 +Y 的<b>圆柱侧面</b>（不含上下底盖），底面在实体位置（玩家脚底），
 * 高 {@code length}、半径 {@code radius}。底面不画是刻意的：光柱是从天上降下来的，
 * 给它加一个底面圆盘会立刻读成"一根实体柱子立在地上"，而不是"一束光"。</p>
 *
 * <h3>属性打包</h3>
 * <p>顶点格式用 {@code POSITION_COLOR_NORMAL}（与冲击波一致），三个属性各有分工：</p>
 * <ul>
 *   <li>{@code Position} —— 局部坐标。光柱轴对齐，于是 {@code length(xz)} 就是局部径向距离、
 *       {@code y} 就是高度，片元侧不用额外属性就能算；</li>
 *   <li>{@code Color.rg} —— 当成贴图坐标用：{@code r = 径向归一化}、{@code g = 高度归一化}。
 *       这样将来光柱改成锥形收束（非圆柱）时，片元侧的取 r/g 两行不用动；</li>
 *   <li>{@code Normal} —— 归一化的 {@code (x, 0, z)} 径向方向，
 *       片元用它做"内侧被点亮"的边缘光（正对视线的面接近 0，剪影边缘接近 1）。</li>
 * </ul>
 *
 * <h3>不需要光影兼容的延后回放</h3>
 * <p>光柱<b>不采样屏幕</b>（不像冲击波那样要把主帧缓冲 blit 出来再折射），
 * 它只采样自己的几何与噪声。因此它在光影包的 G-buffer 阶段绘制也不会被"吃掉" ——
 * 这正是它比冲击波省一整套 {@code LateRenderQueue} 的原因。
 * 唯一需要在光影下复核的是加法混合的颜色是否会过曝，那是调参问题，不是管线问题。</p>
 */
public class VerdictBeamRenderer extends EntityRenderer<VerdictBeamEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆周分段。光柱近景会占满屏幕，段数给足。 */
    private static final int SEGMENTS = 48;
    /** 纵向环数。轴向噪声需要足够的纵向分辨率才不像"贴纸"。 */
    private static final int RINGS = 32;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** 光柱整体亮度（加法混合下的强度总开关）。 */
    private static final float BEAM_BRIGHTNESS = 0.72f;

    /** 实际亮度 = 基准 × 配置倍率。 */
    private static float brightness() {
        return BEAM_BRIGHTNESS * VerdictTuning.beamBrightness();
    }

    /**
     * 相机离光柱中轴多近时，开始放弃背面剔除（相对半径的倍数）。
     *
     * <p>取 {@code radius × 1.6}：这个距离上柱壁已经贴脸、屏幕占比很大，
     * 内壁的光本来就应该看得见。再远一点（相机完全在柱外）就回到纯剔除。</p>
     *
     * <p>⚠️ 这里的 {@code radius} 传的是<b>当前</b>扩散半径，不是起始半径：
     * 柱子扩散到 20 格之后会把站着的玩家整个罩住，于是剔除自动关闭 ——
     * 那正是"站在柱内"这条分支，也正好绕开了柱外那条有问题的画面路径。</p>
     */
    private static final float INSIDE_SOFTEN = 1.6f;

    /**
     * 背面剔除的强度：0 = 全部保留（能看到内壁），1 = 完全剔除背面。
     *
     * <h3>为什么需要这个</h3>
     * <p>背面剔除是"水平看光柱割裂"的解，但它有一个必须处理的副作用：
     * <b>相机在圆柱内部时，所有面的外法线都背对相机</b>，硬剔会让整根光柱
     * 在你眼前消失。而这个技能恰恰是"在你脚下立一根柱、你在它里面"。</p>
     *
     * <p>所以按"相机到中轴的垂直距离"做一个平滑过渡：</p>
     * <pre>
     *   相机在柱外（d &gt;= 1.6R）  → 1（完全不画背面，没有双计）
     *   相机在中轴上（d = 0）     → 0（内壁全画，看得到光）
     * </pre>
     *
     * <p>注意这个量只影响<b>画不画</b>，不影响任何判定 —— 光柱的命中半径
     * 始终是 {@code uRadius}，与渲染无关。</p>
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
        // 平滑过渡：用 smoothstep 而不是线性，避免"游进柱壁时明暗跳变"
        float t = (float) ((dist - inner) / (outer - inner));
        return t * t * (3f - 2f * t);
    }

    /** 近白（爆发）：与 DomeriteLongsword.outlineSecondaryColor() 同值。 */
    private static final int CORE_RGB = 0xF0F8FF;
    /** 天蓝（领域）：与 DomeriteLongsword.outlineColor() 同值。 */
    private static final int EDGE_RGB = 0x87CEFA;

    public VerdictBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VerdictBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        // ── 阴影 pass 必须整体跳过 ──
        //  光影包会在渲染世界之前先渲一遍阴影贴图，那一趟里实体渲染器同样会被调用。
        //  我们的光柱是一根<b>发光的柱子</b>，如果让它在那趟里画出去，
        //  它就会往阴影贴图里写几何 —— 表现为"光柱自己投出一道柱状影子"，
        //  既违反物理直觉，也会在场景里多出一块莫名其妙的暗区。
        //  对照 ShockwaveRenderer：它第一行也是这个守卫。
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        float intensity = entity.intensity(partialTick);
        if (intensity <= 0.004f) return;

        // ── 扩散：网格按<b>起始</b>半径建，之后靠两个缩放系数在顶点着色器里放大 ──
        //
        //  为什么不每帧重建网格：圆柱是 48 段 × 32 环 × 6 顶点 = 9216 个顶点，
        //  而扩散期间半径每帧都在变。重建等于每帧重灌一次 258KB 的缓冲，
        //  只为了让两个系数变一下 —— 那是纯浪费。
        //
        //  ⚠️ 缩放必须<b>只作用于局部几何</b>。poseStack 里已经含了
        //  "把实体搬到它所在世界坐标"的平移，如果顶点阶段乘的是"带平移的坐标"，
        //  柱子会从脚底上方几百格开始长、整个漂在天上（这个坑踩过一次，
        //  详见 rendertype_verdict_beam.vsh 的 uBeamScale 注释）。
        //  这里能这么做的前提是：顶点喂进去的本来就是以脚底为原点的局部坐标，
        //  所以"乘一个系数"只会把几何放大，原点不动。
        float baseRadius = entity.getRadius();
        float baseLength = entity.getLength();
        if (baseRadius <= 0.01f || baseLength <= 0.05f) return;

        // 横向系数：起始半径 → 当前半径
        float scaleXZ = entity.currentRadius(partialTick) / Math.max(baseRadius, 1e-3f);
        // 纵向系数：单独封顶。视觉高度是 440 格量级，如果跟着横向一起 ×7.7
        // 就会到 3400 格 —— 早已越过远裁剪面，读起来是"柱子断在半空"。
        float scaleY = entity.currentHeightScale(partialTick);
        if (scaleXZ <= 0.01f || scaleY <= 0.01f) return;

        // ── 着色器必须已加载，否则整个实体静默跳过 ──
        //  SplendidingShaders.onRegisterShaders 里失败会 LOGGER.error 掉具体原因，
        //  这里不做任何兜底绘制：宁可看不见，也不要画出一根颜色错的光柱。
        ShaderInstance shader = SplendidingShaders.verdictBeamShader;
        if (shader == null) return;

        float time = (entity.tickCount + partialTick) * 0.05f;
        // 扫描球：0 → 1 在 70% 的生命里走完，留出尾段只剩静态光柱
        float scanT = Mth.clamp(entity.lifeProgress(partialTick) / 0.7f, 0f, 1f);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();

        // ── 深度：测试 + 写入，两个都必须显式打开 ──
        //
        //  这一对是本方法过去最严重的漏洞，而且它正好解释了"光柱穿透一切"：
        //    · <b>没有 enableDepthTest()</b> —— 只能靠"上一段渲染恰好留着深度测试"
        //      才能被遮挡。而 {@code Tesselator} 路线不经过 RenderType 的状态设置
        //      （它是给 {@code bufferSource.getBuffer(...)} 用的），所以
        //      {@code hall:rendertype_verdict_beam} 里那句 depthtest: lequal
        //      这条路径根本不会被执行到。对照 {@code ShockwaveRenderer}：
        //      它显式调了 enableDepthTest()，所以冲击波一直是对的。
        //    · <b>depthMask(false)</b> —— 光柱自己不写深度，于是它<b>后面</b>的东西
        //      画在它之后时不会被它挡住，读起来像一张贴在所有物体前面的发光贴纸。
        //
        //  两者一起改才对：只开写入而不开测试，光柱会挡住它后面的东西、却依然
        //  穿过它前面的地形；只开测试而不开写入，则前面的东西能挡住它，
        //  但它挡不住后面的实体。
        //
        //  ⚠️ 代价是"硬遮挡"：光柱与被它挡住的物体之间是一条硬边，而不是
        //  光线逐渐淹没物体的柔和过渡。想要柔和过渡要采样深度纹理做
        //  soft-particle 淡出，那是另一条管线（本项目只有冲击波走屏幕采样，
        //  且它必须配 LateRenderQueue 才能在光影包里正确工作）。
        //  在"穿透"和"硬边"之间，硬边是明显更可接受的那个。
        RenderSystem.depthMask(true);

        // ── 扩散：走矩阵，不走着色器 ──
        //
        //  第一版（能用的那版）只有"在 poseStack 上读一个矩阵"这一步，
        //  顶点着色器就是 { ProjMat * Position }。我为了加扩散，
        //  在顶点着色器里插了一步 `Position * scale` —— 那是错的插法：它把
        //  "几何"和"实体定位"变成了两套东西（定位在矩阵里、缩放在自己手里），于是
        //   · vLocal 的语义被改掉（片元侧的归一化跟着漂）；
        //   · 任何一步没对齐，症状都是"莫名其妙的坐标变换"，而且极难二分定位。
        //
        //  正确做法：缩放本来就该和位移待在同一个地方 —— 矩阵。
        //  在 dispatcher 的 translate 之后压一次 scale，用完立刻 pop 回去。
        //  矩阵的语义（世界位置 = 平移 × 顶点）完全不变，着色器一个字节都不用改。
        //
        //  代价是每帧重建 9216 个顶点的缓冲（几十 KB 的 float 写入），
        //  与 ShockwaveRenderer 每帧做的事同量级，可以接受。
        poseStack.pushPose();
        poseStack.scale(scaleXZ, scaleY, scaleXZ);
        Matrix4f matrix = poseStack.last().pose();

        // ── 光影兼容：pack 激活时不在世界 pass 里画 ──
        //
        //  这是本项目既有的那条约定（见 ShockwaveLateRenderQueue 的类注释）：
        //  光影包激活时，世界 pass 里画的东西落在 <b>GBuffer</b> 上而不是最终画面
        //  —— 光柱这种自定义着色器的自发光体在 GBuffer 里会被 pack 当成
        //  "反照率材质"重新参与光照，于是颜色、亮度、混合全都不受控。
        //
        //  正确做法是入队，等 pack 合成完最终场景后（renderLevel 的 TAIL）
        //  在 MAIN_TARGET 上回放。回放用的是同一套 uniform 与几何，
        //  所以视觉上没有第二条实现 —— 只是画的时间点不同。
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            // 相机位置与两个矩阵都<b>此刻</b>快照：回放发生在下一帧的更晚阶段，
            // 那时再回读 RenderSystem / Camera 拿到的是下一帧的值。
            // 这是本队列第一版写错的地方（症状是"时好时坏、F1 后干脆不显示"），
            // 详见 VerdictBeamLateRenderQueue 的类注释。
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

        // 必须显式绑着色器：Tesselator.end() 用的是 RenderSystem 当前绑定的着色器，
        // 不是 RenderType 里那个。这里只借用 RenderType 拿缓冲尺寸与行模式，
        // 与 ShockwaveRenderer 的做法保持一致。
        RenderSystem.setShader(() -> shader);

        applyUniforms(shader, baseRadius, baseLength, time, intensity * brightness(),
                scanT, entity.lifeProgress(partialTick),
                (entity.getAge() + partialTick) / 20f,
                new Matrix3f(poseStack.last().normal()),
                backCullFactor(entity, entity.currentRadius(partialTick)));

        // ── 一次性的定位探针 ──
        //
        //  "光柱没固定在坐标上"这类反馈，可能的原因至少有四个，而它们在画面上
        //  长得一样：pose 链不对 / uniform 没注册 / 缩放系数为 0 / 相机裁剪。
        //  这一行把四个量一次性写进日志，看一行就能定位，不用再猜。
        //  限频 1 秒，且只在真的在渲染时写。
        logBeamProbe(entity, matrix, baseRadius, baseLength, scaleXZ, scaleY, shader);

        emitGeometry(shader, matrix, baseRadius, baseLength);

        // 与上面的 pushPose() 配对。必须成对 —— 曾经这里少过一次 pop
        // （是更早那版"push 在方法开头"的残留），把 LevelRenderer 共享的
        // PoseStack 弹空，最后在 renderLevel 收尾的 checkPoseStack() 里崩：
        //     java.lang.IllegalStateException: Pose stack not empty
        // 编译期查不出来（push 和 pop 是两行独立语句），症状是"玩着玩着突然崩"。
        poseStack.popPose();

        restoreGlState();
    }

    /**
     * 恢复 GL 状态。depthMask 已经是我们想要的值（true 也是原版实体渲染的常态），
     * 但仍然显式写一遍：不恢复状态是这类自定义渲染最常见的污染源。
     */
    private static void restoreGlState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 设置全部 uniform。立即绘制与光影包下的延后回放<b>共用</b>这一份，
     * 所以两条路径的画法不可能分叉。
     */
    static void applyUniforms(ShaderInstance shader,
                              float baseRadius, float baseLength,
                              float time, float intensity, float scanT,
                              float lifeProgress, float ageSeconds,
                              Matrix3f normalToView, float backCull) {
        setUniform(shader, "uTime", time);
        setUniform(shader, "uIntensity", intensity);
        // uRadius / uHeight 是<b>网格的基准尺寸</b>（起始半径、起始长度）：
        // 片元侧用它们做归一化，所以必须和顶点喂进去的几何一致。
        // 扩散的缩放不经过 uniform —— 它在 poseStack 的矩阵里，见上面那段说明。
        setUniform(shader, "uRadius", baseRadius);
        setUniform(shader, "uHeight", baseLength);
        setUniform(shader, "uScanT", scanT);
        // 生命周期进度：片元侧用它画末段的塌陷收束
        setUniform(shader, "uProgress", lifeProgress);
        // 实体年龄（秒）：三波打击的扫描计时。走真实年龄而不是生命进度，
        // 因为三波打击的时机是 tick 级的固定值（0 / 10 / 30 tick），而生命进度
        // 会被 maxAge 的长度稀释 —— 换个寿命长度就会让视觉与伤害错位。
        setUniform(shader, "uAge", ageSeconds);
        setUniformRGB(shader, "uCoreColor", CORE_RGB);
        setUniformRGB(shader, "uEdgeColor", EDGE_RGB);

        // ── 背面剔除（水平视角"割裂"的修复）──
        //
        //  模型 → 视图 的旋转矩阵。片元的 Normal 属性是模型空间的 (x, 0, z)，
        //  要在视图空间里判断"这个面朝不朝相机"，就必须先把它转过去。
        //  Tesselator 这条路线没有现成的法线矩阵（RenderType 的那套由
        //  RenderSystem 自己处理，Tesselator 不会），所以直接从 poseStack 取 3x3。
        //
        //  ⚠️ 它到"哪个方向算朝相机"的那一步符号约定，fsh 里是靠实测定的，
        //  不要按"视图空间相机朝 -Z"去推导 —— 那条推导把前后壁判反过，
        //  症状是"柱子像被竖着切了一半"。详见 rendertype_verdict_beam.fsh 的 ⓪ 段。
        setUniformMat3(shader, "uNormalToView", new Matrix3f(normalToView));
        //  注意传的是<b>当前</b>半径：柱子扩散到把相机罩住之后，剔除会自动关掉，
        //  这正是"站在柱内看到内壁的光"那条分支。见 backCullFactor。
        setUniform(shader, "uBackCull", backCull);
    }

    /** 发出圆柱几何。同样被立即绘制与延后回放共用。 */
    static void emitGeometry(ShaderInstance shader, Matrix4f matrix,
                             float baseRadius, float baseLength) {
        // 必须显式绑着色器：Tesselator.end() 用的是 RenderSystem 当前绑定的着色器，
        // 不是 RenderType 里那个。这里只借用 RenderType 拿缓冲尺寸与行模式，
        // 与 ShockwaveRenderer 的做法保持一致。
        RenderSystem.setShader(() -> shader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        emitCylinderSide(buffer, matrix, baseRadius, baseLength);
        tess.end();
    }

    /**
     * 一次性定位探针：把"光柱到底画在哪"依赖的每个量都写进日志。
     *
     * <h3>为什么要它</h3>
     * <p>下面四个原因在画面上<b>长得一样</b>（都是"柱子不在它该在的地方"），
     * 但修法完全不同，而我在没有实机的情况下没法把它们分开：</p>
     * <ol>
     *   <li><b>pose 链不对</b> —— {@code matrix} 的平移分量不等于"实体位置 − 相机位置"；</li>
     *   <li><b>uniform 没注册</b> —— 缩放系数停在 JSON 默认值（现在会另外记 error）；</li>
     *   <li><b>缩放系数为 0 / NaN</b> —— 网格塌成一点；</li>
     *   <li><b>相机裁剪</b> —— 几何其实对，只是 1500 格高被远裁剪面切掉了。</li>
     * </ol>
     *
     * <p>看一行日志就能排掉其中的前三个：平移分量对得上就是 1 没问题，
     * 缩放系数是正常数就是 3 没问题。剩 4 的话把 {@code MAX_HEIGHT_SCALE} 调小即可。</p>
     *
     * <p>限频 1 秒，避免刷屏（相机每帧都在动，按内容去重是无效的）。</p>
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

    /** 上次写探针的时间，用于限频。 */
    private static long lastProbeMs;

    // ══════════════════════════════════════════════════════════════
    //  几何：圆柱侧面
    // ══════════════════════════════════════════════════════════════

    /**
     * 发出圆柱侧面。底面在 {@code y = 0}，顶面在 {@code y = length}。
     *
     * <p>两套属性同时写：{@code Color.rg} 是归一化贴图坐标，{@code Normal} 是径向方向。
     * 两者都是"每个顶点逐点算出来"的，所以插值到片元之后依然连续。</p>
     */
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

                // 对应关系：a0/h0 → a0/h1 → a1/h1 → a1/h0
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

    // ══════════════════════════════════════════════════════════════
    //  uniform 工具
    // ══════════════════════════════════════════════════════════════

    /**
     * 传一个 float uniform。
     *
     * <p><b>缺注册会记一条 error，而不是静默跳过。</b></p>
     *
     * <p>原来的写法是 {@code if (u != null) u.set(v);} —— 从不着色的角度它很"稳"，
     * 但它把一整类故障变成了哑的：只要 JSON 的 {@code uniforms} 白名单里少一个名字
     * （或者资源是旧的），这个 uniform 就永远停在 JSON 里的默认值，
     * 而<b>症状和"顶点算错了"一模一样</b>。我们为此查了两轮。</p>
     *
     * <p>现在缺一个就记一次（按名字去重，不刷屏）。这类问题应该在日志里
     * 一行看出来，而不是靠人去猜渲染哪里算错了。</p>
     */
    private static void setUniform(ShaderInstance shader, String name, float value) {
        var u = shader.getUniform(name);
        if (u == null) { warnMissingUniform(name); return; }
        u.set(value);
    }

    /** 已经报过缺失的 uniform 名字，避免每帧刷屏。 */
    private static final java.util.Set<String> REPORTED_MISSING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnMissingUniform(String name) {
        if (REPORTED_MISSING.add(name)) {
            HallMod.LOGGER.error("[VerdictBeam] uniform '{}' 未在 rendertype_verdict_beam.json 的 "
                    + "uniforms 白名单里注册 —— 它会一直停在 JSON 默认值，"
                    + "渲染结果会像\"顶点算错了\"。请检查该 json。", name);
        }
    }

    /**
     * 传一个 3x3 矩阵。
     *
     * <p>{@code Uniform.set(Matrix3f)} 走的是 JOML 的 {@code get(FloatBuffer)}，
     * 也就是<b>列主序</b>写入 —— 与 GLSL 的 {@code mat3} 内存布局一致，
     * 所以不需要转置。这一点容易搞反，症状是"矩阵看起来生效了但方向全错"。</p>
     *
     * <p>uniform 没声明时静默跳过，与其它 setter 一致：着色器没加载/名字写错
     * 都不该让渲染抛异常。</p>
     */
    private static void setUniformMat3(ShaderInstance shader, String name, Matrix3f value) {
        var u = shader.getUniform(name);
        if (u == null) { warnMissingUniform(name); return; }
        u.set(value);
    }

    /** 把 0xRRGGBB 拆成 0..1 的三个 float 传给 vec3 uniform。 */
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
