package org.bytechen.hall.client.mask;

import org.bytechen.hall.api.mask.MaskLayerSpec;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/**
 * 一种可注入的 mask 效果。
 *
 * <h3>这就是「注入系统」的扩展点</h3>
 * <p>想加一个新效果（泛光、边缘光、能量脉冲、扫描线……）只需要做三件事：</p>
 * <ol>
 *   <li>写一个 {@code .vsh/.fsh/.json} 着色器，共用 {@code Sampler0} 采样遮罩；</li>
 *   <li>实现本接口：给出着色器实例、三种 RenderType、以及自己的 uniform 设置；</li>
 *   <li>{@link MaskEffectRegistry#register} 注册，然后物品侧用
 *       {@code "effect": "你的 id"} 引用。</li>
 * </ol>
 * <p>物品渲染管线（注入点、光影延迟回放、上下文开关、层序）由本系统统一负责，
 * 新效果不需要碰 {@code MixinItemRendererCosmic}，也不需要自己写 RenderType 变体
 * —— 那三种变体由 {@code MaskLayerRenderType} 按着色器自动派生。</p>
 *
 * <h3>遮罩怎么被送到着色器</h3>
 * <p>每层会用它的遮罩 sprite 烘一个物品 quad，并把这个 sprite 描进方块图集。
 * 着色器拿到的 {@code UV0} 是<b>图集 UV</b>，不是 sprite 内的局部 UV；要采样
 * 邻域（泛光、描边这类）必须先用 {@code MaskMin/MaskSize} 换算回局部空间，
 * 否则会跨 sprite 采到图集里别人家的贴图 —— {@link MaskUniforms#setSlice} 已经把
 * 这一对 uniform 备好了。</p>
 *
 * @see org.bytechen.hall.api.mask.MaskLayerSpec
 */
public interface MaskEffectPass {

    /** 效果 id，物品侧以 {@code "effect": "<id>"} 引用。 */
    String id();

    /**
     * 本效果的着色器实例，未加载时返回 {@code null}。
     * 返回 {@code null} 的层会被整层静默跳过（而不是画出错的东西）。
     */
    @Nullable ShaderInstance shader();

    /** 无光影时的即时渲染变体。 */
    @Nullable RenderType immediateType();

    /** 光影下延迟回放的变体（世界空间/非一手）。 */
    @Nullable RenderType afterLevelType();

    /** 光影下延迟回放的一手变体。 */
    @Nullable RenderType handAfterLevelType();

    /**
     * 绘制该层之前设置 uniform。此时还没 flush，{@code Uniform.set()} 的值会在
     * 该层自己的 {@code endBatch} 时上传 —— 前提是<b>每层单独 flush</b>，
     * 这一点由 {@code MaskLayerRenderer} 保证。
     *
     * @param layer 该层的配置
     * @param mask  该层遮罩在图集里的 sprite（决定效果在<b>哪里</b>生效）
     * @param base  物品本体贴图在图集里的 sprite（决定效果用什么<b>颜色/亮度</b>；
     *              同时也是该层 quad 的几何来源）。取不到时为 {@code null}，
     *              效果应当退回「只用遮罩自己当光源」而不是崩
     * @param time  游戏 tick 数（和 cosmic 用同一个时间源）
     */
    void apply(MaskLayerSpec layer, @Nullable TextureAtlasSprite mask,
               @Nullable TextureAtlasSprite base, float time);

    /** 着色器是否就绪；未就绪则整层跳过。 */
    default boolean ready() {
        return shader() != null;
    }
}
