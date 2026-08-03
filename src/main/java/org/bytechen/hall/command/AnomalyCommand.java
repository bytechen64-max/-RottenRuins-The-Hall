package org.bytechen.hall.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.hall.api.anomaly.AnomalyType;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;

import java.util.Arrays;
import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 异常测试指令 —— /anomaly
 *
 * <pre>
 * /anomaly add &lt;type&gt; &lt;amount&gt;   — 增加异常进度（可带 target）
 * /anomaly set &lt;type&gt; &lt;value&gt;    — 直接设置异常进度
 * /anomaly get [type]               — 查询异常进度
 * /anomaly clear [type]             — 清除异常进度
 * /anomaly trigger &lt;type&gt;          — 强制执行触发
 * /anomaly adapt &lt;type&gt;             — 给予适应 Buff（15s）
 * </pre>
 */
public final class AnomalyCommand {

    private AnomalyCommand() {}

    /** 带建议的类型参数节点（heat / cold / acid） */
    private static RequiredArgumentBuilder<CommandSourceStack, String> typeArg() {
        return argument("type", StringArgumentType.word())
                .suggests((ctx, builder) ->
                        SharedSuggestionProvider.suggest(
                                Arrays.stream(AnomalyType.values()).map(AnomalyType::getId), builder));
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root = literal("anomaly")
                .requires(src -> src.hasPermission(2));

        // ── add ──
        var addTargetAmount = argument("target", EntityArgument.players())
                .then(typeArg()
                        .then(argument("amount", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> addProgress(
                                        ctx, EntityArgument.getPlayers(ctx, "target"),
                                        parseType(ctx, "type"),
                                        IntegerArgumentType.getInteger(ctx, "amount")))));

        var addSelfAmount = typeArg()
                .then(argument("amount", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> addProgressSelf(
                                ctx, parseType(ctx, "type"),
                                IntegerArgumentType.getInteger(ctx, "amount"))));

        root.then(literal("add").then(addTargetAmount).then(addSelfAmount));

        // ── set ──
        var setTargetValue = argument("target", EntityArgument.players())
                .then(typeArg()
                        .then(argument("value", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> setProgress(
                                        ctx, EntityArgument.getPlayers(ctx, "target"),
                                        parseType(ctx, "type"),
                                        IntegerArgumentType.getInteger(ctx, "value")))));

        var setSelfValue = typeArg()
                .then(argument("value", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> setProgressSelf(
                                ctx, parseType(ctx, "type"),
                                IntegerArgumentType.getInteger(ctx, "value"))));

        root.then(literal("set").then(setTargetValue).then(setSelfValue));

        // ── get ──
        var getTarget = argument("target", EntityArgument.player())
                .executes(ctx -> getAll(ctx, EntityArgument.getPlayer(ctx, "target")))
                .then(typeArg()
                        .executes(ctx -> getProgress(
                                ctx, EntityArgument.getPlayer(ctx, "target"),
                                parseType(ctx, "type"))));

        var getSelf = literal("get")
                .executes(AnomalyCommand::getAllSelf)
                .then(getTarget)
                .then(typeArg()
                        .executes(ctx -> getProgressSelf(ctx, parseType(ctx, "type"))));
        root.then(getSelf);

        // ── clear ──
        var clearTarget = argument("target", EntityArgument.players())
                .executes(ctx -> clearAll(ctx, EntityArgument.getPlayers(ctx, "target")))
                .then(typeArg()
                        .executes(ctx -> clearType(
                                ctx, EntityArgument.getPlayers(ctx, "target"),
                                parseType(ctx, "type"))));

        var clearSelf = literal("clear")
                .executes(AnomalyCommand::clearAllSelf)
                .then(clearTarget)
                .then(typeArg()
                        .executes(ctx -> clearTypeSelf(ctx, parseType(ctx, "type"))));
        root.then(clearSelf);

        // ── trigger ──
        var triggerTarget = argument("target", EntityArgument.players())
                .then(typeArg()
                        .executes(ctx -> forceTrigger(
                                ctx, EntityArgument.getPlayers(ctx, "target"),
                                parseType(ctx, "type"))));

        var triggerSelf = literal("trigger")
                .then(triggerTarget)
                .then(typeArg()
                        .executes(ctx -> forceTriggerSelf(ctx, parseType(ctx, "type"))));
        root.then(triggerSelf);

        // ── adapt ──
        root.then(literal("adapt")
                .then(typeArg()
                        .executes(ctx -> giveAdaptSelf(ctx, parseType(ctx, "type")))));

        dispatcher.register(root);
    }

    // ══════════════════════════════════════════════════════ Helper ══════════════════════════════════════════════════════

    @javax.annotation.Nullable
    private static ServerPlayer getSelf(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getPlayer();
    }

    private static AnomalyCapability getCap(ServerPlayer player) {
        return player.getCapability(CapabilityRegistry.ANOMALY_CAP).orElse(null);
    }

    private static String typeName(AnomalyType type) {
        return switch (type) {
            case HEAT -> "§c热异常";
            case COLD -> "§b冷异常";
            case ACID -> "§a纳酸异常";
        };
    }

    /** 从命令参数解析 AnomalyType（忽略大小写） */
    private static AnomalyType parseType(CommandContext<CommandSourceStack> ctx, String key) {
        String raw = StringArgumentType.getString(ctx, key);
        for (AnomalyType t : AnomalyType.values()) {
            if (t.getId().equalsIgnoreCase(raw)) return t;
        }
        throw new IllegalArgumentException("未知异常类型: " + raw + "。可选: heat, cold, acid");
    }

    // ══════════════════════════════════════════════════════ add ════════════════════════════════════════════════════════

    private static int addProgress(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                    AnomalyType type, int amount) {
        for (ServerPlayer p : targets) {
            AnomalyCapability cap = getCap(p);
            if (cap == null) continue;
            boolean ok = cap.addProgress(p, type, amount);
            if (ok) {
                ctx.getSource().sendSuccess(
                        () -> Component.literal("§e" + p.getName().getString() + " §7的" + typeName(type) + info(type, cap)),
                        true);
            }
        }
        return targets.size();
    }

    private static int addProgressSelf(CommandContext<CommandSourceStack> ctx, AnomalyType type, int amount) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        boolean ok = cap.addProgress(self, type, amount);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("§c无法叠加——你拥有适应 Buff"));
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a已增加 " + typeName(type) + info(type, cap)), true);
        return 1;
    }

    // ══════════════════════════════════════════════════════ set ════════════════════════════════════════════════════════

    private static int setProgress(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                    AnomalyType type, int value) {
        for (ServerPlayer p : targets) {
            AnomalyCapability cap = getCap(p);
            if (cap == null) continue;
            cap.setProgress(type, value);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§e" + p.getName().getString() + " §7的" + typeName(type) + info(type, cap)),
                    true);
        }
        return targets.size();
    }

    private static int setProgressSelf(CommandContext<CommandSourceStack> ctx, AnomalyType type, int value) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        cap.setProgress(type, value);
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a已设置 " + typeName(type) + info(type, cap)), true);
        return 1;
    }

    // ══════════════════════════════════════════════════════ get ═══════════════════════════════════════════════════════

    private static int getAllSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        ctx.getSource().sendSuccess(() -> formatAll(self, cap), false);
        return 1;
    }

    private static int getAll(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        AnomalyCapability cap = getCap(target);
        if (cap == null) return 0;
        ctx.getSource().sendSuccess(() -> formatAll(target, cap), false);
        return 1;
    }

    private static int getProgress(CommandContext<CommandSourceStack> ctx, ServerPlayer target, AnomalyType type) {
        AnomalyCapability cap = getCap(target);
        if (cap == null) return 0;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§e" + target.getName().getString() + " §7的" + typeName(type) + info(type, cap)),
                false);
        return 1;
    }

    private static int getProgressSelf(CommandContext<CommandSourceStack> ctx, AnomalyType type) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        ctx.getSource().sendSuccess(
                () -> Component.literal(typeName(type) + info(type, cap)), false);
        return 1;
    }

    // ══════════════════════════════════════════════════════ clear ═════════════════════════════════════════════════════

    private static int clearAllSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        cap.clearAll();
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除所有异常进度"), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            AnomalyCapability cap = getCap(p);
            if (cap != null) cap.clearAll();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除 " + targets.size() + " 个目标的所有异常进度"), true);
        return targets.size();
    }

    private static int clearType(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                  AnomalyType type) {
        for (ServerPlayer p : targets) {
            AnomalyCapability cap = getCap(p);
            if (cap != null) cap.setProgress(type, 0);
        }
        String name = typeName(type);
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除 " + targets.size() + " 个目标的" + name + "§a进度"), true);
        return targets.size();
    }

    private static int clearTypeSelf(CommandContext<CommandSourceStack> ctx, AnomalyType type) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        cap.setProgress(type, 0);
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除" + typeName(type) + "§a进度"), true);
        return 1;
    }

    // ══════════════════════════════════════════════════════ trigger ═══════════════════════════════════════════════════

    private static int forceTrigger(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                     AnomalyType type) {
        for (ServerPlayer p : targets) {
            AnomalyCapability cap = getCap(p);
            if (cap == null) continue;
            cap.trigger(p, type);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§e已强制触发 " + p.getName().getString() + " §e的" + typeName(type) + "§e§o（进度清零 + 适应 Buff 15s）"),
                    true);
        }
        return targets.size();
    }

    private static int forceTriggerSelf(CommandContext<CommandSourceStack> ctx, AnomalyType type) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        AnomalyCapability cap = getCap(self);
        if (cap == null) return 0;
        cap.trigger(self, type);
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a已强制触发" + typeName(type) + "§a§o（进度清零 + 适应 Buff 15s）"), true);
        return 1;
    }

    // ══════════════════════════════════════════════════════ adapt ════════════════════════════════════════════════════

    private static int giveAdaptSelf(CommandContext<CommandSourceStack> ctx, AnomalyType type) {
        ServerPlayer self = getSelf(ctx);
        if (self == null) return 0;
        self.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                type.getAdaptationEffect(), 300, 0, false, true, true));
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a已给予" + typeName(type) + "适应 Buff§a（15s）"), true);
        return 1;
    }

    // ══════════════════════════════════════════════════════ format ════════════════════════════════════════════════════

    private static String info(AnomalyType type, AnomalyCapability cap) {
        return "§7 进度: §f" + cap.getProgress(type) + "§7/" + type.getMaxProgress();
    }

    private static Component formatAll(ServerPlayer target, AnomalyCapability cap) {
        StringBuilder sb = new StringBuilder("§6═══ §e" + target.getName().getString() + " 异常状态 §6═══\n");
        for (AnomalyType t : AnomalyType.values()) {
            int p = cap.getProgress(t);
            String bar = progressBar(p, t.getMaxProgress());
            sb.append(typeName(t)).append(" ").append(bar).append(" §7").append(p).append("/").append(t.getMaxProgress()).append("\n");
        }
        if (!cap.hasAnyAnomaly()) {
            sb.append("§7无异常状态\n");
        }
        // 显示是否有适应
        sb.append("§8──────────────\n");
        for (AnomalyType t : AnomalyType.values()) {
            boolean adapt = target.hasEffect(t.getAdaptationEffect());
            if (adapt) {
                sb.append(typeName(t)).append("§8: ");
                sb.append("§d适应 ");
                sb.append("\n");
            }
        }
        return Component.literal(sb.toString().trim());
    }

    /** 简单的进度条字符串 */
    private static String progressBar(int value, int max) {
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < max; i++) {
            if (i < value) {
                bar.append("§e▉");
            } else {
                bar.append("§7▉");
            }
        }
        bar.append("§8]");
        return bar.toString();
    }
}
