package org.bytechen.rottenruins.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.text.TextEffectIds;
import org.bytechen.rottenruins.api.text.TextEffectText;
import org.bytechen.rottenruins.client.gui.TextEffectPreviewScreen;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectSettings;
import org.bytechen.rottenruins.client.rend.text.effect.TextEffectStats;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * § 文本特效的客户端调试命令 {@code /rrsfx}（screen / chat / on|off / stats / reset / help）。
 * 特效是纯客户端渲染，所以走 {@link RegisterClientCommandsEvent}，不为纯客户端动作新增协议字段。
 * {@code &} 只在 chat 子命令里逐字符换成 {@code §}（原版输入框会过滤 §），不是全局支持。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TextEffectClientCommands {

    /** 统一前缀，方便在一屏聊天里认出哪些是本命令的输出。 */
    private static final String PREFIX = "§8[§dTextFX§8] ";

    /** {@code /rrsfx help} 正文；末行是活样例 —— 引擎生效时显示为渐变/抖动字，否则露出 {@code rrs:grad:…} 原文。 */
    private static final String HELP = PREFIX + "§7分节符文本特效 · 客户端命令\n"
            + "§8/rrsfx screen §7— §f打开预览屏（8 行样本，实时动画）\n"
            + "§8/rrsfx chat §7<原文> §7— §f把原文发到本地聊天（用 & 代替分节符输入标记）\n"
            + "§8例：§7/rrsfx chat &[rrs:glitch]崩坏&[/] §8（& 即分节符）\n"
            + "§8/rrsfx on§7 / §8off §7— §f运行时开关（只改内存，重启复原）\n"
            + "§8/rrsfx stats §7— §f解析 / 缓存命中 / overlay / 失败计数\n"
            + "§8/rrsfx reset §7— §f清零上面的计数\n"
            + "§8/rrsfx help §7— §f本帮助\n"
            + "§8效果示例：§f"
            + TextEffectText.gradient("腐化的低语", TextEffectIds.DEFAULT_GRAD_FROM, TextEffectIds.DEFAULT_GRAD_TO)
            + "§8 · §f"
            + TextEffectText.shake("不可直视", 1.0F, 2.0F);

    private TextEffectClientCommands() {}

    /** 挂到客户端命令分发器（Forge 总线，仅逻辑客户端触发）；子命令一律 {@code requires(true)}，别照抄服务端的 hasPermission(2)。 */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 排障用：客户端命令不经过服务端，日志是"注册事件真的发到了本类"的唯一证据
        HallMod.LOGGER.info("[TextFX] 客户端命令 /rrsfx 已注册（screen/chat/on/off/stats/reset/help）");

        var screen = literal("screen")
                .requires(source -> true)
                .executes(TextEffectClientCommands::openPreviewScreen);

        var chat = literal("chat")
                .requires(source -> true)
                .then(argument("text", StringArgumentType.greedyString())
                        .executes(TextEffectClientCommands::sendRawToChat));

        var on = literal("on")
                .requires(source -> true)
                .executes(ctx -> setRuntimeEnabled(ctx, true));

        var off = literal("off")
                .requires(source -> true)
                .executes(ctx -> setRuntimeEnabled(ctx, false));

        var stats = literal("stats")
                .requires(source -> true)
                .executes(TextEffectClientCommands::printStats);

        var reset = literal("reset")
                .requires(source -> true)
                .executes(TextEffectClientCommands::resetStats);

        var help = literal("help")
                .requires(source -> true)
                .executes(TextEffectClientCommands::sendHelp);

        // 根节点单独 executes 一次 help：只敲 /rrsfx 时给用法，比报"缺少参数"友好。
        dispatcher.register(literal("rrsfx")
                .requires(source -> true)
                .executes(TextEffectClientCommands::sendHelp)
                .then(screen)
                .then(chat)
                .then(on)
                .then(off)
                .then(stats)
                .then(reset)
                .then(help));
    }

    /** 直接 {@code setScreen} 是安全的：Forge 补丁后的 {@code ChatScreen.handleChatInput} 不会再把新界面关掉。 */
    private static int openPreviewScreen(CommandContext<CommandSourceStack> ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            reply(ctx, Component.literal(PREFIX + "§c当前没有本地玩家 —— 预览屏只能在游戏内打开"));
            return 0;
        }
        minecraft.setScreen(new TextEffectPreviewScreen());
        return 1;
    }

    /** 把标记原样送进本地聊天栏：渲染成颜色/位移说明引擎生效，露出 {@code rrs:grad:…} 原文说明钩子没接管。 */
    private static int sendRawToChat(CommandContext<CommandSourceStack> ctx) {
        String raw = ampersandToSection(StringArgumentType.getString(ctx, "text"));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0;
        minecraft.gui.getChat().addMessage(Component.literal(raw));
        return 1;
    }

    /**
     * 把 {@code &} 逐字符换成 {@code §}（原版输入框过滤分节符但允许 &，这是实机手敲带标记文本的唯一入口）；
     * 只在本命令的输入层转换，不改全局聊天过滤、不引入第二套格式码，其它文本路径仍只认 {@code §}。
     */
    private static String ampersandToSection(String raw) {
        return raw.indexOf('&') < 0 ? raw : raw.replace('&', '§');
    }

    /** {@code /rrsfx on|off} —— 只改内存里的运行时覆盖，不写配置文件（重启回到配置值）。 */
    private static int setRuntimeEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        TextEffectSettings.setRuntimeEnabled(enabled);
        reply(ctx, Component.translatable(enabled
                ? "rotten_ruins.command.rrsfx.enabled"
                : "rotten_ruins.command.rrsfx.disabled"));
        return 1;
    }

    /** {@code /rrsfx stats} —— {@link TextEffectStats#describe()} 自带 § 色码且保证单行，直接发。 */
    private static int printStats(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, Component.literal(TextEffectStats.describe()));
        return 1;
    }

    /** {@code /rrsfx reset} —— 清零计数，方便"从干净状态复现一次再读 stats"。 */
    private static int resetStats(CommandContext<CommandSourceStack> ctx) {
        TextEffectStats.reset();
        reply(ctx, Component.literal(PREFIX + "§a已清零统计（解析 / 缓存命中 / overlay / 失败）"));
        return 1;
    }

    /** {@code /rrsfx help} —— 用法列表。 */
    private static int sendHelp(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, Component.literal(HELP));
        return 1;
    }

    /** 客户端 source 的 {@code sendSuccess} 已被 Forge 重写成直接发本地玩家，不会因"没有权限 2"静默失败。 */
    private static void reply(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
    }
}
