package org.bytechen.hall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.hall.overworld.registry.entities.population.skills.MeteorEventEntity;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 陨石事件的触发指令 —— {@code /meteorevent [distance]}
 *
 * <pre>
 * /meteorevent            沿视线选点（最多 100 格）
 * /meteorevent &lt;distance&gt;  指定选点距离
 * </pre>
 *
 * <p>为什么和已有的 {@code /meteor} 分开：那条指令是给旧的
 * {@code MeteoriteEntity}（物理下落的陨石技能）用的，是"一落地就炸"的即时演算；
 * 这一条是<b>三段式天灾事件</b>（天空显现 10 秒 → 坠落 10 秒 → 爆炸），
 * 两条指令的生命周期与观感完全不同，合成一条只会让人分不清自己在测哪个。</p>
 *
 * <p>选点规则与 {@code /crimsonvow field} 一致：沿视线每 0.5 格步进，碰到第一个
 * 非空气方块就用它上面那一格；一路没碰到就投到视线末端的**地表**（用
 * {@code MOTION_BLOCKING_NO_LEAVES} 高度图），保证事件永远落在一个合理的位置上。</p>
 */
public final class MeteorEventCommand {

    private MeteorEventCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(literal("meteorevent")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> trigger(ctx, 100f))
                .then(argument("distance", FloatArgumentType.floatArg(4f, 256f))
                        .executes(ctx -> trigger(ctx, FloatArgumentType.getFloat(ctx, "distance")))));
    }

    private static int trigger(CommandContext<CommandSourceStack> ctx, float maxDist)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 ground = pickGround(player, maxDist);
        MeteorEventEntity.spawn(player.serverLevel(), player, ground);
        float seconds = (MeteorEventEntity.SIGN_TICKS + MeteorEventEntity.FALL_TICKS) / 20f;
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "陨石事件已触发：落点 %.1f / %.1f / %.1f，%.0f 秒后砸中",
                ground.x, ground.y, ground.z, seconds)), true);
        return 1;
    }

    /** 沿视线找第一个落点；找不到就投到视线末端的地表。 */
    private static Vec3 pickGround(ServerPlayer player, float maxDist) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        for (float d = 2f; d <= maxDist; d += 0.5f) {
            Vec3 p = eye.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(p);
            if (!player.level().getBlockState(bp).isAir()) {
                return new Vec3(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
            }
        }
        Vec3 flat = eye.add(dir.scale(Math.min(maxDist, 40f)));
        int bx = Mth.floor(flat.x);
        int bz = Mth.floor(flat.z);
        int by = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return new Vec3(bx + 0.5, by, bz + 0.5);
    }
}
