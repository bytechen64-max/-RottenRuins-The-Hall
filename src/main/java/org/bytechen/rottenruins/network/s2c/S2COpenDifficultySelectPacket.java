package org.bytechen.rottenruins.network.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：通知客户端打开难度选择界面。
 * 由首个玩家加入世界时服务端发送。
 */
public class S2COpenDifficultySelectPacket {

    public S2COpenDifficultySelectPacket() {}

    public static void encode(S2COpenDifficultySelectPacket msg, FriendlyByteBuf buf) {}

    public static S2COpenDifficultySelectPacket decode(FriendlyByteBuf buf) {
        return new S2COpenDifficultySelectPacket();
    }

    public static void handle(S2COpenDifficultySelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        if (context.getDirection() != net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> org.bytechen.rottenruins.client.network.CommonPacketHandlers.openDifficulty()));
        ctx.get().setPacketHandled(true);
    }

}
