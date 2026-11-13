package org.bytechen.rottenruins.client.cosmic.render;

import org.bytechen.rottenruins.HallMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Cosmic / Corruption 的 RenderType 变体。
 * 即时变体用 EQUAL 深度测试，只在底物已写深度的像素上作画，正好贴合物品轮廓；
 * {@code *_AFTER_LEVEL} 变体走延迟回放：LEQUAL + polygon offset + MAIN_TARGET + COLOR_WRITE。
 */
public abstract class CosmicRenderType extends RenderType {

    public CosmicRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                            int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                            Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /** Polygon offset：延迟回放时把 cosmic 几何推向相机，避免与底物表面 z-fighting。 */
    private static final RenderStateShard.LayeringStateShard SHADER_LAYER_DEPTH_BIAS =
            new RenderStateShard.LayeringStateShard(
                    "splendiding_cosmic_depth_bias",
                    () -> {
                        RenderSystem.polygonOffset(-1.0F, -32.0F);
                        RenderSystem.enablePolygonOffset();
                    },
                    () -> {
                        RenderSystem.polygonOffset(0.0F, 0.0F);
                        RenderSystem.disablePolygonOffset();
                    }
            );

    /** 即时层：EQUAL 深度测试，只在底物已写深度处作画，精确贴合物品轮廓。 */
    public static final RenderType COSMIC = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID,"cosmic").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.cosmicShader))
                    .setDepthTestState(EQUAL_DEPTH_TEST)
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(BLOCK_SHEET)
                    .createCompositeState(true)
    );

    /**
     * 世界空间延迟回放：LEQUAL + polygon offset 防 z-fighting；MAIN_TARGET 绕过光影 GBuffer；
     * 只写 COLOR_WRITE 不污染深度；affectsCrumbling=false 避免多余的缓冲重建。
     */
    public static final RenderType COSMIC_AFTER_LEVEL = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID,"cosmic_after_level").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.cosmicShader))
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(BLOCK_SHEET)
                    .setLayeringState(SHADER_LAYER_DEPTH_BIAS)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );

    /** 第一人称手部延迟回放：光影包下手部常不写主深度，所以用 NO_DEPTH_TEST。 */
    public static final RenderType COSMIC_HAND_AFTER_LEVEL = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID,"cosmic_hand_after_level").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.cosmicShader))
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(BLOCK_SHEET)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );

    /** 即时腐化层（RGB 分离 + 扫描线 + 偏色）：EQUAL 深度，只画底物已写深度处。 */
    public static final RenderType CORRUPTION = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "corruption").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.corruptionShader))
                    .setDepthTestState(EQUAL_DEPTH_TEST)
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(BLOCK_SHEET)
                    .createCompositeState(true)
    );

    /** 世界空间延迟回放：与 {@link #COSMIC_AFTER_LEVEL} 同一套策略。 */
    public static final RenderType CORRUPTION_AFTER_LEVEL = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "corruption_after_level").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.corruptionShader))
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(BLOCK_SHEET)
                    .setLayeringState(SHADER_LAYER_DEPTH_BIAS)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );

    /** 第一人称手部延迟回放：NO_DEPTH_TEST，MAIN_TARGET + COLOR_WRITE。 */
    public static final RenderType CORRUPTION_HAND_AFTER_LEVEL = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "corruption_hand_after_level").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> CosmicShaders.corruptionShader))
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(BLOCK_SHEET)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );
}
