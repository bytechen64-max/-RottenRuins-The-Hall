package org.bytechen.hall.api.anomaly;

import net.minecraft.world.effect.MobEffect;
import org.bytechen.hall.overworld.registry.effect.AcidAnomalyAdaptationEffect;
import org.bytechen.hall.overworld.registry.effect.ColdAnomalyAdaptationEffect;
import org.bytechen.hall.overworld.registry.effect.HeatAnomalyAdaptationEffect;

/**
 * 异常类型枚举 —— 定义所有异常种类。
 * 新增异常类型只需添加枚举常量并扩展 {@link #getAdaptationEffect} 即可。
 */
public enum AnomalyType {
    HEAT("heat", 10),
    COLD("cold", 10),
    ACID("acid", 10);

    private final String id;
    private final int maxProgress;

    AnomalyType(String id, int maxProgress) {
        this.id = id;
        this.maxProgress = maxProgress;
    }

    public String getId() {
        return id;
    }

    /** 异常进度上限（满层触发阈值） */
    public int getMaxProgress() {
        return maxProgress;
    }

    /** 叠满后给予的适应 Buff（15s） */
    public MobEffect getAdaptationEffect() {
        return switch (this) {
            case HEAT -> HeatAnomalyAdaptationEffect.INSTANCE;
            case COLD -> ColdAnomalyAdaptationEffect.INSTANCE;
            case ACID -> AcidAnomalyAdaptationEffect.INSTANCE;
        };
    }

    /** 适应 Buff 时长（tick），默认 15s = 300 tick */
    public int getAdaptationDurationTicks() {
        return 300;
    }
}
