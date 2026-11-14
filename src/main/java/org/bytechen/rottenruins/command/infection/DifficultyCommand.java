package org.bytechen.rottenruins.command.infection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.bytechen.rottenruins.gameplay.infection.difficulty.DifficultyHelper;
import org.bytechen.rottenruins.gameplay.infection.difficulty.DifficultyRegistry;

/**
 * Registers the {@code /infcore difficulty} command subtree.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code /infcore difficulty set <id>} — change difficulty (requires op level 2)</li>
 *   <li>{@code /infcore difficulty get} — show current difficulty</li>
 *   <li>{@code /infcore difficulty list} — list all registered difficulties</li>
 * </ul>
 */
public final class DifficultyCommand {

    private DifficultyCommand() {}

    /**
     * Register the command under {@code /infcore difficulty}.
     * Called from {@code RegisterCommandsEvent}.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("infcore")
                        .then(Commands.literal("difficulty")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                .suggests((ctx, builder) -> {
                                                    for (ResourceLocation id : DifficultyRegistry.getRegisteredIds()) {
                                                        builder.suggest(id.toString());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> setDifficulty(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("get")
                                        .executes(ctx -> getDifficulty(ctx.getSource())))
                                .then(Commands.literal("list")
                                        .executes(ctx -> listDifficulties(ctx.getSource())))
                        )
        );
    }

    // ==================== Executors ====================

    private static int setDifficulty(CommandSourceStack source, String input) {
        ResourceLocation id = ResourceLocation.tryParse(input);
        if (id == null) {
            source.sendFailure(Component.literal(
                    "Invalid difficulty id: " + input + ". Use format: namespace:path (e.g. infcore:hard)"));
            return 0;
        }

        ServerLevel overworld = source.getServer().overworld();
        boolean success = DifficultyHelper.setDifficulty(overworld, id);

        if (success) {
            source.sendSuccess(() -> Component.literal("Global difficulty set to: " + id), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(
                    "Failed to set difficulty to: " + id +
                            " (it may already be the active difficulty or the change was canceled by an event listener)"));
            return 0;
        }
    }

    private static int getDifficulty(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        ResourceLocation current = DifficultyHelper.getDifficulty(overworld);
        source.sendSuccess(() -> Component.literal("Current global difficulty: " + current), false);
        return 1;
    }

    private static int listDifficulties(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        ResourceLocation current = DifficultyHelper.getDifficulty(overworld);

        StringBuilder sb = new StringBuilder("Registered difficulties:");
        for (ResourceLocation id : DifficultyRegistry.getRegisteredIds()) {
            sb.append("\n  ");
            if (id.equals(current)) {
                sb.append("§a[ACTIVE]§r ");
            } else {
                sb.append("§7-§r ");
            }
            sb.append(id);
        }

        String msg = sb.toString();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
