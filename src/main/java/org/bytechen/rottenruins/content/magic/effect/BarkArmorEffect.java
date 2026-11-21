package org.bytechen.rottenruins.content.magic.effect;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;

/**
 * 藤甲术：除雷电与火焰外的伤害全部免疫，火焰伤害翻倍。
 *
 * <p>伤害结算在 {@code WardHandlers}。</p>
 */
public class BarkArmorEffect extends CustomEffectBase {

    public BarkArmorEffect(ResourceLocation id) {
        super(id, CustomEffectCategory.BENEFICIAL, 0x8FBF5F);
    }
}
