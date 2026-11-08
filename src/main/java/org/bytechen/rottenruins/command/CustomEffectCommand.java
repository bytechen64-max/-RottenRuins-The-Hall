package org.bytechen.rottenruins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectRegistry;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 自定义效果调试命令 —— {@code /rotten_ruins ce ...}
 *
 * <pre>
 * /rotten_ruins ce list <targets>                          —— 列出目标身上所有自定义效果
 * /rotten_ruins ce add &lt;targets&gt; &lt;id&gt; [duration] [amp]     —— 施加（duration 省略 = 效果默认值）
 * /rotten_ruins ce remove &lt;targets&gt; &lt;id&gt;                  —— 移除单个
 * /rotten_ruins ce clear &lt;targets&gt;                        —— 清空全部
 * /rotten_ruins ce effects                                 —— 列出注册表里所有效果 id
 * </pre>
 *
 * <p>存在的意义有两个：一是游戏内直接验证这套系统在跑；
 * 二是给"怎么写效果"一个可跑的示例 —— 每条子命令最终都只是一次
 * {@link CustomEffects} 调用，和自己写在业务代码里完全一样。</p>
 */
public final class CustomEffectCommand {

    private CustomEffectCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal(HallMod.MODID)
                .then(literal("ce")
                        .requires(src -> src.hasPermission(2))

                        // 列出注册表
                        .then(literal("effects").executes(ctx -> {
                            StringBuilder sb = new StringBuilder("已注册自定义效果：");
                            for (CustomEffectBase effect : CustomEffectRegistry.all()) {
                                sb.append("\n  ").append(effect.getId())
                                        .append(" [").append(effect.getCategory()).append(']');
                            }
                            send(ctx, sb.toString(), ChatFormatting.AQUA);
                            return CustomEffectRegistry.size();
                        }))

                        // 查看目标身上的效果
                        .then(literal("list")
                                .executes(ctx -> list(ctx, ctx.getSource().getEntityOrException()))
                                .then(argument("targets", EntityArgument.entities())
                                        .executes(ctx -> list(ctx,
                                                EntityArgument.getEntity(ctx, "targets")))))

                        // 施加
                        .then(literal("add")
                                .then(argument("targets", EntityArgument.entity())
                                        .then(argument("id", StringArgumentType.string())
                                                .executes(ctx -> add(ctx, ctx, null, 0))
                                                .then(argument("duration", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> add(ctx, ctx,
                                                                IntegerArgumentType.getInteger(ctx, "duration"), 0))
                                                        .then(argument("amplifier",
                                                                        IntegerArgumentType.integer(0))
                                                                .executes(ctx -> add(ctx, ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "duration"),
                                                                        IntegerArgumentType.getInteger(ctx, "amplifier")))))))) 

                        // 移除单个
                        .then(literal("remove")
                                .then(argument("targets", EntityArgument.entity())
                                        .then(argument("id", StringArgumentType.string())
                                                .executes(ctx -> remove(ctx,
                                                        EntityArgument.getEntity(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "id"))))))

                        // 清空
                        .then(literal("clear")
                                .then(argument("targets", EntityArgument.entities())
                                        .executes(ctx -> {
                                            Entity target = EntityArgument.getEntity(ctx, "targets");
                                            if (!(target instanceof LivingEntity living)) {
                                                send(ctx, "目标不是生物", ChatFormatting.RED);
                                                return 0;
                                            }
                                            int before = CustomEffects.getActiveEffects(living).size();
                                            CustomEffects.removeAllEffects(living);
                                            send(ctx, "已清空 " + before + " 个自定义效果", ChatFormatting.GREEN);
                                            return before;
                                        })))));
    }

    // ══════════════════════════════════════════════════════════════

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                            Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            send(ctx, "目标不是生物", ChatFormatting.RED);
            return 0;
        }
        Collection<CustomEffectInstance> effects = CustomEffects.getActiveEffects(living);
        if (effects.isEmpty()) {
            send(ctx, living.getName().getString() + " 身上没有自定义效果", ChatFormatting.GRAY);
            return 0;
        }
        StringBuilder sb = new StringBuilder(living.getName().getString() + " 的自定义效果：");
        for (CustomEffectInstance instance : effects) {
            sb.append("\n  ").append(instance.getEffect().getId())
                    .append("  剩余 ").append(instance.getDuration()).append(" tick")
                    .append("  层数 ").append(instance.getAmplifier() + 1);
        }
        send(ctx, sb.toString(), ChatFormatting.AQUA);
        return effects.size();
    }

    private static int add(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                           com.mojang.brigadier.context.CommandContext<CommandSourceStack> unused,
                           Integer duration, int amplifier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "targets");
        if (!(entity instanceof LivingEntity living)) {
            send(ctx, "目标不是生物", ChatFormatting.RED);
            return 0;
        }
        CustomEffectBase effect = resolve(StringArgumentType.getString(ctx, "id"));
        if (effect == null) {
            send(ctx, "找不到这个效果（用 /" + HallMod.MODID + " ce effects 看全部 id）",
                    ChatFormatting.RED);
            return 0;
        }
        int ticks = duration == null ? effect.getDefaultDuration() : duration;
        boolean ok = CustomEffects.addEffect(living, effect, ticks, amplifier);
        send(ctx, (ok ? "已施加 " : "被拒绝（canApply / 已存在同层同长）：")
                        + effect.getId() + " → " + living.getName().getString()
                        + "（" + ticks + " tick，层数 " + (amplifier + 1) + "）",
                ok ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return ok ? 1 : 0;
    }

    private static int remove(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                              Entity entity, String rawId) {        if (!(entity instanceof LivingEntity living)) {
            send(ctx, "目标不是生物", ChatFormatting.RED);
            return 0;
        }
        CustomEffectBase effect = resolve(rawId);
        if (effect == null) {
            send(ctx, "找不到这个效果", ChatFormatting.RED);
            return 0;
        }
        boolean ok = CustomEffects.removeEffect(living, effect);
        send(ctx, ok ? "已移除 " + effect.getId() : "目标身上没有 " + effect.getId(),
                ok ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return ok ? 1 : 0;
    }

    /** 支持 {@code rotten_ruins:frostbite} 与简写 {@code frostbite}。 */
    private static CustomEffectBase resolve(String rawId) {
        CustomEffectBase effect = CustomEffectRegistry.get(ResourceLocation.tryParse(rawId));
        if (effect != null) {
            return effect;
        }
        return CustomEffectRegistry.get(new ResourceLocation(HallMod.MODID, rawId));
    }

    private static void send(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                             String message, ChatFormatting color) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(color), false);
    }
}
