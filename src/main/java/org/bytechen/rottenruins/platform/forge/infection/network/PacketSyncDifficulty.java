package org.bytechen.rottenruins.platform.forge.infection.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * S2C packet: syncs the global difficulty to all clients.
 * <p>
 * Sent every time the difficulty changes. Simpler than
 * {@link PacketSyncWorldData} because there is only one global value
 * (no dimension or entity qualifier needed).
 * <p>
 * Client handler is injected via {@link #CLIENT_HANDLER} during
 * client setup.
 */
public class PacketSyncDifficulty {

    private final ResourceLocation difficulty;

    /**
     * Client-side handler. Injected during {@code FMLClientSetupEvent}.
     * Always {@code null} on a dedicated server.
     */
    public static BiConsumer<PacketSyncDifficulty, NetworkEvent.Context> CLIENT_HANDLER = null;

    public PacketSyncDifficulty(ResourceLocation difficulty) {
        this.difficulty = difficulty;
    }

    public ResourceLocation getDifficulty() {
        return difficulty;
    }

    public static void encode(PacketSyncDifficulty packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.difficulty);
    }

    public static PacketSyncDifficulty decode(FriendlyByteBuf buf) {
        return new PacketSyncDifficulty(buf.readResourceLocation());
    }

    public static void handle(PacketSyncDifficulty packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (CLIENT_HANDLER != null) {
                CLIENT_HANDLER.accept(packet, context);
            }
        });
        context.setPacketHandled(true);
    }
}
