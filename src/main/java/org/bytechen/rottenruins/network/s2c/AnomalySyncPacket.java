package org.bytechen.rottenruins.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C 异常数据同步包 —— 将实体的异常进度同步到客户端。
 * 客户端收到后直接覆盖本地 capability 数据。
 */
public class AnomalySyncPacket {
    private final int entityId;
    private final CompoundTag anomalyData;

    public AnomalySyncPacket(int entityId, CompoundTag anomalyData) {
        this.entityId = entityId;
        this.anomalyData = anomalyData;
    }

    public static void encode(AnomalySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeNbt(msg.anomalyData);
    }

    public static AnomalySyncPacket decode(FriendlyByteBuf buf) {
        return new AnomalySyncPacket(buf.readInt(), buf.readNbt());
    }

    public static void handle(AnomalySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        if (context.getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> org.bytechen.rottenruins.client.network.CommonPacketHandlers.applyAnomaly(msg.entityId, msg.anomalyData)));
        ctx.get().setPacketHandled(true);
    }

}
