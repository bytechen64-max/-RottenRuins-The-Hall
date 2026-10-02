package org.bytechen.hall.client.rend.glint;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.function.Supplier;

/**
 * 剪影遮罩用的 RenderType。
 *
 * <p>它跟其他 RenderType 的关键区别是 <b>不绑定输出目标</b>：{@link RenderStateShard.OutputStateShard}
 * 的 setup 是空实现，于是遮罩会被画进"调用方当前绑定的那个 FBO"。
 * 标准的 {@code MAIN_TARGET} / {@code ITEM_ENTITY_TARGET} 之类都会强制改绑，
 * 那样就没法把剪影写进我们自己的离屏遮罩了。</p>
 *
 * <p>其余状态是与"覆盖判定"配套的最小集合：</p>
 * <ul>
 *   <li>{@code LEQUAL} 深度测试 + {@code COLOR_WRITE}（不写深度）——遮罩既被场景几何
 *       遮挡，又不会污染共享的场景深度附件。</li>
 *   <li>{@code NO_TRANSPARENCY}——不混合，保证覆盖率是 0/1 而不是累加出来的中间值。</li>
 *   <li>{@code NO_CULL}——2D 平板物品模型只有单面四边形，剔除背面会让它整个消失。</li>
 * </ul>
 */
public class OutlineMaskRenderType extends RenderType {

    private OutlineMaskRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                  boolean affectsCrumbling, boolean sortOnUpload,
                                  Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * 空实现的输出状态：保持当前绑定的 framebuffer，不切回主目标。
     */
    private static final RenderStateShard.OutputStateShard KEEP_BOUND_TARGET =
            new RenderStateShard.OutputStateShard("hall_outline_mask_keep_bound_target", () -> { }, () -> { });

    public static RenderType create(Supplier<ShaderInstance> shader) {
        return RenderType.create(
                "hall:outline_mask",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(KEEP_BOUND_TARGET)
                        .createCompositeState(false)
        );
    }
}
