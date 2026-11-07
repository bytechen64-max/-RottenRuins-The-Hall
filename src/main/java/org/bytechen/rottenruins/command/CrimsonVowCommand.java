package org.bytechen.rottenruins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowChargeEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowFieldEntity;
import org.bytechen.rottenruins.overworld.registry.items.CrimsonVow;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 绯红誓约「起誓」的测试指令 —— /crimsonvow
 *
 * <pre>
 * /crimsonvow field [radius] [maxAge]
 *   在视线所指的落点直接展开一个巨型法阵（跳过蓄力）。
 *   radius  半径（格），默认 14；maxAge  持续（tick），默认 400 = 20 秒。
 *
 * /crimsonvow charge
 *   在脚下起一个蓄力法阵（纯视觉，3 秒后自己散掉）。
 *
 * /crimsonvow ready
 *   清掉本玩家的起誓冷却（调试用）。
 * </pre>
 *
 * <h3>为什么需要一个指令</h3>
 * <p>这一招的完整链路是"蹲下右键 → 蓄力 3 秒 → 视线选点 → 20 秒法阵 → 终末破坏"，
 * 而其中真正需要反复看的是<b>最后 3.5 秒的终末</b>与<b>地形破坏</b> ——
 * 靠正常释放去调那一段，每改一次参数都要先等 17 秒。</p>
 *
 * <p>指令直接落到"展开法阵"这一步，而且 {@code maxAge} 可以调到很小
 * （例如 120 = 6 秒）以便快速看到终末。这也与项目里其它技能实体的做法一致
 * （{@code /blackhole} / {@code /collapse} / {@code /meteor} 都有）——
 * 一个 20 秒的技能没有调试入口，排查成本会高得离谱。</p>
 */
public final class CrimsonVowCommand {

    private CrimsonVowCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("crimsonvow")
                .requires(src -> src.hasPermission(2))
                .then(literal("field")
                        .executes(ctx -> field(ctx,
                                CrimsonVowFieldEntity.DEFAULT_RADIUS,
                                CrimsonVowFieldEntity.DEFAULT_MAX_AGE))
                        .then(argument("radius", FloatArgumentType.floatArg(2f, 48f))
                                .executes(ctx -> field(ctx,
                                        FloatArgumentType.getFloat(ctx, "radius"),
                                        CrimsonVowFieldEntity.DEFAULT_MAX_AGE))
                                .then(argument("maxAge", IntegerArgumentType.integer(
                                                CrimsonVowFieldEntity.FINALE_TICKS + 20, 72000))
                                        .executes(ctx -> field(ctx,
                                                FloatArgumentType.getFloat(ctx, "radius"),
                                                IntegerArgumentType.getInteger(ctx, "maxAge"))))))
                .then(literal("charge")
                        .executes(CrimsonVowCommand::charge))
                .then(literal("ready")
                        .executes(CrimsonVowCommand::ready)));
    }

    // ══════════════════════════════════════════════════════════════
    //  /crimsonvow field
    // ══════════════════════════════════════════════════════════════

    /**
     * 在<b>视线所指的地面</b>上直接展开法阵。
     *
     * <p>选点规则与蓄满时那一套完全一致（见 {@code CrimsonVowChargeEntity#release}）——
     * 指令只是把"蓄力 3 秒"这一步跳过去，不能走另一条选点逻辑，
     * 否则"指令里好好的、实战里掉进地里"这种事迟早会发生。</p>
     */
    private static int field(CommandContext<CommandSourceStack> ctx, float radius, int maxAge) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }

        // 与实战同一条射线：眼睛 → 视线方向，先打方块，打不到就落到射线末端。
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(CrimsonVowChargeEntity.AIM_RANGE));
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(
                new net.minecraft.world.level.ClipContext(eye, end,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));

        Vec3 center;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            center = new Vec3(hit.getLocation().x, hit.getBlockPos().getY() + 1.02, hit.getLocation().z);
        } else {
            center = new Vec3(end.x, end.y, end.z);
        }

        CrimsonVowFieldEntity field = CrimsonVowFieldEntity.spawn(
                player.level(), player, center, radius, maxAge);

        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "§a绯红法阵已展开 §7id=%d §f半径=%.1f §7持续=%dt（%.1fs）§f落点=(%.1f, %.1f, %.1f)",
                        field.getId(), radius, maxAge, maxAge / 20.0,
                        center.x, center.y, center.z)),
                true);
        return 1;
    }

    // ══════════════════════════════════════════════════════════════
    //  /crimsonvow charge
    // ══════════════════════════════════════════════════════════════

    /**
     * 在脚下起一个蓄力法阵。
     *
     * <p>注意它<b>不会放技能</b>：蓄力实体的推进条件是"主手正举着绯红誓约格挡"
     * （见 {@code CrimsonVowChargeEntity#tick}），没有那个前提它下一 tick 就散了。
     * 所以这条指令的真实用途是"确认渲染有没有问题" —— 举起剑、蹲下右键、
     * 再敲这条指令，法阵会立刻以满蓄力的样子出现。</p>
     */
    private static int charge(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }
        CrimsonVowChargeEntity e = CrimsonVowChargeEntity.spawn(player.level(), player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a蓄力法阵已生成 §7id=" + e.getId()
                        + "（需要主手举剑格挡才会继续推进）"),
                true);
        return 1;
    }

    // ══════════════════════════════════════════════════════════════
    //  /crimsonvow ready
    // ══════════════════════════════════════════════════════════════

    /** 清掉起誓冷却，省得调试时每次都要等 30 秒。 */
    private static int ready(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }
        CrimsonVow.clearSkillCooldown(player);
        ctx.getSource().sendSuccess(() -> Component.literal("§a起誓冷却已清除"), false);
        return 1;
    }
}
