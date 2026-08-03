package org.bytechen.hall.client.rend.glint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.mixin.accessor.AccessorRenderStateShard;

import java.util.function.Supplier;

/**
 * Late-deferred RenderType variants for shader-pack-compatible outline rendering,
 * modeled after {@code mystery_buding.live.render.CosmicRenderType}.
 *
 * <h3>Why late rendering is needed</h3>
 * When Oculus + Iris (or other shader packs) are active, the deferred pipeline
 * captures the 3D scene into multiple GBuffer textures (albedo, normal, depth, …)
 * and then composites them in a final pass.  Custom shaders rendered during the
 * normal {@code ItemRenderer.render()} call would be captured into those GBuffers
 * and misinterpreted — outline colour could be treated as emissive, specular, or
 * reflection data, producing visual artifacts.
 *
 * <p>By enqueuing outline draw calls during the normal item render and replaying
 * them <b>after</b> {@code LevelRenderer.renderLevel()} completes, the outline
 * shader writes directly to the main framebuffer (via {@link RenderStateShard#MAIN_TARGET}),
 * completely bypassing the shader pack's intermediate targets.</p>
 *
 * <h3>Two variants</h3>
 * <ul>
 *   <li><b>World-space</b> ({@link #createLateWorldOutline}):
 *       {@code LEQUAL} depth test + polygon offset.  The item's depth is already
 *       in the depth buffer from the main scene render; LEQUAL with a slight
 *       polygon offset lets the expanded outline pass the depth test while still
 *       being occluded by walls/entities in front of the item.</li>
 *   <li><b>First-person hand</b> ({@link #createLateHandOutline}):
 *       {@code NO_DEPTH_TEST}.  Under many shader packs the first-person hand
 *       does not write to the main depth buffer, so depth testing is disabled
 *       to guarantee the outline is always visible on held items.</li>
 * </ul>
 *
 * <p>Both variants use {@code MAIN_TARGET} output and {@code COLOR_WRITE} mask
 * to avoid polluting the depth buffer or any remaining GBuffer targets.</p>
 */
public final class LateOutlineRenderType extends RenderType {

    private LateOutlineRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                                  int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                  Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * Polygon offset that pushes outline geometry slightly toward the camera,
     * preventing z-fighting between the expanded outline quads and the original
     * item surface.  Only active during late-deferred (after-level) rendering.
     * <p>
     * Values chosen to match the Live mod's proven configuration:
     * {@code polygonOffset(-1.0, -32.0)}.
     */
    private static final RenderStateShard.LayeringStateShard LATE_OUTLINE_DEPTH_BIAS = new RenderStateShard.LayeringStateShard(
            "splendiding_late_outline_depth_bias",
            () -> {
                RenderSystem.polygonOffset(-1.0F, -32.0F);
                RenderSystem.enablePolygonOffset();
            },
            () -> {
                RenderSystem.polygonOffset(0.0F, 0.0F);
                RenderSystem.disablePolygonOffset();
            }
    );

    // ── convenience accessors (prefixed to avoid overriding RenderType methods) ──

    private static RenderStateShard.TransparencyStateShard late$translucent() {
        return AccessorRenderStateShard.splendiding$getTranslucentTransparency();
    }

    private static RenderStateShard.DepthTestStateShard late$lequalDepth() {
        return AccessorRenderStateShard.splendiding$getLequalDepthTest();
    }

    private static RenderStateShard.DepthTestStateShard late$noDepth() {
        return AccessorRenderStateShard.splendiding$getNoDepthTest();
    }

    private static RenderStateShard.CullStateShard late$noCull() {
        return AccessorRenderStateShard.splendiding$getNoCull();
    }

    private static RenderStateShard.WriteMaskStateShard late$colorWrite() {
        return AccessorRenderStateShard.splendiding$getColorWrite();
    }

    // ── factory methods ──────────────────────────────────────────────

    /**
     * Create a late-deferred RenderType for world-space items (ground, item frames,
     * third-person).  Uses {@code LEQUAL} depth test with polygon offset so the
     * outline is properly occluded by geometry in front of the item.
     *
     * @param shader supplier for the outline ShaderInstance (regular non-_sp variant)
     * @param name   short unique name for the render type
     * @return a new RenderType instance
     */
    public static RenderType createLateWorldOutline(Supplier<ShaderInstance> shader, String name) {
        return RenderType.create(
                HallMod.MODID + ":late_world_" + name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                2097152,          // 2 MiB buffer — outline quads can be large for complex items
                false,            // affectsCrumbling = false — avoid unnecessary buffer rebuilds
                false,            // sortOnUpload
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(late$translucent())
                        .setDepthTestState(late$lequalDepth())
                        .setCullState(late$noCull())
                        .setLayeringState(LATE_OUTLINE_DEPTH_BIAS)
                        .setOutputState(MAIN_TARGET)
                        .setWriteMaskState(late$colorWrite())
                        .createCompositeState(false)
        );
    }

    /**
     * Create a late-deferred RenderType for first-person hand items.
     * Uses {@code NO_DEPTH_TEST} because the first-person hand typically does not
     * write to the main depth buffer under shader packs.
     *
     * @param shader supplier for the outline ShaderInstance (regular non-_sp variant)
     * @param name   short unique name for the render type
     * @return a new RenderType instance
     */
    public static RenderType createLateHandOutline(Supplier<ShaderInstance> shader, String name) {
        return RenderType.create(
                HallMod.MODID + ":late_hand_" + name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                2097152,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(late$translucent())
                        .setDepthTestState(late$noDepth())
                        .setCullState(late$noCull())
                        .setOutputState(MAIN_TARGET)
                        .setWriteMaskState(late$colorWrite())
                        .createCompositeState(false)
        );
    }
}
