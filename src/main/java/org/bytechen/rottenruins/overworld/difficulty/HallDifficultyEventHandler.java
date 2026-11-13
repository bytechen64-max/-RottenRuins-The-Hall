package org.bytechen.rottenruins.overworld.difficulty;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.infcore.api.event.DifficultyChangeEvent;

/** Hall 难度事件处理器：难度切换时刷新系数缓存 → 全服广播 → 重算已加载王庭生物倍率 → 记日志。
 *  数值侧一律走 {@link DifficultyScaleProfile#current()}，不要直接比对难度 ID。 */
@Mod.EventBusSubscriber(modid = HallMod.MODID)
public final class HallDifficultyEventHandler {

    private static volatile ResourceLocation currentDifficulty = HallDifficulty.NORMAL;

    private HallDifficultyEventHandler() {}

    public static ResourceLocation getCurrentDifficulty() {
        return currentDifficulty;
    }

    /** 按当前难度重解析系数档案（生命 / 伤害倍率）。
     *  配置项改了但难度没变时，只有这个方法被调用才能让新倍率生效。 */
    public static DifficultyScaleProfile refreshProfile() {
        return DifficultyScaleProfile.refresh(currentDifficulty);
    }

    /** 用给定难度覆盖缓存并刷新系数。启动时必须主动调：{@link DifficultyChangeEvent.Post} 只在切换难度时
     *  触发，重启后缓存会退回硬编码的 normal，陆续加载的怪全按普通档施加且无人纠正（偏差逐次累积）。 */
    public static DifficultyScaleProfile syncDifficulty(ResourceLocation difficulty) {
        currentDifficulty = difficulty == null ? HallDifficulty.NORMAL : difficulty;
        return refreshProfile();
    }

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

    private static String resolveDisplayName(ResourceLocation id) {
        if (HallDifficulty.EASY.equals(id)) return "§a简单";
        if (HallDifficulty.NORMAL.equals(id)) return "§e普通";
        if (HallDifficulty.HARD.equals(id)) return "§6困难";
        // 「无法理解」档用 glitch 特效（逐帧随机位移）；垫 §4 保证特效关闭后剥离标记仍是暗红。
        if (HallDifficulty.INCOMPREHENSIBLE.equals(id)) return "§4§[rrs:glitch:amp=2.0]无法理解§[/]";
        return "§7" + id.toString();
    }
}
