package org.bytechen.rottenruins.platform.forge.combat.slash;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.bytechen.rottenruins.gameplay.combat.slash.SlashRuntime.Hit;
import org.bytechen.rottenruins.api.combat.slash.*;

public final class SlashCriticals {
    private SlashCriticals() {}
    /** Independent slash timing: allow sprint jump cuts and a small rising-side apex window. */
    public static Hit resolve(SlashContext context, ISlashWeapon weapon, LivingEntity target, boolean vanillaEligible) {
        float normal = weapon.slashDamage(context, target);
        if (!weapon.allowsSlashCriticals(context)) return new Hit(normal, false, 1);
        float defaultMultiplier = vanillaEligible ? weapon.slashCriticalMultiplier(context, target) : 1;
        var event = ForgeHooks.getCriticalHit(context.player(), target, vanillaEligible, defaultMultiplier);
        if (event == null) return new Hit(normal, false, 1);
        float multiplier = event.getDamageModifier();
        if (!Float.isFinite(multiplier) || multiplier < 0) return new Hit(normal, false, 1);
        return new Hit(weapon.slashCriticalDamage(context, target, normal, multiplier), true, multiplier);
    }
}
