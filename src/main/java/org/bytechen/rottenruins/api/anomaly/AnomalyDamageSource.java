package org.bytechen.rottenruins.api.anomaly;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;

/** Adds anomaly intent without changing damage type, position or original kill attribution. */
public final class AnomalyDamageSource extends DamageSource {
    private final UUID attacker;
    private final AnomalyAttack attack;

    public AnomalyDamageSource(DamageSource source, UUID attacker, AnomalyAttack attack) {
        super(source.typeHolder(), source.getDirectEntity(), source.getEntity(), source.sourcePositionRaw());
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.attack = Objects.requireNonNull(attack, "attack");
    }

    public UUID attacker() { return attacker; }
    public AnomalyAttack attack() { return attack; }
}
