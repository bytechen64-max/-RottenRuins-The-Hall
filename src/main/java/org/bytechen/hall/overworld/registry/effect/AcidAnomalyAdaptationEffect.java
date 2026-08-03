package org.bytechen.hall.overworld.registry.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 纳酸异常适应 Buff —— 通过外部手段（物品/环境等）获得，期间无法叠加纳酸异常进度。
 */
public class AcidAnomalyAdaptationEffect extends MobEffect {
    public static final AcidAnomalyAdaptationEffect INSTANCE = new AcidAnomalyAdaptationEffect();

    private AcidAnomalyAdaptationEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xBBFF66); // 浅酸绿色
    }
}
