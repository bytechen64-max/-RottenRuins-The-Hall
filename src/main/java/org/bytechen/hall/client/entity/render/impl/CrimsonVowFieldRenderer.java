package org.bytechen.hall.client.entity.render.impl;

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
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.overworld.registry.entities.population.skills.CrimsonVowFieldEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 巨型法阵渲染器 —— 一次绘制里画四样东西。
 *
 * <h3>画什么</h3>
 * <ol>
 *   <li><b>地面法阵</b>（{@code rendertype_crimson_sigil}，uMode=1）：
 *       贴地圆盘，图案是环 / 刻度 / 符文 / 六芒星 / 中心涡 + 周期扩散波；</li>
 *   <li><b>中心的液态铬</b>（{@code rendertype_crimson_cube} —— 文件名是历史遗留）：
 *       一个世界空间包围盒，片元里对"球 / 环 / 圆角方块 / 八面体"熔融的
 *       距离场做光线步进，表面是镜面铬，反射一片只存在于反射里的霓虹 studio。
 *       这就是"中间为着色器渲染的立体内容"；</li>
 *   <li><b>上方的方块扭曲</b>（{@code rendertype_crimson_warp}）：
 *       一根看不见的圆柱，片元对场景拷贝做屏幕空间折射，强度随离地高度衰减；</li>
 *   <li><b>终末光柱</b>（{@code rendertype_crimson_sigil}，uMode=3）：
 *       自天而降的一根<b>世界空间圆柱</b>，柱面上是六条绕柱而下的螺旋光带
 *       （参考的六条横向 Strand 换到柱面坐标上），触地那一帧在柱底来一下
 *       Muzzle 式爆闪并引爆地形破坏。</li>
 * </ol>
 *
 * <h3>绘制顺序是有约束的，不能随便换</h3>
 * <pre>
 *   ① 场景拷贝（每帧一次）
 *   ② 全部「扭曲柱」   ← 它是**替换**像素（把扭曲后的场景写回去），
 *                        所以必须在所有加法混合的东西之前，
 *                        否则会把法阵和立方体一起盖掉
 *   ③ 全部「法阵 / 光柱 / 立方体」 ← 加法混合，叠在扭曲后的画面上
 * </pre>
 * <p>③ 内部也有个约束：光柱必须排在立方体<b>之前</b>。立方体会把剔除面切成
 * FRONT，而光柱要关剔除（玩家会站在柱子里面）—— 顺序反了，光柱就会共用
 * "只画背面"那个状态，症状是柱子凭空缺一半。详见 {@link #drawBeam}。</p>
 * <p>顺序反了会得到两种很典型的症状：立方体在柱子区域里整个消失（扭曲盖住了它），
 * 或者柱子区域里出现一块没被扭曲的方形（立方体先写进拷贝里，又被采样了一次）。</p>
 *
 * <h3>为什么绘制函数收的是 Matrix4f 而不是 PoseStack</h3>
 * <p>立即路径的矩阵来自事件里的 pose 栈（已经 translate 过 {@code -camera}），
 * 延后回放的矩阵则是入队时快照的"视基 + 相机位置"重建出来的。
 * 两条路径共用同一份绘制代码，就不能把 {@code PoseStack} 这个"活的"对象
 * 传进去 —— 回放发生在下一帧，那时 pose 栈早就不是当时那个了。</p>
 */
public class CrimsonVowFieldRenderer extends EntityRenderer<CrimsonVowFieldEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 中心立方体的半边长（格）。 */
    private static final float CORE_SIZE = CrimsonVowFieldEntity.CORE_SIZE;
    /**
     * 立方体中心离地高度 / 半边长。
     *
     * <p>核心小了一半（半边长 7.5 → 3.75）之后这个系数必须跟着调大：原值 1.06
     * 只让底面离地 0.06 个半边长（7.5 格时是 0.45 格，勉强算漂着），
     * 3.75 格时就只剩 0.22 格 —— 视觉上等于坐在地上，"悬浮"没了。
     * 取 1.65：底面离地 0.65 个半边长 ≈ 2.4 格，明显悬空，
     * 同时核心整体仍落在光柱与扭曲柱的高度区间里。</p>
     */
    private static final float CORE_LIFT = 1.65f;

    /**
     * 核心渐变的两端颜色（需求："拥有紫色和粉色的颜色交替渐变"）。
     *
     * <p>两端的取值都在绯红誓约的色域里，但刻意<b>分开色相</b>而不是只差明度：
     * 紫端压掉红分量（0x8A16FF 一线）、粉端压掉蓝分量（0xFF52B4 一线）。
     * 只差明度的话交替读起来是"一明一暗的条纹"，而不是"紫色和粉色在换"。</p>
     *
     * <p>⚠️ 这两端是<b>染色用的乘数</b>（见 fsh 第 ⑦ 段的 {@code mix(vec3(1), grad, .55)}），
     * 不是最终像素色：最终的紫/粉还要乘上本体明暗、菲涅尔与移动补光。
     * 所以取值比"看起来该有的颜色"更饱和一档 —— 按最终色去取，
     * 到画面上会被压成两片接近的淡紫（离屏对照过）。</p>
     */
    private static final float[] CORE_GRAD_PURPLE = { 0.54f, 0.09f, 1.00f };
    private static final float[] CORE_GRAD_PINK   = { 1.00f, 0.32f, 0.71f };
    /**
     * 紫粉交替的额外漂移速度（周期/秒）。
     *
     * <p>自转本身已经会让条纹扫过物体表面，这一项是"自转之外"的余量，
     * 让交替在旋转看起来变慢的时刻也仍然在读秒。取 0.35：大约 3 秒一个来回，
     * 比自转慢，于是它读成"底色在缓缓换",不会和自转抢注意力。</p>
     */
    private static final float CORE_GRAD_RATE = 0.35f;

    /** 扭曲柱半径 / 法阵半径。比法阵略小：柱壁落在阵内，读起来才像"从阵里升起来的"。 */
    private static final float WARP_RADIUS_RATIO = 0.94f;
    /** 光柱半径 / 法阵半径。 */
    private static final float BEAM_RADIUS_RATIO = 0.34f;

    public CrimsonVowFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** 实体渲染阶段不做任何事（见 {@link CrimsonVowFieldRenderHandler} 的类注释）。 */
    @Override
    public void render(CrimsonVowFieldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // no-op
    }

    // ══════════════════════════════════════════════════════════════
    //  参数快照
    // ══════════════════════════════════════════════════════════════

    /**
     * 一帧里这个法阵要用到的全部标量。
     *
     * <p>收成一个不可变对象有两个好处：立即绘制与延后回放可以共用同一份
     * 绘制代码（回放时世界已经不是当时那个世界了，只有快照可信）；
     * 参数从实体上"读一次"而不是"画的时候各读各的"，不会出现半个画面
     * 用旧值、半个画面用新值的撕裂。</p>
     */
    static final class Params {
        final float x, groundY, z;
        final float radius, meshRadius;
        final float intensity, life, wave, finale, beamFall, impact;
        /** 触地之后经过了多少 tick（触地前恒为 0）。 */
        final float impactElapsed;
        /**
         * 触地之后经过的<b>秒</b>；还没触地时是 -1。
         *
         * <p>着色器用它画地面那圈白热冲击环（⑪），并且用"&lt; 0"当"还没触地"的判据 ——
         * 比另开一个布尔 uniform 少一处可能忘记同步的开关。</p>
         */
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
         * 终末螺旋光柱这一帧到底要不要画。
         *
         * <h3>为什么不能直接拿 {@link #intensity} 判</h3>
         * <p>{@code intensity} 里带着实体自己的淡出：它按 {@code 22} tick
         * 从 1 掉到 0，而光柱从开始下落到触地要走 {@code BEAM_FALL_TICKS}
         * tick。<b>光柱还没落地，整体强度就已经是 0 了</b> —— 症状正是
         * "激光刚落下来就整根不见了，只剩一个爆闪"。所以光柱用自己的一条
         * 包络（见 {@link #beamIntensity}），并且只吃 {@code intensity} 的淡入
         * （下落头几 tick 与法阵一起亮起来），不吃它的淡出。</p>
         */
        boolean beamVisible() {
            return beamElapsed >= 0f && beamIntensity() > 0.004f;
        }

        /**
         * 光柱这一帧的总强度：下落期由 {@code beamFall} 淡入到 1，触地后按
         * {@code impactElapsed / BEAM_LINGER_TICKS} 收束到 0；再乘一次实体的淡入。
         *
         * <p>{@code BEAM_FALL_TICKS} 只有 7 tick（快速轰击），所以这条淡入几乎
         * 是瞬时的 —— 刻意如此：一记砸下来的东西不该有"慢慢亮起来"的起手，
         * 它第一帧就该是满亮度的。</p>
         */
        float beamIntensity() {
            float fadeIn = Mth.clamp(intensity, 0f, 1f);
            float fall = Mth.clamp(beamFall, 0f, 1f);
            float imp = Mth.clamp(impactElapsed / BEAM_LINGER_TICKS, 0f, 1f);
            return Math.min(0.18f + 0.82f * fall, 1f - imp) * fadeIn;
        }
    }

    /**
     * 终末光柱的余晖时长（tick）。
     *
     * <p>28 tick ≈ 1.4 秒：够 Muzzle 爆闪完整地"啪"一下再收掉。
     * 再短会读成"闪了一帧就没了"，再长会盖住地形被撕开的画面。</p>
     */
    private static final float BEAM_LINGER_TICKS = 28f;

    /**
     * 光柱三层各自的半径 / 法阵半径。
     *
     * <h3>为什么要三层，而且半径差这么多</h3>
     * <p>一根"等粗的发光管"是这一招以前最致命的问题：它没有层次，也就没有体积。
     * 现在同一根光柱由三根同心圆柱叠出来：</p>
     * <ul>
     *   <li><b>软光晕</b>（0.40 半径，亮度 0.55）—— 最宽、最淡的一层，
     *       负责"这里有一大团光"；它的轮廓波动也最大，于是外沿是毛的；</li>
     *   <li><b>主体</b>（0.245，亮度 1.0）—— 电弧与向下扫的环都长在这一层；</li>
     *   <li><b>白热核</b>（0.075，亮度 1.0）—— 只有主体 1/3 粗的细柱，近乎纯白。
     *       它才是"能量密度"的证据：加法混合下，中心因此比边缘亮一个档，
     *       柱身才读得出是"一根有芯的光柱"而不是"一张半透明的膜"。</li>
     * </ul>
     * <p>三层共用一个 program、一套几何生成器，只是 uniform 不同 —— 代价是三次
     * draw call（每层约 2700 个三角形），换来的是任何单层都做不到的径向层次。</p>
     */
    private static final float BEAM_HALO_RATIO = 0.32f;
    private static final float BEAM_BODY_RATIO = 0.245f;
    private static final float BEAM_CORE_RATIO = 0.075f;

    /** 柱身锥度：顶面半径 / 底面半径。上细下粗才读得出"从上来"。 */
    private static final float BEAM_TOP_TAPER = 0.62f;

    /**
     * 轮廓波动强度（传给 {@code uBeamWobble}），按层给。
     *
     * <p>振幅 = {@code uBeamWobble * 半径}，实际摆幅约 ±0.26 个柱半径。
     * 第一版给 0.55（±0.47 半径）时，5.6 格半径的光晕会被推到 ±2.6 格，
     * 侧影变成一条"毛毛虫"—— 离屏预览里一眼就能看见。需求要的是
     * <b>边缘有波动感</b>，不是"柱子本身在扭"：所以外沿给 0.26，芯给 0.05。
     * 振幅的硬上限（0.85 半径）仍然留在顶点着色器里，防止有人调到自交。</p>
     */
    private static final float BEAM_WOBBLE_HALO = 0.26f;
    private static final float BEAM_WOBBLE_BODY = 0.18f;
    private static final float BEAM_WOBBLE_CORE = 0.05f;

    /**
     * 沿高度的分环数。
     *
     * <p>轮廓波动是把<b>顶点</b>沿半径推开的，只有上下两条棱边的话侧影仍然
     * 是直线（两个端点一连），什么波动都看不见。24 环 × 56 段 = 2688 个三角形，
     * 对一根柱体是完全可接受的量，同时每环只有约 1.5 格高，起伏足够细。</p>
     */
    private static final int BEAM_RINGS = 24;

    /**
     * 天顶裂口的半径 / 法阵半径，以及它离地的高度。
     *
     * <p>裂口必须比法阵大一圈（2.1 倍）才像"天被捅开"，也才罩得住落下来的光柱；
     * 高度取 {@code BEAM_TOP + BEAM_LENGTH + 6} —— 正好在光柱起点（{@code BEAM_TOP}
     * 处，柱顶再往上 {@code BEAM_LENGTH}）的上方，于是光柱像是从那个洞里抽出来的。</p>
     */
    private static final float PORTAL_RADIUS_RATIO = 2.1f;

    /** 预警段幽灵柱的前缘落点高度 / BEAM_TOP（预警结束时停在 28% 高，不触地）。 */
    private static final float GHOST_DESCEND = 0.72f;
    private static final float GHOST_SPAN = 22.0f;

    /**
     * 由一个法阵实体算出这一帧的全部参数。
     *
     * <p>所有随时间变化的量都吃 {@code partialTick} —— 否则 20Hz 的推进
     * 在 144fps 上会是明显的台阶（这正是"记得用部分 tick 插值"那条要求的落点）。</p>
     */
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

        // 触地爆闪：落地后 10 tick 内从 1 掉到 0，供立方体与扭曲柱过载使用。
        float impact = entity.getPhase() >= CrimsonVowFieldEntity.PHASE_IMPACT
                ? Mth.clamp(1f - impactElapsed / 10f, 0f, 1f) : 0f;

        // 触地"秒数"：着色器用它画地面那圈冲击环，-1 表示还没触地。
        // 与 beamFall 同源推算（而不是读 phase）：同步数据的相位可能晚一 tick，
        // 那样"已经砸到地上了、地上的环还没开始扩"会看出来。
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

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    /**
     * 立即路径：画一个法阵。
     *
     * @param mat   视图矩阵（世界 → 视空间），已含 {@code translate(-camera)}
     * @param basis {@link CrimsonVowRenderSupport#viewBasis} 给出的三个视基向量
     */
    static void drawAll(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        drawWarp(p, mat, basis, cam);
        drawBody(p, mat, basis, cam);
    }

    /** 延后回放路径：矩阵与基都在入队时快照好了。 */
    static void drawAllDeferred(Params p, Matrix4f viewMatrix, float[] basis, Vec3 cam) {
        drawWarp(p, viewMatrix, basis, cam);
        drawBody(p, viewMatrix, basis, cam);
    }

    // ══════════════════════════════════════════════════════════════
    //  ① 扭曲柱（替换像素，必须最先画）
    // ══════════════════════════════════════════════════════════════

    /**
     * 画"法阵照耀的 y 轴区域"里那块方块扭曲。
     *
     * <p>它读的是<b>场景拷贝</b>，所以调用方必须已经先做过一次
     * {@link ShockwaveRenderer#doSceneCopy()}（每帧一次，全帧共用）。</p>
     *
     * <p>视空间的两个量（{@code uBaseVS} / {@code uAxisVS}）由 Java 现算：
     * 着色器要做的是"射线 × 圆柱侧面"的解析求交，全在视空间里做最省事，
     * 也避免在片元里再乘一次矩阵。{@code uAxisVS} = R 的第 1 列
     * （世界 +Y 在视空间下的方向），从视基数组里直接取。</p>
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
        // R · e_y = R 的第 1 列。列 1 = (m10, m11, m12)，而 basis 存的是行：
        // 行 0 = (m00,m10,m20)、行 1 = (m01,m11,m21)、行 2 = (m02,m12,m22)。
        // 所以第 1 列 = (basis[1], basis[4], basis[7])。
        float axisX = basis[1], axisY = basis[4], axisZ = basis[7];

        int depthTex = ShockwaveRenderer.resolveDepthTextureFor(Minecraft.getInstance());
        boolean hasDepth = depthTex > 0;

        float warpRadius = Math.max(1.0f, p.radius * WARP_RADIUS_RATIO);

        // 这一趟是"替换"而不是叠加：输出的就是重采样后的场景本身，
        // 所以混合要用标准的 alpha 混合并把 alpha 写 1。
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

    // ══════════════════════════════════════════════════════════════
    //  ② 法阵 + 天顶裂口 + 幽灵柱 + 光柱 + 立方体（加法混合）
    // ══════════════════════════════════════════════════════════════

    private static void drawBody(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        if (p.intensity <= 0.004f) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        drawDisc(p, mat);
        // 天顶裂口与幽灵柱都属于"预告"，画在光柱前面只是为了读代码顺：
        // 三者都是加法混合、都不写深度，先后顺序不影响画面。
        drawPortal(p, mat);
        drawGhost(p, mat, basis, cam);
        // 光柱排在立方体之前：drawCube 会把剔除面切成 FRONT 又还原成 BACK，
        // 而光柱要**关掉剔除**（玩家会站在柱子里面）。顺序反了的话，法阵+光柱
        // 会共用"只画背面"这个状态 —— 症状是柱子的一半凭空消失。
        drawBeam(p, mat, basis, cam);
        drawCube(p, mat, basis, cam);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 贴地圆盘。 */
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
        // 两者的作用范围与法阵完全一致，另开绘制只会多一张对不齐的贴图。
        CrimsonVowRenderSupport.setUniform(shader, "uWarn", p.warn);
        CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", p.impactAge);
        // ⚠️ 轮廓波动是**顶点阶段**的，而圆盘与光柱共用同一个 program：
        // 光柱那一趟把 uBeamWobble / uBeamRadius 设成非零之后，如果这里不显式
        // 清零，紧接着画的圆盘也会被推歪 —— 顶点着色器只看这两个 uniform 的值，
        // 看不出自己这次是在画圆盘还是画柱子，而圆盘的 Color.r 正好是 0..1 的
        // 径向坐标，会让光柱那套波动逻辑整个跑起来。症状是同心环被揉皱。
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamRadius", 0f);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitDisc(buffer, mat, p.x, p.groundY + 0.03f, p.z, p.meshRadius);
        BufferUploader.drawWithShader(buffer.end());
    }

    /**
     * 阵心那颗血晶（方法名沿用旧名 {@code drawCube}）。
     *
     * <p>几何只是一个轴对齐包围盒，真正的画面全在片元里（光线步进一颗
     * 八棱柱身 + 上下尖的晶体，外加三块环绕碎晶）。因此这里的 GL 状态
     * 和其它几样<b>都不一样</b>，是刻意的：</p>
     * <ul>
     *   <li>{@code disableDepthTest()} —— 包围盒的表面不是物体表面，
     *       让深度测试按盒子去判定只会得到"被剖开一半"的错觉。
     *       真正的遮挡由片元采样深度纹理完成；</li>
     *   <li>{@code cullFace(GL_FRONT)} —— 只画背面。这样每条视线只有
     *       <b>一个</b>片元跑步进；正反面都画会让同一条视线步进两次，
     *       亮度翻倍并在盒子棱边留下接缝；</li>
     *   <li>只画背面还有个附带好处：相机站在盒子里时背面依然在，
     *       效果不会凭空消失。</li>
     * </ul>
     */
    private static void drawCube(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonCubeShader;
        if (shader == null || p.intensity <= 0.004f) return;

        // 展开期从小长到大：起阵那一下要有"长出来"的过程，而不是凭空一个球。
        float deploy = Mth.clamp((p.life * CrimsonVowFieldEntity.DEFAULT_MAX_AGE)
                / CrimsonVowFieldEntity.DEPLOY_TICKS, 0f, 1f);
        float grow = 0.25f + 0.75f * (deploy * deploy * (3f - 2f * deploy));

        // 终末时整个核心往上抬、同时被"吸"得略微收紧 —— 读起来像被光柱拽上去。
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
        // 只画背面。RenderSystem 没有 cullFace 包装（原版不缓存剔除面），
        // 所以这里直接下 GL 调用；成对地在末尾恢复成 BACK。
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
        // 紫 / 粉交替渐变：色相两端与漂移速度。自转那一半复用着色器里本来就有的
        // g_rot（由 uTime 驱动），所以 Java 这边不需要再传角度 ——
        // 少一个 uniform 就少一处"两边角速度对不上"的可能（见 fsh 的 ⑦）。
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

    // ══════════════════════════════════════════════════════════════
    //  ③ 终末的螺旋光柱（世界空间几何，加法混合）
    // ══════════════════════════════════════════════════════════════

    /**
     * 自天而降的一道光（{@code rendertype_crimson_sigil}，uMode = 3）。
     *
     * <h3>为什么它是世界空间几何，而不是一层屏幕叠层</h3>
     * <p>上一版试过把参考里的六条正弦光带做成全屏叠层（
     * {@code CrimsonVowFinaleOverlay}），实机反馈是"看起来像贴在屏幕上的、
     * 从左往右扫过去的一条条"。这不是参数没调好，而是<b>屏幕空间的东西本来
     * 就长这样</b>：光带横贯画面、与相机的朝向无关。所以这一版把两个屏幕轴
     * 换成柱面坐标（绕柱角度 ← 屏幕 x，柱面高度 ← 屏幕 y），横向光带就成了
     * <b>绕柱而下的电弧</b>，相位里的 {@code -|timescale| * uTime} 让它们
     * 整体向下流 —— "立体"与"自上而下"两条要求由同一个改动同时满足。</p>
     *
     * <h3>它现在是三层，不是一根柱子</h3>
     * <p>同一根光柱由三根同心圆柱叠出来（软光晕 / 主体 / 白热核，见
     * {@link #BEAM_HALO_RATIO} 那一段），柱底再补一个向下的锥当枪尖。
     * 加法混合下"宽而暗的壳 + 细而亮的芯"才有体积；单层圆柱无论怎么调色，
     * 读出来都是一张半透明的膜。</p>
     *
     * <h3>几何：只发"这一帧可见的那一截"</h3>
     * <pre>
     *   下落期（raw &lt; 1）：柱底从 BEAM_TOP 二次缓入落到 groundY，柱长恒为 BEAM_LENGTH
     *   触地后（raw = 1）：柱底从地面往上抬，柱顶几乎不动 —— 像被天空吸回去
     * </pre>
     * <p>两处喂的都是<b>绝对世界高度</b>（见
     * {@link CrimsonVowRenderSupport#emitCylinder}）：片元里的电弧相位与落点
     * 判定都活在"格"上，折成比例的话柱子下落时图案会跟着一起伸缩。</p>
     *
     * <h3>为什么单独设一遍 GL 状态</h3>
     * <p>柱面是<b>开口</b>的，而 {@code emitCylinder} 里两个三角形的绕序并不一致
     * （第一个逆时针、第二个顺时针），任何一侧的剔除都会让柱子缺一半；再加上
     * 玩家完全可能站在柱子里面。所以这里显式关剔除，画完还原 —— 与
     * {@link #drawCube} 里"成对地切 cullFace"是同一个理由。</p>
     *
     * @param basis 视基三向量。光柱的轮廓波动要沿"离柱轴的水平方向"推顶点，
     *              而顶点已经是视空间坐标 —— 所以要把柱轴的世界位置换算到视空间，
     *              见 {@code emitBeamCylinder} 里的 {@code uBeamAxisVS}。用视基
     *              做这一步与立方体同源，两条路径不会出现"立即画对了、光影下歪了"。
     */
    private static void drawBeam(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null || !p.beamVisible()) return;

        BeamSpan span = beamSpan(p);
        float impact = Mth.clamp(1f - p.impactElapsed / 10f, 0f, 1f);

        // 光柱要不要写深度：和法阵/立方体一样是自发光的加法层，不写。
        // 但深度测试必须留着 —— 前方的方块与生物要挡得住它，这正是"它站在
        // 世界里"最直接的证据（叠层版本没有这个能力）。
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

        // 每一层都：主体（从枪尖根部到柱顶）+ 自己的枪尖锥。
        // 枪尖必须**每层各来一个**、而且半径等于该层半径 —— 共用一个小锥的话，
        // 最外层（光晕）的开口圆筒会露出来，从下往上看就是一只"亮碗"。
        float[] radii = { haloR, bodyR, coreR };
        float[] wobbles = { BEAM_WOBBLE_HALO, BEAM_WOBBLE_BODY, BEAM_WOBBLE_CORE };
        float[] scales = { 0.55f, 1.00f, 1.00f };
        // uLayer：4 = 外光晕（新增，shader 里用解析体积做"宽而软"的一层），
        // 0 = 主体（等离子鞘 + 细电弧 + 下扫脉冲），1 = 白热核（丝状电流）。
        float[] layers = { 4f, 0f, 1f };
        // 枪尖按层给不同的长度与亮度：外光晕几乎不留尖（靠 shader 的柱顶淡出收口）、
        // 主体留短尖、白热核留长尖。三层叠出来是"锥形收敛"，不再是三支铅笔。
        // 逐层锥度：外光晕几乎不锥化（锥化 = 下宽上窄 = 钟形罩），主体/核保留 0.62。
        float[] tapers = { 0.93f, BEAM_TOP_TAPER, BEAM_TOP_TAPER };
        float[] tipLen = { 0.30f, 0.55f, 0.95f };
        // 外光晕层（半径 5.6 格）不画枪尖锥：那是一只 5.6 格半径的浅锥，
        // 从下往上看就是「灯罩 / 蘑菇伞」。它的开口改由 shader 的底部淡出收掉。
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

            // 枪尖按层给亮度（见上）：给满会在加法下先饱和成纯白，
            // 于是柱头变成一张平的白纸 —— 那是"塑料感"的另一个来源。
            float ti = hi * tipFade * tipBright[i];
            if (ti > 0.004f) {
                emitBeamCylinder(shader, p, mat, basis, cam, span,
                        span.bottom(), span.bottom() + headLen, 0.02f, r,
                        layer, ti, 0f, 4);
            }
        }

        // 触地飞溅（uLayer = 5）：柱底一圈宽喇叭 + 角向火舌，只在触地后 10 tick 内存在。
        // 它替掉了旧版"三层枪尖叠成一支铅笔"的观感 —— 落地那一下该是"炸开"，不是"戳住"。
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

    // ══════════════════════════════════════════════════════════════
    //  ③·补 天顶裂口 / 幽灵柱（终末预警）
    // ══════════════════════════════════════════════════════════════

    /** 裂口圆盘的离地高度（格）。 */
    private static float portalY(Params p) {
        return p.groundY + CrimsonVowFieldEntity.BEAM_TOP
                + CrimsonVowFieldEntity.BEAM_LENGTH + 6f;
    }

    /**
     * 天顶裂口（{@code uMode = 4}）：张在 {@link #portalY} 处的一张水平圆盘。
     *
     * <h3>它为什么值得多花一次 draw call</h3>
     * <p>张力里最贵的一半是<b>预告</b>。这一招的光柱原本是凭空出现的 ——
     * 观众只会看到"闪了一下"。头顶先裂开 1.5 秒，再让光柱从那个洞口抽下来，
     * 同一道光才有了"从哪里来"。圆盘是水平几何：会随视角产生透视、会被山挡住，
     * 所以它是"长在世界里"的，不是贴在镜头上的。</p>
     *
     * <p>触地之后它自己也会炸一下再收（{@code uImpactAge} 驱动的闪光），
     * 然后 {@code fade} 把它淡掉 —— 裂口不该比法阵活得久。</p>
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
        // ⚠️ 圆盘不接受柱面轮廓波动，必须显式清零：同一个 program 的 uniform 是
        // 全局状态，光柱那一趟会把 uBeamWobble 设成非零。不清零的话圆盘会被
        // 当成柱面推皱（旧版圆盘那一路就有这条注释，这里同样适用）。
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitDisc(buffer, mat, p.x, y, p.z, r);
        BufferUploader.drawWithShader(buffer.end());
    }

    /**
     * 幽灵柱（{@code uLayer = 2}）：从裂口一直垂到半空的一根极淡光柱。
     *
     * <h3>它做的事只有一件：把"天上那个洞"和"地上这个阵"连起来</h3>
     * <p>没有它，裂口是一个悬在头顶的装饰，光柱是另一件事；有了它，观众看到的是
     * 一条<b>已经从天上通到地上的通道</b>，只是在等最后那一下灌满。</p>
     *
     * <p>预警段只往下灌到 {@link #GHOST_DESCEND}（55%）就停 —— 灌到底会让观众
     * 以为"已经打完了"，真正落下的那一下反而失去意义。光柱开始下落后，幽灵柱的
     * 下端直接跟着光柱前缘走，两段在视觉上连成一条。</p>
     */
    private static void drawGhost(Params p, Matrix4f mat, float[] basis, Vec3 cam) {
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null || !p.warnVisible()) return;

        float portalY = portalY(p);
        // 预警段把光"灌"到哪，这里就先算出来 —— 光柱开始下落后要用它兜底。
        // ⚠️ 不能直接切到光柱前缘：光柱是从 BEAM_TOP 开始的，而预警结束时幽灵柱
        // 已经灌到 28% 高，直接切会让这根柱子**凭空跳上去 40 格**（离屏里看得很清楚）。
        // 取两者的较低者，通道才会连续地继续往下走。
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

        // 比主体宽一倍多、比光晕窄：它表达的是"这一整条通道"，不是那根光柱本身。
        // 太宽会变成一块糊住画面的紫雾（7.5 格半径时整屏都被它罩住）。
        float r = Math.max(0.9f, p.radius * BEAM_BODY_RATIO * 1.7f);
        BeamSpan shaft = new BeamSpan(bottom, portalY, 0f);
        emitBeamCylinder(shader, p, mat, basis, cam, shaft, bottom, portalY,
                r, r * 0.85f, 2f, intensity, 0.28f, 12);
    }

    // ══════════════════════════════════════════════════════════════
    //  ③·补 光柱的分层发射
    // ══════════════════════════════════════════════════════════════

    /**
     * 发一层光柱：写好这一层的全部 uniform，再发一段（可带锥度的）圆柱。
     *
     * <p>四层（光晕 / 主体 / 白热核 / 枪尖）共用这一个函数，差别全在参数里。
     * uniform 必须<b>整组</b>重写，不能只改半径：同一个 program 的 uniform 是
     * 全局状态，上一层留下的 {@code uBeamHead} / {@code uBeamSpan} / {@code uLayer}
     * 会让下一层用自己的尺寸去算别人的图案 —— 症状是"核心层的头亮在柱身中段"
     * 这种很难从画面上反推的错位。</p>
     *
     * @param fx        图案用的段（{@code uBeamHead} / {@code uBeamSpan} 读它）
     * @param geomBottom 几何下沿的世界 Y。枪尖锥的几何从柱底开始，而图案的前缘
     *                   仍然是 {@code fx.bottom()} —— 两者必须分开给，否则锥会把
     *                   "前缘"往下拽，柱头光斑就跑到锥尖下面去了。
     * @param geomTop   几何上沿的世界 Y
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
        // uRadius / uMeshRadius 在光柱分支里是恒等变换，照给是为了让
        // `r = Color.r * (uMeshRadius / uRadius)` 不把上一层的光柱半径带进来。
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

        // 柱轴：世界位置上就是 (p.x, p.groundY, p.z)，换算到视空间给顶点着色器。
        // 顶点那边用 "视空间顶点 − 视空间柱轴" 直接得到径向 —— 这是整条链上
        // 唯一不会出错的做法：任何"从相机指向顶点"的近似都跟真实径向差近 90°，
        // 推出来的顶点会落在切向上，轮廓逐行等宽、看起来像根本没生效。
        float[] axisVS = new float[3];
        CrimsonVowRenderSupport.worldToView(basis, p.x, p.groundY, p.z,
                cam.x, cam.y, cam.z, axisVS);
        CrimsonVowRenderSupport.setUniform3(shader, "uBeamAxisVS",
                axisVS[0], axisVS[1], axisVS[2]);
        // 柱轴方向 = 世界 +Y 在视空间的方向 = R 的第 1 列 = (basis[1], basis[4], basis[7])
        // （理由见 drawWarp 里对 uAxisVS 的同一段推导）。
        CrimsonVowRenderSupport.setUniform3(shader, "uBeamAxisDir",
                basis[1], basis[4], basis[7]);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitCylinder(buffer, mat, p.x, geomBottom, p.z,
                baseRadius, topRadius, geomTop, rings);
        BufferUploader.drawWithShader(buffer.end());
    }

    // ══════════════════════════════════════════════════════════════
    //  ③·补 光柱这一帧的几何：底 / 顶 / 收束
    // ══════════════════════════════════════════════════════════════

    /** 光柱这一帧的可见段：底（前缘）、顶（尾端）、收束进度。 */
    private record BeamSpan(float bottom, float top, float collapse) {}

    /** 0..1 的平滑过渡（smoothstep）。 */
    private static float smoothstep01(float x) {
        float t = Mth.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * 算出这一帧光柱的可见段。
     *
     * <h3>下落用二次缓入，不是匀速</h3>
     * <pre>
     *   fall = raw²   →  raw 0.5 时只走了 25%，raw 1.0 时 100%
     * </pre>
     * <p>匀速下落读起来像电梯。二次缓入让前 0.2 秒那个"头"几乎悬在天上，
     * 然后越来越快地砸下来 —— 那半秒的"悬"就是张力本身。落地时刻不变
     * （{@code raw = 1} 仍然是触地），所以服务端的结算相位不受影响。</p>
     *
     * <h3>触地之后从下往上烧断</h3>
     * <p>旧版收束是把柱底抬起、柱顶压下，最后剩一小截<b>悬在半空</b>的光 ——
     * 物理上说不通，看起来也像一根被剪断的绳子。现在只抬柱底：光柱像被天空
     * 吸回去一样，从地面往上一寸寸熄灭。地面那圈灼痕由法阵圆盘上的 ⑪ 接力。</p>
     *
     * <p>⚠️ 柱长必须独立于 fall。写成 {@code BEAM_TOP * (1 - fall)} 会让柱顶与
     * 柱底同时落地，整根柱子在最后几 tick 缩成地上一个点 —— 落地那一帧是空的。
     * 这个坑在离屏预览里复现过，别再踩。</p>
     */
    private static BeamSpan beamSpan(Params p) {
        float raw = Mth.clamp(p.beamFall, 0f, 1f);
        // 1.45 次幂：前 0.2 秒"头"还悬在天上，之后越来越快。
        // ⚠️ 指数不能太大：纯二次（2.0）时，raw=0.5 的头只走了 25%，
        // 站在 30 格外平视的玩家要等到最后 0.2 秒才看见它 —— 那半秒的
        // "悬停"就变成"什么都没有"。1.45 是"读得出迟疑、又仍在往下掉"的折中。
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
