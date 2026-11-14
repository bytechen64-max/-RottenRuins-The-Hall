package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Loader-independent transport boundary, installed by the platform at mod construction. */
public final class StoryRuntime {
    public interface Services { void send(ServerPlayer player, CompoundTag payload); }
    private static Services services;
    private StoryRuntime() {}
    public static void install(Services value) {
        if (services != null) throw new IllegalStateException("Story runtime already installed");
        services = java.util.Objects.requireNonNull(value);
    }
    public static Services services() {
        if (services == null) throw new IllegalStateException("Story runtime is not installed");
        return services;
    }
}
