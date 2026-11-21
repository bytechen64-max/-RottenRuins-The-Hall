package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.content.magic.effect.FlameSlashEffect;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.jetbrains.annotations.NotNull;

/** 焰斩：获得 3 + 法强 次挥剑附带红色剑气。 */
public class FlameSlash extends CMagicBaseItem {

    private static final int BASE_CHARGES = 3;
    private static final int DURATION = 1200;

    public FlameSlash() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.IMMEDIATELY, MagicType.FIRE)
                .manaCost(100)
                .cooldownTicks(600)
                .build());
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        double power = MagicStats.spellPower(caster, stack);
        int charges = BASE_CHARGES + (int) Math.floor(power);
        FlameSlashEffect.grant(caster, charges, DURATION);

        ServerLevel level = caster.serverLevel();
        level.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 1.0f, 1.1f);
        return true;
    }
}
