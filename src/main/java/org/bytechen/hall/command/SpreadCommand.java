package org.bytechen.hall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.core.blockspread.BlockSpreadManager;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 方块扩散测试指令 —— /spread
 *
 * <pre>
 * /spread [radius]    — 以玩家为中心，在指定半径内传播 hall:spread 类型的方块扩散（默认半径 5）
 * </pre>
 */
public final class SpreadCommand {

    private SpreadCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("spread")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> spread(ctx, 5))
                .then(argument("radius", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> spread(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static int spread(CommandContext<CommandSourceStack> ctx, int radius) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        ResourceLocation type = new ResourceLocation(HallMod.MODID, "spread");

        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (BlockSpreadManager.hasSpreadRule(state, type)) {
                        if (BlockSpreadManager.applySpread(level, pos, state, type)) {
                            count++;
                        }
                    }
                }
            }
        }

        int finalCount = count;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a方块扩散完成！共转换 §6" + finalCount + " §a个方块（半径 " + radius + "）"),
                true);

        return count;
    }
}
