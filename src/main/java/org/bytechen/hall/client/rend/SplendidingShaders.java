package org.bytechen.hall.client.rend;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.mixin.accessor.AccessorRenderStateShard;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.client.rend.glint.OutlineMaskRenderType;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SplendidingShaders {

    // ── shader instances ──
    public static ShaderInstance lightningOutlineShader;
    public static ShaderInstance itemGlintShader;
    public static ShaderInstance shockwaveShader;

    // ── 物品描边：屏幕空间剪影遮罩 + 环形膨胀（见 ItemOutlinePipeline）──
    // 旧的"放大壳"实现整体移除：纯色 / gradient / warp_fbm 三个独立 outline
    // 着色器、各自的 RenderType、以及光影用的 _sp 变体和延迟回放 RenderType
    // 都不再需要 —— 三条"描边着色器"收敛成一个几何算法 + 一个颜色模式。
    public static ShaderInstance outlineMaskShader;
    public static ShaderInstance outlineRingShader;
    @Nullable
    private static RenderType outlineMaskRenderType;

    // ── GUI overlay shaders ──
    public static ShaderInstance guiHorrorVoronoiShader;
    public static ShaderInstance guiGlowShader;
    /**
     * 创造物品栏「分区隔断行」的流动色带（GUI：一条 160×18 的极光缎带）。
     *
     * <p>它没有对应的 RenderType —— 和 tooltip 底板那条路一样走
     * {@code BufferUploader.drawWithShader} 立即绘制，GL 状态写在
     * {@code client/gui/creative/CreativeTabDividerRenderer} 里。
     * 为 null 时隔断行退回一条纯色细线（分区功能本身不受影响）。</p>
     */
    public static ShaderInstance guiTabDividerShader;
    /** 难度选择界面的四档「印记」（程序化多边形几何 + 逐 profile 图案）。 */
    public static ShaderInstance difficultySigilShader;
    /** 坍缩使徒 boss 血条的「血量内容」着色器（纯黑底 + 彩色星尘，mask 红通道定形状）。 */
    public static ShaderInstance collapsarBarShader;
    /** 坍缩使徒的背部六芒星光环（参考 glorb/field 彩虹噪声 + 六芒星 SDF）。 */
    public static ShaderInstance collapsarHaloShader;
    public static ShaderInstance blackHoleShader;
    /** 天穹裁决的垂直光柱（自主发光体积光束，纯 Local 空间，不需要场景拷贝）。 */
    public static ShaderInstance verdictBeamShader;
    /** 裁决领域的地面光纹（水平圆盘，极坐标六分对称）。 */
    public static ShaderInstance verdictFieldShader;
    /** 绯红誓约背板（程序化几何 + 自发光纹路，挂在玩家背后）。 */
    public static ShaderInstance crimsonBackplateShader;
    /**
     * tooltip 底板的热力学流动（GUI：一块被从下方加热的板）。
     *
     * <p>它没有对应的 RenderType —— 和血条那条路一样走
     * {@code BufferUploader.drawWithShader} 立即绘制，GL 状态写在
     * {@code client/tooltip/TooltipShaders} 里。为 null 时 tooltip 退回纯色底板。</p>
     */
    public static ShaderInstance tooltipThermalShader;

    // ── render types (lazy) ──
    private static RenderType lightningOutlineRenderType;
    private static RenderType itemGlintRenderType;
    private static RenderType shockwaveRenderType;
    private static RenderType shockwaveLateRenderType;
    private static RenderType verdictBeamRenderType;
    /** 背板的"板体"通道：普通透明混合，画背面与正面底。 */
    private static RenderType crimsonBackplateRenderType;
    /** 背板的"发光"通道：加法混合，只画绯红纹路。 */
    private static RenderType crimsonBackplateGlowRenderType;

    /** 描边颜色模式 key（旧名保留：现在只决定颜色怎么算，不再对应独立着色器）。 */
    public static final String KEY_DEFAULT = "default";
    public static final String KEY_GRADIENT = "gradient";
    public static final String KEY_WARP_FBM = "warp_fbm";

    /**
     * 剪影遮罩 RenderType。它的输出目标是"调用方当前绑定的 FBO"，
     * 因此只能配合 ItemOutlinePipeline 使用。着色器未加载时返回 null。
     */
    @Nullable
    public static RenderType getOutlineMaskRenderType() { return outlineMaskRenderType; }

    // ── registration ──

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        // lightning outline
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "lightning_outline"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> lightningOutlineShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // GUI glint
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_item_glint"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> itemGlintShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // 描边剪影遮罩 —— 把"物品占了哪些像素 + 该用什么颜色描边"写进离屏贴图
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "outline_mask"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        outlineMaskShader = shader;
                        outlineMaskRenderType = OutlineMaskRenderType.create(() -> outlineMaskShader);
                    });
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // 描边环形合成 —— 全屏把遮罩膨胀成等宽硬边环
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "outline_ring"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> outlineRingShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // shockwave — 空气折射/波前透镜（见 rendertype_shockwave.fsh）
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_shockwave"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> shockwaveShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // GUI horror voronoi overlay (fullscreen post-process)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_gui_horror_voronoi"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiHorrorVoronoiShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // GUI icon glow (texture-based edge dilation for difficulty select)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_gui_glow"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiGlowShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // 创造物品栏分区隔断行的流动色带（GUI：程序化极光缎带，无贴图）
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "gui_tab_divider"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiTabDividerShader = shader);
        } catch (Throwable t) {
            // 加载失败不会让分区功能失效：CreativeTabDividerRenderer 会退回一条纯色细线。
            HallMod.LOGGER.error("[Shaders] 加载 gui_tab_divider 失败，"
                    + "创造物品栏的分区隔断行将退回纯色细线", t);
        }

        // 难度选择界面的四档印记（程序化顶点几何 + 逐 profile 角度图案）
        // 用 POSITION_TEX_COLOR：Position 是屏幕坐标，UV0 传 (归一化半径, 角度)，
        // Color.rgb 传环带边缘靠近程度、Color.a 传环权重 —— 详见 DifficultySigilMesh。
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_difficulty_sigil"),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> difficultySigilShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_difficulty_sigil 失败，难度印记将退化为旧的发光效果", t);
        }

        // black hole (raymarched gravitational lensing, view-space billboard)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_blackhole"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> blackHoleShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // 坍缩使徒 boss 血条内容（GUI：纯黑底 + 彩色星尘，形状由 mask 贴图红通道决定）
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_gui_collapsar_bar"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> collapsarBarShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            // 异常消息里带着色器名，堆栈里带 GLSL 编译器的原始报错。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        // tooltip 底板的热力学流动（GUI：一块被从下方加热的板，无贴图）
        // 加载失败不会让 tooltip 变丑太多：TooltipStyleHook 会退回纯色底板。
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_tooltip_thermal"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> tooltipThermalShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_tooltip_thermal 失败，"
                    + "tooltip 底板将退回纯色（见 TooltipStyleHook）", t);
        }

        // 坍缩使徒背部六芒星光环（世界空间 billboard，程序化生成、无贴图）
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_collapsar_halo"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> collapsarHaloShader = shader);
        } catch (Throwable t) {
            // 必须走 LOGGER 而不是 printStackTrace()：printStackTrace 只写 stderr，
            // 不会进 run/logs/latest.log —— 上次光环消失就是被这一条坑了半天。
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_collapsar_halo 失败，光环将不可见", t);
        }

        // 天穹裁决的垂直光柱 —— 自主发光体积光束
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_verdict_beam"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> verdictBeamShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_verdict_beam 失败，天穹裁决的光柱将不可见", t);
        }

        // 裁决领域的地面光纹
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_verdict_field"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> verdictFieldShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log，
            // 于是"着色器没加载成功"会在日志里彻底消失（光环消失那次就是这么查了半天的）。
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_verdict_field 失败，裁决领域的地面光纹将不可见", t);
        }

        // 绯红誓约背板 —— 程序化几何 + 自发光纹路（POSITION_TEX，逐顶点参数编在 UV0 里）
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_crimson_backplate"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> crimsonBackplateShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_crimson_backplate 失败，绯红誓约背板将不可见", t);
        }

    }

    // ── built-in render types ──

    /**
     * 天穹裁决的垂直光柱 RenderType。
     *
     * <p>与冲击波不同的三点，都是有意的：</p>
     * <ul>
     *   <li><b>加法混合。</b>光柱是自发光体积，不是折射层 —— 它的作用是"把这条线点亮"，
     *       所以混合用 {@code ONE / ONE}，颜色叠到近白即自然泛光。
     *       这也让它<b>不需要</b>冲击波那套场景拷贝（把主帧缓冲 blit 到 copyTex），
     *       因为它不采样屏幕，只采样自己的几何。</li>
     *   <li><b>写深度。</b>渲染器侧设 {@code depthMask(true)}，RenderType 侧的
     *       {@code writeMaskState} 与之对应 —— <b>两处必须同时是"写"</b>。
     *       曾经两处都是 false，症状就是"光柱穿透实体和方块"，读起来像一张
     *       贴在所有物体前面的发光贴纸而不像一根立起来的柱子。
     *       反过来，代价是光柱边缘与它挡住的物体之间是一条硬边（要柔和过渡
     *       得采样深度纹理做 soft-particle，那是另一条管线）。</li>
     *   <li><b>不剔除背面。</b>玩家常常站在光柱内部，关剔除之后内壁也会被画出来，
     *       于是它会读成"一根玻璃管"而不是"一个亮色补丁"。</li>
     * </ul>
     *
     * <p>缓冲给得很大 —— <b>且这里的数字是字节数，不是顶点数</b>：
     * 圆柱 48 段 × 32 环 × 6 顶点 = 9216 顶点，而 {@code POSITION_COLOR_NORMAL}
     * 每个顶点 = 3×4(Pos) + 4(Color) + 3(I) + 1(Pad) + 3×1(Normal) + 1(Pad) = 28 字节，
     * 合计约 258KB。给 65536（≈ 2340 顶点）会在近景直接溢出。
     * 拿 524288 与冲击波同一档 —— 它那边是 32×64×6 顶点，量级一致。</p>
     */
    public static RenderType getVerdictBeamRenderType() {
        if (verdictBeamShader == null) return null;
        if (verdictBeamRenderType == null) {
            verdictBeamRenderType = RenderType.create(HallMod.MODID + ":verdict_beam",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES, 524288,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> verdictBeamShader))
                            .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                            .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                            .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                            // 写深度：与 VerdictBeamRenderer 里的 depthMask(true) 成对。
                            // 两处都必须是"写"，只改一处不会生效、症状还一样。
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorDepthWrite())
                            .createCompositeState(false));
        }
        return verdictBeamRenderType;
    }

    // ── 绯红誓约背板 ────────────────────────────────────────────

    /**
     * 背板的"底"通道：<b>普通 alpha 混合</b> + 写深度 + <b>剔除背面</b>。
     *
     * <h3>为什么写深度</h3>
     * <p>它是一块会遮挡的实体装饰：站在墙后该消失，挡在别的实体前面也该挡住。
     * 不写深度的话它会像贴在所有物体前面的一张发光贴纸。</p>
     *
     * <h3>为什么这次可以剔除背面（上一版不行）</h3>
     * <p>背饰的板面朝人体背面（{@code +Z}），从这个方向看过去是正面 —— 玩家自己
     * 在第三人称、别人在你背后，看到的都是这一面。几何的绕序就是按这个朝向烘的
     * （扇形逆时针 ⇒ 法线朝 {@code +Z}），所以直接开剔除是安全的。</p>
     * <p>上一版是对着相机的 billboard，那时"板面朝哪"每帧都在变，只能关剔除
     * 并在几何里补一份反绕序。</p>
     *
     * <h3>缓冲大小：这个数字是字节数，不是顶点数</h3>
     * <p>{@code POSITION_TEX} 每条顶点 = 3×4(Pos) + 2×4(UV) = 20 字节。
     * 本几何 = 48 段 × 3 顶点 = 144 条 = 2.9KB，给 65536（≈ 3276 顶点）
     * 有 20 倍余量。多人同屏时每个玩家一次性写完整份再 flush，
     * 所以余量按"单个玩家的顶点数"算就够。</p>
     */
    public static RenderType getCrimsonBackplateRenderType() {
        if (crimsonBackplateShader == null) return null;
        if (crimsonBackplateRenderType == null) {
            crimsonBackplateRenderType = RenderType.create(HallMod.MODID + ":crimson_backplate",
                    DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES, 65536,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> crimsonBackplateShader))
                            .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                            .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                            .setCullState(AccessorRenderStateShard.splendiding$getCull())
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorDepthWrite())
                            .createCompositeState(false));
        }
        return crimsonBackplateRenderType;
    }

    /**
     * 背板的"亮"通道：<b>加法混合</b> + 不写深度。
     *
     * <p>加法让五边形顶点、六芒星角与条带是"往上加光"而不是"覆盖颜色"，
     * 这是它看起来在发亮的原因；不写深度是因为它叠在同一块板面之上，
     * 写深度只会把随后画的半透明几何挡掉。</p>
     */
    public static RenderType getCrimsonBackplateGlowRenderType() {
        if (crimsonBackplateShader == null) return null;
        if (crimsonBackplateGlowRenderType == null) {
            crimsonBackplateGlowRenderType = RenderType.create(HallMod.MODID + ":crimson_backplate_glow",
                    DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES, 65536,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> crimsonBackplateShader))
                            .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                                    "hall_backplate_additive", () -> {
                                        RenderSystem.enableBlend();
                                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                                GlStateManager.DestFactor.ONE);
                                    }, () -> {
                                        RenderSystem.disableBlend();
                                        RenderSystem.defaultBlendFunc();
                                    }))
                            .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                            .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                            .createCompositeState(false));
        }
        return crimsonBackplateGlowRenderType;
    }

    /**
     * 背板每一遍绘制前把 uniform 写好。
     *
     * <p>必须在<b>每一遍</b>之前调用：uniform 是全局状态，加法那一遍如果不重设，
     * 就会沿用上一遍的值（表现为"发光层跟着底层一起变暗"）。</p>
     *
     * @param time   连续时间（tick + partialTick），驱动三层的自转与条带移动
     * @param anim   本帧的展开 / 格挡进度
     * @param glow   true = 这一遍是加法"亮"层。此时只输出高光部分，
     *               板面的暗底留给"底"那一遍画，否则加法会把暗部也叠上去
     */
    public static void setBackplateUniforms(float time,
                                            org.bytechen.hall.client.rend.backplate.CrimsonVowBackplateRig.Anim anim,
                                            boolean glow) {
        ShaderInstance sh = crimsonBackplateShader;
        if (sh == null) return;

        setUniform(sh, "Time", time);
        setUniform(sh, "Reveal", anim.unfold());
        setUniform(sh, "Block", anim.block());
        // 常亮 0.55：不格挡时也要看得清三层结构，否则它就只是一团暗影
        setUniform(sh, "Intensity", glow ? (0.55F + 1.05F * anim.block()) : 1.0F);
        // 三层各自的自转方向与速度（内圈最慢、条带最快）
        if (sh.getUniform("Spin") != null) sh.safeGetUniform("Spin").set(0.55F, 1.0F, 2.4F);
        // 层半径：内圈 0.60、外圈 0.80
        if (sh.getUniform("RingRadius") != null) sh.safeGetUniform("RingRadius").set(0.80F, 0.60F);

        // 像素尺寸 → 片元用它把细线宽换算成屏幕空间恒定像素（与 fwidth 互为保险：
        // fwidth 在极细线上会趋于 0，那时靠这个下限托底）。
        // 用窗口的物理像素宽而不是 GUI 缩放宽：着色器写在片元坐标里，物理像素才对。
        int winW = Math.max(1, net.minecraft.client.Minecraft.getInstance()
                .getWindow().getWidth());
        float px = 1.0F / (float) winW;
        setUniform(sh, "PixelWidth", net.minecraft.util.Mth.clamp(px, 1.0E-5F, 4.0E-3F));

        // 色板：内圈深绯红 → 外圈雾粉紫（F）。片元按归一化半径在两者之间插值。
        setUniform(sh, "VowColor", 0.66F, 0.055F, 0.20F);
        setUniform(sh, "AccentColor", 0.96F, 0.50F, 1.0F);
    }

    /** 写一个 float uniform（名字不存在时静默跳过 —— 着色器可能把没用到的 uniform 优化掉）。 */
    private static void setUniform(ShaderInstance sh, String name, float value) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(value);
    }

    /** 写一个 vec3 uniform。 */
    private static void setUniform(ShaderInstance sh, String name, float x, float y, float z) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(x, y, z);
    }

    public static RenderType getLightningOutlineRenderType() {
        if (lightningOutlineShader == null) return null;
        if (lightningOutlineRenderType == null) {
            lightningOutlineRenderType = makeRenderType("lightning_outline",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES,
                    () -> lightningOutlineShader, null, true, true);
        }
        return lightningOutlineRenderType;
    }

    public static RenderType getItemGlintRenderType() {
        if (itemGlintShader == null) return null;
        if (itemGlintRenderType == null) {
            itemGlintRenderType = makeRenderType("item_glint",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    () -> itemGlintShader, null, true, false);
        }
        return itemGlintRenderType;
    }

    public static RenderType getShockwaveRenderType() {
        if (shockwaveShader == null) return null;
        if (shockwaveRenderType == null) {
            // Large buffer: 32×64×6 = 12288 vertices, ~344KB
            shockwaveRenderType = RenderType.create(HallMod.MODID + ":shockwave",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES, 524288,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shockwaveShader))
                            .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                            .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                            .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                            .createCompositeState(false));
        }
        return shockwaveRenderType;
    }

    /**
     * Late-deferred shockwave RenderType for shader-pack compatibility.
     * Same as {@link #getShockwaveRenderType()} but with {@code MAIN_TARGET}
     * output so the shockwave writes directly to the main framebuffer,
     * bypassing the shader pack's GBuffer pipeline.
     *
     * <p>Built once and cached — the previous implementation created a new
     * RenderType (plus its 512KB buffer) on every call.</p>
     */
    public static RenderType getLateShockwaveRenderType() {
        if (shockwaveShader == null) return null;
        if (shockwaveLateRenderType == null) {
            shockwaveLateRenderType = RenderType.create(HallMod.MODID + ":late_shockwave",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES, 524288,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shockwaveShader))
                            .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                            .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                            .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                            .setOutputState(AccessorRenderStateShard.splendiding$getMainTarget())
                            .createCompositeState(false));
        }
        return shockwaveLateRenderType;
    }

    // ── helpers ──

    private static RenderType makeRenderType(String name, VertexFormat format,
                                              VertexFormat.Mode mode,
                                              java.util.function.Supplier<ShaderInstance> shader,
                                              @Nullable RenderStateShard.TextureStateShard tex,
                                              boolean depth, boolean depthWrite) {
        return RenderType.create(HallMod.MODID + ":" + name, format, mode, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(tex != null ? tex
                                : new RenderStateShard.EmptyTextureStateShard(() -> { }, () -> { }))
                        .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                        .setDepthTestState(depth
                                ? AccessorRenderStateShard.splendiding$getLequalDepthTest()
                                : AccessorRenderStateShard.splendiding$getNoDepthTest())
                        .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                        .setWriteMaskState(depthWrite
                                ? AccessorRenderStateShard.splendiding$getColorDepthWrite()
                                : AccessorRenderStateShard.splendiding$getColorWrite())
                        .createCompositeState(false));
    }
}
