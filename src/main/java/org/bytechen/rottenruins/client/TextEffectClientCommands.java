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
 * § 文本特效的客户端调试命令 —— {@code /rrsfx}。
 *
 * <pre>
 * /rrsfx screen              打开文本特效预览屏（8 行样本，实时动画）
 * /rrsfx chat &lt;原文&gt;        把原文逐字发到本地聊天栏（用 &amp; 代替 § 输入标记）
 * /rrsfx on | /rrsfx off     运行时开关（只改内存，不写配置，重启复原）
 * /rrsfx stats               解析 / 缓存命中 / overlay / 失败计数
 * /rrsfx reset               清零上面的计数
 * /rrsfx help                本帮助
 * </pre>
 *
 * <h3>为什么是客户端命令，而不是服务端命令 + S2C 包</h3>
 * <p>文本特效是<b>纯客户端渲染</b>：效果标记只是文本里的普通字符，服务端既不需要认识它、
 * 也不需要参与渲染（见 {@link TextEffectIds} 的类注释）。若走服务端命令，就要再多一条
 * "请客户端开预览屏"的网络包，把纯客户端动作绕服务端一圈；而
 * {@link RegisterClientCommandsEvent} 是 Forge 只在本机客户端发出的事件，注册即可用，
 * 不新增任何协议字段。项目此前没有客户端命令，这是第一处 —— 以后调试纯客户端功能都可以照这个形状写。</p>
 *
 * <h3>为什么反馈不自己做 {@code Minecraft.gui.getChat().addMessage}</h3>
 * <p>客户端命令拿到的 source 是 Forge 的 {@code ClientCommandSourceStack}，它把
 * {@code sendSuccess} 重写成"直接发给本地玩家"（跳过"只发给有权限的管理员"那套服务端逻辑）。
 * 所以这里可以和服务端命令（{@code command/*.java}）写得完全一样：{@code sendSuccess} +
 * lang 键，颜色/文案归 lang 管，命令里不硬编码颜色。</p>
 *
 * <h3>为什么只有 {@code /rrsfx chat} 走 {@code addMessage}</h3>
 * <p>这条子命令的目的就是"把带标记的原文<b>原样</b>丢进聊天栏"，看渲染结果判断引擎有没有接管。
 * {@code Minecraft.gui.getChat().addMessage} 是通往聊天栏最短的一条路（客户端 source 的
 * {@code sendSuccess} 最终也是 {@code displayClientMessage} → 同一个 {@code ChatComponent}），
 * 少一层包装就少一个变量，排障时结论更硬。</p>
 *
 * <h3>{@code &} 代替 {@code §}：只在本命令里，不是全局支持</h3>
 * <p>原版聊天输入框会过滤分节符字符（{@code EditBox.insertText} → {@code SharedConstants.filterText}
 * 去掉 {@code §}），但 {@code &} 可以正常手打。为了让实机测试不用改代码/点击事件就能验证效果，
 * {@code /rrsfx chat} 会把参数里所有 {@code &} <b>逐字符</b>换成 {@code §} 再发进聊天栏
 * （例如 {@code /rrsfx chat &[rrs:glitch]崩坏&[/]}）。
 * <p><b>这只是本调试命令的输入便利</b>：没有改原版聊天过滤、也没有在解析层引入第二套格式码 ——
 * 别的文本路径（lang / datapack / 物品与实体代码里的 {@link TextEffectText}）仍然只认 {@code §}。
 * 以后若有人想"全局支持 {@code &}"，那是另一个决定（会和资源包/其它 mod 的 {@code &} 约定冲突），
 * 不要顺手改这里就当已经支持了。
 * <p>模块作者正常写特效文本的位置仍是 lang / datapack / 物品代码里的 {@link TextEffectText}。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TextEffectClientCommands {

    /** 统一前缀，方便在一屏聊天里认出哪些是本命令的输出。 */
    private static final String PREFIX = "§8[§dTextFX§8] ";

    /**
     * {@code /rrsfx help} 的正文。
     * <p>多行 {@code Component.literal} 在原版聊天里会按 {@code \n} 分成多行
     * （与服务端命令 {@code AnomalyCommand#formatAll} 的写法一致）。最后一行是活的样例：
     * 引擎生效时应显示为渐变/抖动的字，没生效时会露出 {@code rrs:grad:…} 原文 —— 这本身就是判断依据。
     */
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

    // ══════════════════════════════════════════════════════════════
    // 注册
    // ══════════════════════════════════════════════════════════════

    /**
     * 把 {@code /rrsfx} 挂到<b>客户端</b>命令分发器上（Forge 总线，只在逻辑客户端触发）。
     * <p>子命令全部显式 {@code requires(source -> true)}：客户端命令本来只有本地玩家能执行，
     * 写出来是防止以后有人照抄服务端命令时顺手加上 {@code hasPermission(2)}。
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 实机排障用：日志里能确认"命令注册事件真的发到了本类"（客户端命令不经过服务端，没有别的痕迹）。
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

    // ══════════════════════════════════════════════════════════════
    // 子命令
    // ══════════════════════════════════════════════════════════════

    /**
     * {@code /rrsfx screen} —— 打开预览屏。
     * <p>直接 {@code setScreen} 是安全的：原版 {@code ChatScreen.handleChatInput} 在 Forge 里被
     * 打了补丁（返回 {@code minecraft.screen == this}），命令里换了界面之后聊天框<b>不会</b>
     * 再把新界面关掉 —— 否则这条命令一执行就会闪一下消失。
     */
    private static int openPreviewScreen(CommandContext<CommandSourceStack> ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            reply(ctx, Component.literal(PREFIX + "§c当前没有本地玩家 —— 预览屏只能在游戏内打开"));
            return 0;
        }
        minecraft.setScreen(new TextEffectPreviewScreen());
        return 1;
    }

    /**
     * {@code /rrsfx chat <原文>} —— 把原文发到本地聊天栏。
     * <p>先把 {@code &} 换成 {@code §}（原因见 {@link #ampersandToSection(String)}），再过
     * {@code addMessage}。这条命令不经过服务端：标记会原样进入聊天栏的 {@code Component}，
     * 再由客户端 Font/解析层决定怎么渲染。因此：
     * <ul>
     *   <li>标记被渲染成颜色/位移 → 引擎生效；</li>
     *   <li>看到 {@code rrs:grad:…} 之类的字面文本 → 引擎的钩子没接管（或被 {@code /rrsfx off} 关掉）。</li>
     * </ul>
     */
    private static int sendRawToChat(CommandContext<CommandSourceStack> ctx) {
        String raw = ampersandToSection(StringArgumentType.getString(ctx, "text"));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0;
        minecraft.gui.getChat().addMessage(Component.literal(raw));
        return 1;
    }

    /**
     * 把 {@code &} 逐字符换成 {@code §}（U+00A7）。
     *
     * <h3>为什么只在这里转换</h3>
     * <p>原版聊天输入框会过滤分节符（{@code EditBox.insertText} → {@code SharedConstants.filterText}），
     * 但 {@code &} 可以正常手打 —— 这是实机测试能直接"手敲"带标记文本的唯一入口
     * （{@code /rrsfx chat &[rrs:glitch]崩坏&[/]}）。
     * <p>转换刻意只放在<b>本命令的输入层</b>，而不是全局聊天过滤或解析层：
     * 不改原版输入规则、不引入第二套格式码，免得以后有人以为本 mod 全局支持 {@code &}
     * （那样会和资源包/其它 mod 的 {@code &} 约定打架）。其它文本路径仍只认 {@code §}。
     * <p>{@code &&} 直接变成两个 {@code §}：逐字符替换即可，调试命令不设计转义语法 ——
     * 少一层规则就少一处出错可能。
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

    // ══════════════════════════════════════════════════════════════
    // 反馈
    // ══════════════════════════════════════════════════════════════

    /**
     * 往本地聊天栏发一行命令反馈。
     * <p>客户端命令的 source 是 {@code ClientCommandSourceStack}，其 {@code sendSuccess}
     * 已被 Forge 重写成"直接给本地玩家发消息"，所以这里不需要碰 {@code Minecraft.gui}，
     * 也不会因为"没有权限 2"而静默失败。
     */
    private static void reply(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
    }
}
