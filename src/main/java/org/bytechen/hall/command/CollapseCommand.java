package org.bytechen.hall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity.CollapseType;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 坍缩测试指令 —— /collapse
 *
 * <pre>
 * /collapse spawn &lt;type&gt; [spawnDur] [maintainDur] [endDur] [color1] [color2] [radius]
 *   type: ender_dragon | hypercube | icosahedron | prism
 *   在玩家位置生成坍缩实体
 *
 * /collapse types  — 列出所有坍缩类型
 * </pre>
 */
public final class CollapseCommand {

    private CollapseCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var typeArg = argument("type", StringArgumentType.word())
                .suggests((ctx, builder) ->
                        SharedSuggestionProvider.suggest(
                                new String[]{"ender_dragon", "hypercube", "icosahedron", "prism"}, builder));

        dispatcher.register(literal("collapse")
                .requires(src -> src.hasPermission(2))
                // /collapse spawn <type> [spawnDur] [maintainDur] [endDur] [color1] [color2] [radius]
                .then(literal("spawn")
                        .then(typeArg
                                .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                        20, 60, 20, 0xFFFFFF, 0x8888FF, 16f))
                                .then(argument("spawnDur", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "spawnDur"),
                                                60, 20, 0xFFFFFF, 0x8888FF, 16f))
                                        .then(argument("maintainDur", IntegerArgumentType.integer(0, 600))
                                                .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "spawnDur"),
                                                        IntegerArgumentType.getInteger(ctx, "maintainDur"),
                                                        20, 0xFFFFFF, 0x8888FF, 16f))
                                                .then(argument("endDur", IntegerArgumentType.integer(1, 200))
                                                        .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                                                IntegerArgumentType.getInteger(ctx, "spawnDur"),
                                                                IntegerArgumentType.getInteger(ctx, "maintainDur"),
                                                                IntegerArgumentType.getInteger(ctx, "endDur"),
                                                                0xFFFFFF, 0x8888FF, 16f))
                                                        .then(argument("color1", IntegerArgumentType.integer(0, 0xFFFFFF))
                                                                .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                                                        IntegerArgumentType.getInteger(ctx, "spawnDur"),
                                                                        IntegerArgumentType.getInteger(ctx, "maintainDur"),
                                                                        IntegerArgumentType.getInteger(ctx, "endDur"),
                                                                        IntegerArgumentType.getInteger(ctx, "color1"),
                                                                        0x8888FF, 16f))
                                                                .then(argument("color2", IntegerArgumentType.integer(0, 0xFFFFFF))
                                                                        .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                                                                IntegerArgumentType.getInteger(ctx, "spawnDur"),
                                                                                IntegerArgumentType.getInteger(ctx, "maintainDur"),
                                                                                IntegerArgumentType.getInteger(ctx, "endDur"),
                                                                                IntegerArgumentType.getInteger(ctx, "color1"),
                                                                                IntegerArgumentType.getInteger(ctx, "color2"),
                                                                                16f))
                                                                        .then(argument("radius", FloatArgumentType.floatArg(1f, 64f))
                                                                                .executes(ctx -> spawnCollapse(ctx, parseType(ctx, "type"),
                                                                                        IntegerArgumentType.getInteger(ctx, "spawnDur"),
                                                                                        IntegerArgumentType.getInteger(ctx, "maintainDur"),
                                                                                        IntegerArgumentType.getInteger(ctx, "endDur"),
                                                                                        IntegerArgumentType.getInteger(ctx, "color1"),
                                                                                        IntegerArgumentType.getInteger(ctx, "color2"),
                                                                                        FloatArgumentType.getFloat(ctx, "radius")))))))))))

                // /collapse types — 列出所有类型
                .then(literal("types")
                        .executes(CollapseCommand::listTypes))
        );
    }

    private static CollapseType parseType(CommandContext<CommandSourceStack> ctx, String key) {
        String raw = StringArgumentType.getString(ctx, key).toLowerCase();
        return switch (raw) {
            case "ender_dragon", "dragon", "ender" -> CollapseType.ENDER_DRAGON;
            case "hypercube", "cube", "tesseract" -> CollapseType.HYPERCUBE;
            case "icosahedron", "icosa", "ico" -> CollapseType.ICOSAHEDRON;
            case "prism", "transforming_prism", "poly" -> CollapseType.TRANSFORMING_PRISM;
            default -> throw new IllegalArgumentException(
                    "未知坍缩类型: " + raw + "。可选: ender_dragon, hypercube, icosahedron, prism");
        };
    }

    private static int spawnCollapse(CommandContext<CommandSourceStack> ctx, CollapseType type,
                                      int spawnDur, int maintainDur, int endDur,
                                      int color1, int color2, float radius) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }

        Vec3 pos = player.position().add(0, 1.5, 0);

        CollapseEntity entity = CollapseEntity.spawn(
                player.level(), pos, type,
                spawnDur, maintainDur, endDur,
                color1, color2, radius, player, null);

        String typeName = type.name().toLowerCase();
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§a已生成坍缩实体 §6" + typeName + " §a在 §e"
                                + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)
                                + "\n§7  生成:§f" + spawnDur + "t §7维持:§f" + maintainDur
                                + "t §7结束:§f" + endDur + "t"
                                + "\n§7  颜色1:§f#" + String.format("%06X", color1)
                                + " §7颜色2:§f#" + String.format("%06X", color2)
                                + " §7范围:§f" + radius),
                true);

        return 1;
    }

    private static int listTypes(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§6═══ 坍缩渲染类型 ═══\n"
                                + "§e0 §fender_dragon  §7- 末影龙死亡式光芒四射，光束旋转\n"
                                + "§e1 §fhypercube     §7- 四维超立方体在三维空间的投影\n"
                                + "§e2 §ficosahedron   §7- 二十面体（线框+半透明面片）\n"
                                + "§e3 §fprism         §7- 变换多棱柱（3棱→20棱连续变换）"
                ), false);
        return 1;
    }
}
