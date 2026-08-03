package org.bytechen.hall.overworld.difficulty;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.api.event.DifficultyChangeEvent;

/**
 * Hall 难度事件处理器。
 * <p>
 * 难度切换时全服广播通知 + 日志记录。
 * 游戏逻辑（伤害倍率、扩散速度等）由你自己在需要的地方通过
 * {@link HallDifficultyEventHandler#getCurrentDifficulty()} 或
 * {@link org.bytechen.infcore.core.difficulty.DifficultyHelper#getDifficulty}
 * 查询即可。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID)
public final class HallDifficultyEventHandler {

    /** 当前服务端难度缓存（由 Post 事件更新） */
    private static volatile ResourceLocation currentDifficulty = HallDifficulty.NORMAL;

    private HallDifficultyEventHandler() {}

    /**
     * 获取当前服务端难度。
     * 客户端使用 {@link org.bytechen.infcore.core.difficulty.DifficultyHelper#getClientDifficulty()}。
     */
    public static ResourceLocation getCurrentDifficulty() {
        return currentDifficulty;
    }

    // ==================== 难度切换通知 ====================

    @SubscribeEvent
    public static void onDifficultyPost(DifficultyChangeEvent.Post event) {
        currentDifficulty = event.getNewDifficulty();

        String displayName = resolveDisplayName(currentDifficulty);
        for (ServerPlayer player : event.getOverworld().getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(
                            "§8[§d难度§8] §f全局难度已切换为: " + displayName),
                    false);
        }

        HallMod.LOGGER.info("Hall difficulty switched to: {} ({})",
                currentDifficulty, displayName);
    }

    // ==================== 辅助 ====================

    private static String resolveDisplayName(ResourceLocation id) {
        if (HallDifficulty.EASY.equals(id)) return "§a简单";
        if (HallDifficulty.NORMAL.equals(id)) return "§e普通";
        if (HallDifficulty.HARD.equals(id)) return "§6困难";
        if (HallDifficulty.INCOMPREHENSIBLE.equals(id)) return "§c§k!!§r §4无法理解 §c§k!!";
        return "§7" + id.toString();
    }
}
