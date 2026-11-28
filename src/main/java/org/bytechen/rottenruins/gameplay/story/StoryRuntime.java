package org.bytechen.rottenruins.gameplay.story;

import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.core.story.StoryMemory;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/** Loader-independent transport boundary, installed by the platform at mod construction. */
public final class StoryRuntime {
    public interface Services {
        void send(ServerPlayer player, StoryMessage message);
        StoryMemory loadMemory(Player player);
        void saveMemory(Player player, StoryMemory memory);
        void copyMemory(Player original, Player replacement, Set<String> currentOnceKeys);
    }
    private static volatile Services services;
    private StoryRuntime() {}
    public static synchronized void install(Services value) {
        if (services != null) throw new IllegalStateException("Story runtime already installed");
        services = java.util.Objects.requireNonNull(value);
    }
    public static Services services() {
        if (services == null) throw new IllegalStateException("Story runtime is not installed");
        return services;
    }
}
