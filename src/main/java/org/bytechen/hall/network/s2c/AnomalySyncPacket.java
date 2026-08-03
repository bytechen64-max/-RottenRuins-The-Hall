package org.bytechen.hall.network.s2c;

import org.bytechen.hall.api.anomaly.AnomalyType;
import org.bytechen.hall.overworld.registry.capability.CapabilityAttacher;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
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
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(AnomalySyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(msg.entityId);
        if (!(entity instanceof LivingEntity living)) return;

        AnomalyCapability cap = CapabilityAttacher.getCapability(
                living, CapabilityRegistry.ANOMALY_CAP, null);
        if (cap != null && msg.anomalyData != null) {
            cap.deserializeNBT(msg.anomalyData);
        }
    }
}
