package org.bytechen.hall.network.s2c;


import org.bytechen.hall.client.rend.HitboxCubeRenderer;
import org.bytechen.hall.client.rend.LightningEffectRenderer;
import org.bytechen.hall.client.rend.MeteoriteGroundEffectRenderer;
import org.bytechen.hall.client.rend.gui.GuiShaderEffect;
import org.bytechen.hall.client.rend.gui.GuiShaderManager;
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
        ctx.get().enqueueWork(() -> {
            // 根据 typeId 分发给对应的渲染器
            if ("lightning".equals(msg.typeId)) {
                LightningEffectRenderer.handleSyncPacket(msg.instanceId, msg.data);
            } else if ("hitbox_cube".equals(msg.typeId)) {
                HitboxCubeRenderer.handleSyncPacket(msg.instanceId, msg.data);
            } else if ("meteorite_ground".equals(msg.typeId)) {
                MeteoriteGroundEffectRenderer.handleSyncPacket(msg.instanceId, msg.data);
            } else if ("gui_shader".equals(msg.typeId)) {
                // GUI shader overlay effect dispatch
                GuiShaderEffect effect = GuiShaderManager.fromNbt(msg.data);
                if (effect != null) {
                    GuiShaderManager.getInstance().addEffect(msg.instanceId, effect);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}