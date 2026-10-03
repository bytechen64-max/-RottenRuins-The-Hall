package org.bytechen.hall.overworld.difficulty;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bytechen.hall.HallMod;

/**
 * 配置重载后让难度倍率跟上。
 *
 * <h3>为什么单独一个类</h3>
 * <p>{@link ModConfigEvent.Reloading} 实现 {@code IModBusEvent}，只在
 * <b>mod 事件总线</b>上派发；而 {@link HallDifficultyAttributes}（实体进场）
 * 与服务器启动事件都在 <b>FORGE 总线</b>上。{@code @Mod.EventBusSubscriber}
 * 的 bus 是类级别的，一个类挂不了两条总线，所以这里独立出来只放 mod 总线的事。
 *
 * <h3>为什么要处理这个事件</h3>
 * <p>难度系数是配置项（{@code SplendidingConfig.difficulty*Scale}）。
 * 玩家在游戏里改完配置文件点重载、或用模组菜单改完保存时，
 * 场上已有的怪不会自己变 —— 必须在这里主动重算一遍，
 * 否则「改了配置但看起来没反应」，和这个模组之前踩过的配置迁移坑是同一类问题。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HallDifficultyConfigReload {

    private HallDifficultyConfigReload() {}

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        // 只认本模组的配置：别人的配置文件重载与难度倍率无关
        if (event.getConfig() == null || event.getConfig().getModId() == null) return;
        if (!HallMod.MODID.equals(event.getConfig().getModId())) return;

        // 这一步必须无条件执行：即使当前没有服务端实例（主菜单、配置界面），
        // 也要把系数缓存刷成新值，等玩家进世界时直接就是对的。
        DifficultyScaleProfile profile = HallDifficultyEventHandler.refreshProfile();

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            HallMod.LOGGER.info("[hall-difficulty] 配置重载（无运行中的服务端），系数已更新：生命 x{}，伤害 x{}",
                    profile.healthScale(), profile.damageScale());
            return;
        }

        int count = HallDifficultyAttributes.refreshAllLoaded(server, profile,
                DifficultyScaleProfile.tierOf(HallDifficultyEventHandler.getCurrentDifficulty()));
        HallMod.LOGGER.info("[hall-difficulty] 配置重载，系数更新为 生命 x{} / 伤害 x{}，已刷新 {} 只王庭生物",
                profile.healthScale(), profile.damageScale(), count);
    }
}
