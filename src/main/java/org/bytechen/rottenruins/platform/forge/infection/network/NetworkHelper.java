package org.bytechen.rottenruins.platform.forge.infection.network;

import org.bytechen.rottenruins.core.infection.InfectionIds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通信辅助类。
 * <p>
 * 管理 Forge SimpleChannel，提供方便的包发送方法。
 */
public final class NetworkHelper {

    private static int packetId = 0;

    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(InfectionIds.NAMESPACE, "main"),
            () -> "1.0",
            s -> true,
            s -> true
    );

    private NetworkHelper() {}

    /**
     * 注册所有网络包。在模组构造时调用。
     */
    public static void register() {
        NETWORK.registerMessage(packetId++, PacketSyncCapability.class,
                PacketSyncCapability::encode, PacketSyncCapability::decode, PacketSyncCapability::handle);
        NETWORK.registerMessage(packetId++, PacketSyncWorldData.class,
                PacketSyncWorldData::encode, PacketSyncWorldData::decode, PacketSyncWorldData::handle);
        NETWORK.registerMessage(packetId++, PacketSyncDifficulty.class,
                PacketSyncDifficulty::encode, PacketSyncDifficulty::decode, PacketSyncDifficulty::handle);
    }

    /**
     * 发送包到指定玩家。
     */
    public static <MSG> void sendToPlayer(ServerPlayer player, MSG msg) {
        NETWORK.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /**
     * 发送包到所有客户端。
     */
    public static <MSG> void sendToAllClients(MSG msg) {
        NETWORK.send(PacketDistributor.ALL.noArg(), msg);
    }

    /**
     * 发送包到追踪指定实体的所有客户端。
     */
    public static <MSG> void sendToClient(Entity entity, MSG msg) {
        NETWORK.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg);
    }

    /**
     * 发送包到服务端。
     */
    public static <MSG> void sendToServer(MSG msg) {
        NETWORK.sendToServer(msg);
    }
}
