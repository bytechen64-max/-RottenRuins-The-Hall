package org.bytechen.hall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.hall.overworld.registry.capability.threat.ThreatHelper;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 威胁点数指令 —— /hall threat
 *
 * <pre>
 * /hall threat get [targets]        — 查询威胁点数（默认查自己）
 * /hall threat set &lt;targets&gt; &lt;值&gt;  — 直接设置
 * /hall threat add &lt;targets&gt; &lt;值&gt;  — 增减（可为负）
 * /hall threat reset [targets]      — 清零（等同玩家死亡时的重置）
 * </pre>
 *
 * 威胁点数规则：初始 0；非王庭生物击杀王庭生物后 +「被击杀者最大生命值 / 5」；
 * 玩家威胁点数低于 {@link ThreatHelper#TARGET_THREAT_THRESHOLD} 时王庭生物不会主动索敌；
 * 玩家死亡重置为 0。
 */
public final class ThreatCommand {

    private ThreatCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("hall")
                .then(literal("threat")
                        .requires(src -> src.hasPermission(2))

                        // /hall threat get [targets]
                        .then(literal("get")
                                .executes(ctx -> query(ctx, ctx.getSource().getEntity()))
                                .then(argument("targets", EntityArgument.entities())
                                        .executes(ctx -> {
                                            int count = 0;
                                            for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
                                                query(ctx, target);
                                                count++;
                                            }
                                            return count;
                                        })))

                        // /hall threat set <targets> <value>
                        .then(literal("set")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))

                        // /hall threat add <targets> <value>
                        .then(literal("add")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))

                        // /hall threat reset [targets]
                        .then(literal("reset")
                                .executes(ctx -> reset(ctx, ctx.getSource().getEntity()))
                                .then(argument("targets", EntityArgument.entities())
                                        .executes(ctx -> {
                                            int count = 0;
                                            for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
                                                ThreatHelper.resetThreat(target);
                                                feedback(ctx, name(target) + " 的威胁点数已重置为 0");
                                                count++;
                                            }
                                            return count;
                                        })))));
    }

    private static int query(CommandContext<CommandSourceStack> ctx, Entity target) {
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("请指定一个目标，或由实体执行该指令"));
            return 0;
        }
        int threat = ThreatHelper.getThreat(target);
        boolean targetedByHall = threat >= ThreatHelper.TARGET_THREAT_THRESHOLD;
        feedback(ctx, name(target) + " 的威胁点数：" + threat
                + (targetedByHall ? "（王庭生物会主动索敌）" : "（低于阈值，王庭生物不会主动索敌）"));
        return threat;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        int count = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            ThreatHelper.setThreat(target, value);
            feedback(ctx, name(target) + " 的威胁点数已设为 " + value);
            count++;
        }
        return count;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, int delta) throws CommandSyntaxException {
        int count = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            int now = ThreatHelper.addThreat(target, delta);
            feedback(ctx, name(target) + " 的威胁点数：" + now + "（变化 " + (delta >= 0 ? "+" : "") + delta + "）");
            count++;
        }
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Entity target) {
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("请指定一个目标，或由实体执行该指令"));
            return 0;
        }
        ThreatHelper.resetThreat(target);
        feedback(ctx, name(target) + " 的威胁点数已重置为 0");
        return 1;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.AQUA), false);
    }

    private static String name(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getName().getString();
        }
        return entity.getType().getDescription().getString();
    }
}
