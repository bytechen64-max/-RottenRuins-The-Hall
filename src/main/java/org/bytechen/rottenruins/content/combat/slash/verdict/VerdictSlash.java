package org.bytechen.rottenruins.content.combat.slash.verdict;
import org.bytechen.rottenruins.api.combat.slash.*;
import net.minecraft.sounds.SoundEvents;
public final class VerdictSlash {
    public static final SlashSpec SPEC=SlashSpec.builder().range(4.3,0,132,1.1)
            .timing(9,8,.42F).damage(1,.4F,20,1)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP,SoundEvents.PLAYER_ATTACK_STRONG,1,1.1F)
            .trail(new SlashTrail(SlashTrail.SHADER,SlashTrail.DEFAULT_TEXTURE,0xFFDAECFF,.7F,2.9F,9)).build();
    private VerdictSlash(){}
}
