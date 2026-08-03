package org.bytechen.hall.overworld.registry.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 热异常适应 Buff —— 通过外部手段（物品/环境等）获得，期间无法叠加热异常进度。
 */
public class HeatAnomalyAdaptationEffect extends MobEffect {
    public static final HeatAnomalyAdaptationEffect INSTANCE = new HeatAnomalyAdaptationEffect();

    private HeatAnomalyAdaptationEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF9944); // 浅橙红色
    }
}
