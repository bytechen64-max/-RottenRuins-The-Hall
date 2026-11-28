package org.bytechen.rottenruins.network.s2c;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端特效同步包，由服务端发送，触发客户端渲染器创建或更新特效。
 */
public class EffectSyncPacket {
    private final String typeId;
    private final UUID instanceId;
    private final CompoundTag data;

    public EffectSyncPacket(String typeId, UUID instanceId, CompoundTag data) {
        this.typeId = typeId;
        this.instanceId = instanceId;
        this.data = data;
    }

    public static void encode(EffectSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.typeId);
        buf.writeUUID(msg.instanceId);
        buf.writeNbt(msg.data);
    }

    public static EffectSyncPacket decode(FriendlyByteBuf buf) {
        return new EffectSyncPacket(buf.readUtf(), buf.readUUID(), buf.readNbt());
    }

    public static void handle(EffectSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT) {
            ctx.get().setPacketHandled(true);
            return;
        }
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> org.bytechen.rottenruins.client.network.CommonPacketHandlers.applyEffect(msg.typeId, msg.instanceId, msg.data));
        });
        ctx.get().setPacketHandled(true);
    }
}