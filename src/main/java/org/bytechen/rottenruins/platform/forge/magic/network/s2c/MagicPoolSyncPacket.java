package org.bytechen.rottenruins.platform.forge.magic.network.s2c;

import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventorySnapshot;
import org.bytechen.rottenruins.platform.minecraft.magic.MagicNetworkCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C spell-pool replication using a detached typed snapshot, not the save-format NBT. */
public class MagicPoolSyncPacket {

    private final int entityId;
    private final SpellInventorySnapshot state;
    private final int usableSlots;

    public MagicPoolSyncPacket(int entityId, SpellInventorySnapshot state, int usableSlots) {
        this.entityId = entityId;
        this.state = state;
        this.usableSlots = usableSlots;
    }

    public static void encode(MagicPoolSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.usableSlots);
        MagicNetworkCodec.writePool(buf, msg.state);
    }

    public static MagicPoolSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int usableSlots = buf.readVarInt();
        return new MagicPoolSyncPacket(entityId, MagicNetworkCodec.readPool(buf), usableSlots);
    }

    public static void handle(MagicPoolSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.bytechen.rottenruins.client.magic.MagicPacketHandlers.applyPool(msg.entityId, msg.state, msg.usableSlots)));
        ctx.setPacketHandled(true);
    }

}
