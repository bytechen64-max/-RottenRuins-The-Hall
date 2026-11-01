package org.bytechen.rottenruins.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceWireBlock;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SourceWireColors {
    private SourceWireColors() {}

    public static int color(int power) {
        if (power == 0) return 0x174478;
        float strength = power / 15.0F;
        int red = 20 + Math.round(20 * strength);
        int green = 100 + Math.round(120 * strength);
        int blue = 180 + Math.round(75 * strength);
        return red << 16 | green << 8 | blue;
    }

    @SubscribeEvent
    public static void blocks(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> color(state.getValue(SourceWireBlock.POWER)), RegisterBlock.SOURCE_WIRE.get());
    }

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> color(15), RegisterBlock.SOURCE_WIRE.get().asItem());
    }
}
