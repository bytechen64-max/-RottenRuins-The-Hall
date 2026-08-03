package org.bytechen.hall.overworld.difficulty;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.s2c.S2COpenDifficultySelectPacket;
import org.bytechen.infcore.core.difficulty.DifficultyHelper;

/**
 * 难度选择流程的服务器端处理。
 *
 * <h3>触发时机</h3>
 * 首个玩家首次加入世界时，检查 {@link HallWorldData}：
 * 如果尚未选择难度，则向该玩家发送 {@link S2COpenDifficultySelectPacket}，
 * 客户端收到后打开 {@code DifficultySelectScreen}。
 *
 * <h3>选择确认</h3>
 * 玩家在界面上选择后，客户端发送 {@code C2SDifficultySelectPacket}，
 * 由 {@link #handleDifficultySelection(ServerPlayer, ResourceLocation)} 处理：
 * 调用 {@link DifficultyHelper#setDifficulty}，标记 {@link HallWorldData}，广播难度变更。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID)
public final class DifficultySelectHandler {

    private DifficultySelectHandler() {}

    /**
     * 玩家登录时检查是否需要弹出难度选择。
     * 仅在难度尚未被选择且当前仅有一名玩家在线时触发。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var server = player.getServer();
        if (server == null) return;

        var overworld = server.overworld();
        HallWorldData data = overworld.getDataStorage()
                .computeIfAbsent(HallWorldData::load, HallWorldData::create, HallWorldData.dataName());

        if (!data.isDifficultySelected()) {
            // 仅向首个玩家弹出选择界面
            if (server.getPlayerList().getPlayerCount() <= 1) {
                NetworkHelper.sendToPlayer(player, new S2COpenDifficultySelectPacket());
                HallMod.LOGGER.info("Sent difficulty select screen to first player: {}",
                        player.getGameProfile().getName());
            }
        }
    }

    /**
     * 处理玩家提交的难度选择（由 C2S 包调用）。
     *
     * @param player     发起选择的玩家
     * @param difficulty 选择的难度 ResourceLocation
     */
    public static void handleDifficultySelection(ServerPlayer player, ResourceLocation difficulty) {
        var server = player.getServer();
        if (server == null) return;

        var overworld = server.overworld();
        HallWorldData data = overworld.getDataStorage()
                .computeIfAbsent(HallWorldData::load, HallWorldData::create, HallWorldData.dataName());

        // 防止重复选择
        if (data.isDifficultySelected()) {
            HallMod.LOGGER.warn("Player {} attempted to select difficulty, but it was already set",
                    player.getGameProfile().getName());
            return;
        }

        // 校验是否为有效的 Hall 难度
        if (!isValidHallDifficulty(difficulty)) {
            HallMod.LOGGER.warn("Player {} sent invalid difficulty: {}", player.getGameProfile().getName(), difficulty);
            return;
        }

        // 应用难度
        boolean applied = DifficultyHelper.setDifficulty(overworld, difficulty);
        if (applied) {
            data.markDifficultySelected();
            HallMod.LOGGER.info("First difficulty selected by {}: {}", player.getGameProfile().getName(), difficulty);
        }
    }

    /**
     * 检查给定的 ResourceLocation 是否为 Hall 定义的四档难度之一。
     */
    private static boolean isValidHallDifficulty(ResourceLocation id) {
        return HallDifficulty.EASY.equals(id)
                || HallDifficulty.NORMAL.equals(id)
                || HallDifficulty.HARD.equals(id)
                || HallDifficulty.INCOMPREHENSIBLE.equals(id);
    }
}
