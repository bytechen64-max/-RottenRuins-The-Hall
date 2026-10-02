package org.bytechen.hall.client.mask.render;

import org.bytechen.hall.HallMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * mask 效果层的 RenderType 工厂。
 *
 * <h3>为什么是工厂而不是常量</h3>
 * <p>cosmic 层的三种变体（即时 / 延迟 / 延迟一手）是写死的常量，因为只有那一个
 * 着色器。本系统要能不断加新效果，所以这里按「效果 id + 着色器」<b>按需派生</b>
 * 同样的三种变体：新效果只要给出自己的 {@link ShaderInstance}，就自动获得完整
 * 的光影兼容能力，不需要再写一份 RenderType 样板。</p>
 *
 * <h3>深度策略：LEQUAL + polygon offset（而不是 cosmic 的 EQUAL）</h3>
 * <p>cosmic 用 {@code EQUAL}，所以它<b>只能</b>画在物品自己占的像素上 —— 对星空
 * 是对的（星空不该溢出剑身）。但泛光的意义恰恰是溢出到物品轮廓之外，{@code EQUAL}
 * 会把外圈整片丢掉。这里改用 {@code LEQUAL} 加一点往相机方向的 polygon offset：</p>
 * <ul>
 *   <li>物品表面本身照常通过（深度相等，{@code LEQUAL} 成立），光晕核心在；</li>
 *   <li>物品轮廓外的背景像素深度更远，也通过 —— 光晕得以溢出；</li>
 *   <li>挡在物品前面的东西仍然挡住它（真的比物品近的像素不会通过），不会穿墙。</li>
 * </ul>
 *
 * <h3>混合：ADDITIVE（而不是 cosmic 的 TRANSLUCENT）</h3>
 * <p>发光层只有加法混合是自洽的：遮罩外是纯黑，加法下「黑 = 加 0」，于是不需要
 * 任何 alpha 剪裁它天然不可见，叠加多个层也只是不断加亮。项目里的描边系统反过来
 * 特意用 {@code TRANSLUCENT}（因为那里的黑要真的压下去），两者取向不同、都对。</p>
 *
 * <p>副作用是 alpha 通道不参与配色，所以 <b>opacity 由着色器自己乘进 rgb</b>
 * （见 {@code mask_glow.fsh}）—— 否则它会变成一个拧了没反应的旋钮。</p>
 *
 * <h3>⚠️ 这里的状态会被着色器 json 里的 "blend" 盖掉</h3>
 * <p>本类的 {@code setTransparencyState} 只在 {@code setupRenderState()} 阶段生效，
 * 而 {@code ShaderInstance.apply()} 紧接着在同一个 draw 里调用
 * {@code BlendMode.apply()}（1.20.1 的 {@code ShaderInstance.java:330}）—— <b>后者
 * 更晚，所以赢</b>。而 shader json <b>不写 "blend" 时的默认值是「混合被关闭」
 * （{@code new BlendMode()} → {@code disableBlend()}），既不是加法也不是 alpha</b>：
 * 后果是这一层会把自己的输出原样 REPLACE 进帧缓冲，遮罩的黑区（rgb=0）于是
 * 变成一整块黑色平面把人挡住。</p>
 * <p>所以：<b>改了这里的 TransparencyState，必须同时改 {@code mask_glow.json} 的
 * "blend" 节点，两处不一致时以 json 为准。</b></p>
 *
 * <h3>缓存与着色器重载</h3>
 * <p>缓存按「效果 id → (着色器实例, RenderType)」成对存。F3+T 重载资源后
 * {@code ShaderInstance} 会换成新实例，届时缓存对不上就被替换掉 —— 如果只按
 * 效果 id 缓存，重载后会一直拿着旧的着色器实例，症状是「改了 fsh 却毫无变化」。</p>
 */
public abstract class MaskLayerRenderType extends RenderType {

    /**
     * 初始缓冲字节数。BLOCK 格式每顶点 32 字节、一个 quad 128 字节，
     * 所以 64KB 够 512 个 quad —— 而我们是「每层每物品 flush 一次」，
     * 远远用不完。给得比 cosmic 的 2MB 小得多，是因为那个数字是写死的常量
     * 而这里是每个效果三份；BufferBuilder 在真的不够时会自己扩容
     * （"Needed to grow BufferBuilder buffer"），所以小一点只省内存、不冒险。
     */
    private static final int BUFFER_BYTES = 65536;

    /**
     * 往相机方向推一点的 polygon offset。取值与 cosmic 延迟层一致：
     * 因子 -1、单位 -32。
     */
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

    // 缓存的是「着色器实例 + RenderType」这一对，见类注释最后一节。

    private record Cached(ShaderInstance shader, RenderType type) {}

    private static final Map<String, Cached> IMMEDIATE = new HashMap<>();
    private static final Map<String, Cached> AFTER_LEVEL = new HashMap<>();
    private static final Map<String, Cached> HAND_AFTER_LEVEL = new HashMap<>();

    public MaskLayerRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                               int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                               Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * 无光影时的即时变体。{@code LEQUAL} + polygon offset，输出到当前绑定的目标。
     *
     * <p>{@code COLOR_WRITE} 不能省：不显式指定的话默认是 {@code COLOR_DEPTH_WRITE}，
     * 而我们的 polygon offset 会把写进去的深度<b>往相机推一截</b>。下一个用
     * {@code EQUAL} 深度测试的层（崩坏层就是）再拿自己未偏移的深度去比，就永远
     * 比不上了 —— 症状是「给物品加了个泛光，崩坏层/描边突然消失」。
     * 半透明的加法叠加层本来也不该写深度。</p>
     */
    public static RenderType immediate(String effectId, ShaderInstance shader) {
        if (shader == null) return null;
        Cached c = IMMEDIATE.get(effectId);
        if (c != null && c.shader() == shader) return c.type();

        RenderType type = RenderType.create(
                HallMod.MODID + ":mask_layer_" + effectId,
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, BUFFER_BYTES,
                true,   // affectsCrumbling —— 与 cosmic 即时层保持一致
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

    /**
     * 光影下延迟回放的世界空间变体：{@code MAIN_TARGET} 输出 + 只写颜色。
     *
     * <p>{@code MAIN_TARGET} 是关键 —— 它让这一层直接写主帧缓冲，绕开光影包的
     * GBuffer，否则会被当成「场景里的一个不透明物」而丢掉自发光外观。</p>
     */
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

    /**
     * 光影下延迟回放的一手变体：{@code NO_DEPTH_TEST}。
     *
     * <p>很多光影包下第一人称手臂根本不写主深度缓冲，于是 {@code LEQUAL} 会把
     * 整个手持层判死。这里索性不测深度 —— 代价是手持物品的泛光不再被世界几何
     * 遮挡，但那只在手伸进方块里的一瞬间看得出来。</p>
     */
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
