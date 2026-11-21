package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.jetbrains.annotations.NotNull;

/** 圣翼：30 × (法强 / 4 + 1) 秒的鞘翅飞行，空中再按跳跃可冲刺。 */
public class HolyWings extends CMagicBaseItem {

    private static final int BASE_SECONDS = 30;

    public HolyWings() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.IMMEDIATELY, MagicType.LIGHT)
                .manaCost(400)
                .cooldownTicks(2400)
                .build());
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        double power = MagicStats.spellPower(caster, stack);
        int ticks = (int) (BASE_SECONDS * 20 * (power / 4.0d + 1.0d));
        CustomEffects.addEffect(caster, RegisterCustomEffect.HOLY_WINGS, ticks, 0);

        ServerLevel level = caster.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD,
                caster.getX(), caster.getY() + 1.2d, caster.getZ(),
                30, 0.6d, 0.8d, 0.6d, 0.05d);
        level.playSound(null, caster.blockPosition(), SoundEvents.ELYTRA_FLYING,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }
}
