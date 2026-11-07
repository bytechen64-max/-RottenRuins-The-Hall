package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Aimed blink to a visible, collision-free spot with solid footing. */
public class ArcaneStep extends CMagicBaseItem {
    private static final double BASE_RANGE = 18.0d;
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2, -3, -4, -5};
    private static final double[] FOOT_HEIGHTS = {0.0d, 0.5d};

    public ArcaneStep() {
        super(defaultProperties(), MagicType.MYSTERIOUS, UsingType.IMMEDIATELY,
                40, 100, 0, 0, 0.0f);
    }

    @Override
    public double baseTargetRange() {
        return BASE_RANGE;
    }

    @Override
    protected void appendSpellHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
        tooltip.add(Component.translatable("item.rotten_ruins.arcane_step.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean canCast(@NotNull ServerPlayer caster, @NotNull ItemStack stack) {
        return !caster.isPassenger() && findDestination(caster, stack) != null;
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        Vec3 destination = findDestination(caster, stack);
        if (destination == null) return false;
        ServerLevel level = caster.serverLevel();
        Vec3 origin = caster.position();
        for (int i = 0; i <= 18; i++) {
            Vec3 point = origin.add(destination.subtract(origin).scale(i / 18.0d));
            level.sendParticles(ParticleTypes.PORTAL, point.x, point.y + 1.0d, point.z,
                    2, 0.12d, 0.3d, 0.12d, 0.02d);
        }
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.5f);
        caster.teleportTo(destination.x, destination.y, destination.z);
        caster.resetFallDistance();
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                destination.x, destination.y + 1.0d, destination.z,
                60, 0.45d, 0.9d, 0.45d, 0.08d);
        level.playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.85f, 1.1f);
        return true;
    }

    @Nullable
    private static Vec3 findDestination(ServerPlayer caster, ItemStack stack) {
        ServerLevel level = caster.serverLevel();
        Vec3 eye = caster.getEyePosition();
        double range = Math.min(MagicStats.targetRange(caster, stack), 32.0d);
        if (range < 2.0d) return null;
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        HitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        BlockPos anchor;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            Direction face = blockHit.getDirection();
            anchor = blockHit.getBlockPos().relative(face);
        } else {
            anchor = BlockPos.containing(end.x, end.y - caster.getEyeHeight(), end.z);
        }
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy : HEIGHT_OFFSETS) {
                    BlockPos feet = anchor.offset(dx, dy, dz);
                    if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) continue;
                    for (double halfStep : FOOT_HEIGHTS) {
                        Vec3 candidate = new Vec3(feet.getX() + 0.5d,
                                feet.getY() + halfStep, feet.getZ() + 0.5d);
                        if (candidate.distanceToSqr(caster.position()) < 4.0d
                                || candidate.distanceToSqr(caster.position()) > (range + 1.0d) * (range + 1.0d)) continue;
                        AABB box = caster.getBoundingBox().move(candidate.subtract(caster.position()));
                        if (!level.noCollision(caster, box)
                                || level.noCollision(caster, box.move(0.0d, -0.25d, 0.0d))) continue;
                        Vec3 targetEye = candidate.add(0.0d, caster.getEyeHeight(), 0.0d);
                        HitResult sight = level.clip(new ClipContext(eye, targetEye,
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                        if (sight.getType() != HitResult.Type.MISS) continue;
                        double score = candidate.distanceToSqr(hit.getLocation().subtract(0.0d, caster.getEyeHeight(), 0.0d));
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }
}
