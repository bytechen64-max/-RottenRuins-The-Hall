package org.bytechen.hall.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.hall.overworld.difficulty.DifficultySelectHandler;

import java.util.function.Supplier;

/**
 * 客户端→服务端：玩家在难度选择界面点击选择后发送。
 * 携带玩家选择的难度 ResourceLocation。
 */
public class C2SDifficultySelectPacket {

    private final ResourceLocation difficulty;

    public C2SDifficultySelectPacket(ResourceLocation difficulty) {
        this.difficulty = difficulty;
    }

    public static void encode(C2SDifficultySelectPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.difficulty);
    }

    public static C2SDifficultySelectPacket decode(FriendlyByteBuf buf) {
        return new C2SDifficultySelectPacket(buf.readResourceLocation());
    }

    public static void handle(C2SDifficultySelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            DifficultySelectHandler.handleDifficultySelection(player, msg.difficulty);
        });
        ctx.get().setPacketHandled(true);
    }
}
