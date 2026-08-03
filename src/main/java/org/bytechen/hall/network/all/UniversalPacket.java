package org.bytechen.hall.network.all;

import org.bytechen.hall.network.all.tools.UniversalPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UniversalPacket {
    private final String action;
    private final UniversalPacketData<?> data;

    public UniversalPacket(String action, UniversalPacketData<?> data) { this.action = action; this.data = data; }

    public static UniversalPacket decode(FriendlyByteBuf buf) {
        String action = buf.readUtf();
        UniversalPacketData<?> data = UniversalPacketData.read(buf);
        return new UniversalPacket(action, data);
    }

    public void encode(FriendlyByteBuf buf) { buf.writeUtf(this.action); this.data.write(buf); }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> PacketActionRegistry.dispatch(this.action, this.data, context));
        context.setPacketHandled(true);
    }
}
