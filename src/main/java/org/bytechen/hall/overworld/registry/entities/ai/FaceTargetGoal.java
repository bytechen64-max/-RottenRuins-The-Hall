package org.bytechen.hall.overworld.registry.entities.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class FaceTargetGoal extends Goal {

    private final PathfinderMob mob;
    private final double maxDistance;
    private final float headTurnSpeed;
    private final float bodyTurnSpeed;

    public FaceTargetGoal(PathfinderMob mob, double maxDistance) {
        this(mob, maxDistance, 10f, 3f);
    }

    public FaceTargetGoal(PathfinderMob mob, double maxDistance, float headTurnSpeed, float bodyTurnSpeed) {
        this.mob = mob;
        this.maxDistance = maxDistance;
        this.headTurnSpeed = headTurnSpeed;
        this.bodyTurnSpeed = bodyTurnSpeed;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) <= this.maxDistance * this.maxDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        double dx = target.getX() - this.mob.getX();
        double dy = target.getEyeY() - this.mob.getEyeY();
        double dz = target.getZ() - this.mob.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = (float) (-(Mth.atan2(dy, horizontalDist) * (180.0 / Math.PI)));

        // Head yaw
        float headYaw = this.mob.getYHeadRot();
        float headDiff = Mth.wrapDegrees(targetYaw - headYaw);
        float headStep = Mth.clamp(headDiff, -this.headTurnSpeed, this.headTurnSpeed);
        this.mob.setYHeadRot(headYaw + headStep);

        // Head pitch
        float headPitch = this.mob.getXRot();
        float pitchDiff = Mth.wrapDegrees(targetPitch - headPitch);
        float pitchStep = Mth.clamp(pitchDiff, -this.headTurnSpeed * 0.5f, this.headTurnSpeed * 0.5f);
        this.mob.setXRot(headPitch + pitchStep);

        // Body yaw (slowly follows head)
        float bodyDiff = Mth.wrapDegrees(this.mob.getYHeadRot() - this.mob.yBodyRot);
        float bodyStep = Mth.clamp(bodyDiff, -this.bodyTurnSpeed, this.bodyTurnSpeed);
        this.mob.yBodyRot += bodyStep;
    }
}
