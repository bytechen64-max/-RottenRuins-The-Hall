package org.bytechen.hall.overworld.registry.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 冷异常适应 Buff —— 通过外部手段（物品/环境等）获得，期间无法叠加冷异常进度。
 */
public class ColdAnomalyAdaptationEffect extends MobEffect {
    public static final ColdAnomalyAdaptationEffect INSTANCE = new ColdAnomalyAdaptationEffect();

    private ColdAnomalyAdaptationEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x88DDFF); // 浅冰蓝色
    }
}
