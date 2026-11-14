package org.bytechen.rottenruins.gameplay.infection;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.bytechen.rottenruins.api.infection.event.BlockSpreadEvent;
import org.bytechen.rottenruins.api.infection.event.EntityEvolveEvent;

/** Platform operations needed by infection execution, bound during mod construction. */
public final class InfectionRuntime {
    public interface Services {
        ResourceLocation entityId(EntityType<?> type);
        EntityType<?> entity(ResourceLocation id);
        ResourceLocation blockId(Block block);
        Block block(ResourceLocation id);
        boolean beforeSpread(BlockSpreadEvent.Pre event);
        void afterSpread(BlockSpreadEvent.Post event);
        void afterEvolution(EntityEvolveEvent event);
        ResourceLocation difficulty(ServerLevel level);
        boolean changeDifficulty(ServerLevel level, ResourceLocation difficulty);
    }
    private static volatile Services services;
    private InfectionRuntime() {}
    public static synchronized void install(Services value) {
        Objects.requireNonNull(value, "services");
        if (services != null) throw new IllegalStateException("Infection runtime already installed");
        services = value;
    }
    public static Services services() {
        Services value = services;
        if (value == null) throw new IllegalStateException("Infection runtime not installed");
        return value;
    }
}
