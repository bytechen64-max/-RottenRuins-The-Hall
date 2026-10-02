package org.bytechen.hall.network;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.network.all.UniversalPacket;
import org.bytechen.hall.network.c2s.C2SDifficultySelectPacket;
import org.bytechen.hall.network.c2s.PacketSyncKeyframe;
import org.bytechen.hall.network.s2c.AnomalySyncPacket;
import org.bytechen.hall.network.s2c.EffectSyncPacket;
import org.bytechen.hall.network.s2c.S2COpenDifficultySelectPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

public class NetworkHelper {
    private static int packetId = 0;

    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "main"), () -> "1.0", s -> true, s -> true);

    public static void register() {
        // ── C2S 处理器（服务端侧的业务逻辑，包序号不变）──
        // UniversalPacket 只需要一个 action 处理器注册，不用新增 registerMessage
        org.bytechen.hall.network.c2s.VoidSwordStrikePacket.register();

        NETWORK.registerMessage(packetId++, UniversalPacket.class,
                UniversalPacket::encode, UniversalPacket::decode, UniversalPacket::handle);
        NETWORK.registerMessage(packetId++, PacketSyncKeyframe.class,
                PacketSyncKeyframe::encode, PacketSyncKeyframe::decode, PacketSyncKeyframe::handle);
        NETWORK.registerMessage(packetId++, EffectSyncPacket.class,
                EffectSyncPacket::encode, EffectSyncPacket::decode, EffectSyncPacket::handle);
        NETWORK.registerMessage(packetId++, AnomalySyncPacket.class,
                AnomalySyncPacket::encode, AnomalySyncPacket::decode, AnomalySyncPacket::handle);
        NETWORK.registerMessage(packetId++, S2COpenDifficultySelectPacket.class,
                S2COpenDifficultySelectPacket::encode, S2COpenDifficultySelectPacket::decode, S2COpenDifficultySelectPacket::handle);
        NETWORK.registerMessage(packetId++, C2SDifficultySelectPacket.class,
                C2SDifficultySelectPacket::encode, C2SDifficultySelectPacket::decode, C2SDifficultySelectPacket::handle);
    }

    public static <MSG> void sendToPlayer(ServerPlayer player, MSG msg) { NETWORK.send(PacketDistributor.PLAYER.with(() -> player), msg); }
    public static <MSG> void sendToAllClients(MSG msg) { NETWORK.send(PacketDistributor.ALL.noArg(), msg); }
    public static <MSG> void sendToALLClient(MSG msg) { NETWORK.send(PacketDistributor.ALL.noArg(), msg); }
    public static <MSG> void sendToClient(Entity entity, MSG msg) { NETWORK.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg); }
    public static <MSG> void sendToServer(MSG msg) { NETWORK.sendToServer(msg); }

    // Helper methods for static effect renderers
    public static void sendLightningToAll(UUID instanceId, CompoundTag data) {
        EffectSyncPacket packet = new EffectSyncPacket("lightning", instanceId, data);
        sendToALLClient(packet);
    }

    public static void sendCubeToAll(UUID instanceId, CompoundTag data) {
        EffectSyncPacket packet = new EffectSyncPacket("hitbox_cube", instanceId, data);
        sendToALLClient(packet);
    }

    public static void sendMeteoriteGroundEffectToAll(UUID instanceId, CompoundTag data) {
        EffectSyncPacket packet = new EffectSyncPacket("meteorite_ground", instanceId, data);
        sendToALLClient(packet);
    }

    /**
     * Send a fullscreen GUI shader overlay effect to a specific player.
     * @param player          target player
     * @param effectType      shader type key (e.g. "horror_voronoi")
     * @param instanceId      unique instance UUID
     * @param duration        total effect duration in ticks
     * @param fadeInDuration  ticks for fade-in (0→1)
     * @param fadeOutDuration ticks for fade-out (1→0)
     */
    public static void sendGuiShaderToPlayer(ServerPlayer player, String effectType,
                                              UUID instanceId, int duration,
                                              int fadeInDuration, int fadeOutDuration) {
        CompoundTag data = new CompoundTag();
        data.putString("type", effectType);
        data.putInt("duration", duration);
        data.putInt("fadeInDuration", fadeInDuration);
        data.putInt("fadeOutDuration", fadeOutDuration);
        EffectSyncPacket packet = new EffectSyncPacket("gui_shader", instanceId, data);
        sendToPlayer(player, packet);
    }
}
