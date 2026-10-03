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
 * 难度切换时：刷新难度系数缓存 → 全服广播通知 → 按新难度重算已加载王庭生物的倍率 → 记日志。
 * <p>
 * 游戏逻辑（扩散速度、索敌阈值等）通过
 * {@link HallDifficultyEventHandler#getCurrentDifficulty()} 或
 * {@link org.bytechen.infcore.core.difficulty.DifficultyHelper#getDifficulty}
 * 查询；数值侧一律走 {@link DifficultyScaleProfile#current()}，不要直接比对难度 ID。
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

    // ==================== 系数缓存 ====================

    /**
     * 按当前难度重新解析系数档案（生命 / 伤害倍率）。
     * <p>难度切换与配置重载时各调一次。配置项改了但难度没变时，
     * 只有这个方法被调用才能让新倍率生效。
     *
     * @return 刷新后的档案
     */
    public static DifficultyScaleProfile refreshProfile() {
        return DifficultyScaleProfile.refresh(currentDifficulty);
    }

    /**
     * 用给定难度覆盖缓存，并同步刷新系数档案。
     *
     * <h3>为什么必须有这个方法</h3>
     * <p>{@link #currentDifficulty} 的初值是硬编码的 {@link HallDifficulty#NORMAL}，
     * 它只由 {@link DifficultyChangeEvent.Post} 更新 —— 而那个事件在「切换难度」时才触发，
     * <b>服务器启动时不会触发</b>。于是在一次重启之后，缓存会退回默认的 normal，
     * 而存档里实际存着的可能是 {@code hall:incomprehensible}：
     * 重启后陆续加载的怪会全部按普通档施加、标记也写成 {@code normal}，
     * 并且没有任何东西会把它们纠正回来（没有事件、也没有别的地方读存档里的难度）。
     * 这个偏差会随着每次重启累积。
     * <p>所以启动时必须主动从世界的持久化数据里把难度读回来，走这个方法写进缓存。
     *
     * @param difficulty 刚读回的世界难度
     * @return 刷新后的档案
     */
    public static DifficultyScaleProfile syncDifficulty(ResourceLocation difficulty) {
        currentDifficulty = difficulty == null ? HallDifficulty.NORMAL : difficulty;
        return refreshProfile();
    }

    // ==================== 难度切换通知 ====================

    @SubscribeEvent
    public static void onDifficultyPost(DifficultyChangeEvent.Post event) {
        currentDifficulty = event.getNewDifficulty();

        // 先刷系数，再刷新实体 —— 顺序反了会让整场的怪用旧倍率重算一遍
        DifficultyScaleProfile profile = refreshProfile();
        int refreshed = HallDifficultyAttributes.refreshAllLoaded(
                event.getOverworld().getServer(), profile,
                DifficultyScaleProfile.tierOf(currentDifficulty));

        String displayName = resolveDisplayName(currentDifficulty);
        for (ServerPlayer player : event.getOverworld().getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(
                            "§8[§d难度§8] §f全局难度已切换为: " + displayName),
                    false);
        }

        HallMod.LOGGER.info("Hall difficulty switched to: {} ({}) — health x{}, damage x{}, {} creatures rescaled",
                currentDifficulty, displayName,
                profile.healthScale(), profile.damageScale(), refreshed);
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
