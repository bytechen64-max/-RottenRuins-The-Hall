package org.bytechen.rottenruins.platform.forge.combat.slash;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.bytechen.rottenruins.api.combat.slash.*;
import org.bytechen.rottenruins.gameplay.combat.slash.SlashRuntime;

public final class ForgeSlashServices implements SlashRuntime.Services {
    private static final ThreadLocal<Boolean> RESOLVING_TARGET = ThreadLocal.withInitial(() -> false);
    static boolean resolvingTarget() { return RESOLVING_TARGET.get(); }
    @Override public boolean allowAttack(SlashContext context, LivingEntity target) {
        RESOLVING_TARGET.set(true);
        try { return ForgeHooks.onPlayerAttackTarget(context.player(), target); }
        finally { RESOLVING_TARGET.remove(); }
    }
    @Override public SlashRuntime.Hit critical(SlashContext context, ISlashWeapon weapon, LivingEntity target, boolean eligible) {
        return SlashCriticals.resolve(context, weapon, target, eligible);
    }
    @Override public void broadcast(SlashContext c, int sequence, boolean cancelled) {
        var s = c.spec();
        SlashNetwork.broadcast(c.player(), new SlashNetwork.Visual(c.player().getId(), sequence, cancelled, c.pose(), c.comboIndex(),
                s.animationTicks(), s.impactProgress(), (float)s.arcDegrees(), c.stack().copy(), s.trail(), c.forward(), c.cutDirection()));
    }
}
