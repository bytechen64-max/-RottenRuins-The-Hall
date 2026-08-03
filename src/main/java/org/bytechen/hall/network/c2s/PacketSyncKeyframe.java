package org.bytechen.hall.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncKeyframe {
    private final int entityId;
    private final String key;

    public PacketSyncKeyframe(int entityId, String key) {
        this.entityId = entityId;
        this.key = key;

    }

    // 读取数据
    public static PacketSyncKeyframe decode(FriendlyByteBuf buf) {
        return new PacketSyncKeyframe(buf.readInt(), buf.readUtf());
    }

    // 写入数据
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeUtf(this.key);

    }

    public static void handle(PacketSyncKeyframe msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 获取发送包的玩家所在的世界
            var player = ctx.get().getSender();
            if (player == null) return;
            Entity entity = player.level().getEntity(msg.entityId);
            // 核心适配逻辑：只要实现了接口就调用
            if (entity instanceof IKeyframeHandler handler) {
                handler.onKeyframeTrigger(msg.key);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}