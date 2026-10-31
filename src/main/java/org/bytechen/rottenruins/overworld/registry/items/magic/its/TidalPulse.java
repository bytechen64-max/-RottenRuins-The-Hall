package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Defensive water wave: displacement in combat and temporary underwater mobility. */
public class TidalPulse extends CMagicBaseItem {
    private static final DustParticleOptions WATER_DUST =
            new DustParticleOptions(new Vector3f(0.17f, 0.60f, 0.94f), 1.0f);
    private static final double RADIUS = 6.5d;

    public TidalPulse() {
        super(defaultProperties(), MagicType.WATER, UsingType.IMMEDIATELY,
                40, 100, 0, 0, 9.0f);
    }

    @Override
    protected void appendSpellHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
        tooltip.add(Component.translatable("item.rotten_ruins.tidal_pulse.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        ServerLevel level = caster.serverLevel();
        Vec3 center = caster.getBoundingBox().getCenter();
        caster.clearFire();
        caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0));
        caster.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 240, 0));
        caster.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 160, 0));
        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, RADIUS)) {
            if (!victim.isAlive()) continue;
            float damage = MagicStats.spellDamage(caster, stack, victim, null, baseDamage());
            SpellDamageUtil.hurt(victim, stack, caster, damage);
            Vec3 away = victim.position().subtract(caster.position());
            Vec3 horizontal = new Vec3(away.x, 0.0d, away.z);
            if (horizontal.lengthSqr() < 1.0e-4d) horizontal = caster.getLookAngle().multiply(1, 0, 1);
            if (horizontal.lengthSqr() > 1.0e-4d) {
                double resistance = Math.min(1.0d, victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double strength = Math.max(0.0d, 1.35d - 0.07d * Math.sqrt(away.lengthSqr())) * (1.0d - resistance);
                Vec3 push = horizontal.normalize().scale(strength);
                victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, 0.25d * (1.0d - resistance), push.z));
                victim.hurtMarked = true;
            }
        }
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2.0d / 28.0d;
            double x = Math.cos(angle);
            double z = Math.sin(angle);
            level.sendParticles(WATER_DUST,
                    caster.getX() + x * RADIUS, caster.getY() + 0.45d,
                    caster.getZ() + z * RADIUS,
                    2, 0.10d, 0.35d, 0.10d, 0.0d);
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    caster.getX() + x * (RADIUS * 0.6d), caster.getY() + 0.8d,
                    caster.getZ() + z * (RADIUS * 0.6d),
                    2, 0.12d, 0.3d, 0.12d, 0.01d);
        }
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z,
                100, RADIUS * 0.55d, 0.7d, RADIUS * 0.55d, 0.12d);
        level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_SPLASH,
                SoundSource.PLAYERS, 1.2f, 0.65f);
        level.playSound(null, caster.blockPosition(), SoundEvents.CONDUIT_ACTIVATE,
                SoundSource.PLAYERS, 0.45f, 1.4f);
        return true;
    }
}
