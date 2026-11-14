package org.bytechen.rottenruins.client.infection;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.bytechen.rottenruins.HallMod;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.rottenruins.platform.forge.infection.capability.AbstractCapability;
import org.bytechen.rottenruins.platform.forge.infection.capability.CapabilityRegistry;
import org.bytechen.rottenruins.gameplay.infection.difficulty.DifficultyHelper;
import org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncCapability;
import org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncDifficulty;
import org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncWorldData;
import org.bytechen.rottenruins.platform.minecraft.infection.worlddata.WorldDataRegistry;

/**
 * 客户端数据包处理器。
 * <p>
 * 仅在物理客户端上加载，通过 {@link PacketSyncCapability#CLIENT_HANDLER} 注入。
 * 在客户端设置阶段由本类客户端事件订阅方法调用 {@link #init()} 完成绑定。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        init();
    }

    /**
     * 绑定所有客户端数据包处理器。
     * 在 {@code FMLClientSetupEvent} 中调用。
     */
    public static void init() {
        PacketSyncCapability.CLIENT_HANDLER = ClientPacketHandlers::handleSyncCapability;
        PacketSyncWorldData.CLIENT_HANDLER = ClientPacketHandlers::handleSyncWorldData;
        PacketSyncDifficulty.CLIENT_HANDLER = ClientPacketHandlers::handleSyncDifficulty;
    }

    /**
     * 处理能力同步数据包。
     * <p>
     * 根据包中的 entityId 找到对应实体，将同步数据写入其能力实例。
     */
    private static void handleSyncCapability(PacketSyncCapability packet, NetworkEvent.Context context) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (entity == null) return;

        Capability<?> cap = CapabilityRegistry.getById(packet.getCapabilityId());
        if (cap == null) return;

        entity.getCapability(cap).ifPresent(capInstance -> {
            if (capInstance instanceof AbstractCapability<?> absCap) {
                absCap.readSyncData(packet.getSyncData());
            }
        });
    }

    /**
     * 处理世界数据同步包。
     * <p>
     * 将服务端推送的世界数据缓存到 {@link WorldDataRegistry} 的客户端存储中。
     * 下游模组通过 {@link WorldDataRegistry#getClientData} 读取。
     */
    private static void handleSyncWorldData(PacketSyncWorldData packet, NetworkEvent.Context context) {
        WorldDataRegistry.putClientData(
                packet.getDimension(),
                packet.getWorldDataId(),
                packet.getData());
    }

    /**
     * 处理难度同步数据包。
     * <p>
     * 将服务端推送的全局难度写入客户端缓存。
     */
    private static void handleSyncDifficulty(PacketSyncDifficulty packet, NetworkEvent.Context context) {
        DifficultyHelper.setClientDifficulty(packet.getDifficulty());
    }
}
