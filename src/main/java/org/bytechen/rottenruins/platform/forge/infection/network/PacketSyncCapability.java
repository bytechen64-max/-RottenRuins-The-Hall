package org.bytechen.rottenruins.platform.forge.infection.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * S2C 能力数据同步包。
 * <p>
 * 服务端将实体能力数据同步到追踪该实体的所有客户端。
 * 客户端处理器通过 {@link #CLIENT_HANDLER} 静态字段注入。
 */
public class PacketSyncCapability {

    private final int entityId;
    private final ResourceLocation capabilityId;
    private final CompoundTag syncData;

    /**
     * 客户端处理器。在客户端设置阶段注入。
     * 专用服务端上为 null，handle 方法会安全跳过。
     */
    public static BiConsumer<PacketSyncCapability, NetworkEvent.Context> CLIENT_HANDLER = null;

    public PacketSyncCapability(int entityId, ResourceLocation capabilityId, CompoundTag syncData) {
        this.entityId = entityId;
        this.capabilityId = capabilityId;
        this.syncData = syncData;
    }

    public int getEntityId() { return entityId; }
    public ResourceLocation getCapabilityId() { return capabilityId; }
    public CompoundTag getSyncData() { return syncData; }

    public static void encode(PacketSyncCapability packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        buf.writeResourceLocation(packet.capabilityId);
        buf.writeNbt(packet.syncData);
    }

    public static PacketSyncCapability decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        ResourceLocation capabilityId = buf.readResourceLocation();
        CompoundTag syncData = buf.readNbt();
        return new PacketSyncCapability(entityId, capabilityId, syncData);
    }

    public static void handle(PacketSyncCapability packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (CLIENT_HANDLER != null) {
                CLIENT_HANDLER.accept(packet, context);
            }
        });
        context.setPacketHandled(true);
    }
}
