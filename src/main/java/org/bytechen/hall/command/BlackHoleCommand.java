package org.bytechen.hall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 黑洞测试指令 —— /blackhole
 *
 * <pre>
 * /blackhole spawn [radius] [bend] [lifetime]
 *   radius   史瓦西半径（方块），默认 1.5
 *   bend     引力弯曲强度，默认 3.0（越大透镜越强）
 *   lifetime 存活时长（tick），默认 400
 *   在玩家位置生成纯视觉黑洞实体
 * </pre>
 */
public final class BlackHoleCommand {

    private BlackHoleCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("blackhole")
                .requires(src -> src.hasPermission(2))
                .then(literal("spawn")
                        .executes(ctx -> spawn(ctx, 1.5f, 3.0f, 400))
                        .then(argument("radius", FloatArgumentType.floatArg(0.1f, 64f))
                                .executes(ctx -> spawn(ctx,
                                        FloatArgumentType.getFloat(ctx, "radius"),
                                        3.0f, 400))
                                .then(argument("bend", FloatArgumentType.floatArg(0f, 50f))
                                        .executes(ctx -> spawn(ctx,
                                                FloatArgumentType.getFloat(ctx, "radius"),
                                                FloatArgumentType.getFloat(ctx, "bend"),
                                                400))
                                        .then(argument("lifetime", IntegerArgumentType.integer(1, 72000))
                                                .executes(ctx -> spawn(ctx,
                                                        FloatArgumentType.getFloat(ctx, "radius"),
                                                        FloatArgumentType.getFloat(ctx, "bend"),
                                                        IntegerArgumentType.getInteger(ctx, "lifetime"))))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx,
                             float radius, float bend, int lifetime) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }

        Vec3 pos = player.position().add(0, 1.5, 0);

        BlackHoleEntity.spawn(player.level(), pos, radius, bend, lifetime);

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§a已生成黑洞实体在 §e"
                                + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)
                                + "\n§7  半径:§f" + String.format("%.2f", radius)
                                + " §7弯曲:§f" + String.format("%.2f", bend)
                                + " §7存活:§f" + lifetime + "t"),
                true);

        return 1;
    }
}
