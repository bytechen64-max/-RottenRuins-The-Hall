package org.bytechen.rottenruins.platform.forge.infection.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * S2C 世界数据同步包。
 * <p>
 * 服务端将世界数据同步到所有客户端。
 * 与 {@link PacketSyncCapability} 对等，但面向世界（维度）级别数据。
 * <p>
 * 客户端处理器通过 {@link #CLIENT_HANDLER} 静态字段注入，
 * 在客户端设置阶段由 {@code ClientModEvents} 绑定。
 */
public class PacketSyncWorldData {

    private final ResourceLocation dimension;
    private final ResourceLocation worldDataId;
    private final CompoundTag data;

    /**
     * 客户端处理器。在客户端设置阶段注入。
     * 专用服务端上为 null，handle 方法会安全跳过。
     */
    public static BiConsumer<PacketSyncWorldData, NetworkEvent.Context> CLIENT_HANDLER = null;

    public PacketSyncWorldData(ResourceLocation dimension, ResourceLocation worldDataId, CompoundTag data) {
        this.dimension = dimension;
        this.worldDataId = worldDataId;
        this.data = data;
    }

    public ResourceLocation getDimension() { return dimension; }
    public ResourceLocation getWorldDataId() { return worldDataId; }
    public CompoundTag getData() { return data; }

    public static void encode(PacketSyncWorldData packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension);
        buf.writeResourceLocation(packet.worldDataId);
        buf.writeNbt(packet.data);
    }

    public static PacketSyncWorldData decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        ResourceLocation worldDataId = buf.readResourceLocation();
        CompoundTag data = buf.readNbt();
        return new PacketSyncWorldData(dimension, worldDataId, data);
    }

    public static void handle(PacketSyncWorldData packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (CLIENT_HANDLER != null) {
                CLIENT_HANDLER.accept(packet, context);
            }
        });
        context.setPacketHandled(true);
    }
}
