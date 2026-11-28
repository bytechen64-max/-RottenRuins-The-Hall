package org.bytechen.rottenruins.content.magic.effect;

import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;

/**
 * 虚化：身体半透明。
 *
 * <p>半透明由 {@code client} 的 {@code LivingEntityRenderer} mixin 按效果状态每帧换渲染类型，
 * 服务端这边只负责标记状态，不加发光描边也不出粒子。</p>
 */
public class EtherealEffect extends CustomEffectBase {

    public static final int COLOR = 0x9AD8FF;

    public EtherealEffect(net.minecraft.resources.ResourceLocation id) {
        super(id, CustomEffectCategory.NEUTRAL, COLOR);
    }

    @Override
    public int getDefaultDuration() {
        return 10;
    }
}
