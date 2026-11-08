package org.bytechen.rottenruins.network.s2c;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectHolder;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;

import java.util.function.Supplier;

/**
 * S2C 自定义效果同步包 —— 把服务端权威的效果快照整体覆盖到客户端副本上。
 *
 * <p>和 {@code AnomalySyncPacket} 同一套做法：服务端序列化整个
 * {@link CustomEffectHolder}，客户端反序列化覆盖本地副本（那份副本只用于显示与倒计时，
 * 不做任何判定）。</p>
 *
 * <p>为什么整份发而不是增量：效果数量本来就少（通常 0~3 个），整份发省掉
 * "增删改"三种包和它们的一致性维护，代价可以忽略。</p>
 */
public class CustomEffectSyncPacket {

    private final int entityId;
    private final CompoundTag data;

    public CustomEffectSyncPacket(int entityId, CompoundTag data) {
        this.entityId = entityId;
        this.data = data;
    }

    public static void encode(CustomEffectSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeNbt(msg.data);
    }

    public static CustomEffectSyncPacket decode(FriendlyByteBuf buf) {
        return new CustomEffectSyncPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(CustomEffectSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(CustomEffectSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(msg.entityId);
        if (!(entity instanceof LivingEntity living) || msg.data == null) {
            return;
        }
        CustomEffectHolder holder = CustomEffects.getHolder(living);
        if (holder != null) {
            holder.readSnapshot(msg.data);
        }
    }
}
