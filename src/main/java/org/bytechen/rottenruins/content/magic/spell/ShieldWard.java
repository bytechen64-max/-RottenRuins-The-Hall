package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.content.magic.effect.ShieldEffect;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.jetbrains.annotations.NotNull;

/** 护盾术：蓄力 1 秒后获得能抵挡固定次数的护盾。 */
public class ShieldWard extends CMagicBaseItem {

    private static final int BASE_CHARGES = 10;
    private static final int CHARGES_PER_POWER = 3;
    private static final int DURATION = 600;

    public ShieldWard() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.CHARGE, MagicType.NATURE)
                .manaCost(150)
                .cooldownTicks(1800)
                .chargeTicks(20)
                .minChargeRatio(1.0f)
                .build());
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        double power = MagicStats.spellPower(caster, stack);
        int charges = BASE_CHARGES + (int) Math.floor(CHARGES_PER_POWER * power);
        ShieldEffect.grant(caster, charges, DURATION);

        ServerLevel level = caster.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                caster.getX(), caster.getY() + 1.0d, caster.getZ(),
                40, 0.9d, 1.0d, 0.9d, 0.0d);
        level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }
}
