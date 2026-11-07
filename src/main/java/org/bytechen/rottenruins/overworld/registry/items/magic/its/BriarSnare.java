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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Entangles creatures around the point under the crosshair. */
public class BriarSnare extends CMagicBaseItem {
    private static final DustParticleOptions BRIAR_DUST =
            new DustParticleOptions(new Vector3f(0.22f, 0.65f, 0.18f), 1.0f);
    private static final double RADIUS = 4.5d;

    public BriarSnare() {
        super(defaultProperties(), MagicType.NATURE, UsingType.IMMEDIATELY,
                50, 140, 0, 0, 7.0f);
    }

    @Override
    protected void appendSpellHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
        tooltip.add(Component.translatable("item.rotten_ruins.briar_snare.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public double baseTargetRange() {
        return 28.0d;
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        ServerLevel level = caster.serverLevel();
        Vec3 center = MagicHandle.resolveAimPoint(caster, MagicStats.targetRange(caster, stack));
        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, RADIUS)) {
            if (!victim.isAlive()) continue;
            float damage = MagicStats.spellDamage(caster, stack, victim, null, baseDamage());
            SpellDamageUtil.hurt(victim, stack, caster, damage);
            if (victim.isAlive()) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI * 2.0d / 8.0d;
                    level.sendParticles(BRIAR_DUST,
                            victim.getX() + Math.cos(angle) * 0.7d,
                            victim.getY() + 0.2d + i * 0.13d,
                            victim.getZ() + Math.sin(angle) * 0.7d,
                            1, 0.02d, 0.02d, 0.02d, 0.0d);
                }
            }
        }
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI * 2.0d / 32.0d;
            level.sendParticles(BRIAR_DUST,
                    center.x + Math.cos(angle) * RADIUS, center.y + 0.2d + (i % 4) * 0.2d,
                    center.z + Math.sin(angle) * RADIUS,
                    2, 0.1d, 0.15d, 0.1d, 0.0d);
        }
        level.sendParticles(ParticleTypes.COMPOSTER, center.x, center.y + 0.4d, center.z,
                70, RADIUS * 0.55d, 0.6d, RADIUS * 0.55d, 0.06d);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GRASS_BREAK,
                SoundSource.PLAYERS, 1.2f, 0.55f);
        return true;
    }
}
