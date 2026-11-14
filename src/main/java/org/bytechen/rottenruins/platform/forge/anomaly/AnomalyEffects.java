package org.bytechen.rottenruins.platform.forge.anomaly;

import net.minecraft.world.effect.MobEffect;
import org.bytechen.rottenruins.api.anomaly.AnomalyType;
import org.bytechen.rottenruins.overworld.registry.effect.AcidAnomalyAdaptationEffect;
import org.bytechen.rottenruins.overworld.registry.effect.ColdAnomalyAdaptationEffect;
import org.bytechen.rottenruins.overworld.registry.effect.HeatAnomalyAdaptationEffect;

public final class AnomalyEffects {
    private AnomalyEffects() {}
    public static MobEffect adaptation(AnomalyType type) {
        return switch (type) {
            case HEAT -> HeatAnomalyAdaptationEffect.INSTANCE;
            case COLD -> ColdAnomalyAdaptationEffect.INSTANCE;
            case ACID -> AcidAnomalyAdaptationEffect.INSTANCE;
        };
    }
}
