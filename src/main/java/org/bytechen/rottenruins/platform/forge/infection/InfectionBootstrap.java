package org.bytechen.rottenruins.platform.forge.infection;

import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.core.infection.InfectionIds;
import org.bytechen.rottenruins.utils.logging.InfectionLog;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.bytechen.rottenruins.command.infection.DifficultyCommand;
import org.bytechen.rottenruins.platform.forge.infection.network.NetworkHelper;
import org.bytechen.rottenruins.utils.logging.ThrottledLogger;

/** Infection systems owned and initialized by Rotten Ruins. */
public final class InfectionBootstrap {

    private InfectionBootstrap() {}

    public static void init(IEventBus modEventBus) {
        if (ModList.get().isLoaded(InfectionIds.NAMESPACE)) {
            throw new IllegalStateException("Rotten Ruins includes the infection core. "
                    + "Remove the standalone infcore jar from the mods directory.");
        }
        org.bytechen.rottenruins.gameplay.infection.InfectionRuntime.install(new ForgeInfectionServices());
        InfectionBootstrap core = new InfectionBootstrap();

        modEventBus.addListener(core::commonSetup);

        MinecraftForge.EVENT_BUS.register(core);

        NetworkHelper.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        InfectionLog.LOGGER.info("Rotten Ruins infection core common setup complete.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        InfectionLog.LOGGER.info("Rotten Ruins infection core server starting.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 把节流器窗口内还没汇总的事件数补一条日志，避免关服时丢掉统计
        ThrottledLogger.flushAll();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DifficultyCommand.register(event.getDispatcher());
    }

}
