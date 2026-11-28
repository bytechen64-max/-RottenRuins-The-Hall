package org.bytechen.rottenruins.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.story.StoryText;
import org.bytechen.rottenruins.gameplay.story.StoryFonts;
import org.bytechen.rottenruins.gameplay.story.StoryCatalog;
import org.bytechen.rottenruins.gameplay.story.StoryDirector;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryTextCommand {
    private static final SimpleCommandExceptionType UNKNOWN_FONT = new SimpleCommandExceptionType(
            Component.literal("未知字体。可选：default, wenkai, xiaowei, mashan"));
    private static final SuggestionProvider<CommandSourceStack> FONT_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(StoryFonts.PRESETS.stream().map(StoryFonts.Preset::key), builder);
    private StoryTextCommand() {}
    private static final DynamicCommandExceptionType UNKNOWN_STORY = new DynamicCommandExceptionType(
            value -> Component.translatable("rotten_ruins.story.unknown", value));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal(HallMod.MODID)
                .then(literal("story").requires(source -> source.hasPermission(2))
                        .then(definitionCommand())
                        .then(literal("list").executes(ctx -> {
                            var ids = StoryCatalog.snapshot().stories().keySet().stream().map(Object::toString).sorted().toList();
                            ctx.getSource().sendSuccess(() -> Component.literal(String.join(", ", ids)), false);
                            return ids.size();
                        }))
                        .then(literal("trigger").then(argument("players", EntityArgument.players())
                                .then(argument("event", StringArgumentType.word()).suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(StoryCatalog.snapshot().rules().stream()
                                                .map(org.bytechen.rottenruins.core.story.StoryRule::event).distinct(), builder))
                                        .executes(ctx -> {
                                            int count = 0;
                                            for (var player : EntityArgument.getPlayers(ctx, "players"))
                                                if (StoryDirector.trigger(player, StringArgumentType.getString(ctx, "event"), StoryDirector.context(player))) count++;
                                            return count;
                                        }))))
                        .then(literal("flag").then(argument("players", EntityArgument.players())
                                .then(argument("flag", ResourceLocationArgument.id())
                                        .then(argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                                            var players = EntityArgument.getPlayers(ctx, "players");
                                            for (var player : players) StoryDirector.flag(player,
                                                    ResourceLocationArgument.getId(ctx, "flag").toString(), BoolArgumentType.getBool(ctx, "enabled"));
                                            return players.size();
                                        })))))
                        .then(message("show", false))
                        .then(message("append", true))
                        .then(fontMessage("show_font", false))
                        .then(fontMessage("append_font", true))
                        .then(literal("preview").then(argument("font", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(StoryFonts.PRESETS.stream().map(StoryFonts.Preset::key),
                                                java.util.stream.Stream.of("all")), builder))
                                .executes(StoryTextCommand::preview)))
                        .then(literal("clear").then(argument("players", EntityArgument.players())
                                .executes(ctx -> {
                                    var players = EntityArgument.getPlayers(ctx, "players");
                                    players.forEach(player -> { StoryDirector.cancel(player); StoryText.clear(player); });
                                    return players.size();
                                })))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> definitionCommand() {
        return literal("play").then(argument("players", EntityArgument.players())
                .then(argument("story", ResourceLocationArgument.id()).suggests((ctx, builder) ->
                        SharedSuggestionProvider.suggestResource(StoryCatalog.snapshot().stories().keySet(), builder))
                        .executes(ctx -> {
                            var id = ResourceLocationArgument.getId(ctx, "story");
                            var story = StoryCatalog.snapshot().stories().get(id);
                            if (story == null) throw UNKNOWN_STORY.create(id);
                            var players = EntityArgument.getPlayers(ctx, "players");
                            for (var player : players) StoryDirector.play(player, story);
                            return players.size();
                        })));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> fontMessage(String name, boolean append) {
        return literal(name).then(argument("players", EntityArgument.players())
                .then(argument("seconds", IntegerArgumentType.integer(1, 600))
                        .then(argument("angle", FloatArgumentType.floatArg(-180, 180))
                                .then(argument("font", StringArgumentType.word()).suggests(FONT_SUGGESTIONS)
                                        .then(argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                                            var preset = font(ctx);
                                            var players = EntityArgument.getPlayers(ctx, "players");
                                            Component text = Component.literal(StringArgumentType.getString(ctx, "text")
                                                    .replace("\\n", "\n")).withStyle(style -> style.withFont(preset.resource()));
                                            if (Component.Serializer.toJson(text).length() > StoryText.MAX_TEXT_LENGTH) return 0;
                                            for (var player : players) {
                                                StoryText.send(player, text, IntegerArgumentType.getInteger(ctx, "seconds") * 20,
                                                        FloatArgumentType.getFloat(ctx, "angle"), StoryText.DEFAULT_DISTANCE, 0, append);
                                            }
                                            return players.size();
                                        }))))));
    }

    private static StoryFonts.Preset font(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var preset = StoryFonts.find(StringArgumentType.getString(ctx, "font"));
        if (preset == null) throw UNKNOWN_FONT.create();
        return preset;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        boolean all = StringArgumentType.getString(ctx, "font").equals("all");
        var fonts = all ? StoryFonts.PRESETS : java.util.List.of(font(ctx));
        boolean append = false;
        for (var preset : fonts) {
            // Each preview uses identical three-line content and the same timing and placement.
            Component sample = Component.translatable("rotten_ruins.story.opening")
                    .withStyle(style -> style.withFont(preset.resource()));
            StoryText.send(player, sample, 80, 0, StoryText.DEFAULT_DISTANCE, 0, append);
            append = true;
        }
        String order = fonts.stream().map(font -> font.key() + "（" + font.displayName() + "）")
                .collect(java.util.stream.Collectors.joining(" → "));
        ctx.getSource().sendSuccess(() -> Component.literal("剧情字体预览：" + order), false);
        return fonts.size();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> message(String name, boolean append) {
        return literal(name).then(argument("players", EntityArgument.players())
                .then(argument("seconds", IntegerArgumentType.integer(1, 600))
                        .then(argument("angle", FloatArgumentType.floatArg(-180, 180))
                                .then(argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                                    var players = EntityArgument.getPlayers(ctx, "players");
                                    Component text = Component.literal(StringArgumentType.getString(ctx, "text")
                                            .replace("\\n", "\n"));
                                    if (Component.Serializer.toJson(text).length() > StoryText.MAX_TEXT_LENGTH) return 0;
                                    for (var player : players) {
                                        StoryText.send(player, text, IntegerArgumentType.getInteger(ctx, "seconds") * 20,
                                                FloatArgumentType.getFloat(ctx, "angle"), StoryText.DEFAULT_DISTANCE, 0, append);
                                    }
                                    return players.size();
                                })))));
    }
}
