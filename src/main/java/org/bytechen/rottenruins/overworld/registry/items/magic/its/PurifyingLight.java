package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Restores and cleanses allies while striking hostile creatures, especially undead. */
public class PurifyingLight extends CMagicBaseItem {
    private static final double RADIUS = 7.0d;

    public PurifyingLight() {
        super(defaultProperties(), MagicType.LIGHT, UsingType.IMMEDIATELY,
                60, 200, 0, 0, 8.0f);
    }

    @Override
    protected void appendSpellHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
        tooltip.add(Component.translatable("item.rotten_ruins.purifying_light.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        ServerLevel level = caster.serverLevel();
        Vec3 center = caster.getBoundingBox().getCenter();
        float healing = (float) Mth.clamp(8.0d * MagicStats.spellPower(caster, stack), 2.0d, 24.0d);
        restore(caster, healing);
        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, RADIUS)) {
            if (!victim.isAlive()) continue;
            if (isAlly(caster, victim)) {
                restore(victim, healing);
                level.sendParticles(ParticleTypes.END_ROD,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5d, victim.getZ(),
                        16, 0.4d, 0.7d, 0.4d, 0.02d);
            } else if (victim instanceof Monster || victim.getMobType() == MobType.UNDEAD) {
                boolean undead = victim.getMobType() == MobType.UNDEAD;
                float base = undead ? baseDamage() * 1.75f : baseDamage();
                float damage = MagicStats.spellDamage(caster, stack, victim, null, base);
                SpellDamageUtil.hurt(victim, stack, caster, damage);
                level.sendParticles(undead ? ParticleTypes.FLASH : ParticleTypes.END_ROD,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5d, victim.getZ(),
                        undead ? 1 : 10, 0.3d, 0.5d, 0.3d, 0.0d);
            }
        }
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI * 2.0d / 32.0d;
            double x = center.x + Math.cos(angle) * RADIUS;
            double z = center.z + Math.sin(angle) * RADIUS;
            level.sendParticles(ParticleTypes.END_ROD, x, caster.getY() + 0.3d, z,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.FIREWORK, x, caster.getY() + 1.6d, z,
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                100, RADIUS * 0.45d, 1.2d, RADIUS * 0.45d, 0.035d);
        level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0f, 1.25f);
        return true;
    }

    private static boolean isAlly(ServerPlayer caster, LivingEntity target) {
        if (target instanceof Player player) {
            // Players without teams can still cooperate on ordinary survival servers.
            return (caster.getTeam() == null && player.getTeam() == null)
                    || caster.isAlliedTo(player)
                    || !caster.canHarmPlayer(player);
        }
        return caster.isAlliedTo(target)
                || (target instanceof TamableAnimal pet && pet.isOwnedBy(caster));
    }

    private static void restore(LivingEntity target, float healing) {
        target.heal(healing);
        for (MobEffectInstance effect : List.copyOf(target.getActiveEffects())) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                target.removeEffect(effect.getEffect());
            }
        }
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
    }
}
