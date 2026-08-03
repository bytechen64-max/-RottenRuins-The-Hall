package org.bytechen.hall.client.cosmic.render;

import org.bytechen.hall.HallMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom RenderType variants for cosmic starfield rendering,
 * matching {@code mystery_buding.live.render.CosmicRenderType} exactly.
 *
 * <h3>Three variants</h3>
 * <table>
 *   <tr><th>Variant</th><th>Depth test</th><th>Output</th><th>Write mask</th><th>Use case</th></tr>
 *   <tr><td>{@link #COSMIC}</td><td>EQUAL</td><td>(default)</td><td>(default)</td><td>Immediate render, no shader pack</td></tr>
 *   <tr><td>{@link #COSMIC_AFTER_LEVEL}</td><td>LEQUAL + polygon offset</td><td>MAIN_TARGET</td><td>COLOR_WRITE</td><td>Deferred world-space replay</td></tr>
 *   <tr><td>{@link #COSMIC_HAND_AFTER_LEVEL}</td><td>NO_DEPTH_TEST</td><td>MAIN_TARGET</td><td>COLOR_WRITE</td><td>Deferred first-person replay</td></tr>
 * </table>
 *
 * <p>The {@code EQUAL} depth test in {@code COSMIC} ensures the cosmic shader
 * only draws on pixels where the base item already wrote depth — perfectly
 * fitting the item silhouette without exceeding it.</p>
 */
public abstract class CosmicRenderType extends RenderType {

    public CosmicRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                            int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                            Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /** Polygon offset that pushes cosmic geometry slightly toward the camera
     *  to prevent z-fighting with the base item surface during late replay. */
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

    /**
     * Immediate cosmic layer render.  Uses {@code EQUAL} depth test so the cosmic
     * starfield only appears on pixels where the base item has already written
     * depth — precisely fitting the item silhouette.
     */
    public static final RenderType COSMIC = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID,"cosmic").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,   // affectsCrumbling
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
     * Late-deferred world-space cosmic render.
     * <ul>
     *   <li>{@code LEQUAL} depth test — base item depth already written during
     *       the main scene render, plus polygon offset to avoid z-fighting.</li>
     *   <li>{@code MAIN_TARGET} output — forces render to the main framebuffer,
     *       bypassing the shader pack's GBuffer pipeline.</li>
     *   <li>{@code COLOR_WRITE} only — does not pollute the depth buffer.</li>
     *   <li>{@code affectsCrumbling = false} — avoids unnecessary buffer rebuilds.</li>
     * </ul>
     */
    public static final RenderType COSMIC_AFTER_LEVEL = RenderType.create(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID,"cosmic_after_level").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,  // affectsCrumbling
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

    /**
     * Late-deferred first-person hand cosmic render.
     * <ul>
     *   <li>{@code NO_DEPTH_TEST} — the first-person hand does not write to
     *       the main depth buffer under many shader packs.</li>
     *   <li>{@code MAIN_TARGET} output + {@code COLOR_WRITE} only, same as above.</li>
     * </ul>
     */
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

    // ── Corruption shader RenderTypes ───────────────────────────

    /**
     * Immediate corruption layer (RGB split + scanlines + colour shift).
     * Uses {@code EQUAL} depth so corruption only paints where the
     * base item already wrote depth.
     */
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

    /**
     * Late-deferred world-space corruption render.  Same strategy as
     * {@link #COSMIC_AFTER_LEVEL}: LEQUAL depth, polygon offset,
     * MAIN_TARGET output, COLOR_WRITE only.
     */
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

    /**
     * Late-deferred first-person hand corruption render.
     * NO_DEPTH_TEST, MAIN_TARGET output, COLOR_WRITE only.
     */
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
