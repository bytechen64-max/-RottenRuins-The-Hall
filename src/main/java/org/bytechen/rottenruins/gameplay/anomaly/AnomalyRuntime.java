package org.bytechen.rottenruins.gameplay.anomaly;

import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.api.anomaly.AnomalyAttack;
import org.bytechen.rottenruins.api.anomaly.AnomalyType;
import org.bytechen.rottenruins.core.anomaly.AnomalyState;

public final class AnomalyRuntime {
    public interface Services {
        AnomalyState<AnomalyType> state(LivingEntity entity);
        boolean addProgress(LivingEntity entity, AnomalyType type, int amount);
        AnomalyAttack attack(DamageSource source);
    }

    private static volatile Services services;
    private AnomalyRuntime() {}

    public static synchronized void install(Services value) {
        Objects.requireNonNull(value, "services");
        if (services != null) throw new IllegalStateException("Anomaly runtime already installed");
        services = value;
    }

    public static Services services() {
        Services value = services;
        if (value == null) throw new IllegalStateException("Anomaly runtime not installed");
        return value;
    }
}
