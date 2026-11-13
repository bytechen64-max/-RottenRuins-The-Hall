package org.bytechen.rottenruins.client.rend;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.mixin.accessor.AccessorRenderStateShard;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.client.rend.glint.OutlineMaskRenderType;
import org.jetbrains.annotations.Nullable;
import org.bytechen.rottenruins.utils.ModUtils;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SplendidingShaders {

    public static ShaderInstance lightningOutlineShader;
    public static ShaderInstance itemGlintShader;
    public static ShaderInstance shockwaveShader;

    public static ShaderInstance outlineMaskShader;
    public static ShaderInstance outlineRingShader;
    /** 描边光雾的模糊 pass；为 null 时整条模糊链跳过，硬环照常画。 */
    @Nullable
    public static ShaderInstance outlineHaloBlurShader;

    /** 物品的体积火焰（fire_2）：世界空间盒子内 ray marching 3D 噪声 + 场景深度截断 + Beer 累积。 */
    @Nullable
    public static ShaderInstance fireVolumeShader;

    @Nullable
    public static ShaderInstance flameSimShader;
    @Nullable
    public static ShaderInstance flameCompositeShader;
    @Nullable
    public static ShaderInstance arcCompositeShader;
    @Nullable
    public static ShaderInstance fractureCompositeShader;
    @Nullable
    private static RenderType outlineMaskRenderType;

    public static ShaderInstance guiHorrorVoronoiShader;
    public static ShaderInstance guiGlowShader;
    /** 创造物品栏分区隔断行的流动色带；无 RenderType，走 {@code BufferUploader.drawWithShader} 立即绘制。 */
    public static ShaderInstance guiTabDividerShader;
    public static ShaderInstance difficultySigilShader;
    public static ShaderInstance magicStarfieldShader;
    public static ShaderInstance magicConstellationShader;
    public static ShaderInstance magicPulseShader;
    @Nullable
    public static ShaderInstance wheelSliceShader;
    @Nullable
    public static ShaderInstance wheelAuraShader;
    @Nullable
    public static ShaderInstance wheelCoreShader;
    /** 逐物理像素抗锯齿的 UI 形状（{@code uScale = GUI scale}）。 */
    @Nullable
    public static ShaderInstance wheelSdfShader;
    public static ShaderInstance collapsarBarShader;
    public static ShaderInstance collapsarHaloShader;
    public static ShaderInstance blackHoleShader;
    public static ShaderInstance verdictBeamShader;
    public static ShaderInstance verdictFieldShader;
    public static ShaderInstance crimsonBackplateShader;

    public static ShaderInstance crimsonSigilShader;
    public static ShaderInstance crimsonCubeShader;
    public static ShaderInstance crimsonWarpShader;
    /** 终末的 Strand + Muzzle 全屏叠层：目前<b>没有绘制方</b>，注册与着色器文件按需求刻意保留。 */
    public static ShaderInstance crimsonStrandShader;

    /** 陨石事件：天空十字星 / 下降拖尾 / 火球，三者共用时间轴与配色（{@code uMode} 0/1/2）。 */
    public static ShaderInstance meteorEventShader;
    /** 全屏 3bit 红黑爆炸屏效：{@code ProjMat} 由调用方给，顶点着色器不读 ModelViewMat。 */
    public static ShaderInstance meteorScreenShader;
    public static ShaderInstance meteorHeatShader;

    /** tooltip 底板的热力学流动；无 RenderType，走 {@code BufferUploader.drawWithShader} 立即绘制。 */
    public static ShaderInstance tooltipThermalShader;

    private static RenderType lightningOutlineRenderType;
    private static RenderType itemGlintRenderType;
    private static RenderType shockwaveRenderType;
    private static RenderType shockwaveLateRenderType;
    private static RenderType verdictBeamRenderType;
    private static RenderType crimsonBackplateRenderType;
    private static RenderType crimsonBackplateGlowRenderType;

    /** 描边颜色模式 key（旧名保留：现在只决定颜色怎么算，不再对应独立着色器）。 */
    public static final String KEY_DEFAULT = "default";
    public static final String KEY_GRADIENT = "gradient";
    public static final String KEY_WARP_FBM = "warp_fbm";
    public static final String KEY_FIRE = "fire";
    /** 燃烧描边第二版：世界空间体积火焰，与 {@link #KEY_FIRE}（屏幕空间火丝）不是一回事，GUI 不画。 */
    public static final String KEY_FIRE_2 = "fire_2";

    /** 剪影遮罩 RenderType：输出到调用方当前绑定的 FBO，只能配合 ItemOutlinePipeline 使用。 */
    @Nullable
    public static RenderType getOutlineMaskRenderType() { return outlineMaskRenderType; }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("lightning_outline"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> lightningOutlineShader = shader);
        } catch (Throwable t) {
            // 必须走 LOGGER：printStackTrace() 只写 stderr，不进 run/logs/latest.log（光环消失那次就是这么查的）。
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_item_glint"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> itemGlintShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("outline_mask"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        outlineMaskShader = shader;
                        outlineMaskRenderType = OutlineMaskRenderType.create(() -> outlineMaskShader);
                    });
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("outline_ring"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> outlineRingShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("outline_halo_blur"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> outlineHaloBlurShader = shader);
        } catch (Throwable t) {
            // 加载失败只是"没有光雾"（ItemOutlinePipeline 会跳过整条模糊链），硬环照常。
            HallMod.LOGGER.error("[Shaders] 加载 outline_halo_blur 失败，描边光雾将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("fire_volume"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> fireVolumeShader = shader);
        } catch (Throwable t) {
            // 加载失败 = 没有体积火焰，描边/光雾照常。
            HallMod.LOGGER.error("[Shaders] 加载 fire_volume 失败，体积火焰将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("fracture_composite"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> fractureCompositeShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 fracture_composite 失败，裂纹效果将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("arc_composite"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> arcCompositeShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 arc_composite 失败，电弧效果将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("flame_sim"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> flameSimShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 flame_sim 失败，火焰效果将不可见", t);
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("flame_composite"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> flameCompositeShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 flame_composite 失败，火焰效果将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_shockwave"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> shockwaveShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_gui_horror_voronoi"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiHorrorVoronoiShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_gui_glow"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiGlowShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("gui_tab_divider"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiTabDividerShader = shader);
        } catch (Throwable t) {
            // 加载失败不会让分区功能失效：CreativeTabDividerRenderer 会退回一条纯色细线。
            HallMod.LOGGER.error("[Shaders] 加载 gui_tab_divider 失败，"
                    + "创造物品栏的分区隔断行将退回纯色细线", t);
        }

        // 用 POSITION_TEX_COLOR：Position 是屏幕坐标，UV0 传 (归一化半径, 角度)，Color.rgb 传环带边缘靠近程度、a 传环权重。
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_difficulty_sigil"),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> difficultySigilShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_difficulty_sigil 失败，难度印记将退化为旧的发光效果", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("magic_starfield"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> magicStarfieldShader = shader);
        } catch (Throwable t) {
            // 加载失败只是没有流动星空：MagicScreen 会退回静态星点背景。
            HallMod.LOGGER.error("[Shaders] 加载 magic_starfield 失败，"
                    + "魔法星图将退回静态星空", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("magic_constellation"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> magicConstellationShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 magic_constellation 失败，"
                    + "魔法星图的槽位连线将退回星点虚线", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("magic_pulse"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> magicPulseShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 magic_pulse 失败，"
                    + "魔法星图将没有放入法术时的光点反馈", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("wheel_slice"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> wheelSliceShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 wheel_slice 失败，"
                    + "施法轮盘的扇区将退回纯色绘制", t);
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("wheel_aura"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> wheelAuraShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 wheel_aura 失败，"
                    + "施法轮盘将没有外圈光环", t);
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("wheel_core"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> wheelCoreShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 wheel_core 失败，"
                    + "施法轮盘中心将退回纯色圆盘", t);
        }
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("sdf_ui"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> wheelSdfShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 sdf_ui 失败，"
                    + "轮盘/装载界面的圆环与圆角将退回方块绘制", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_blackhole"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> blackHoleShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_gui_collapsar_bar"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> collapsarBarShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 着色器加载失败", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_tooltip_thermal"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> tooltipThermalShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_tooltip_thermal 失败，"
                    + "tooltip 底板将退回纯色（见 TooltipStyleHook）", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_collapsar_halo"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> collapsarHaloShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_collapsar_halo 失败，光环将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_verdict_beam"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> verdictBeamShader = shader);
        } catch (Throwable t) {
            // 一律走 LOGGER：printStackTrace() 只写 stderr，不会进 run/logs/latest.log。
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_verdict_beam 失败，天穹裁决的光柱将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_verdict_field"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> verdictFieldShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_verdict_field 失败，裁决领域的地面光纹将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_crimson_backplate"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> crimsonBackplateShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_crimson_backplate 失败，绯红誓约背板将不可见", t);
        }

        //  每个都单独 try：一个挂了不会连坐其它特效。
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_crimson_sigil"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> crimsonSigilShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_crimson_sigil 失败，绯红法阵的地面图案将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_crimson_cube"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> crimsonCubeShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_crimson_cube 失败，法阵中心的立方体将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_crimson_warp"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> crimsonWarpShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_crimson_warp 失败，法阵上方的扭曲将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_crimson_strand"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> crimsonStrandShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_crimson_strand 失败，终末的降下光幕将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_meteor_event"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> meteorEventShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_meteor_event 失败，陨石事件将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_meteor_screen"),
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> meteorScreenShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_meteor_screen 失败，3bit 红黑屏效将不可见", t);
        }

        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ModUtils.modLoc("rendertype_meteor_heat"),
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> meteorHeatShader = shader);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[Shaders] 加载 rendertype_meteor_heat 失败，陨石热浪折射将不可见", t);
        }

    }

    /**
     * 天穹裁决的垂直光柱 RenderType：加法混合（不采样屏幕，故不需要场景拷贝）+ 写深度 + 不剔除背面。
     * 缓冲参数是<b>字节数</b>不是顶点数：48×32×6 顶点 × 28 字节 ≈ 258KB，给 524288 与冲击波同档。
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
                            // 写深度：与 VerdictBeamRenderer 的 depthMask(true) 成对，两处都必须是"写"。
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorDepthWrite())
                            .createCompositeState(false));
        }
        return verdictBeamRenderType;
    }

    /** 背板的"底"通道：普通 alpha 混合 + 写深度 + 剔除背面（billboard 永远正对镜头，只看到正面）。 */
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

    /** 背板的"亮"通道：加法混合 + 不写深度；靠 {@code GlowPass} uniform 与底遍区分，两遍之间必须 endBatch。 */
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
     * 背板每一遍绘制前写 uniform：必须在每遍之前调用且两遍之间 {@code endBatch}，
     * 因为 {@code Uniform.set()} 只是记值、真正上传在 flush（{@code ShaderInstance.apply()}）。
     * {@code GlowPass} 把两遍分开（0 = 暗部结构，1 = 纯自发光）；{@code time} 单位是 tick 而非秒（片元按 20 tick/s 算心跳）。
     */
    public static void setBackplateUniforms(float time,
                                            org.bytechen.rottenruins.client.rend.backplate.CrimsonVowBackplateRig.Anim anim,
                                            boolean glow) {
        ShaderInstance sh = crimsonBackplateShader;
        if (sh == null) return;

        setUniform(sh, "Time", time);
        setUniform(sh, "Reveal", anim.unfold());
        setUniform(sh, "Block", anim.block());
        setUniform(sh, "GlowPass", glow ? 1.0F : 0.0F);
        // 底遍 1.0；亮遍平时 0.55，格挡拉满时 1.60。
        setUniform(sh, "Intensity", glow ? (0.55F + 1.05F * anim.block()) : 1.0F);
        if (sh.getUniform("Spin") != null) sh.safeGetUniform("Spin").set(0.55F, 1.0F, 2.4F);
        setUniform(sh, "VowColor", 0.66F, 0.055F, 0.20F);
        setUniform(sh, "AccentColor", 0.96F, 0.50F, 1.0F);
    }

    /** 写一个 float uniform（名字不存在时静默跳过 —— 着色器可能把没用到的 uniform 优化掉）。 */
    private static void setUniform(ShaderInstance sh, String name, float value) {
        if (sh.getUniform(name) != null) sh.safeGetUniform(name).set(value);
    }

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
            // 缓冲大小按字节算：32×64×6 = 12288 顶点 ≈ 344KB
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

    /** Late-deferred shockwave RenderType for shader-pack compatibility: same state but {@code MAIN_TARGET} output, built once and cached. */
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
