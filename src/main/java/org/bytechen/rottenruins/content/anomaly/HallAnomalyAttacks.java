package org.bytechen.rottenruins.content.anomaly;

import java.util.Map;
import org.bytechen.rottenruins.api.anomaly.AnomalyAttack;
import org.bytechen.rottenruins.api.anomaly.AnomalyType;

/** Hall content declarations. Registry objects are resolved by the platform at use time. */
public final class HallAnomalyAttacks {
    public static final AnomalyAttack HEAT = new AnomalyAttack(AnomalyType.HEAT, 1, 0);
    public static final AnomalyAttack COLD = new AnomalyAttack(AnomalyType.COLD, 1, 0);
    public static final AnomalyAttack ACID = new AnomalyAttack(AnomalyType.ACID, 1, 0);
    public static final AnomalyAttack HEAT_HEAVY = new AnomalyAttack(AnomalyType.HEAT, 2, 20);
    public static final AnomalyAttack COLD_HEAVY = new AnomalyAttack(AnomalyType.COLD, 2, 20);
    public static final AnomalyAttack ACID_DRAIN = new AnomalyAttack(AnomalyType.ACID, 1, 20);
    public static final AnomalyAttack GHOST = new AnomalyAttack(AnomalyType.COLD, 1, 20);
    public static final AnomalyAttack BLACK_HOLE = new AnomalyAttack(AnomalyType.COLD, 1, 20);
    public static final AnomalyAttack METEOR = new AnomalyAttack(AnomalyType.HEAT, 1, 20);

    private static final Map<String, AnomalyAttack> MOBS = Map.ofEntries(
            Map.entry("hall_player", HEAT), Map.entry("hall_enderman", COLD),
            Map.entry("hall_skeleton", COLD), Map.entry("hall_skeleton_arrow", COLD_HEAVY),
            Map.entry("scout", ACID), Map.entry("pursuer", ACID),
            Map.entry("abnormal_squid", ACID), Map.entry("monolith", COLD_HEAVY),
            Map.entry("bonecrusher", HEAT_HEAVY), Map.entry("rampart", COLD_HEAVY),
            Map.entry("heavy_bomb", HEAT_HEAVY), Map.entry("heavy_bomb_tnt", HEAT_HEAVY),
            Map.entry("winding_column", ACID_DRAIN), Map.entry("hearthstone", HEAT_HEAVY),
            Map.entry("collapsar", COLD_HEAVY), Map.entry("meteor_event", HEAT_HEAVY));

    private HallAnomalyAttacks() {}

    public static AnomalyAttack find(String namespace, String path, boolean explosion) {
        if (!"rotten_ruins".equals(namespace)) return null;
        AnomalyAttack attack = MOBS.get(path);
        if (attack == null || !explosion || attack.amount() >= 2) return attack;
        return new AnomalyAttack(attack.type(), 2, 20);
    }
}
