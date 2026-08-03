package org.bytechen.hall.network.s2c;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.hall.client.gui.DifficultySelectScreen;

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
        ctx.get().enqueueWork(() -> openScreen());
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen() {
        Minecraft.getInstance().setScreen(new DifficultySelectScreen());
    }
}
