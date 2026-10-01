package org.bytechen.hall.client.rend;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.mixin.accessor.AccessorRenderStateShard;
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
    /** 坍缩使徒 boss 血条的「血量内容」着色器（纯黑底 + 彩色星尘，mask 红通道定形状）。 */
    public static ShaderInstance collapsarBarShader;
    /** 坍缩使徒的背部六芒星光环（参考 glorb/field 彩虹噪声 + 六芒星 SDF）。 */
    public static ShaderInstance collapsarHaloShader;
    public static ShaderInstance blackHoleShader;
    /** 天穹裁决的垂直光柱（自主发光体积光束，纯 Local 空间，不需要场景拷贝）。 */
    public static ShaderInstance verdictBeamShader;
    /** 裁决领域的地面光纹（水平圆盘，极坐标六分对称）。 */
    public static ShaderInstance verdictFieldShader;

    // ── render types (lazy) ──
    private static RenderType lightningOutlineRenderType;
    private static RenderType itemGlintRenderType;
    private static RenderType shockwaveRenderType;
    private static RenderType shockwaveLateRenderType;
    private static RenderType verdictBeamRenderType;

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
     *   <li><b>不写深度。</b>44 格高的柱体写深度会把后面的实体/粒子全剪掉，
     *       留下一个"墙角一样的硬遮挡"。开 LEQUAL 深度测试（被地形正确遮挡）
     *       但关写深度，是这类体积特效的标准取舍。</li>
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
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                            .createCompositeState(false));
        }
        return verdictBeamRenderType;
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
