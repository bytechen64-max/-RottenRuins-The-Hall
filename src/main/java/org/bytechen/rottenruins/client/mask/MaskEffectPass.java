package org.bytechen.rottenruins.client.mask;

import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/** 一种可注入的 mask 效果（扩展点）：实现本接口 + 自带着色器 + 注册，物品侧用 {@code "effect": "<id>"} 引用；
 *  坑点：着色器 UV0 是图集 UV（邻域采样前须用 MaskMin/MaskSize 换回局部空间），apply 期间还没 flush，
 *  每层必须单独 endBatch；shader() 返回 null 的层整层静默跳过。 */
public interface MaskEffectPass {

    String id();

    @Nullable ShaderInstance shader();

    @Nullable RenderType immediateType();

    @Nullable RenderType afterLevelType();

    @Nullable RenderType handAfterLevelType();

    void apply(MaskLayerSpec layer, @Nullable TextureAtlasSprite mask,
               @Nullable TextureAtlasSprite base, float time);

    default boolean ready() {
        return shader() != null;
    }
}
