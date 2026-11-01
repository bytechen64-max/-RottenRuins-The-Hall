package org.bytechen.rottenruins.overworld.registry.entities.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

import java.util.EnumSet;
import java.util.List;

/**
 * 腐化系主动狩猎 Goal。
 *
 * <p>比 infcore 的 {@link org.bytechen.infcore.api.goal.InfectedTargetGoal} 更"积极"：</p>
 * <ul>
 *   <li>扫描间隔 0 tick（每 tick 扫描）（原版是 10 tick）；</li>
 *   <li>索敌半径 40 格（原版合并配置是 40 格）；</li>
 *   <li>不做视线检查，隔墙也能锁定；</li>
 *   <li>同阵营腐化生物不互殴，其余（王庭、动物、村民、玩家）一律可锁。</li>
 * </ul>
 */
public class CorruptionHuntGoal extends Goal {

    private static final int SCAN_INTERVAL = 0;

    private final Mob mob;
    private final double rangeSqr;
    private int scanCooldown = 0;

    public CorruptionHuntGoal(Mob mob, double range) {
        this.mob = mob;
        this.rangeSqr = range * range;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_INTERVAL;

        LivingEntity target = findNearestTarget();
        if (target == null) return false;

        mob.setTarget(target);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        return mob.distanceToSqr(target) <= rangeSqr;
    }

    private LivingEntity findNearestTarget() {
        Level level = mob.level();
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, mob.getBoundingBox().inflate(Math.sqrt(rangeSqr)), this::isValidTarget);

        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double dist = mob.distanceToSqr(candidate);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private boolean isValidTarget(LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive() || !candidate.isAttackable()) return false;
        if (!mob.canAttackType(candidate.getType())) return false;

        if (candidate instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        // 同阵营（腐化）不互殴；王庭 / 普通生物都可猎杀
        if (candidate instanceof IInfectedEntity infected
                && RottenFaction.isCorruption(infected.getInfectionType())) {
            return false;
        }
        return true;
    }
}
