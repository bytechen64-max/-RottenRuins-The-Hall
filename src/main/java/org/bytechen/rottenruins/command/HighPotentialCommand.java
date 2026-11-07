package org.bytechen.rottenruins.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** 查询当前维度的高势区：/rotten_ruins high_potential [chunkX chunkZ]。 */
public final class HighPotentialCommand {
    private HighPotentialCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal(HallMod.MODID)
                .then(literal("high_potential")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                            ChunkPos chunk = new ChunkPos(pos);
                            return query(ctx.getSource(), chunk.x, chunk.z);
                        })
                        .then(argument("chunkX", IntegerArgumentType.integer())
                                .then(argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(ctx -> query(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                IntegerArgumentType.getInteger(ctx, "chunkZ")))))));
    }

    private static int query(CommandSourceStack source, int chunkX, int chunkZ) {
        boolean highPotential = HighPotentialChunks.isHighPotentialChunk(source.getLevel(), chunkX, chunkZ);
        source.sendSuccess(() -> Component.literal("区块 (" + chunkX + ", " + chunkZ + ") "
                + (highPotential ? "是高势区" : "不是高势区")), false);
        return highPotential ? 1 : 0;
    }
}
