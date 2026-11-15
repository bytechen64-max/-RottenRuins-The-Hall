package org.bytechen.rottenruins.platform.forge.anomaly;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/** Owned by one player's capability; drained after vanilla damage finishes, never stored globally. */
public final class AnomalyDamageInbox {
    private final List<LivingDamageEvent> pending = new ArrayList<>();

    public void add(LivingDamageEvent event) { pending.add(event); }

    public List<LivingDamageEvent> drain() {
        if (pending.isEmpty()) return List.of();
        List<LivingDamageEvent> result = new ArrayList<>(pending);
        pending.clear();
        return result;
    }
}
