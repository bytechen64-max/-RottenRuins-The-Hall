package org.bytechen.rottenruins.platform.forge.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.gui.markdown.MarkdownScreen;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MarkdownClientCommands {
    private static final ResourceLocation DEMO = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "demo");

    private MarkdownClientCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("rrsmd")
                .executes(context -> {
                    MarkdownScreen.open(DEMO);
                    return 1;
                })
                .then(argument("document", StringArgumentType.greedyString()).executes(context -> {
                    String input = StringArgumentType.getString(context, "document").strip();
                    ResourceLocation id = input.contains(":")
                            ? ResourceLocation.tryParse(input)
                            : ResourceLocation.tryBuild(HallMod.MODID, input);
                    if (id == null || id.getPath().isEmpty()) {
                        context.getSource().sendFailure(Component.translatable("rotten_ruins.gui.markdown.invalid", input));
                        return 0;
                    }
                    MarkdownScreen.open(id);
                    return 1;
                })));
    }
}
