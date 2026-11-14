package org.bytechen.rottenruins.content.combat.slash.domerite;

import net.minecraft.sounds.SoundEvents;
import org.bytechen.rottenruins.api.combat.slash.*;

/** Procedural blue-white trail. The weapon's dynamic attack attribute still supplies base damage. */
public final class DomeriteSlash {
    public static final SlashSpec SPEC = SlashSpec.builder().range(3.8, 0, 128, 1.05)
            .timing(14, 12, 0.47F).damage(1, 0.4F, 16, 1)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 0.85F)
            .trail(new SlashTrail(SlashTrail.SHADER, SlashTrail.DEFAULT_TEXTURE, 0xFFA8D9FF, 0.62F, 2.5F, 9)).build();
    private DomeriteSlash() {}
}
