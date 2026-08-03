package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.bytechen.hall.HallMod;

/**
 * Custom {@link RenderType} for collapse entity rendering.
 * <p>
 * Uses {@link VertexFormat.Mode#TRIANGLES} with {@link DefaultVertexFormat#POSITION_COLOR}
 * and additive blending ({@code SRC_ALPHA, ONE}), matching the original
 * {@link RenderType#lightning()} render state but using TRIANGLES instead of QUADS
 * draw mode so that programmatic triangle geometry renders correctly.
 */
public abstract class CollapseRenderType extends RenderType {

    public CollapseRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                              int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                              Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * Additive-blended, no-cull, depth-testing, TRIANGLES render type for collapse effects.
     * Render state mirrors {@link RenderType#lightning()} exactly except for {@code TRIANGLES} mode.
     */
    public static final RenderType COLLAPSE = RenderType.create(
            HallMod.MODID + ":collapse_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)
    );
}
