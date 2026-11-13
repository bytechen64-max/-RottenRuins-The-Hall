package org.bytechen.rottenruins.client.mask.render;

import org.bytechen.rottenruins.HallMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * mask 效果层的 RenderType 工厂：按「效果 id + 着色器」派生四种变体（即时 / 表面 / 延迟 / 延迟一手）。
 * <p>深度用 {@code LEQUAL} + 往相机方向的 polygon offset（cosmic 的 {@code EQUAL} 会丢掉轮廓外的像素，
 * 泛光就没法溢出）；混合用 {@code ADDITIVE}（遮罩外纯黑 = 加 0，天然不可见），故 opacity 由着色器乘进 rgb。
 * <p>⚠️ 本类的 {@code setTransparencyState} 会被 shader json 的 "blend" 盖掉（{@code ShaderInstance.apply()}
 * 更晚；json 缺省是关混合 → REPLACE，黑区会变成一块黑板），两处须同改，冲突时以 json 为准。
 * <p>缓存按「着色器实例 + RenderType」成对存：F3+T 重载后实例会换，只按 id 缓存会一直用旧的。
 */
public abstract class MaskLayerRenderType extends RenderType {

    /** 初始缓冲字节数：64KB（BLOCK 每 quad 128 字节 ≈ 512 quad），BufferBuilder 不够会自扩容。 */
    private static final int BUFFER_BYTES = 65536;

    /** 往相机方向推一点的 polygon offset，取值与 cosmic 延迟层一致：因子 -1、单位 -32。 */
    private static final LayeringStateShard DEPTH_BIAS = new LayeringStateShard(
            "hall_mask_layer_depth_bias",
            () -> {
                RenderSystem.polygonOffset(-1.0F, -32.0F);
                RenderSystem.enablePolygonOffset();
            },
            () -> {
                RenderSystem.polygonOffset(0.0F, 0.0F);
                RenderSystem.disablePolygonOffset();
            });

    private record Cached(ShaderInstance shader, RenderType type) {}

    private static final Map<String, Cached> IMMEDIATE = new HashMap<>();
    private static final Map<String, Cached> AFTER_LEVEL = new HashMap<>();
    private static final Map<String, Cached> HAND_AFTER_LEVEL = new HashMap<>();
    private static final Map<String, Cached> SURFACE = new HashMap<>();

    public MaskLayerRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                               int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                               Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /** 无光影时的即时变体，输出到当前绑定的目标。
     *  {@code COLOR_WRITE} 不能省：默认写深度会把 offset 推近的深度写进去，之后用 {@code EQUAL}
     *  深度测试的层（崩坏 / 描边）就比不上、直接消失。 */
    public static RenderType immediate(String effectId, ShaderInstance shader) {
        if (shader == null) return null;
        Cached c = IMMEDIATE.get(effectId);
        if (c != null && c.shader() == shader) return c.type();

        RenderType type = RenderType.create(
                HallMod.MODID + ":mask_layer_" + effectId,
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, BUFFER_BYTES,
                true,
                false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setLayeringState(DEPTH_BIAS)
                        .setLightmapState(LIGHTMAP)
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setTextureState(BLOCK_SHEET)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true));
        IMMEDIATE.put(effectId, new Cached(shader, type));
        return type;
    }

    /** 「遮罩窗口」表面变体：半透明混合（{@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}）、只写颜色。
     *  窗口内 alpha=1 → 换成着色器内容，窗口外 alpha=0 → 原贴图照常可见（如 {@code death_core}）。
     *  ⚠️ 必须与 shader json 的 "blend" 一致，缺省会关混合、窗口外变成黑色。 */
    public static RenderType surface(String effectId, ShaderInstance shader) {
        if (shader == null) return null;
        Cached c = SURFACE.get(effectId);
        if (c != null && c.shader() == shader) return c.type();

        RenderType type = RenderType.create(
                HallMod.MODID + ":mask_layer_surface_" + effectId,
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, BUFFER_BYTES,
                true, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setLayeringState(DEPTH_BIAS)
                        .setLightmapState(LIGHTMAP)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setTextureState(BLOCK_SHEET)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true));
        SURFACE.put(effectId, new Cached(shader, type));
        return type;
    }

    /** 光影下延迟回放的世界空间变体。
     *  {@code MAIN_TARGET} 是关键：直接写主帧缓冲、绕开光影包的 GBuffer，否则会被当成
     *  「场景里的一个不透明物」而丢掉自发光外观。 */
    public static RenderType afterLevel(String effectId, ShaderInstance shader) {
        if (shader == null) return null;
        Cached c = AFTER_LEVEL.get(effectId);
        if (c != null && c.shader() == shader) return c.type();

        RenderType type = RenderType.create(
                HallMod.MODID + ":mask_layer_" + effectId + "_after_level",
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, BUFFER_BYTES,
                false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setLayeringState(DEPTH_BIAS)
                        .setLightmapState(LIGHTMAP)
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setTextureState(BLOCK_SHEET)
                        .setOutputState(MAIN_TARGET)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        AFTER_LEVEL.put(effectId, new Cached(shader, type));
        return type;
    }

    /** 光影下延迟回放的一手变体：{@code NO_DEPTH_TEST}。
     *  很多光影包下第一人称手臂不写主深度缓冲，{@code LEQUAL} 会把整个手持层判死；
     *  代价是手持物品的泛光不再被世界几何遮挡（只在手伸进方块里时看得出来）。 */
    public static RenderType handAfterLevel(String effectId, ShaderInstance shader) {
        if (shader == null) return null;
        Cached c = HAND_AFTER_LEVEL.get(effectId);
        if (c != null && c.shader() == shader) return c.type();

        RenderType type = RenderType.create(
                HallMod.MODID + ":mask_layer_" + effectId + "_hand_after_level",
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, BUFFER_BYTES,
                false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setLightmapState(LIGHTMAP)
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setTextureState(BLOCK_SHEET)
                        .setOutputState(MAIN_TARGET)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        HAND_AFTER_LEVEL.put(effectId, new Cached(shader, type));
        return type;
    }
}
