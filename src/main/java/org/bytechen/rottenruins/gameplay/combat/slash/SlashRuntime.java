package org.bytechen.rottenruins.gameplay.combat.slash;

import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.api.combat.slash.*;

/** Loader hooks and visual delivery for the server combat engine. */
public final class SlashRuntime {
    public record Hit(float damage, boolean critical, float multiplier) {}
    public interface Services {
        boolean allowAttack(SlashContext context, LivingEntity target);
        Hit critical(SlashContext context, ISlashWeapon weapon, LivingEntity target, boolean eligible);
        void broadcast(SlashContext context, int sequence, boolean cancelled);
    }
    private static volatile Services services;
    private SlashRuntime() {}
    public static synchronized void install(Services value) {
        java.util.Objects.requireNonNull(value);
        if (services != null) throw new IllegalStateException("Slash runtime already installed");
        services = value;
    }
    public static Services services() {
        if (services == null) throw new IllegalStateException("Slash runtime not installed");
        return services;
    }
}
