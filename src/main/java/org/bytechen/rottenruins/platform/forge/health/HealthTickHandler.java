package org.bytechen.rottenruins.platform.forge.health;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.utils.health.HealthEngine;
import org.bytechen.rottenruins.utils.health.runtime.HealthController;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 改血引擎的平台接线：每服务端 tick 驱动一次粘性重写（watches）。
 *
 * <p>bccore 把这步放在自己 mod 主类的 {@code ServerTickEvent} 监听里；我们没有独立 mod，
 * 所以放在平台层，让 {@code utils.health} 保持不依赖 Forge。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealthTickHandler {

    private HealthTickHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            HealthController.INSTANCE.tickServer();
        } catch (Throwable failure) {
            HealthEngine.LOGGER.debug("[改血引擎] tick 异常", failure);
        }
    }
}
