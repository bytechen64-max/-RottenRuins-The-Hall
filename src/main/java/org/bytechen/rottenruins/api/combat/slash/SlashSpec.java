package org.bytechen.rottenruins.api.combat.slash;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import java.util.List;
import java.util.Objects;

/** Immutable per-swing snapshot. Obtain one through builder(), optionally per pose/player. */
public record SlashSpec(double reach, double innerReach, double arcDegrees, double halfThickness,
                        int cooldownTicks, int animationTicks, float impactProgress, int comboResetTicks,
                        float damageMultiplier, float knockback, int maxTargets, int durabilityCost,
                        boolean hitAllies, boolean requireLineOfSight, List<SlashPose> poses,
                        SoundEvent swingSound, SoundEvent hitSound, float volume, float pitch, SlashTrail trail) {
    public SlashSpec {
        poses = List.copyOf(poses); Objects.requireNonNull(trail);
        if (!Double.isFinite(reach + innerReach + arcDegrees + halfThickness) || reach <= 0 || reach > 32
                || innerReach < 0 || innerReach >= reach || arcDegrees <= 0 || arcDegrees > 360
                || halfThickness <= 0 || halfThickness > 16 || cooldownTicks < 1 || cooldownTicks > 12000
                || animationTicks < 2 || animationTicks > 200 || !Float.isFinite(impactProgress)
                || impactProgress <= 0 || impactProgress >= 1 || comboResetTicks < 1 || comboResetTicks > 12000
                || !Float.isFinite(damageMultiplier + knockback + volume + pitch) || damageMultiplier < 0
                || knockback < 0 || knockback > 10 || volume < 0 || volume > 16 || pitch <= 0 || pitch > 4
                || maxTargets < 1 || maxTargets > 128 || durabilityCost < 0 || durabilityCost > 100
                || poses.isEmpty() || poses.size() > 32) throw new IllegalArgumentException("Invalid slash specification");
    }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private double reach = 3, inner = 0, angle = 116, thickness = 0.9;
        private int cooldown = 12, animation = 10, reset = 30, targets = 16, durability = 1;
        private float impact = 0.47F, damage = 1, knockback = 0.3F, volume = 0.8F, pitch = 1;
        private boolean allies, sight = true;
        private List<SlashPose> poses = List.of(SlashPose.values());
        private SoundEvent swing = SoundEvents.PLAYER_ATTACK_SWEEP, hit = SoundEvents.PLAYER_ATTACK_STRONG;
        private SlashTrail trail = SlashTrail.DISABLED;
        public Builder range(double reach, double inner, double angle, double halfThickness) {
            this.reach = reach; this.inner = inner; this.angle = angle; this.thickness = halfThickness; return this;
        }
        public Builder timing(int cooldown, int animation, float impact) { this.cooldown = cooldown; this.animation = animation; this.impact = impact; return this; }
        public Builder combo(int resetTicks, SlashPose... poses) { reset = resetTicks; this.poses = List.of(poses); return this; }
        public Builder damage(float multiplier, float knockback, int maxTargets, int durabilityCost) {
            damage = multiplier; this.knockback = knockback; targets = maxTargets; durability = durabilityCost; return this;
        }
        public Builder targeting(boolean allies, boolean lineOfSight) { this.allies = allies; sight = lineOfSight; return this; }
        public Builder sounds(SoundEvent swing, SoundEvent hit, float volume, float pitch) { this.swing = swing; this.hit = hit; this.volume = volume; this.pitch = pitch; return this; }
        public Builder trail(SlashTrail trail) { this.trail = trail; return this; }
        public SlashSpec build() { return new SlashSpec(reach, inner, angle, thickness, cooldown, animation, impact,
                reset, damage, knockback, targets, durability, allies, sight, poses, swing, hit, volume, pitch, trail); }
    }
}
