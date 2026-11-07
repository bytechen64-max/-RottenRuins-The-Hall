package org.bytechen.rottenruins.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;

import java.util.function.Supplier;

public record FilterSettingsPacket(int containerId,int fc,int bandwidth,int gain,boolean negative) {
    public static void encode(FilterSettingsPacket packet,FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeVarInt(packet.fc); buffer.writeVarInt(packet.bandwidth);
        buffer.writeVarInt(packet.gain); buffer.writeBoolean(packet.negative);
    }
    public static FilterSettingsPacket decode(FriendlyByteBuf buffer) {
        return new FilterSettingsPacket(buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readBoolean());
    }
    public static void handle(FilterSettingsPacket packet,Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(()->{
            var player=context.get().getSender();
            if (player!=null && player.containerMenu instanceof SourceMachineMenu menu && menu.containerId==packet.containerId) {
                menu.configure(player,packet.fc,packet.bandwidth,packet.gain,packet.negative);
            }
        });
        context.get().setPacketHandled(true);
    }
}
